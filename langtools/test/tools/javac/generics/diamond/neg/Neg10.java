/*
 * @test /nodynamiccopyright/
 * @bug 6939620 7020044
 *
 * @summary  Check that 'complex' diamond can infer type that is too specific
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
