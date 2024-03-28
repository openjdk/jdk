/**
 * @test /nodynamiccopyright/
 * @bug 8324651
 * @summary Support for derived record creation expression
 * @compile/fail/ref=SourceLevelCheck.out --release 22 -XDrawDiagnostics SourceLevelCheck.java
 */
public class SourceLevelCheck {
    public static void main(String... args) {
        R r = new R(0);
        r = r with {
            val = -1;
        };
    }
    record R(int val) {}
}
