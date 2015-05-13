/*
 * @test /nodynamiccopyright/
 * @bug 8078473
 * @summary  javac diamond finder crashes when used to build java.base module
 * @compile/ref=T8078473.out T8078473.java -XDrawDiagnostics -XDfind=diamond
 */

class T8078473<P, Q> {

    static class C<T, U> {
        C(T8078473<?, ?> p) {}
    }

    {
        new C<Q, Q>(this) {};
        new C<Q, Q>(this);
    }
}
