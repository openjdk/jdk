/*
 * @test /nodynamiccopyright/
 * @bug 4707960 6183529
 * @summary javac accepts unicode digits - sometimes crashing
 * @author gafter
 *
 * @compile/fail/ref=NonasciiDigit2.out -XDrawDiagnostics  NonasciiDigit2.java
 */
public class NonasciiDigit2 {
    public static void main(String[] args) {
        // error: only ASCII allowed in constants
        int i = 1\uff11;
    }
}
