/*
 * @test
 * @bug 8341964
 * @summary Add mechanism to disable different parts of TLS cipher suite
 * @run testng TLSCipherConstraintChainedAfter
 */
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.security.Security;

import javax.net.ssl.SSLContext;

/**
 * SSLContext loads "jdk.tls.disabledAlgorithms" system property statically when
 * it's being loaded into memory, so we can't call Security.setProperty("jdk.tls.disabledAlgorithms")
 * more than once per test class. Thus, we need a separate test class each time we need
 * to modify "jdk.tls.disabledAlgorithms" config value for testing.
 *
 */
public class TLSCipherConstraintChainedAfter {

    private static final String SECURITY_PROPERTY = "jdk.tls.disabledAlgorithms";
    private static final String TEST_ALGORITHMS = "Rsa kx & keySize < 1024";

    @BeforeTest
    void setUp() throws Exception {
        Security.setProperty(SECURITY_PROPERTY, TEST_ALGORITHMS);
    }

    @Test
    public void testChainedAfter() throws Exception {
        try {
            SSLContext.getInstance("TLS");
        } catch (ExceptionInInitializerError e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals("TLSCipherConstraint should not be linked with other constraints. Constraint: " +
                    TEST_ALGORITHMS,
                    e.getCause().getMessage());
            return;
        }
        fail();
    }
}
