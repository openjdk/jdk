/*
 * @test /nodynamiccopyright/
 * @bug 7030150
 * @summary Type inference for generic instance creation failed for formal type parameter
 *          check that explicit type-argument that does not conform to bound is rejected
 * @compile/fail/ref=Neg03.out -XDrawDiagnostics Neg03.java
 */

class Neg03 {

    static class Foo<X> {
        <T extends Integer> Foo(T t) {}
    }

    Foo<Integer> fi1 = new <String> Foo<>(1);
    Foo<Integer> fi2 = new <String> Foo<Integer>(1);
}
