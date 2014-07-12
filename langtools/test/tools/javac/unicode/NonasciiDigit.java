/*
 * @test /nodynamiccopyright/
 * @bug 4707960 6183529
 * @summary javac accepts unicode digits - sometimes crashing
 * @author gafter
 *
 * @compile/fail/ref=NonasciiDigit.out -XDrawDiagnostics  NonasciiDigit.java
 */
public class NonasciiDigit {
    public static void main(String[] args) {
        // error: floating literals use ascii only
        float f = 0.\uff11;
    }
}
