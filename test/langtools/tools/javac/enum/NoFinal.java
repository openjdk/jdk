/*
 * @test /nodynamiccopyright/
 * @bug 5097250 5087624
 * @summary Finalize methods on enums must be compile time error
 * @author Peter von der Ahé
 * @compile/fail/ref=NoFinal.out -XDrawDiagnostics  NoFinal.java
 */

enum NoFinal {
    A {
        protected void finalize() {
            System.err.println("FISK");
        }
    };
}
