/*
 * @test /nodynamiccopyright/
 * @bug 6939620 6894753
 *
 * @summary  Switch to 'complex' diamond inference scheme
 * @author mcimadamore
 * @compile/fail/ref=Neg08.out Neg08.java -XDrawDiagnostics
 *
 */

class Neg08 {
    static class Foo<X> {
        Foo(X x) {  }
    }

    static class DoubleFoo<X,Y> {
        DoubleFoo(X x,Y y) {  }
    }

    static class TripleFoo<X,Y,Z> {
        TripleFoo(X x,Y y,Z z) {  }
    }

    Foo<? extends Integer> fi = new Foo<>(1);
    Foo<?> fw = new Foo<>(fi);
    Foo<? extends Double> fd = new Foo<>(3.0);
    DoubleFoo<?,?> dw = new DoubleFoo<>(fi,fd);
    Foo<String> fs = new Foo<>("one");
    TripleFoo<?,?,?> tw = new TripleFoo<>(fi,fd,fs);
}
