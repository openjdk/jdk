/*
 * @test /nodynamiccopyright/
 * @bug 6840638
 *
 * @summary  Project Coin: Improved Type Inference for Generic Instance Creation (aka 'diamond')
 * @author mcimadamore
 * @compile/fail/ref=Neg05.out Neg05.java -source 1.7 -XDrawDiagnostics
 *
 */

class Neg05<U> {

    class Foo<V> {
        Foo(V x) {}
        <Z> Foo(V x, Z z) {}
    }

    void testRare_1() {
        Neg05<?>.Foo<String> f1 = new Neg05.Foo<>(""); //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f2 = new Neg05.Foo<>(""); //new Foo<Integer> created
        Neg05<?>.Foo<?> f3 = new Neg05.Foo<>(""); //new Foo<Object> created
        Neg05<?>.Foo<? super String> f4 = new Neg05.Foo<>(""); //new Foo<Object> created

        Neg05<?>.Foo<String> f5 = new Neg05.Foo<>(""){}; //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f6 = new Neg05.Foo<>(""){}; //new Foo<Integer> created
        Neg05<?>.Foo<?> f7 = new Neg05.Foo<>(""){}; //new Foo<Object> created
        Neg05<?>.Foo<? super String> f8 = new Neg05.Foo<>(""){}; //new Foo<Object> created

        Neg05<?>.Foo<String> f9 = new Neg05.Foo<>("", ""); //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f10 = new Neg05.Foo<>("", ""); //new Foo<Integer> created
        Neg05<?>.Foo<?> f11 = new Neg05.Foo<>("", ""); //new Foo<Object> created
        Neg05<?>.Foo<? super String> f12 = new Neg05.Foo<>("", ""); //new Foo<Object> created

        Neg05<?>.Foo<String> f13 = new Neg05.Foo<>("", ""){}; //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f14 = new Neg05.Foo<>("", ""){}; //new Foo<Integer> created
        Neg05<?>.Foo<?> f15 = new Neg05.Foo<>("", ""){}; //new Foo<Object> created
        Neg05<?>.Foo<? super String> f16 = new Neg05.Foo<>("", ""){}; //new Foo<Object> created
    }

    void testRare_2(Neg05 n) {
        Neg05<?>.Foo<String> f1 = n.new Foo<>(""); //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f2 = n.new Foo<>(""); //new Foo<Integer> created
        Neg05<?>.Foo<?> f3 = n.new Foo<>(""); //new Foo<Integer> created
        Neg05<?>.Foo<? super String> f4 = n.new Foo<>(""); //new Foo<Integer> created

        Neg05<?>.Foo<String> f5 = n.new Foo<>(""){}; //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f6 = n.new Foo<>(""){}; //new Foo<Integer> created
        Neg05<?>.Foo<?> f7 = n.new Foo<>(""){}; //new Foo<Integer> created
        Neg05<?>.Foo<? super String> f8 = n.new Foo<>(""){}; //new Foo<Integer> created

        Neg05<?>.Foo<String> f9 = n.new Foo<>("", ""); //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f10 = n.new Foo<>("", ""); //new Foo<Integer> created
        Neg05<?>.Foo<?> f11 = n.new Foo<>("", ""); //new Foo<Integer> created
        Neg05<?>.Foo<? super String> f12 = n.new Foo<>("", ""); //new Foo<Integer> created

        Neg05<?>.Foo<String> f13 = n.new Foo<>("", ""){}; //new Foo<Integer> created
        Neg05<?>.Foo<? extends String> f14 = n.new Foo<>("", ""){}; //new Foo<Integer> created
        Neg05<?>.Foo<?> f15 = n.new Foo<>("", ""){}; //new Foo<Integer> created
        Neg05<?>.Foo<? super String> f16 = n.new Foo<>("", ""){}; //new Foo<Integer> created
    }
}
