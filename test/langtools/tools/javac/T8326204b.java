import java.util.Map;

/*
 * @test /nodynamiccopyright/
 * @bug 8326204
 * @summary yield statements doesn't allow cast expressions with more than 1 type arguments
 * @compile/fail/ref=T8326204b.out -XDrawDiagnostics --should-stop=at=FLOW -XDdev T8326204b.java
 */
public class T8326204b {
    private static void t(int i) { yield((Map<String, String>) null, 2); }

    private static void yield(Map<String, String> m, int j) { }
}
