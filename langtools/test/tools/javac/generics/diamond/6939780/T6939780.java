/*
 * @test /nodynamiccopyright/
 * @bug 6939780 7020044 8009459
 *
 * @summary  add a warning to detect diamond sites
 * @author mcimadamore
 * @compile/ref=T6939780_7.out -Xlint:-options -source 7 T6939780.java -XDrawDiagnostics -XDfindDiamond
 * @compile/ref=T6939780_8.out T6939780.java -XDrawDiagnostics -XDfindDiamond
 *
 */

class T6939780 {

    static class Foo<X extends Number> {
        Foo() {}
        Foo(X x) {}
    }

    void testAssign() {
        Foo<Number> f1 = new Foo<Number>(1);
        Foo<?> f2 = new Foo<Number>();
        Foo<?> f3 = new Foo<Integer>();
        Foo<Number> f4 = new Foo<Number>(1) {};
        Foo<?> f5 = new Foo<Number>() {};
        Foo<?> f6 = new Foo<Integer>() {};
    }

    void testMethod() {
        gn(new Foo<Number>(1));
        gw(new Foo<Number>());
        gw(new Foo<Integer>());
        gn(new Foo<Number>(1) {});
        gw(new Foo<Number>() {});
        gw(new Foo<Integer>() {});
    }

    void gw(Foo<?> fw) { }
    void gn(Foo<Number> fn) { }
}
