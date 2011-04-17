/*
 * @test /nodynamiccopyright/
 * @bug 7030150
 * @summary Type inference for generic instance creation failed for formal type parameter
 *          check that compiler rejects bad number of explicit type-arguments
 * @compile/fail/ref=Neg02.out -XDrawDiagnostics Neg02.java
 */

class Neg02 {

    static class Foo<X> {
        <T> Foo(T t) {}
    }

    Foo<Integer> fi1 = new <String, Integer> Foo<>("");
    Foo<Integer> fi2 = new <String, Integer> Foo<Integer>("");
}
