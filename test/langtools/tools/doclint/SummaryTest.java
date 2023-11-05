/*
 * @test /nodynamiccopyright/
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref SummaryTest.out SummaryTest.java
 */

/** No comment. */
public class SummaryTest {
    /**
     {@summary legal} **/
    public void m_legal() {}

    /** <p> {@summary illegal} **/
    public void m_illegal_html() {}

    /** text {@summary illegal} **/
    public void m_illegal_text() {}

    /** &amp;{@summary illegal} **/
    public void m_illegal_entity() {}

    /** @@{@summary illegal} **/
    public void m_illegal_escape() {}

    /** {@summary legal} text {@summary illegal} **/
    public void m_illegal_repeat() {}

    /** . */
    private SummaryTest() { }
}
