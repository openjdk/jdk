/*
 * @test
 * @summary Negative test for singleobject classes
 * @compile/fail/ref=SingleObjectNegTest.out -XDrawDiagnostics SingleObjectNegTest.java
 */

public class SingleObjectNegTest {

    singleobject class S1 {
    }

    abstract singleobject class S2 {
    }

    static class Sub extends S1 {
    }

    singleobject class S3 {
        public S3() {}
    }

    void test() {
        S1 s = new S1();
    }
}
