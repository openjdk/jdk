/*
 * @test /nodynamiccopyright/
 * @bug 8388986
 * @summary infinite loop in Types.glbFlattened
 * @compile/fail/ref=InfiniteLoopGLBTest2.out -XDrawDiagnostics InfiniteLoopGLBTest2.java
 */

class InfiniteLoopGLBTest2 {
    class Main {
        static public final C<? super A<Double>, ?, ?> test() {
            final C<? super A<Double>, ?, ?> x = null;
            x.f().f(null);
            return x;
        }
    }

    class A<T> {}

    abstract class B<F, B extends F> {}

    abstract class C<N extends A<Number>, R extends B<? extends N, ? extends N>, E extends Object & Runnable & Comparable<? super E>> {
        abstract public R f();
        public E f(){return null;};
    }
}
