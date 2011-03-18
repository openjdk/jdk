/*
 * @test /nodynamiccopyright/
 * @bug 6939780 7020044
 *
 * @summary  add a warning to detect diamond sites
 * @author mcimadamore
 * @compile/ref=T6939780.out T6939780.java -XDrawDiagnostics -XDfindDiamond
 *
 */

class T6939780 {

    void test() {
        class Foo<X extends Number> {
            Foo() {}
            Foo(X x) {}
        }
        Foo<Number> f1 = new Foo<Number>(1);
        Foo<?> f2 = new Foo<Number>();
        Foo<?> f3 = new Foo<Integer>();
        Foo<Number> f4 = new Foo<Number>(1) {};
        Foo<?> f5 = new Foo<Number>() {};
        Foo<?> f6 = new Foo<Integer>() {};
    }
}
