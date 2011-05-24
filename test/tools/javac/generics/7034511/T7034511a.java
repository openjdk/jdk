/*
 * @test /nodynamiccopyright/
 * @ignore backing out 7034511, see 7040883
 * @bug     7034511 7040883
 * @summary Loophole in typesafety
 * @compile/fail/ref=T7034511a.out -XDrawDiagnostics T7034511a.java
 */

class T7034511a {

    interface A<T> {
        void foo(T x);
    }

    interface B<T> extends A<T[]> { }

    static abstract class C implements B<Integer> {
        <T extends B<?>> void test(T x, String[] ss) {
            x.foo(ss);
        }
    }
}
