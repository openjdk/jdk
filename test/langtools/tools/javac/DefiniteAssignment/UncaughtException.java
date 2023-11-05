/*
 * @test /nodynamiccopyright/
 * @bug 4053998
 * @summary Compiler used to not notice the uncaught checked exception.
 * @author turnidge
 *
 * @compile/fail/ref=UncaughtException.out -XDrawDiagnostics  UncaughtException.java
 */

interface I {
    void throwCheckedException ();
}

class A {
    public void throwCheckedException () throws Throwable {
        throw new Throwable();
    }
}

class B extends A implements I {
}

public class UncaughtException {
}
