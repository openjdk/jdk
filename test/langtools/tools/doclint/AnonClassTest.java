/*
 * @test /nodynamiccopyright/
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-missing AnonClassTest.java
 * @run main DocLintTester -Xmsgs:missing -ref AnonClassTest.out AnonClassTest.java
 */

/** Class comment. */
public enum AnonClassTest {

    /**
     * E1 comment.
     * This member uses an anonymous class, which should not trigger a warning.
     */
    E1 {
        // no comment: should give warning
        int field;

        // no comment: should give warning
        void m() { }

        // no comment required: should not give warning
        @java.lang.Override
        public void m1() { }
    },

    // This member also uses an anonymous class,
    // but there should only be a single warning for the member itself.
    E2 { },

    /** E3 comment. This member does not use an anonymous class. */
    E3;

    /** Method comment. */
    public void m1() { }
}
