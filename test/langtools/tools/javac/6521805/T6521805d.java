/*
 * @test /nodynamiccopyright/
 * @bug 6521805
 * @summary Regression: JDK5/JDK6 javac allows write access to outer class reference
 * @author mcimadamore
 *
 * @compile/fail/ref=T6521805d.out T6521805d.java -XDrawDiagnostics
 */

import java.util.Objects;

class T6521805 {

    static class Inner extends T6521805.Outer {

        Inner(T6521805 t) {
            t.super();
        }

        T6521805 this$0 = null;

        public void foo() {
            this$0 = new T6521805();
        }
    }

    class Outer {
        {
            // access enclosing instance so this$0 field is generated
            Objects.requireNonNull(T6521805.this);
        }
    }

}
