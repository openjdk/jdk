/*
 * @test /nodynamiccopyright/
 * @bug 8003280
 * @summary Add lambda tests
 *  check case of ambiguous method call with lambda whose body cannot
            complete normally
 * @compile/fail/ref=TargetType23.out -XDrawDiagnostics TargetType23.java
 */

class TargetType23 {

    interface Sam0 {
        void m();
    }

    interface Sam1 {
        int m();
    }

    interface Sam2 {
        String m();
    }

    interface Sam3<A> {
        A m();
    }


    void call(Sam0 s) { }
    void call(Sam1 s) { }
    void call(Sam2 s) { }
    <Z> void call(Sam3<Z> s) { }

    void test() {
        call(()-> { throw new RuntimeException(); }); //ambiguous - both call(Sam0), call(Sam2), call(Sam3) match
    }
}
