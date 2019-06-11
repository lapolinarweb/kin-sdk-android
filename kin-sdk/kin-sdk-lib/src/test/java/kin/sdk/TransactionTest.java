package kin.sdk;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import kin.base.Account;
import kin.base.CreateAccountOperation;
import kin.base.KeyPair;
import kin.base.MemoText;
import kin.base.Network;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23, manifest = Config.NONE)
public class TransactionTest {

    private static final String ACCOUNT_ID_FROM = "GDJVKIY3UQKMTQZHXR36ZQUNFYO45XLM6IHWO6TYQ53M5KEXBNMJYWVR";
    private static final String SECRET_SEED_FROM = "SB73L5FFTZMN6FHTOOWYEBVFTLUWQEWBLSCI4WLZADRJWENDBYL6QD6P";

    private MockWebServer mockWebServer;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mockServer();
        Network.useTestNetwork();
    }

    private RawTransaction createTransaction() {
        KeyPair source = KeyPair.fromSecretSeed(SECRET_SEED_FROM);
        KeyPair destination = KeyPair.fromAccountId(ACCOUNT_ID_FROM);
        long sequenceNumber = 2908908335136768L;
        Account account = new Account(source, sequenceNumber);
        RawTransaction transaction = new TransactionBuilder(source, account, "test")
            .addOperation(new CreateAccountOperation.Builder(destination, "2000").build())
            .setFee(100)
            .build();

        KinAccountImpl mockKinAccountImpl = mock(KinAccountImpl.class);
        when(mockKinAccountImpl.getKeyPair()).thenReturn(source);

        return transaction;
    }

    private void mockServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }


    @Test
    public void getTransactionEnvelope_success() {
        RawTransaction transaction = createTransaction();
        String transactionEnvelope = "AAAAANNVIxukFMnDJ7x37MKNLh3O3WzyD2d6eId2zqiXC1icAAAAZAAKVaMAAAABAAAAAAAAAAEAAAAHMS10ZXN0LQAAAAABAAAAAAAAAAAAAAAA01UjG6QUycMnvHfswo0uHc7dbPIPZ3p4h3bOqJcLWJwAAAAAC+vCAAAAAAAAAAABlwtYnAAAAEA639AzCBE9ROc1WEhKOPRilq4MJsgv+WWVB+EBTndDbUPM3v3FuKAMTVfQZA3amAclenBe04fW5xGBU6dqR3gE";

        assertThat(transaction.transactionEnvelope(), equalTo(transactionEnvelope));
    }

    @Test
    public void decodeTransaction_success() throws Exception {
        String transactionEnvelope = "AAAAANNVIxukFMnDJ7x37MKNLh3O3WzyD2d6eId2zqiXC1icAAAAZAAKVaMAAAABAAAAAAAAAAEAAAAHMS10ZXN0LQAAAAABAAAAAAAAAAAAAAAA01UjG6QUycMnvHfswo0uHc7dbPIPZ3p4h3bOqJcLWJwAAAAAC+vCAAAAAAAAAAABlwtYnAAAAEA639AzCBE9ROc1WEhKOPRilq4MJsgv+WWVB+EBTndDbUPM3v3FuKAMTVfQZA3amAclenBe04fW5xGBU6dqR3gE";
        RawTransaction transaction = RawTransaction.decodeRawTransaction(transactionEnvelope);

        assertThat("GDJVKIY3UQKMTQZHXR36ZQUNFYO45XLM6IHWO6TYQ53M5KEXBNMJYWVR", equalTo(transaction.source()));
        assertThat(2908908335136769L, equalTo(transaction.sequenceNumber()));
        assertThat(100, equalTo(transaction.fee()));
        assertThat("1-test-", equalTo(((MemoText)transaction.memo()).getText()));
        assertThat("c3cfd6795a332cee3e427787852f3d167dec2c416ed26334a9b7ce634211a6cb", equalTo(transaction.id().id()));
        assertThat(1, equalTo(transaction.operations().length));
        assertThat(1, equalTo(transaction.signatures().size()));
        assertThat(transactionEnvelope, equalTo(transaction.transactionEnvelope()));

    }

    // TODO: 2019-06-10 add builder tests

    @Test
    public void addSignature_success() {
        RawTransaction transaction = createTransaction();
        KinAccountImpl mockKinAccount = mock(KinAccountImpl.class);
        when(mockKinAccount.getKeyPair()).thenReturn(KeyPair.fromSecretSeed(SECRET_SEED_FROM));

        assertThat(transaction.signatures().size(), equalTo(1));
        transaction.addSignature(mockKinAccount);
        assertThat(transaction.signatures().size(), equalTo(2));
    }


}
