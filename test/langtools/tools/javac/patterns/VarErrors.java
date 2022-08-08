/*
 * @test /nodynamiccopyright/
 * @summary Verify errors related to var patterns
 * @compile/fail/ref=VarErrors.out --enable-preview -source ${jdk.version} -XDrawDiagnostics -XDshould-stop.at=FLOW -XDdev VarErrors.java
 */
public class VarErrors {
    void testIf(CharSequence cs) {
        if (cs instanceof var v) {}
    }
    void testSwitchStatement(CharSequence cs) {
        switch (cs) {
            case var v -> {}
        }
    }
    void testSwitchExpression(CharSequence cs) {
        int i = switch (cs) {
            case var v -> 0;
        };
    }
}
