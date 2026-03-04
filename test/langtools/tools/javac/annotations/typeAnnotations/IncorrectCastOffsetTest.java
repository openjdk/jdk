/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8214934
 * @summary Wrong type annotation offset on casts on expressions
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavapTask
 * @run compile IncorrectCastOffsetTest.java
 * @run main IncorrectCastOffsetTest
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;

import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

public class IncorrectCastOffsetTest {
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TypeUse {}

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TypeUse2 {}

    class AnnotatedCast1 {
        private static String checkcast(boolean test, Object obj, Object obj2) {
            return (@TypeUse String)(test ? obj : obj2);
        }
    }

    class AnnotatedCast2 {
        private static String checkcast(Object obj) {
            return (@TypeUse String)(obj);
        }
    }

    class AnnotatedCast3 {
        private static String checkcast(boolean test, Object obj, Object obj2) {
            return (@TypeUse @TypeUse2 String)(test ? obj : obj2);
        }
    }

    class AnnotatedCast4 {
        private static String checkcast(Object obj) {
            return (@TypeUse String)(@TypeUse2 CharSequence)(obj);
        }
    }

    ToolBox tb;

    IncorrectCastOffsetTest() {
        tb = new ToolBox();
    }

    public static void main(String args[]) {
        IncorrectCastOffsetTest incorrectCastOffsetTest = new IncorrectCastOffsetTest();
        incorrectCastOffsetTest.run();
    }

    void run() {
        test("IncorrectCastOffsetTest$AnnotatedCast1.class",
                /*
                 * generated code:
                 *  0: iload_0
                 *  1: ifeq          8
                 *  4: aload_1
                 *  5: goto          9
                 *  8: aload_2
                 *  9: checkcast     #13                 // class java/lang/String
                 * 12: areturn
                 */
                List.of(
                        "RuntimeVisibleTypeAnnotations:",
                        "0: #35(): CAST, offset=9, type_index=0"
                )
        );
        test("IncorrectCastOffsetTest$AnnotatedCast2.class",
                /*
                 * generated code:
                 *  0: aload_0
                 *  1: checkcast     #13                 // class java/lang/String
                 *  4: areturn
                 */
                List.of(
                        "RuntimeVisibleTypeAnnotations:",
                        "0: #31(): CAST, offset=1, type_index=0"
                )
        );
        test("IncorrectCastOffsetTest$AnnotatedCast3.class",
                /*
                 * generated code:
                 *  0: iload_0
                 *  1: ifeq          8
                 *  4: aload_1
                 *  5: goto          9
                 *  8: aload_2
                 *  9: checkcast     #13                 // class java/lang/String
                 * 12: areturn
                 */
                List.of(
                        "RuntimeVisibleTypeAnnotations:",
                        "0: #35(): CAST, offset=9, type_index=0",
                        "IncorrectCastOffsetTest$TypeUse",
                        "1: #36(): CAST, offset=9, type_index=0",
                        "IncorrectCastOffsetTest$TypeUse2"
                )
        );
        test("IncorrectCastOffsetTest$AnnotatedCast4.class",
                /*
                 * generated code:
                 *  0: aload_0
                 *  1: checkcast     #13                 // class java/lang/CharSequence
                 *  4: checkcast     #15                 // class java/lang/String
                 *  7: areturn
                 */
                List.of(
                        "RuntimeVisibleTypeAnnotations:",
                        "0: #33(): CAST, offset=4, type_index=0",
                        "IncorrectCastOffsetTest$TypeUse",
                        "#34(): CAST, offset=1, type_index=0",
                        "IncorrectCastOffsetTest$TypeUse2"
                )
        );
    }

    void test(String clazz, List<String> expectedOutput) {
        Path pathToClass = Paths.get(ToolBox.testClasses, clazz);
        String javapOut = new JavapTask(tb)
                .options("-v", "-p")
                .classes(pathToClass.toString())
                .run()
                .getOutput(Task.OutputKind.DIRECT);

        for (String expected : expectedOutput) {
            if (!javapOut.contains(expected))
                throw new AssertionError("unexpected output");
        }
    }

}
