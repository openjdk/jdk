/*
 * @test /nodynamiccopyright/
 * @bug 6840638
 *
 * @summary  Project Coin: Improved Type Inference for Generic Instance Creation (aka 'diamond')
 * @author mcimadamore
 * @compile/fail/ref=Neg04.out Neg04.java -source 1.7 -XDrawDiagnostics
 *
 */

class Neg04 {

    void test() {
        class Foo<V extends Number> {
            Foo(V x) {}
            <Z> Foo(V x, Z z) {}
        }
        Foo<String> n1 = new Foo<>(""); //new Foo<Integer> created
        Foo<? extends String> n2 = new Foo<>(""); //new Foo<Integer> created
        Foo<?> n3 = new Foo<>(""); //new Foo<Object> created
        Foo<? super String> n4 = new Foo<>(""); //new Foo<Object> created

        Foo<String> n5 = new Foo<>(""){}; //new Foo<Integer> created
        Foo<? extends String> n6 = new Foo<>(""){}; //new Foo<Integer> created
        Foo<?> n7 = new Foo<>(""){}; //new Foo<Object> created
        Foo<? super String> n8 = new Foo<>(""){}; //new Foo<Object> created

        Foo<String> n9 = new Foo<>("", ""); //new Foo<Integer> created
        Foo<? extends String> n10 = new Foo<>("", ""); //new Foo<Integer> created
        Foo<?> n11 = new Foo<>("", ""); //new Foo<Object> created
        Foo<? super String> n12 = new Foo<>("", ""); //new Foo<Object> created

        Foo<String> n13 = new Foo<>("", ""){}; //new Foo<Integer> created
        Foo<? extends String> n14 = new Foo<>("", ""){}; //new Foo<Integer> created
        Foo<?> n15 = new Foo<>("", ""){}; //new Foo<Object> created
        Foo<? super String> n16 = new Foo<>("", ""){}; //new Foo<Object> created
    }
}
