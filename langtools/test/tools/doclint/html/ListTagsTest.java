/*
 * @test /nodynamiccopyright/
 * @bug 8006251 8013405
 * @summary test list tags
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs -ref ListTagsTest.out ListTagsTest.java
 */

/** */
public class ListTagsTest {
    /**
     *  <dl> <dt> abc <dd> def </dl>
     *  <ol> <li> abc </ol>
     *  <ol> <li value="1"> abc </ol>
     *  <ol> <li value> bad </ol>
     *  <ol> <li value="a"> bad </ol>
     *  <ul> <li> abc </ul>
     */
    public void supportedTags() { }
}
