/*
 * @test /nodynamiccopyright/
 * @bug 8207224
 * @summary Javac compiles source code despite illegal use of unchecked conversions
 * @compile/fail/ref=ReturnTypeSubstitutableTest.out -XDrawDiagnostics ReturnTypeSubstitutableTest.java
 * @compile -source 12 ReturnTypeSubstitutableTest.java
 */

class ReturnTypeSubstitutableTest {
    abstract class AbstractDemo<Request extends AbstractResult, Response extends AbstractResult> {
        protected abstract Response test(Request request);
    }

    abstract interface AbstractResult {}

    abstract interface SimpleResult extends AbstractResult {}

    class Result1 implements SimpleResult {}

    class OtherResult implements AbstractResult {}

    public class SimpleDemo<Request extends AbstractResult, Response extends AbstractResult> extends AbstractDemo<Request, Response> {
        @Override
        protected SimpleResult test(AbstractResult request) {
            return null;
        }
    }
}
