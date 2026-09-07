/*
 * @test /nodynamiccopyright/
 * @bug 8388948
 * @summary javac crashes with NullPointerException in Types.intersect during method type inference of complex
 *          overlapping intersection bounds
 * @compile/fail/ref=NPEInTypeIntersectTest.out -XDrawDiagnostics NPEInTypeIntersectTest.java
 */

import java.util.function.Function;

class NPEInTypeIntersectTest {
    interface MyFunction<A, B> extends Function<A, B> {}

    abstract class Container<T> {
        abstract T get();
    }

    class NestedContainer<X> extends Container<NestedContainer<X>> {
        @Override
        public NestedContainer<X> get() {
            return this;
        }
    }

    void foo() {
        NestedContainer<MyFunction<Integer[], String[]>> container = new NestedContainer<>();
        process(container, container);
    }

    private static <A, B, C extends A & B, D extends A & C, E extends B & C, F extends D & E> void process(
            Container<F> f, Container<E> e) {}
}
