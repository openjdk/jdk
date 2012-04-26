/**
 * @test /nodynamiccopyright/
 * @bug 7154127
 * @summary Inference cleanup: remove bound check analysis from visitors in Types.java
 * @compile/fail/ref=T7154127.out -XDrawDiagnostics T7154127.java
 */
class T7154127 {

    static class B<V> {}

    static class D extends B<E> {}
    static class E extends B<D> {}

    static class Triple<U,V,W> { }

    static <T, Y extends B<U>, U extends B<Y>> Triple<T, Y, U> m() { return null; }

    void test() {
       Triple<B, ? extends D, ? extends E> t = m();
    }
}
