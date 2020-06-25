/*
 * @test /nodynamiccopyright/
 * @bug 8247955
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -XhtmlVersion:html5 -Xmsgs:-accessibility AccessibilityTest5.java
 * @run main DocLintTester -XhtmlVersion:html5 -ref AccessibilityTest5.out AccessibilityTest5.java
 */

// This test should be merged into AccessibilityTest.java when we drop support for html4.

/** */
public class AccessibilityTest5 {
    /**
     * <table><caption>ok</caption><tr><th>head<tr><td>data</table>
     */
    public void table_with_caption() { }

    /**
     * <table><tr><th>head<tr><td>data</table>
     */
    public void table_without_caption() { }

    /**
     * <table role="presentation"><tr><th>head<tr><td>data</table>
     */
    public void table_presentation() { }
}

