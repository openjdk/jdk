/*
 * @test /nodynamiccopyright/
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref ReturnTest.out ReturnTest.java
 */

/** No comment. */
public class ReturnTest {
    /**
     {@return legal} **/
    public int m_legal() { return 0; }

    /** <p> {@return illegal} **/
    public int m_illegal_html() { return 0; }

    /** text {@return illegal} **/
    public int m_illegal_text() { return 0; }

    /** &amp;{@return illegal} **/
    public int m_illegal_entity() { return 0; }

    /** @@{@return illegal} **/
    public int m_illegal_escape() { return 0; }

    /** {@return legal} text {@return illegal} **/
    public int m_illegal_repeat() { return 0; }

    /** . */
    private ReturnTest() { }
}
