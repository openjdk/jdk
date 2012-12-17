/*
 * @test /nodynamiccopyright/
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptySinceTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptySinceTest.out EmptySinceTest.java
 */

/** . */
public class EmptySinceTest {
    /** @since */
    int emptySince() { }
}
