/*
 * @test /nodynamiccopyright/
 * @bug 8337980
 * @summary Test compiler crash due to failure to resolve method ambiguity
 * @compile/fail/ref=MethodAmbiguityCrash1.out -XDrawDiagnostics MethodAmbiguityCrash1.java
 */
public class MethodAmbiguityCrash1 {

    public interface A {
        int op();
    }

    public abstract static class B {
        abstract int op();
    }

    public abstract static class C extends B implements A {

        public static int test() {
            return op();    // compile should fail here
        }
    }
}
