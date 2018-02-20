package kin.core;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import kin.core.exception.CreateAccountException;
import kin.core.exception.DeleteAccountException;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;

public class KinClient {

    private static final String STORE_NAME = "KinKeyStore";
    private final ServiceProvider serviceProvider;
    private final KeyStore keyStore;
    private final TransactionSender transactionSender;
    private final AccountActivator accountActivator;
    private final BalanceQuery balanceQuery;
    @NonNull
    private final List<KinAccountImpl> kinAccounts = new ArrayList<>(1);

    /**
     * KinClient is an account manager for a {@link KinAccount}.
     *
     * @param context the android application context
     * @param provider the service provider provides blockchain network parameters
     */
    public KinClient(@NonNull Context context, @NonNull ServiceProvider provider) {
        this.serviceProvider = provider;
        Server server = initServer();
        keyStore = initKeyStore(context.getApplicationContext());
        transactionSender = new TransactionSender(server, keyStore, provider.getKinAsset());
        accountActivator = new AccountActivator(server, keyStore, provider.getKinAsset());
        balanceQuery = new BalanceQuery(server, provider.getKinAsset());
        loadAccounts();
    }

    @VisibleForTesting
    KinClient(ServiceProvider serviceProvider, KeyStore keyStore, TransactionSender transactionSender,
        AccountActivator accountActivator, BalanceQuery balanceQuery) {
        this.serviceProvider = serviceProvider;
        this.keyStore = keyStore;
        this.transactionSender = transactionSender;
        this.accountActivator = accountActivator;
        this.balanceQuery = balanceQuery;
        loadAccounts();
    }

    private Server initServer() {
        if (serviceProvider.isMainNet()) {
            Network.usePublicNetwork();
        } else {
            Network.useTestNetwork();
        }
        return new Server(serviceProvider.getProviderUrl());
    }

    private KeyStore initKeyStore(Context context) {
        SharedPrefStore store = new SharedPrefStore(context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE));
        Encryptor encryptor = EncryptorFactory.create(context, store);
        return new KeyStoreImpl(store, encryptor);
    }

    private void loadAccounts() {
        List<Account> accounts = null;
        try {
            accounts = keyStore.loadAccounts();
        } catch (LoadAccountException e) {
            e.printStackTrace();
        }
        if (accounts != null && !accounts.isEmpty()) {
            for (Account account : accounts) {
                kinAccounts.add(new KinAccountImpl(account, transactionSender, accountActivator, balanceQuery));
            }
        }
    }

    /**
     * Creates and adds an account.
     * <p>Once created, the account information will be stored securely on the device and can
     * be accessed again via the {@link #getAccount(int)} method.</p>
     *
     * @param passphrase a passphrase provided by the user that will be used to store the account private key securely.
     * @return {@link KinAccount} the account created store the key.
     */
    public @NonNull
    KinAccount addAccount(@NonNull String passphrase) throws CreateAccountException {
        Account account = keyStore.newAccount();
        KinAccountImpl newAccount = new KinAccountImpl(account, transactionSender, accountActivator, balanceQuery);
        kinAccounts.add(newAccount);
        return newAccount;
    }

    /**
     * Returns an account at input index.
     *
     * @return the account at the input index or null if there is no such account
     */
    public KinAccount getAccount(int index) {
        if (index >= 0 && kinAccounts.size() > index) {
            return kinAccounts.get(index);
        }
        return null;
    }

    /**
     * @return true if there is an existing account
     */
    public boolean hasAccount() {
        return getAccountCount() != 0;
    }

    /**
     * Returns the number of existing accounts
     */
    public int getAccountCount() {
        return kinAccounts.size();
    }

    /**
     * Deletes the account at input index (if it exists)
     *
     * @param passphrase the passphrase used when the account was created
     */
    public void deleteAccount(int index, @NonNull String passphrase) throws DeleteAccountException {
        if (index >= 0 && getAccountCount() > index) {
            keyStore.deleteAccount(index);
            KinAccountImpl removedAccount = kinAccounts.remove(index);
            removedAccount.markAsDeleted();
        }
    }

    /**
     * Deletes all accounts.
     * WARNING - if you don't export your account before deleting it, you will lose all your Kin.
     */
    public void wipeoutAccount() {
        keyStore.clearAllAccounts();
        for (KinAccountImpl kinAccount : kinAccounts) {
            kinAccount.markAsDeleted();
        }
        kinAccounts.clear();
    }

    public ServiceProvider getServiceProvider() {
        return serviceProvider;
    }

}