/*
 * @test /nodynamiccopyright/
 * @bug 8145987
 * @summary Assertion failure when compiling stream with type annotation
 * @compile/fail/ref=AnnotatedMethodSelectorTest.out -XDrawDiagnostics AnnotatedMethodSelectorTest.java
 */

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

class AnnotatedMethodSelectorTest {
    @interface A {}
    @Target(ElementType.TYPE_USE)
    @interface TA {}
    static public void main(String... args) {
        java.util.@A Arrays.stream(args);
        java.util.@TA Arrays.stream(args);
    }
}
