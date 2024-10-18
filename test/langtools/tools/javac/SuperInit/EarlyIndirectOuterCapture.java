/*
 * @test /nodynamiccopyright/
 * @bug 8334248
 * @summary Invalid error for early construction local class constructor method reference
 * @compile/fail/ref=EarlyIndirectOuterCapture.out -XDrawDiagnostics EarlyIndirectOuterCapture.java
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
                super(/* which enclosing instance here ? */new InnerSuperclass() { });
            }

            InnerInnerOuter(boolean b) {
                super(InnerOuter.this.new InnerSuperclass() { }); // ok, explicit
            }
        }
    }
}
