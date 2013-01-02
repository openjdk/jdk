/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyVersionTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyVersionTest.out EmptyVersionTest.java
 */

/** . */
public class EmptyVersionTest {
    /** @version */
    int missingVersion() { }
}
