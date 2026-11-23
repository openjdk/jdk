/*
 * @test /nodynamiccopyright/
 * @summary Check behavior of synzhronized key word on value classes instances and methods.
 * @enablePreview
 * @compile/fail/ref=CheckSynchronized.out -XDrawDiagnostics CheckSynchronized.java
 */

value final class CheckSynchronized implements java.io.Serializable {
    synchronized void foo() { // <<-- ERROR, no monitor associated with `this'
    }
    void goo() {
        synchronized(this) {} // <<-- ERROR, no monitor associated with `this'
    }
    synchronized static void zoo(CheckSynchronized cs) { // OK, static method.
        synchronized(cs) {    // <<-- ERROR, no monitor associated with value class instance.
        }

        CheckSynchronized csr = cs;
        synchronized(csr) {
            // Error, no identity.
        }

        synchronized(x) {
            // Error, no identity.
        }

        Object o = cs;
        synchronized(o) {
            // Error BUT not discernible at compile time
        }
        java.io.Serializable jis = cs;
        synchronized(jis) {
            // Error BUT not discernible at compile time
        }
    }
    static int x = 10;

    value record CheckSynchronizedRecord(int x, int y) {
        synchronized void foo() { // <<-- ERROR, no monitor associated with `this'
        }
        synchronized static void zoo() { // OK, static method.
        }
    }
}
