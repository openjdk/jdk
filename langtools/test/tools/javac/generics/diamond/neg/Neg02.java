/*
 * @test /nodynamiccopyright/
 * @bug 6840638
 *
 * @summary  Project Coin: Improved Type Inference for Generic Instance Creation (aka 'diamond')
 * @author mcimadamore
 * @compile/fail/ref=Neg02.out Neg02.java -source 1.7 -XDrawDiagnostics
 *
 */

class Neg02 {

    static class Foo<X extends Number> {
        Foo(X x) {}
        <Z> Foo(X x, Z z) {}
    }

    void testSimple() {
        Foo<String> f1 = new Foo<>(""); //new Foo<Integer> created
        Foo<? extends String> f2 = new Foo<>(""); //new Foo<Integer> created
        Foo<?> f3 = new Foo<>(""); //new Foo<Object> created
        Foo<? super String> f4 = new Foo<>(""); //new Foo<Object> created

        Foo<String> f5 = new Foo<>(""){}; //new Foo<Integer> created
        Foo<? extends String> f6 = new Foo<>(""){}; //new Foo<Integer> created
        Foo<?> f7 = new Foo<>(""){}; //new Foo<Object> created
        Foo<? super String> f8 = new Foo<>(""){}; //new Foo<Object> created

        Foo<String> f9 = new Foo<>("", ""); //new Foo<Integer> created
        Foo<? extends String> f10 = new Foo<>("", ""); //new Foo<Integer> created
        Foo<?> f11 = new Foo<>("", ""); //new Foo<Object> created
        Foo<? super String> f12 = new Foo<>("", ""); //new Foo<Object> created

        Foo<String> f13 = new Foo<>("", ""){}; //new Foo<Integer> created
        Foo<? extends String> f14 = new Foo<>("", ""){}; //new Foo<Integer> created
        Foo<?> f15 = new Foo<>("", ""){}; //new Foo<Object> created
        Foo<? super String> f16 = new Foo<>("", ""){}; //new Foo<Object> created
    }

    void testQualified() {
        Foo<String> f1 = new Neg02.Foo<>(""); //new Foo<Integer> created
        Foo<? extends String> f2 = new Neg02.Foo<>(""); //new Foo<Integer> created
        Foo<?> f3 = new Neg02.Foo<>(""); //new Foo<Object> created
        Foo<? super String> f4 = new Neg02.Foo<>(""); //new Foo<Object> created

        Foo<String> f5 = new Neg02.Foo<>(""){}; //new Foo<Integer> created
        Foo<? extends String> f6 = new Neg02.Foo<>(""){}; //new Foo<Integer> created
        Foo<?> f7 = new Neg02.Foo<>(""){}; //new Foo<Object> created
        Foo<? super String> f8 = new Neg02.Foo<>(""){}; //new Foo<Object> created

        Foo<String> f9 = new Neg02.Foo<>("", ""); //new Foo<Integer> created
        Foo<? extends String> f10 = new Neg02.Foo<>("", ""); //new Foo<Integer> created
        Foo<?> f11 = new Neg02.Foo<>("", ""); //new Foo<Object> created
        Foo<? super String> f12 = new Neg02.Foo<>("", ""); //new Foo<Object> created

        Foo<String> f13 = new Neg02.Foo<>("", ""){}; //new Foo<Integer> created
        Foo<? extends String> f14 = new Neg02.Foo<>("", ""){}; //new Foo<Integer> created
        Foo<?> f15 = new Neg02.Foo<>("", ""){}; //new Foo<Object> created
        Foo<? super String> f16 = new Neg02.Foo<>("", ""){}; //new Foo<Object> created
    }
}
