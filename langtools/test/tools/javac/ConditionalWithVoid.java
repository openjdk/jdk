/*
 * @test /nodynamiccopyright/
 * @bug 4974927
 * @summary The compiler was allowing void types in its parsing of conditional expressions.
 * @author tball
 *
 * @compile/fail/ref=ConditionalWithVoid.out -XDrawDiagnostics ConditionalWithVoid.java
 */
public class ConditionalWithVoid {
    public int test(Object o) {
        // Should fail to compile since Object.wait() has a void return type.
        System.out.println(o instanceof String ? o.hashCode() : o.wait());
    }
}
