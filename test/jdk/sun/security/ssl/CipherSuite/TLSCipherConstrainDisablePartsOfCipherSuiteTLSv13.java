/*
 * @test
 * @bug 8341964
 * @summary Add mechanism to disable different parts of TLS cipher suite
 * @run testng/othervm TLSCipherConstrainDisablePartsOfCipherSuiteTLSv13
 */

import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.security.Security;
import java.util.List;

public class TLSCipherConstrainDisablePartsOfCipherSuiteTLSv13 extends NoDesRC4DesEdeCiphSuite {

    private static final String SECURITY_PROPERTY = "jdk.tls.disabledAlgorithms";
    private static final String TEST_ALGORITHMS = "ECDHE kx";
    private static final String[] CIPHER_SUITES = new String[] {
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256",
        "TLS_CHACHA20_POLY1305_SHA256"
    };
    static final List<Integer> CIPHER_SUITES_IDS = List.of(
        0x1302,
        0x1301,
        0x1303
    );

    protected String getProtocol() {
        return "TLSv1.3";
    }

    @BeforeTest
    void setUp() throws Exception {
        Security.setProperty(SECURITY_PROPERTY, TEST_ALGORITHMS);
    }

    @Test
    public void testDefault() throws Exception {
        assertTrue(testDefaultCase(CIPHER_SUITES_IDS));
    }

    @Test
    public void testAddDisabled() throws Exception {
        assertTrue(testEngAddDisabled(CIPHER_SUITES, CIPHER_SUITES_IDS));
    }

    @Test
    public void testOnlyDisabled() throws Exception {
        assertTrue(testEngOnlyDisabled(CIPHER_SUITES));
    }
}
