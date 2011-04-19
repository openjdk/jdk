/*
 * @test /nodynamiccopyright/
 * @bug 7030150
 * @summary Type inference for generic instance creation failed for formal type parameter
 *          check that explicit type-argument that causes resolution failure is rejected
 * @compile/fail/ref=Neg01.out -XDrawDiagnostics Neg01.java
 */

class Neg01 {

    static class Foo<X> {
        <T> Foo(T t) {}
    }

    Foo<Integer> fi1 = new <String> Foo<>(1);
    Foo<Integer> fi2 = new <String> Foo<Integer>(1);
}
