/*
 * @test
 * @bug 8341964
 * @summary Add mechanism to disable different parts of TLS cipher suite
 * @run testng TLSCipherConstrainDisablePartsOfCipherSuite
 */

import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.security.Security;
import java.util.List;

public class TLSCipherConstrainDisablePartsOfCipherSuite extends NoDesRC4DesEdeCiphSuite {

    private static final String SECURITY_PROPERTY = "jdk.tls.disabledAlgorithms";
    private static final String TEST_ALGORITHMS = "ECDH kx, Rsa kx, ECDSA authn, DH_anoN KX, NuLL Authn";
    private static final String[] CIPHER_SUITES = new String[] {
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA"
    };
    static final List<Integer> CIPHER_SUITES_IDS = List.of(
            0x009D,
            0xC02E,
            0xC02C,
            0x0034,
            0xC018
    );

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
