/*
 * @test /nodynamiccopyright/
 * @bug 8334488
 * @library /tools/javac/lib
 * @summary Verify the error message generated for early access from inner class
 * @modules jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @enablePreview
 * @compile/fail/ref=EarlyInnerAccessErrorMessageTest.out -XDrawDiagnostics EarlyInnerAccessErrorMessageTest.java
 * @build SuperCallRemover
 * @compile/fail/ref=EarlyInnerAccessErrorMessageTestWarnings.out -Xlint:initialization -Werror -XDrawDiagnostics -processor SuperCallRemover EarlyInnerAccessErrorMessageTest.java
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
