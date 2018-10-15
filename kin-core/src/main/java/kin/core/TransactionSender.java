package kin.core;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.List;
import kin.core.Environment.KinAsset;
import kin.core.exception.AccountNotActivatedException;
import kin.core.exception.AccountNotFoundException;
import kin.core.exception.InsufficientKinException;
import kin.core.exception.OperationFailedException;
import kin.core.exception.TransactionFailedException;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Memo;
import org.stellar.sdk.PaymentOperation;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.Transaction.Builder;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.HttpResponseException;
import org.stellar.sdk.responses.SubmitTransactionResponse;

class TransactionSender {

    private static final int MEMO_BYTES_LENGTH_LIMIT = 21; //Stellar text memo length limitation(in bytes) is 28 but we add 7 more bytes which includes the appId and some characters.
    private static String APP_ID_VERSION_PREFIX = "1";
    private static final String INSUFFICIENT_KIN_RESULT_CODE = "op_underfunded";

    private final Server server; //horizon server
    private final KinAsset kinAsset;
    private final String appId;

    TransactionSender(Server server, KinAsset kinAsset, String appId) {
        this.server = server;
        this.kinAsset = kinAsset;
        this.appId = appId;
    }

    @NonNull
    TransactionId sendTransaction(@NonNull KeyPair from, @NonNull String publicAddress,
        @NonNull BigDecimal amount)
        throws OperationFailedException {
        return sendTransaction(from, publicAddress, amount, null);
    }

    @NonNull
    TransactionId sendTransaction(@NonNull KeyPair from, @NonNull String publicAddress, @NonNull BigDecimal amount,
        @Nullable String memo)
        throws OperationFailedException {

        checkParams(from, publicAddress, amount, memo);
        memo = addAppIdToMemo(memo);

        KeyPair addressee = generateAddresseeKeyPair(publicAddress);
        verifyAddresseeAccount(addressee);
        AccountResponse sourceAccount = loadSourceAccount(from);
        Transaction transaction = buildTransaction(from, amount, addressee, sourceAccount, memo);
        return sendTransaction(transaction);
    }

    @NonNull
    private String addAppIdToMemo(@Nullable String memo) {
        if (memo == null) {
            memo = "";
        } else {
            memo = memo.trim(); // remove leading and trailing whitespaces
        }
        StringBuilder sb = new StringBuilder();
        sb.append(APP_ID_VERSION_PREFIX)
          .append("-")
          .append(appId)
          .append("-")
          .append(memo);
        return sb.toString();
    }

    private void checkParams(@NonNull KeyPair from, @NonNull String publicAddress, @NonNull BigDecimal amount,
        @Nullable String memo) {
        Utils.checkNotNull(from, "account");
        Utils.checkNotNull(amount, "amount");
        checkAddressNotEmpty(publicAddress);
        checkForNegativeAmount(amount);
        checkMemo(memo);
    }

    @SuppressWarnings("ConstantConditions")
    private void checkAddressNotEmpty(@NonNull String publicAddress) {
        if (publicAddress == null || publicAddress.isEmpty()) {
            throw new IllegalArgumentException("Addressee not valid - public address can't be null or empty");
        }
    }

    private void checkForNegativeAmount(@NonNull BigDecimal amount) {
        if (amount.signum() == -1) {
            throw new IllegalArgumentException("Amount can't be negative");
        }
    }

    private void checkMemo(String memo) {
        try {
            Log.d("TEST", "memo length = " + memo.length() + ", memo byte length = " + memo.getBytes().length + ", memo byte in utf-8 length = " + memo.getBytes("UTF-8").length);
            if (memo != null && memo.getBytes("UTF-8").length > MEMO_BYTES_LENGTH_LIMIT) {
                throw new IllegalArgumentException("Memo cannot be longer that 21 bytes(UTF-8 characters)");
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Memo text have unsupported characters encoding");
        }
    }

    @NonNull
    private KeyPair generateAddresseeKeyPair(@NonNull String publicAddress) throws OperationFailedException {
        try {
            return KeyPair.fromAccountId(publicAddress);
        } catch (Exception e) {
            throw new OperationFailedException("Invalid addressee public address format", e);
        }
    }

    @NonNull
    private Transaction buildTransaction(@NonNull KeyPair from, @NonNull BigDecimal amount, KeyPair addressee,
        AccountResponse sourceAccount, @Nullable String memo) {

        Builder transactionBuilder = new Builder(sourceAccount)
            .addOperation(
                new PaymentOperation.Builder(addressee, kinAsset.getStellarAsset(), amount.toString()).build());
        if (memo != null) {
            transactionBuilder.addMemo(Memo.text(memo));
        }
        Transaction transaction = transactionBuilder.build();
        transaction.sign(from);
        return transaction;
    }

    private void verifyAddresseeAccount(KeyPair addressee) throws OperationFailedException {
        AccountResponse addresseeAccount;
        addresseeAccount = loadAccount(addressee);
        checkKinTrust(addresseeAccount);
    }

    private AccountResponse loadAccount(@NonNull KeyPair from) throws OperationFailedException {
        AccountResponse sourceAccount;
        try {
            sourceAccount = server.accounts().account(from);
        } catch (HttpResponseException httpError) {
            if (httpError.getStatusCode() == 404) {
                throw new AccountNotFoundException(from.getAccountId());
            } else {
                throw new OperationFailedException(httpError);
            }
        } catch (IOException e) {
            throw new OperationFailedException(e);
        }
        if (sourceAccount == null) {
            throw new OperationFailedException("can't retrieve data for account " + from.getAccountId());
        }
        return sourceAccount;
    }

    private void checkKinTrust(AccountResponse accountResponse) throws AccountNotActivatedException {
        if (!kinAsset.hasKinTrust(accountResponse)) {
            throw new AccountNotActivatedException(accountResponse.getKeypair().getAccountId());
        }
    }

    private AccountResponse loadSourceAccount(@NonNull KeyPair from) throws OperationFailedException {
        AccountResponse sourceAccount;
        sourceAccount = loadAccount(from);
        checkKinTrust(sourceAccount);
        return sourceAccount;
    }

    @NonNull
    private TransactionId sendTransaction(Transaction transaction) throws OperationFailedException {
        try {
            SubmitTransactionResponse response = server.submitTransaction(transaction);
            if (response == null) {
                throw new OperationFailedException("can't get transaction response");
            }
            if (response.isSuccess()) {
                return new TransactionIdImpl(response.getHash());
            } else {
                return createFailureException(response);
            }
        } catch (IOException e) {
            throw new OperationFailedException(e);
        }
    }

    private TransactionId createFailureException(SubmitTransactionResponse response)
        throws TransactionFailedException, InsufficientKinException {
        TransactionFailedException transactionException = Utils.createTransactionException(response);
        if (isInsufficientKinException(transactionException)) {
            throw new InsufficientKinException();
        } else {
            throw transactionException;
        }
    }

    private boolean isInsufficientKinException(TransactionFailedException transactionException) {
        List<String> resultCodes = transactionException.getOperationsResultCodes();
        return resultCodes != null && resultCodes.size() > 0 && INSUFFICIENT_KIN_RESULT_CODE.equals(resultCodes.get(0));
    }
}
