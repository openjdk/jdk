/*
 * @test /nodynamiccopyright/
 * @bug 8268320 8312984
 * @summary Verify user-friendly errors are reported for ill-formed pattern.
 * @compile/fail/ref=PatternErrorRecovery.out -XDrawDiagnostics -XDshould-stop.at=FLOW -XDdev PatternErrorRecovery.java
 * @compile/fail/ref=PatternErrorRecovery-old.out --release 20 -XDrawDiagnostics -XDshould-stop.at=FLOW PatternErrorRecovery.java
 */
public class PatternErrorRecovery {
    void errorRecoveryNoPattern1(Object o) {
        switch (o) {
            case String: break;
            case Object obj: break;
        }
    }
    int errorRecoveryNoPattern2(Object o) {
        return switch(o) {
            case R(var v, ) -> 1;
            default -> -1;
        };
    }

    record R(String x) {}
}
