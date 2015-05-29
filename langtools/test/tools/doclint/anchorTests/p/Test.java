/* @test /nodynamiccopyright/
 * @bug 8025246
 * @summary doclint is showing error on anchor already defined when it's not
 * @library ../..
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref Test.out Test.java
 * @compile/fail/ref=Test.javac.out -XDrawDiagnostics -Werror -Xdoclint:all Test.java
 */

package p;

/**
 * <a name="dupTest">dupTest</a>
 * <a name="dupTest">dupTest again</a>
 *
 * <a name="dupTestField">dupTestField</a>
 * <a name="dupTestMethod">dupTestMethod</a>

 * <a name="okClass">okClass</a>
 * <a name="okField">okField</a>
 * <a name="okMethod">okMethod</a>
 */
public class Test {
    /** <a name="dupTestField">dupTestField again</a> */
    public int f;

    /** <a name="dupTestMethod">dupTestMethod again</a> */
    public void m() { }

    /**
     * <a name="dupNested">dupNested</a>
     * <a name="dupNested">dupNested again</a>
     * <a name="dupNestedField">dupNestedField</a>
     * <a name="dupNestedMethod">dupNestedMethod</a>
     *
     * <a name="okClass">okClass again</a>
     */
    public class Nested {
        /**
         * <a name="dupNestedField">dupNestedField</a>
         *
         * <a name="okField">okField again</a>
         */
        public int f;

        /**
         * <a name="dupNestedMethod">dupNestedMethod</a>
         *
         * <a name="okMethod">okMethod again</a>
         */
        public void m() { }
    }
}
