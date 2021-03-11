/*
 * @test /nodynamiccopyright/
 * @bug 8254274
 * @summary lint should warn when an instance of a value based class is synchronized upon
 * @compile/fail/ref=ExternalAbuseOfVbc.out -XDrawDiagnostics -Werror -Xlint ExternalAbuseOfVbc.java
 * @compile/fail/ref=ExternalAbuseOfVbc.out -XDrawDiagnostics -Werror -Xlint:all ExternalAbuseOfVbc.java
 * @compile/fail/ref=ExternalAbuseOfVbc.out -XDrawDiagnostics -Werror -Xlint:synchronization ExternalAbuseOfVbc.java
 * @compile/ref=LintModeOffAbuseOfVbc.out -XDrawDiagnostics -Werror -Xlint:-synchronization ExternalAbuseOfVbc.java
 */

public final class ExternalAbuseOfVbc {

    final Integer val = Integer.valueOf(42);
    final String ref = "String";

    void abuseVbc() {
        synchronized(ref) {      // OK
            synchronized (val) { // WARN
            }
        }
    }
}

