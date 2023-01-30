/*
 * @test /nodynamiccopyright/
 * @bug 8268320
 * @summary Verify user-friendly errors are reported for ill-formed pattern.
 * @compile/fail/ref=PatternErrorRecovery.out -XDrawDiagnostics -XDshould-stop.at=FLOW PatternErrorRecovery.java
 * @compile/fail/ref=PatternErrorRecovery-old.out --release 20 -XDrawDiagnostics -XDshould-stop.at=FLOW PatternErrorRecovery.java
 */
public class PatternErrorRecovery {
    void errorRecoveryNoPattern1(Object o) {
        switch (o) {
            case String: break;
            case Object obj: break;
        }
    }
}
