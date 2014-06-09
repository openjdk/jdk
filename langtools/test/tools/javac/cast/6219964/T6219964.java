/*
 * @test    /nodynamiccopyright/
 * @bug     6219964
 * @summary Compiler allows illegal cast of anonymous inner class
 * @compile/fail/ref=T6219964.out -XDrawDiagnostics  T6219964.java
 */

public class T6219964 {
    interface I { }
    void foo() {
        new Object() {
            I bar() {
                return (I)this;
            }
        };
    }
}
