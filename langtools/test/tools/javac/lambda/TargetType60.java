/*
 * @test /nodynamiccopyright/
 * @bug 8007462
 * @summary Fix provisional applicability for method references
 * @compile/fail/ref=TargetType60.out -XDrawDiagnostics TargetType60.java
 */
class TargetType60 {

    interface Sam0 {
        void m();
    }

    interface Sam1<X> {
        void m(X x);
    }

    interface Sam2<X,Y> {
        void m(X x, Y y);
    }

    void m0() { }
    void m1(String s) { }
    void m2(String s1, String s2) { }

    void m01() { }
    void m01(String s) { }

    void m012() { }
    void m012(String s) { }
    void m012(String s1, String s2) { }

    static String g(Sam0 s) { return null; }
    static <U> U g(Sam1<U> s) { return null; }
    static <U> U g(Sam2<U,String> s) { return null; }

    void testBound() {
        String s1 = g(this::m0); //ok - resolves to g(Sam0)
        String s2 = g(this::m1); //ok - resolves to g(Sam1)
        String s3 = g(this::m2); //ok - resolves to g(Sam2)
        String s4 = g(this::m01);//ambiguous (g(Sam0), g(Sam1) apply)
        String s5 = g(this::m012);//ambiguous (g(Sam0), g(Sam1), g(Sam2) apply)
    }

    static void testUnbound() {
        TargetType60 s1 = g(TargetType60::m0); //ok - resolves to g(Sam1)
        TargetType60 s2 = g(TargetType60::m1); //ok - resolves to g(Sam2)
        TargetType60 s3 = g(TargetType60::m2); //none is applicable
        TargetType60 s4 = g(TargetType60::m01);//ambiguous (g(Sam1), g(Sam2) apply)
        TargetType60 s5 = g(TargetType60::m012);//ambiguous (g(Sam1), g(Sam2) apply)
    }
}
