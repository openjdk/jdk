/*
* @test
* @bug 8367049
* @summary URLPermission must reject empty/missing host authority with IAE (no SIOOBE)
* @run main EmptyAuthorityTest
*/
import java.net.URLPermission;

public class EmptyAuthorityTest {
    public static void main(String[] args) {
        // Empty or missing host in authority
        expectIAE("http:///path");
        expectIAE("https:///x");


        // userinfo with missing host
        expectIAE("http://@/x");
        expectIAE("http://user@/x ");

        // missing host but port present
        expectIAE("http://:80/x");

        // IPv6 brackets with no address
        expectIAE("http://[]/x");

        // Valid forms (should not throw)
        ok("http://example.com/x");
        ok("http://example.com:80/x");
        ok("http://[::1]/x");
        ok("http://[::1]:8080/x");

        System.out.println("OK: EmptyAuthorityTest passed");
    }

    static void expectIAE(String url) {
        try {
            new URLPermission(url);
            throw new RuntimeException("Expected IllegalArgumentException for: " + url);
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }

    static void ok(String url) {
        new URLPermission(url);
    }
}