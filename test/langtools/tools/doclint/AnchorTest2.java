/*
 * @test /nodynamiccopyright/
 * @bug 8020313
 * @summary doclint doesn't reset HTML anchors correctly
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref AnchorTest2.out AnchorTest2.java AnchorTest2a.java
 * @run main DocLintTester -ref AnchorTest2.out AnchorTest2a.java AnchorTest2.java
 */

/** */
public class AnchorTest2 {
    /** <a name="AnchorTest2"> </a> */
    public void a_name_AnchorTest2() { }

    /** <a name="AnchorTest2"> </a> */
    public void a_name_AnchorTest2_already_defined() { }

    /** <a name="AnchorTest2a"> </a> */
    public void a_name_AnchorTest2a_defined_in_other_file() { }
}
