/*
 * @test /nodynamiccopyright/
 * @bug 8004832 8247815
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-missing EmptyVersionTest.java
 * @run main DocLintTester -Xmsgs:missing -ref EmptyVersionTest.out EmptyVersionTest.java
 */

/** . */
public class EmptyVersionTest {
    /** @version */
    void missingVersion() { }
}
