/*
 * @test /nodynamiccopyright/
 * @bug 8382877
 * @compile/ref=HeapPollutionParam.out -Xlint:unchecked -XDrawDiagnostics HeapPollutionParam.java
 */
abstract class HeapPollutionParam<A> {
    abstract <A> HeapPollutionParam<A> m1(A head, A... tail);
    @SuppressWarnings("unchecked") abstract <A> HeapPollutionParam<A> m2(A head, A... tail);
    abstract <A> HeapPollutionParam<A> m3(@SuppressWarnings("unchecked") A head, A... tail);
    abstract <A> HeapPollutionParam<A> m4(A head, @SuppressWarnings("unchecked") A... tail);
}
