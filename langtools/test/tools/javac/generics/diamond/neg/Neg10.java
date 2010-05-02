/*
 * @test /nodynamiccopyright/
 * @bug 6939620
 *
 * @summary  Switch to 'complex' diamond inference scheme
 * @author mcimadamore
 * @compile/fail/ref=Neg10.out Neg10.java -XDrawDiagnostics
 *
 */

class Neg10 {
    static class Foo<X> {
        Foo(X x) {}
    }

    Foo<Number> fw = new Foo<>(1);
}
