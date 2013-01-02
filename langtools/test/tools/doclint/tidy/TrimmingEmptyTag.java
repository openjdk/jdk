/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref TrimmingEmptyTag.out TrimmingEmptyTag.java
 */

// tidy: Warning: trimming empty <.*>

/**
 * <b></b>
 * <table summary=description></table>
 * <table><caption></caption></table>
 * <code></code>
 * <dl></dl>
 * <dl><dt></dt><dd></dd></dl>
 * <font></font>
 * <i></i>
 * <ol></ol>
 * <p></p>
 * <pre></pre>
 * <span></span>
 * <tt></tt>
 * <ul></ul>
 * <ul><li></li></ul>
 */
public class TrimmingEmptyTag { }
