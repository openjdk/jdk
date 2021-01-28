/*
 * @test /nodynamiccopyright/
 * @bug 8145987
 * @summary Assertion failure when compiling stream with type annotation
 * @compile/fail/ref=AnnotatedMethodSelectorTest.out -XDrawDiagnostics AnnotatedMethodSelectorTest.java
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class AnnotatedMethodSelectorTest {
    @Target(ElementType.METHOD)
    @interface A {}
    static public void main(String... args) {
        java.util.@A Arrays.stream(args);
    }
}
