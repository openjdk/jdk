/*
 * @test /nodynamiccopyright/
 * @bug 6939620
 *
 * @summary  Switch to 'complex' diamond inference scheme
 * @author mcimadamore
 * @compile/fail/ref=Neg11.out Neg11.java -XDrawDiagnostics
 *
 */

class Neg11 {

    void test() {
        class Foo<X extends Number> { }
        Foo<?> f1 = new UndeclaredName<>(); //this is deliberate: aim is to test erroneous path
        Foo<?> f2 = new UndeclaredName<>() {}; //this is deliberate: aim is to test erroneous path
    }
}
