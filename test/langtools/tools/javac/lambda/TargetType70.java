/*
 * @test /nodynamiccopyright/
 * @bug 8011028 8375572
 * @summary wildcard function types cannot be derived from dependent bounds
 * @compile/fail/ref=TargetType70.out -XDrawDiagnostics TargetType70.java
 */
class TargetType70  {

    static class Sup {}
    static class Sub extends Sup {}

    interface I<T extends GenSup<U>, U> {
        T m(U o);
    }

    static class GenSup<T> {
        GenSup(T f) { }
    }

    static class GenSub<T> extends GenSup<T> {
        GenSub(T f) { super(f); }
    }

    <T extends Sup> void m(I<? extends GenSup<T>, T> o1, T o2) { }

    void test(Sub sub) {
        m(GenSub::new, sub);
    }
}
