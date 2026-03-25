/*
 * @test /nodynamiccopyright/
 * @bug 8334248
 * @summary Invalid error for early construction local class constructor method reference
 * @build InitializationWarningTester
 * @run main InitializationWarningTester EarlyIndirectOuterCapture
 */

public class EarlyIndirectOuterCapture {

    EarlyIndirectOuterCapture() {
        this(null);
    }

    EarlyIndirectOuterCapture(InnerSuperclass inner) { }

    class InnerSuperclass { }

    static class InnerOuter extends EarlyIndirectOuterCapture {     // accessible
        class InnerInnerOuter extends EarlyIndirectOuterCapture {   // not accessible
            InnerInnerOuter() {
                super(new InnerSuperclass() { }); // should this be accepted?, InnerSuperclass is not an inner class of InnerInnerOuter
            }

            InnerInnerOuter(boolean b) {
                super(InnerOuter.this.new InnerSuperclass() { }); // ok, explicit
            }
        }
    }
}
