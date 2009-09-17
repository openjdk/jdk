/*
 * @test /nodynamiccopyright/
 * @bug 6840638
 *
 * @summary  Project Coin: Improved Type Inference for Generic Instance Creation (aka 'diamond')
 * @author mcimadamore
 * @compile Pos03.java -source 1.7
 * @run main Pos03
 *
 */

public class Pos03<U> {

    class Foo<V> {
        Foo(V x) {}
        <Z> Foo(V x, Z z) {}
    }

    void testSimple() {
        Foo<Integer> f1 = new Foo<>(1); //new Foo<Integer> created
        Foo<? extends Integer> f2 = new Foo<>(1); //new Foo<Integer> created
        Foo<?> f3 = new Foo<>(1); //new Foo<Object> created
        Foo<? super Integer> f4 = new Foo<>(1); //new Foo<Object> created

        Foo<Integer> f5 = new Foo<>(1){}; //new Foo<Integer> created
        Foo<? extends Integer> f6 = new Foo<>(1){}; //new Foo<Integer> created
        Foo<?> f7 = new Foo<>(1){}; //new Foo<Object> created
        Foo<? super Integer> f8 = new Foo<>(1){}; //new Foo<Object> created

        Foo<Integer> f9 = new Foo<>(1, ""); //new Foo<Integer> created
        Foo<? extends Integer> f10 = new Foo<>(1, ""); //new Foo<Integer> created
        Foo<?> f11 = new Foo<>(1, ""); //new Foo<Object> created
        Foo<? super Integer> f12 = new Foo<>(1, ""); //new Foo<Object> created

        Foo<Integer> f13 = new Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<? extends Integer> f14 = new Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<?> f15 = new Foo<>(1, ""){}; //new Foo<Object> created
        Foo<? super Integer> f16 = new Foo<>(1, ""){}; //new Foo<Object> created
    }

    void testQualified_1() {
        Foo<Integer> f1 = new Pos03<U>.Foo<>(1); //new Foo<Integer> created
        Foo<? extends Integer> f2 = new Pos03<U>.Foo<>(1); //new Foo<Integer> created
        Foo<?> f3 = new Pos03<U>.Foo<>(1); //new Foo<Object> created
        Foo<? super Integer> f4 = new Pos03<U>.Foo<>(1); //new Foo<Object> created

        Foo<Integer> f5 = new Pos03<U>.Foo<>(1){}; //new Foo<Integer> created
        Foo<? extends Integer> f6 = new Pos03<U>.Foo<>(1){}; //new Foo<Integer> created
        Foo<?> f7 = new Pos03<U>.Foo<>(1){}; //new Foo<Object> created
        Foo<? super Integer> f8 = new Pos03<U>.Foo<>(1){}; //new Foo<Object> created

        Foo<Integer> f9 = new Pos03<U>.Foo<>(1, ""); //new Foo<Integer> created
        Foo<? extends Integer> f10 = new Pos03<U>.Foo<>(1, ""); //new Foo<Integer> created
        Foo<?> f11 = new Pos03<U>.Foo<>(1, ""); //new Foo<Object> created
        Foo<? super Integer> f12 = new Pos03<U>.Foo<>(1, ""); //new Foo<Object> created

        Foo<Integer> f13 = new Pos03<U>.Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<? extends Integer> f14 = new Pos03<U>.Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<?> f15 = new Pos03<U>.Foo<>(1, ""){}; //new Foo<Object> created
        Foo<? super Integer> f16 = new Pos03<U>.Foo<>(1, ""){}; //new Foo<Object> created
    }

    void testQualified_2(Pos03<U> p) {
        Foo<Integer> f1 = p.new Foo<>(1); //new Foo<Integer> created
        Foo<? extends Integer> f2 = p.new Foo<>(1); //new Foo<Integer> created
        Foo<?> f3 = p.new Foo<>(1); //new Foo<Object> created
        Foo<? super Integer> f4 = p.new Foo<>(1); //new Foo<Object> created

        Foo<Integer> f5 = p.new Foo<>(1){}; //new Foo<Integer> created
        Foo<? extends Integer> f6 = p.new Foo<>(1){}; //new Foo<Integer> created
        Foo<?> f7 = p.new Foo<>(1){}; //new Foo<Object> created
        Foo<? super Integer> f8 = p.new Foo<>(1){}; //new Foo<Object> created

        Foo<Integer> f9 = p.new Foo<>(1, ""); //new Foo<Integer> created
        Foo<? extends Integer> f10 = p.new Foo<>(1, ""); //new Foo<Integer> created
        Foo<?> f11 = p.new Foo<>(1, ""); //new Foo<Object> created
        Foo<? super Integer> f12 = p.new Foo<>(1, ""); //new Foo<Object> created

        Foo<Integer> f13 = p.new Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<? extends Integer> f14 = p.new Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<?> f15 = p.new Foo<>(1, ""){}; //new Foo<Object> created
        Foo<? super Integer> f16 = p.new Foo<>(1, ""){}; //new Foo<Object> created
    }

    public static void main(String[] args) {
        Pos03<String> p3 = new Pos03<>();
        p3.testSimple();
        p3.testQualified_1();
        p3.testQualified_2(p3);
    }
}
