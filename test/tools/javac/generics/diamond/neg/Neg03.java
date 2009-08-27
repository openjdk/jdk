/*
 * @test /nodynamiccopyright/
 * @bug 6840638
 *
 * @summary  Project Coin: Improved Type Inference for Generic Instance Creation (aka 'diamond')
 * @author mcimadamore
 * @compile/fail/ref=Neg03.out Neg03.java -source 1.7 -XDrawDiagnostics
 *
 */

class Neg03<U> {

    class Foo<V extends Number> {
        Foo(V x) {}
        <Z> Foo(V x, Z z) {}
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

    void testQualified_1() {
        Foo<String> f1 = new Neg03<U>.Foo<>(""); //new Foo<Integer> created
        Foo<? extends String> f2 = new Neg03<U>.Foo<>(""); //new Foo<Integer> created
        Foo<?> f3 = new Neg03<U>.Foo<>(""); //new Foo<Object> created
        Foo<? super String> f4 = new Neg03<U>.Foo<>(""); //new Foo<Object> created

        Foo<String> f5 = new Neg03<U>.Foo<>(""){}; //new Foo<Integer> created
        Foo<? extends String> f6 = new Neg03<U>.Foo<>(""){}; //new Foo<Integer> created
        Foo<?> f7 = new Neg03<U>.Foo<>(""){}; //new Foo<Object> created
        Foo<? super String> f8 = new Neg03<U>.Foo<>(""){}; //new Foo<Object> created

        Foo<String> f9 = new Neg03<U>.Foo<>("", ""); //new Foo<Integer> created
        Foo<? extends String> f10 = new Neg03<U>.Foo<>("", ""); //new Foo<Integer> created
        Foo<?> f11 = new Neg03<U>.Foo<>("", ""); //new Foo<Object> created
        Foo<? super String> f12 = new Neg03<U>.Foo<>("", ""); //new Foo<Object> created

        Foo<String> f13 = new Neg03<U>.Foo<>("", ""){}; //new Foo<Integer> created
        Foo<? extends String> f14 = new Neg03<U>.Foo<>("", ""){}; //new Foo<Integer> created
        Foo<?> f15 = new Neg03<U>.Foo<>("", ""){}; //new Foo<Object> created
        Foo<? super String> f16 = new Neg03<U>.Foo<>("", ""){}; //new Foo<Object> created
    }

    void testQualified_2(Neg03<U> n) {
        Foo<String> f1 = n.new Foo<>(""); //new Foo<Integer> created
        Foo<? extends String> f2 = n.new Foo<>(""); //new Foo<Integer> created
        Foo<?> f3 = n.new Foo<>(""); //new Foo<Integer> created
        Foo<? super String> f4 = n.new Foo<>(""); //new Foo<Integer> created

        Foo<String> f5 = n.new Foo<>(""){}; //new Foo<Integer> created
        Foo<? extends String> f6 = n.new Foo<>(""){}; //new Foo<Integer> created
        Foo<?> f7 = n.new Foo<>(""){}; //new Foo<Integer> created
        Foo<? super String> f8 = n.new Foo<>(""){}; //new Foo<Integer> created

        Foo<String> f9 = n.new Foo<>("", ""); //new Foo<Integer> created
        Foo<? extends String> f10 = n.new Foo<>("", ""); //new Foo<Integer> created
        Foo<?> f11 = n.new Foo<>("", ""); //new Foo<Integer> created
        Foo<? super String> f12 = n.new Foo<>("", ""); //new Foo<Integer> created

        Foo<String> f13 = n.new Foo<>("", ""){}; //new Foo<Integer> created
        Foo<? extends String> f14 = n.new Foo<>("", ""){}; //new Foo<Integer> created
        Foo<?> f15 = n.new Foo<>("", ""){}; //new Foo<Integer> created
        Foo<? super String> f16 = n.new Foo<>("", ""){}; //new Foo<Integer> created
    }
}
