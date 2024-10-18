/*
 * @test
 * @bug 8341964
 * @summary Add mechanism to disable different parts of TLS cipher suite
 * @modules java.base/sun.security.util
 * @run testng/othervm DisabledAlgorithmConstraintsTest
 */

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import sun.security.util.AlgorithmDecomposer;
import sun.security.util.DisabledAlgorithmConstraints;

import java.security.CryptoPrimitive;
import java.security.Security;
import java.util.Set;

/**
 * Set of DisabledAlgorithmConstraints tests using base AlgorithmDecomposer
 */
public class DisabledAlgorithmConstraintsTest {

    static final String SECURITY_PROPERTY = "testProperty";
    static final String TEST_ALGORITHMS = "ECDH, Rsa kx, TESTALGO authn, All kx, All authn";

    private DisabledAlgorithmConstraints dac;

    @BeforeSuite
    void setUp() throws Exception {
        Security.setProperty(SECURITY_PROPERTY, TEST_ALGORITHMS);
        dac = new DisabledAlgorithmConstraints(SECURITY_PROPERTY, new AlgorithmDecomposer());
    }

    @Test
    public void testDisabledAlgorithmConstraints_Algorithm() {
        assertFalse(dac.permits(Set.of(CryptoPrimitive.KEY_AGREEMENT), "ECDH", null));
    }

    @Test
    public void testDisabledAlgorithmConstraints_KeyExchange() {
        assertFalse(dac.permits(Set.of(CryptoPrimitive.KEY_AGREEMENT), "RSA", null));
        assertTrue(dac.permits(Set.of(CryptoPrimitive.SIGNATURE), "RSA", null));
    }

    @Test
    public void testDisabledAlgorithmConstraints_Authentication() {
        assertTrue(dac.permits(Set.of(CryptoPrimitive.KEY_AGREEMENT), "TestAlgo", null));
        assertFalse(dac.permits(Set.of(CryptoPrimitive.SIGNATURE), "TestAlgo", null));
    }

    @Test
    public void testDisabledAlgorithmConstraints_KeyExchange_Authentication() {
        assertFalse(dac.permits(Set.of(CryptoPrimitive.KEY_AGREEMENT), "ALL", null));
        assertFalse(dac.permits(Set.of(CryptoPrimitive.SIGNATURE), "ALL", null));
    }
}
