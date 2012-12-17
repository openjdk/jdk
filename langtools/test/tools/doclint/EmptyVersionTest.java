/*
 * @test /nodynamiccopyright/
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyVersionTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyVersionTest.out EmptyVersionTest.java
 */

/** . */
public class EmptyVersionTest {
    /** @version */
    int missingVersion() { }
}
