/*
 * @test /nodynamiccopyright/
 * @bug 8388986
 * @summary infinite loop when removing type containment pair in Types.containsTypeRecursive
 * @compile/fail/ref=InfiniteLoopGLBTest.out -XDrawDiagnostics InfiniteLoopGLBTest.java
 */

import java.util.List;

class InfiniteLoopGLBTest {
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
