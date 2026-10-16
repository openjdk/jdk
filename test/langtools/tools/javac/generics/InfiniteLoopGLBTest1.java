/*
 * @test /nodynamiccopyright/
 * @bug 8388986
 * @summary infinite loop in Types.glbFlattened
 * @compile/fail/ref=InfiniteLoopGLBTest1.out -XDrawDiagnostics InfiniteLoopGLBTest1.java
 */

import java.util.List;

class InfiniteLoopGLBTest1 {
    class Min {
        void m(Object o) {
            ((C<? super List<Double>, ?>) o).f().hashCode();
        }
    }

    class B<F, G extends F> {}

    class C<N extends List<Number>, R extends B<? extends N, ? extends N>> {
        R f() {
            return null;
        }
    }
}
