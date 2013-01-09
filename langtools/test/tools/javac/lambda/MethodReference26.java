/*
 * @test /nodynamiccopyright/
 * @bug 8003280
 * @summary Add lambda tests
 *  check strict method conversion does not allow loose method reference conversion
 * @compile/fail/ref=MethodReference26.out -XDrawDiagnostics MethodReference26.java
 */

class MethodReference26 {

    static void m(Integer i) { }

    interface SAM {
        void m(int x);
    }

    static void call(int i, SAM s) {   }
    static void call(Integer i, SAM s) {   }

    static void test() {
        call(1, MethodReference26::m); //ambiguous
    }
}
