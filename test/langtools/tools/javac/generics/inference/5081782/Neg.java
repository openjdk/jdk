/*
 * @test    /nodynamiccopyright/
 * @bug     5081782
 * @summary type arguments to non-generic methods
 * @author  Peter von der Ahé
 * @compile/fail/ref=Neg.out -XDrawDiagnostics  Neg.java
 */

public class Neg {
    static void m() {}
    public static void meth() {
        Neg.<Can,I,write,a,little,story,here>m();
    }
}
