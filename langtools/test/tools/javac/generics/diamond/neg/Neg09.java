/*
 * @test /nodynamiccopyright/
 * @bug 6939620 6894753
 *
 * @summary  Switch to 'complex' diamond inference scheme
 * @author mcimadamore
 * @compile/fail/ref=Neg09.out Neg09.java -XDrawDiagnostics
 *
 */

class Neg09 {
    static class Foo<X extends Number & Comparable<Number>> {}
    static class DoubleFoo<X extends Number & Comparable<Number>,
                           Y extends Number & Comparable<Number>> {}
    static class TripleFoo<X extends Number & Comparable<Number>,
                           Y extends Number & Comparable<Number>,
                           Z> {}

    Foo<?> fw = new Foo<>();
    DoubleFoo<?,?> dw = new DoubleFoo<>();
    TripleFoo<?,?,?> tw = new TripleFoo<>();
}
