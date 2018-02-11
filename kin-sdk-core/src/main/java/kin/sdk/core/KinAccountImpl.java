package kin.sdk.core;

import java.math.BigDecimal;
import kin.sdk.core.exception.AccountDeletedException;
import kin.sdk.core.exception.OperationFailedException;
import kin.sdk.core.exception.PassphraseException;
import org.stellar.sdk.KeyPair;


final class KinAccountImpl extends AbstractKinAccount {

    private final ClientWrapper clientWrapper;
    private final Account account;
    private boolean isDeleted = false;

    /**
     * Creates a {@link KinAccount} from existing {@link KeyPair}
     *
     * @param clientWrapper that will be use to call to Kin smart-contract.
     * @param account the existing Account.
     */
    KinAccountImpl(ClientWrapper clientWrapper, Account account) {
        this.account = account;
        this.clientWrapper = clientWrapper;
    }

    @Override
    public String getPublicAddress() {
        if (!isDeleted) {
            return account.getAccountId();
        }
        return null;
    }

    @Override
    public String exportKeyStore(String passphrase, String newPassphrase)
        throws PassphraseException, OperationFailedException {
        checkValidAccount();
        return null;
    }

    @Override
    public TransactionId sendTransactionSync(String publicAddress, String passphrase, BigDecimal amount)
        throws OperationFailedException, PassphraseException {
        checkValidAccount();
        return clientWrapper.sendTransaction(account, passphrase, publicAddress, amount);
    }

    @Override
    public Balance getBalanceSync() throws OperationFailedException {
        checkValidAccount();
        return clientWrapper.getBalance(account);
    }

    @Override
    public void activateSync(String passphrase) throws OperationFailedException {
        checkValidAccount();
        clientWrapper.activateAccount(account, passphrase);
    }

    void markAsDeleted() {
        isDeleted = true;
    }

    private void checkValidAccount() throws AccountDeletedException {
        if (isDeleted) {
            throw new AccountDeletedException();
        }
    }

}
