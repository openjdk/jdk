/*
 * @test /nodynamiccopyright/
 * @bug 8145987
 * @summary Assertion failure when compiling stream with type annotation
 * @compile/fail/ref=AnnotatedMethodSelectorTest2.out -XDrawDiagnostics AnnotatedMethodSelectorTest2.java
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class AnnotatedMethodSelectorTest2<T> {
    @Target(ElementType.METHOD)
    @interface A {}
    class Inner {}
    static public void main(String... args) {
        new AnnotatedMethodSelectorTest2<@A String>() {
            java.util.@A List l;
        }.hashCode();
    }
}
