/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8005220
 * @summary javap must display repeating annotations
 */
import java.io.*;
import java.util.*;

/**
 * This class extends the abstract {@link Tester} test-driver, and
 * encapusulates a number of test-case classes (i.e. classes extending
 * this class and annotated with {@code TestCase}).
 * <p>
 * By default (no argument), this test runs all test-cases, except
 * if annotated with {@code ignore}.
 * <p>
 * Individual test cases can be executed using a run action.
 * <p>
 * Example: @run main RepeatingTypeAnnotations RepeatingTypeAnnotations$TC4
 * <p>
 * Note: when specific test-cases are run, additional debug output is
 * produced to help debugging. Test annotated with {@code ignore}
 * can be executed explicitly.
 */
public class RepeatingTypeAnnotations extends Tester {

    /**
     * Main method instantiates test and run test-cases.
     */
    public static void main(String... args) throws Exception {
        Tester tester = new RepeatingTypeAnnotations();
        tester.run(args);
    }

    /**
     * Testcases are classes extending {@code RepeatingTypeAnnotations},
     * and calling {@link setSrc}, followed by one or more invocations
     * of {@link verify} in the body of the constructor.
     */
    public RepeatingTypeAnnotations() {
        setSrc(new TestSource(template));
    }

    /**
     * Common template for test cases. The line TESTCASE is
     * replaced with the specific lines of individual tests.
     */
    private static final String[] template = {
        "import java.lang.annotation.*;",
        "class Test {",
        "    @Repeatable(As.class)",
        "    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})",
        "    @Retention(RetentionPolicy.CLASS)",
        "    @interface A {",
        "        Class f() default int.class;",
        "    }",

        "    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})",
        "    @Retention(RetentionPolicy.CLASS)",
        "    @interface As { A[] value(); }",

        "    @Repeatable(Bs.class)",
        "    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})",
        "    @Retention(RetentionPolicy.CLASS)",
        "    @interface B {",
        "        Class f() default int.class;",
        "    }",

        "    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})",
        "    @Retention(RetentionPolicy.CLASS)",
        "    @interface Bs { B[] value(); }",

        "    @Repeatable(Cs.class)",
        "    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})",
        "    @Retention(RetentionPolicy.RUNTIME)",
        "    @interface C {",
        "        Class f() default int.class;",
        "    }",

        "    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})",
        "    @Retention(RetentionPolicy.RUNTIME)",
        "    @interface Cs { C[] value(); }",
        "TESTCASE",
        "}"
    };

    /*
     * The test cases covers annotation in the following locations:
     * - static and non-static fields
     * - local variables
     * - constructor and method return type and parameter types
     * - casts in class and method contexts.
     * For the above locations the test-cases covers:
     * - single annotation type
     * - two annotation types with same retention
     * - two annotation types with different retention
     * - three annotation types, two of same retention, one different.
     */

    @TestCase
    @ignore // 8008082:missing type annotation for cast
    public static class TC1 extends RepeatingTypeAnnotations {
        public TC1() {
            setSrc("    static String so = \"hello world\";",
                   "    public @A @A @A Object o = (@A @A @A String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "0: #25(#26=[@#27(),@#27(),@#27()]): FIELD",
                   "1: #25(#26=[@#27(),@#27(),@#27()]): CAST, offset=5");
        }
    }

    @TestCase
    public static class TC2 extends RepeatingTypeAnnotations {
        public TC2() {
            setSrc("    static String so = \"hello world\";",
                   "    public @A @B @A Object o = (@B @A @B String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "0: #25(#26=[@#27(),@#27()]): FIELD",
                   "1: #28(): FIELD",
                   "2: #29(#26=[@#28(),@#28()]): CAST, offset=5",
                   "3: #27(): CAST, offset=5");
        }
    }

    @TestCase
    public static class TC3 extends RepeatingTypeAnnotations {
        public TC3() {
            setSrc("    static String so = \"hello world\";",
                   "    public @A @A @C Object o = (@B @C @B String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "0: #25(): FIELD",
                   "1: #25(): CAST, offset=5",
                   "RuntimeVisibleTypeAnnotations",
                   "0: #27(#28=[@#29(),@#29()]): FIELD",
                   "1: #30(#28=[@#31(),@#31()]): CAST, offset=5");
        }
    }

    @TestCase
    public static class TC4 extends RepeatingTypeAnnotations {
        public TC4() {
            setSrc("    static String so = \"hello world\";",
                   "    public @A @B @C Object o = (@C @B @A String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "RuntimeVisibleTypeAnnotations",
                   "0: #25(): FIELD",
                   "1: #25(): CAST, offset=5",
                   "0: #27(): FIELD",
                   "1: #28(): FIELD",
                   "2: #28(): CAST, offset=5",
                   "3: #27(): CAST, offset=5");
        }
    }

    @TestCase
    @ignore // 8008082:missing type annotation for cast
    public static class TC5 extends RepeatingTypeAnnotations {
        public TC5() {
            setSrc("    static String so = \"hello world\";",
                   "    public static @A @A @A Object o = (@B @B @B String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "0: #25(#26=[@#27(),@#27(),@#27()]): FIELD",
                   "1: #28(#26=[@#29(),@#29(),@#29()]): CAST, offset=5, type_index=0");
        }
    }

    @TestCase
    public static class TC6 extends RepeatingTypeAnnotations {
        public TC6() {
            setSrc("    static String so = \"hello world\";",
                   "    public static @A @B @A Object o = (@B @A @B String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "0: #25(#26=[@#27(),@#27()]): FIELD",
                   "1: #28(): FIELD",
                   "2: #29(#26=[@#28(),@#28()]): CAST, offset=5",
                   "3: #27(): CAST, offset=5");
        }
    }

    @TestCase
    public static class TC7 extends RepeatingTypeAnnotations {
        public TC7() {
            setSrc("    static String so = \"hello world\";",
                   "    public static @A @A @C Object o = (@B @C @B String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "RuntimeVisibleTypeAnnotations",
                   "0: #25(): FIELD",
                   "1: #25(): CAST, offset=5",
                   "0: #27(#28=[@#29(),@#29()]): FIELD",
                   "1: #30(#28=[@#31(),@#31()]): CAST, offset=5");
        }
    }

    @TestCase
    public static class TC8 extends RepeatingTypeAnnotations {
        public TC8() {
            setSrc("    static String so = \"hello world\";",
                   "    public static @A @B @C Object o = (@C @B @A String) Test.so;");
            verify("RuntimeInvisibleTypeAnnotations",
                   "RuntimeVisibleTypeAnnotations",
                   "0: #25(): FIELD",
                   "1: #25(): CAST, offset=5",
                   "0: #27(): FIELD",
                   "1: #28(): FIELD",
                   "2: #28(): CAST, offset=5",
                   "3: #27(): CAST, offset=5");
        }
    }

    @TestCase
    @ignore // 8008082:missing type annotation for cast
    public static class TC9 extends RepeatingTypeAnnotations {
        public TC9() {
            setSrc("    public Test(@A @A @A Object o, @A int i, long l) {",
                   "        @A @A @A String ls = (@B @B @B String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations",
                   "0: #34(#35=[@#36(),@#36(),@#36()]): METHOD_FORMAL_PARAMETER, param_index=0",
                   "1: #36(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "2: #37(#35=[@#38(),@#38(),@#38()]): CAST, offset=4, type_index=0",
                   "3: #34(#35=[@#36(),@#36(),@#36()]): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}");
        }
    }

    @TestCase
    public static class TC10 extends RepeatingTypeAnnotations {
        public TC10() {
            setSrc("    public Test(@A @A @B Object o, @A @B int i, long l) {",
                   "        @A @A @B String ls = (@B @A @B String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations:",
                   "0: #34(#35=[@#36(),@#36()]): METHOD_FORMAL_PARAMETER, param_index=0",
                   "1: #37(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "2: #36(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "3: #37(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "4: #38(#35=[@#37(),@#37()]): CAST, offset=4, type_index=0",
                   "5: #36(): CAST, offset=4, type_index=0",
                   "6: #34(#35=[@#36(),@#36()]): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}",
                   "7: #37(): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}");
        }
    }

    @TestCase
    public static class TC11 extends RepeatingTypeAnnotations {
        public TC11() {
            setSrc("    public Test(@C @C @A Object o, @A @B int i, long l) {",
                   "        @C @C @A String ls = (@A @A @C String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations",
                   "RuntimeVisibleTypeAnnotations",
                   "0: #34(#35=[@#36(),@#36()]): METHOD_FORMAL_PARAMETER, param_index=0",
                   "1: #36(): CAST, offset=4",
                   "2: #34(#35=[@#36(),@#36()]): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}",
                   "0: #38(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "1: #38(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "2: #39(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "3: #40(#35=[@#38(),@#38()]): CAST, offset=4",
                   "4: #38(): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}");
        }
    }

    @TestCase
    public static class TC12 extends RepeatingTypeAnnotations {
        public TC12() {
            setSrc("    public Test(@A @B @C Object o, @A @C int i, long l) {",
                   "        @A @B @C String ls = (@C @A @B String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations",
                   "RuntimeVisibleTypeAnnotations",
                   "0: #34(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "1: #34(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "2: #34(): CAST, offset=4",
                   "3: #34(): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}",
                   "0: #36(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "1: #37(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "2: #36(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "3: #36(): CAST, offset=4",
                   "4: #37(): CAST, offset=4",
                   "5: #36(): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}",
                   "6: #37(): LOCAL_VARIABLE, {start_pc=10, length=1, index=5}");
        }
    }

    @TestCase
    @ignore // 8008082:missing type annotation for cast
    public static class TC13 extends RepeatingTypeAnnotations {
        public TC13() {
            setSrc("    public @A @A @A String foo(@A @A @A Object o, @A int i, long l) {",
                   "        @A @A @A String ls = (@B @B @B String) o;",
                   "        return (@A @A @A String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations",
                   "0: #36(#37=[@#38(),@#38(),@#38()]): METHOD_RETURN",
                   "1: #36(#37=[@#38(),@#38(),@#38()]): METHOD_FORMAL_PARAMETER, param_index=0",
                   "2: #38(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "3: #39(#37=[@#40(),@#40(),@#40()]): CAST, offset=0, type_index=0",
                   "4: #36(#37=[@#38(),@#38(),@#38()]): CAST, offset=6, type_index=0",
                   "5: #36(#37=[@#38(),@#38(),@#38()]): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}");
        }
    }

    @TestCase
    public static class TC14 extends RepeatingTypeAnnotations {
        public TC14() {
            setSrc("    public @A @B @B String foo(@A @A @B Object o, @A @B int i, long l) {",
                   "        @A @A @B String ls = (@B @A @B String) o;",
                   "        return (@A @B @B String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations",
                    "0: #36(): METHOD_RETURN",
                    "1: #37(#38=[@#39(),@#39()]): METHOD_RETURN",
                    "2: #40(#38=[@#36(),@#36()]): METHOD_FORMAL_PARAMETER, param_index=0",
                    "3: #39(): METHOD_FORMAL_PARAMETER, param_index=0",
                    "4: #36(): METHOD_FORMAL_PARAMETER, param_index=1",
                    "5: #39(): METHOD_FORMAL_PARAMETER, param_index=1",
                    "6: #37(#38=[@#39(),@#39()]): CAST, offset=0",
                    "7: #36(): CAST, offset=0",
                    "8: #36(): CAST, offset=6",
                    "9: #37(#38=[@#39(),@#39()]): CAST, offset=6",
                    "10: #40(#38=[@#36(),@#36()]): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}",
                    "11: #39(): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}");
        }
    }

    @TestCase
    public static class TC15 extends RepeatingTypeAnnotations {
        public TC15() {
            setSrc("    public @A @A @C String foo(@C @C @A Object o, @A @B int i, long l) {",
                   "        @C @C @A String ls = (@A @A @C String) o;",
                   "        return (@C @B @B String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations",
                    "RuntimeVisibleTypeAnnotations",
                    "0: #36(): METHOD_RETURN",
                    "1: #37(#38=[@#36(),@#36()]): METHOD_FORMAL_PARAMETER, param_index=0",
                    "2: #36(): CAST, offset=0",
                    "3: #36(): CAST, offset=6",
                    "4: #37(#38=[@#36(),@#36()]): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}",
                    "0: #40(#38=[@#41(),@#41()]): METHOD_RETURN",
                    "1: #41(): METHOD_FORMAL_PARAMETER, param_index=0",
                    "2: #41(): METHOD_FORMAL_PARAMETER, param_index=1",
                    "3: #42(): METHOD_FORMAL_PARAMETER, param_index=1",
                    "4: #40(#38=[@#41(),@#41()]): CAST, offset=0",
                    "5: #43(#38=[@#42(),@#42()]): CAST, offset=6",
                    "6: #41(): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}");
        }
    }

    @TestCase
    public static class TC16 extends RepeatingTypeAnnotations {
        public TC16() {
            setSrc("    public @A @B @C String foo(@A @B @C Object o, @A @C int i, long l) {",
                   "        @A @B @C String ls = (@C @A @B String) o;",
                   "        return (@B @A @C String) o;",
                   "    }");
            verify("RuntimeInvisibleTypeAnnotations",
                   "RuntimeVisibleTypeAnnotations",
                   "0: #36(): METHOD_RETURN",
                   "1: #36(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "2: #36(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "3: #36(): CAST, offset=0",
                   "4: #36(): CAST, offset=6",
                   "5: #36(): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}",
                   "0: #38(): METHOD_RETURN",
                   "1: #39(): METHOD_RETURN",
                   "2: #38(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "3: #39(): METHOD_FORMAL_PARAMETER, param_index=0",
                   "4: #38(): METHOD_FORMAL_PARAMETER, param_index=1",
                   "5: #38(): CAST, offset=0",
                   "6: #39(): CAST, offset=0",
                   "7: #39(): CAST, offset=6",
                   "8: #38(): CAST, offset=6",
                   "9: #38(): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}",
                   "10: #39(): LOCAL_VARIABLE, {start_pc=6, length=5, index=5}");
        }
    }
}
