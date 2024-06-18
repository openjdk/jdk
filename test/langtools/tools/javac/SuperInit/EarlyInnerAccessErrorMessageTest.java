/*
 * @test /nodynamiccopyright/
 * @bug 8334488
 * @summary Verify the error message generated for early access from inner class
 * @compile/fail/ref=EarlyInnerAccessErrorMessageTest.out -XDrawDiagnostics EarlyInnerAccessErrorMessageTest.java
 * @enablePreview
 */
public class EarlyInnerAccessErrorMessageTest {
    int x;
    EarlyInnerAccessErrorMessageTest() {
        class Inner {
            { System.out.println(x); }
        }
        super();
    }
}
