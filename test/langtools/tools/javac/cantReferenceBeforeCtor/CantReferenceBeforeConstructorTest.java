/*
 * @test /nodynamiccopyright/
 * @bug 8270835
 * @summary regression after JDK-8261006
 * @compile/fail/ref=CantReferenceBeforeConstructorTest.out -XDrawDiagnostics CantReferenceBeforeConstructorTest.java
 */

public class CantReferenceBeforeConstructorTest {
    // test case I
    class A extends B {
        A(int i) {}

        class C extends B {
            class D extends S {
                D(float f) {
                    C.super.ref.super(); // ok
                }
            }
        }
    }

    class B {
        B ref;
        class S {}
    }

    // test case II
    class AA extends BB.CC {
        AA() {
            this.super();    // error
        }
    }

    class BB {
        class CC extends BB {
            void m() {
                BB.this.f();
            }
        }
        void f() {}
    }
}
