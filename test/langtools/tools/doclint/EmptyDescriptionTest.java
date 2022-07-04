
/*
 * @test /nodynamiccopyright/
 * @bug 8272374
 * @summary doclint should report missing "body" comments
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-missing EmptyDescriptionTest.java
 * @run main DocLintTester -Xmsgs:missing -ref EmptyDescriptionTest.out EmptyDescriptionTest.java
 */

/** . */
public class EmptyDescriptionTest {
    // a default constructor triggers its own variant of "no comment"

    // no comment
    public int f1;

    // empty comment
    /** */
    public int f2;

    // empty description
    /**
     * @since 1.0
     */
    public int f3;

    // deprecated: no diagnostic
    /**
     * @deprecated do not use
     */
    public int f4;

    // no comment
    public int m1() { return 0; }

    // empty comment
    /** */
    public int m2() { return 0; }

    // empty description
    /**
     * @return 0
     */
    public int m3() { return 0; }

    // deprecated: no diagnostic
    /**
     * @deprecated do not use
     * @return 0
     */
    public int m4() { return 0; };

    /**
     * A class containing overriding methods.
     * Overriding methods with missing/empty comments do not generate messages
     * since they are presumed to inherit descriptions as needed.
     */
    public static class Nested extends EmptyDescriptionTest {
        /** . */ Nested() { }

        @Override
        public int m1() { return 1; }

        // empty comment
        /** */
        @Override
        public int m2() { return 1; }

        // empty description
        /**
         * @return 1
         */
        @Override
        public int m3() { return 1; }

    }
}
