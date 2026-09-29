/*
 * @test /nodynamiccopyright/
 * @bug 8337980
 * @summary Test compiler crash due to failure to resolve method ambiguity
 * @compile/fail/ref=MethodAmbiguityCrash2.out -XDrawDiagnostics MethodAmbiguityCrash2.java
 */
public class MethodAmbiguityCrash2 {

    public interface A {
        int op();
    }

    public abstract static class B {
        public abstract int op();
    }

    public abstract static class C extends B implements A {

        public C(int x) {
        }

        public C() {
            this(op());     // compile should fail here
        }
    }
}
