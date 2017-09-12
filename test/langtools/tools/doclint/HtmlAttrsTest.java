/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-html HtmlAttrsTest.java
 * @run main DocLintTester -ref HtmlAttrsTest.out HtmlAttrsTest.java
 */

/** */
public class HtmlAttrsTest {
    /**
     * <p xyz> text </p>
     */
    public void unknown() { }

    /**
     * <img name="x" alt="alt">
     */
    public void obsolete() { }

    /**
     * <font size="3"> text </font>
     */
    public void obsolete_use_css() { }
}

