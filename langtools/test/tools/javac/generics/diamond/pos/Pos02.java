/*
 * @test /nodynamiccopyright/
 * @bug 6840638
 *
 * @summary  Project Coin: Improved Type Inference for Generic Instance Creation (aka 'diamond')
 * @author mcimadamore
 * @compile Pos02.java -source 1.7
 * @run main Pos02
 */

public class Pos02 {

    static class Foo<X> {
        Foo(X x) {}
        <Z> Foo(X x, Z z) {}
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

    void testQualified() {
        Foo<Integer> f1 = new Pos02.Foo<>(1); //new Foo<Integer> created
        Foo<? extends Integer> f2 = new Pos02.Foo<>(1); //new Foo<Integer> created
        Foo<?> f3 = new Pos02.Foo<>(1); //new Foo<Object> created
        Foo<? super Integer> f4 = new Pos02.Foo<>(1); //new Foo<Object> created

        Foo<Integer> f5 = new Pos02.Foo<>(1){}; //new Foo<Integer> created
        Foo<? extends Integer> f6 = new Pos02.Foo<>(1){}; //new Foo<Integer> created
        Foo<?> f7 = new Pos02.Foo<>(1){}; //new Foo<Object> created
        Foo<? super Integer> f8 = new Pos02.Foo<>(1){}; //new Foo<Object> created

        Foo<Integer> f9 = new Pos02.Foo<>(1, ""); //new Foo<Integer> created
        Foo<? extends Integer> f10 = new Pos02.Foo<>(1, ""); //new Foo<Integer> created
        Foo<?> f11 = new Pos02.Foo<>(1, ""); //new Foo<Object> created
        Foo<? super Integer> f12 = new Pos02.Foo<>(1, ""); //new Foo<Object> created

        Foo<Integer> f13 = new Pos02.Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<? extends Integer> f14 = new Pos02.Foo<>(1, ""){}; //new Foo<Integer> created
        Foo<?> f15 = new Pos02.Foo<>(1, ""){}; //new Foo<Object> created
        Foo<? super Integer> f16 = new Pos02.Foo<>(1, ""){}; //new Foo<Object> created
    }

    public static void main(String[] args) {
        Pos02 p2 = new Pos02();
        p2.testSimple();
        p2.testQualified();
    }
}
