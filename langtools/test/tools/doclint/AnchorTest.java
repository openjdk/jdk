/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @build DocLintTester
 * @run main DocLintTester -ref AnchorTest.out AnchorTest.java
 */

/** */
public class AnchorTest {
    // tests for <a name=value>

    /**
     * <a name=foo></a>
     */
    public void a_name_foo() { }

    /**
     * <a name=foo></a>
     */
    public void a_name_already_defined() { }

    /**
     * <a name=></a>
     */
    public void a_name_empty() { }

    /**
     * <a name=123 ></a>
     */
    public void a_name_invalid() { }

    /**
     * <a name ></a>
     */
    public void a_name_missing() { }

    // tests for <a id=value>

    /**
     * <a id=a_id_foo></a>
     */
    public void a_id_foo() { }

    /**
     * <a id=foo></a>
     */
    public void a_id_already_defined() { }

    /**
     * <a id=></a>
     */
    public void a_id_empty() { }

    /**
     * <a id=123 ></a>
     */
    public void a_id_invalid() { }

    /**
     * <a id ></a>
     */
    public void a_id_missing() { }

    // tests for id=value on non-<a> tags

    /**
     * <p id=p_id_foo>text</p>
     */
    public void p_id_foo() { }

    /**
     * <p id=foo>text</p>
     */
    public void p_id_already_defined() { }

    /**
     * <p id=>text</p>
     */
    public void p_id_empty() { }

    /**
     * <p id=123 >text</p>
     */
    public void p_id_invalid() { }

    /**
     * <p id >text</p>
     */
    public void p_id_missing() { }


}
