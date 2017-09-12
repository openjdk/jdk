/*
 * @test /nodynamiccopyright/
 * @bug 4974927 8064464
 * @summary The compiler was allowing void types in its parsing of conditional expressions.
 * @author tball
 *
 * @compile/fail/ref=ConditionalWithVoid.out -XDrawDiagnostics ConditionalWithVoid.java
 */
public class ConditionalWithVoid {
    public void test(Object o) {
        // Should fail to compile since Object.wait() has a void return type. Poly case.
        System.out.println(o instanceof String ? o.hashCode() : o.wait());
        // Should fail to compile since Object.wait() has a void return type. Standalone case.
        (o instanceof String ? o.hashCode() : o.wait()).toString();
    }
}
