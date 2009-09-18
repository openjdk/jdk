/*
 * @test /nodynamiccopyright/
 * @bug 6840638
 *
 * @summary  Project Coin: Improved Type Inference for Generic Instance Creation (aka 'diamond')
 * @author mcimadamore
 * @compile Pos04.java -source 1.7
 * @run main Pos04
 *
 */

public class Pos04<U> {

    void test() {
        class Foo<V> {
            Foo(V x) {}
            <Z> Foo(V x, Z z) {}
        }
        Foo<Integer> p1 = new Foo<>(1); //new Foo<Integer> created
        Foo<? extends Integer> p2 = new Foo<>(1); //new Foo<Integer> created
        Foo<?> p3 = new Foo<>(1); //new Foo<Object> created
        Foo<? super Integer> p4 = new Foo<>(1); //new Foo<Object> created

        Foo<Integer> p5 = new Foo<>(1){}; //new Foo<Integer> created
        Foo<? extends Integer> p6 = new Foo<>(1){}; //new Foo<Integer> created
        Foo<?> p7 = new Foo<>(1){}; //new Foo<Object> created
        Foo<? super Integer> p8 = new Foo<>(1){}; //new Foo<Object> created

        Foo<Integer> p9 = new Foo<>(1, ""); //new Foo<Integer> created
        Foo<? extends Integer> p10 = new Foo<>(1, ""); //new Foo<Integer> created
        Foo<?> p11 = new Foo<>(1, ""); //new Foo<Object> created
        Foo<? super Integer> p12 = new Foo<>(1, ""); //new Foo<Object> created

        Foo<Integer> p13 = new Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<? extends Integer> p14 = new Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<?> p15 = new Foo<>(1, ""){}; //new Foo<Object> created
        Foo<? super Integer> p16 = new Foo<>(1, ""){}; //new Foo<Object> created
    }

    public static void main(String[] args) {
        Pos04<String> p4 = new Pos04<>();
        p4.test();
    }
}
