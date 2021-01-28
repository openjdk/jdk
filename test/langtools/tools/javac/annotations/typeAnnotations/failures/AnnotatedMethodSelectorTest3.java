/*
 * @test /nodynamiccopyright/
 * @bug 8145987
 * @summary Assertion failure when compiling stream with type annotation
 * @compile/fail/ref=AnnotatedMethodSelectorTest3.out -XDrawDiagnostics AnnotatedMethodSelectorTest3.java
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class AnnotatedMethodSelectorTest3 {
    @Target(ElementType.METHOD)
    @interface A {}
    static <T> AnnotatedMethodSelectorTest3 id() {
        return null;
    }
    static public void main(String... args) {
        AnnotatedMethodSelectorTest3.<@A String> id().id().id().id().id();
    }
}
