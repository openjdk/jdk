/**
 * @test /nodynamiccopyright/
 * @bug 6747671
 * @summary -Xlint:rawtypes
 * @compile/ref=T6747671.out -XDrawDiagnostics -Xlint:rawtypes T6747671.java
 */


class T6747671<E> {

    static class B<X> {}

    class A<X> {
        class X {}
        class Z<Y> {}
    }


    A.X x1;//raw warning
    A.Z z1;//raw warning

    T6747671.B<Integer> b1;//ok
    T6747671.B b2;//raw warning

    A<String>.X x2;//ok
    A<String>.Z<Integer> z2;//ok
    A<B>.Z<A<B>> z3;//raw warning (2)

    void test(Object arg1, B arg2) {//raw warning
        boolean b = arg1 instanceof A;//raw warning
        Object a = (A)arg1;//raw warning
        A a2 = new A() {};//raw warning (2)
        a2.new Z() {};//raw warning
    }
}
