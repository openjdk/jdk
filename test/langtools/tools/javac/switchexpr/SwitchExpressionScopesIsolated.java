/*
 * @test /nodynamiccopyright/
 * @bug 8206986
 * @summary Verify that scopes in rule cases are isolated.
 * @compile/fail/ref=SwitchExpressionScopesIsolated.out -XDrawDiagnostics --enable-preview -source ${jdk.version} SwitchExpressionScopesIsolated.java
 */

public class SwitchExpressionScopesIsolated {

    private String scopesIsolated(int i) {
        return switch (i) {
            case 0 -> { String res = ""; break res; }
            case 1 -> { res = ""; break res; }
            default -> { res = ""; break res; }
        };
    }

}
