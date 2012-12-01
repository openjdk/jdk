/*
 * @test /nodynamiccopyright/
 * @bug 8003280
 * @summary Add lambda tests
 *  simple test for lambda candidate check
 * @compile/fail/ref=LambdaConv18.out -XDrawDiagnostics -XDidentifyLambdaCandidate=true LambdaConv18.java
 */

class LambdaConv18 {

    interface SAM {
        void m();
    }

    interface NonSAM {
        void m1();
        void m2();
    }

    SAM s1 = new SAM() { public void m() {} };
    NonSAM s2 = new NonSAM() { public void m1() {}
                              public void m2() {} };
    NonExistent s3 = new NonExistent() { public void m() {} };
}
