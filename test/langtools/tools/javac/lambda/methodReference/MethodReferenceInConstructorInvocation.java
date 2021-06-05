/*
 * @test /nodynamiccopyright/
 * @bug 8261006
 * @summary fail to parse broken interface::method in lambda
 * @compile/fail/ref=MethodReferenceInConstructorInvocation.out -XDrawDiagnostics MethodReferenceInConstructorInvocation.java
 */

import java.util.function.Supplier;

public class MethodReferenceInConstructorInvocation {
    interface Bar {
        default String getString() {
            return "";
        }
    }

    static class Foo implements Bar {

        public Foo() {
            this(Bar.super::getString);
        }

        public Foo(Supplier<String> sString) {
        }
    }
}
