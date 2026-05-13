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
 * @bug 8384131
 * @summary Lowering of array enhanced for loop misses a synthetic cast
 * @library /tools/lib
 * @modules java.compiler
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.JavapTask
 * @run main T8384131
 */
import java.util.List;
import java.util.stream.Collectors;

import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

public class T8384131 {

    static class C { }
    interface I {
        default void f() {}
    }

    static class Test<Z extends C & I> {
        void testArray(Z[] arr) {
            for (I i : arr) {
                i.f();
            }
        }

        void testList(List<Z> l) {
            for (I i : l) {
                i.f();
            }
        }
    }

    static class CI extends C implements I { }

    public static void main(String[] args) throws Exception {
        // neg tests
        expectCCE(() -> new Test().testArray(new C[] { new C() }));
        expectCCE(() -> new Test().testList(List.of(new C())));

        // pos tests
        new Test<CI>().testArray(new CI[] { new CI() });
        checkPrimitiveConversionsResults();
        checkPrimitiveConversionsBytecode();
    }

    static void expectCCE(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected CCE");
        } catch (ClassCastException expected) {
            // expected
        } catch (IncompatibleClassChangeError wrong) {
            throw new AssertionError("got ICCE", wrong);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Conversions to Inspect">
    static <T extends Integer> int integerTypeVariableArrayToIntWideningReferenceAndUnboxing(T[] values) {
        for (int i : values) {
            return i;
        }
        return 0;
    }

    static <T extends Short> int shortTypeVariableArrayToIntWideningReferenceAndUnboxingAndPrimitiveWidening(T[] values) {
        for (int i : values) {
            return i;
        }
        return 0;
    }

    static long intArrayToLongPrimitiveWidening(int[] values) {
        for (long l : values) {
            return l;
        }
        return 0L;
    }

    static int integerArrayToIntUnboxing(Integer[] values) {
        for (int i : values) {
            return i;
        }
        return 0;
    }

    static long integerArrayToLongUnboxingAndPrimitiveWidening(Integer[] values) {
        for (long l : values) {
            return l;
        }
        return 0L;
    }

    static Integer intArrayToIntegerBoxing(int[] values) {
        for (Integer i : values) {
            return i;
        }
        return null;
    }

    static Object intArrayToObjectBoxingAndWideningReference(int[] values) {
        for (Object o : values) {
            return o;
        }
        return null;
    }
    // </editor-fold>

    static void checkPrimitiveConversionsResults() {
        expectEquals(1, integerTypeVariableArrayToIntWideningReferenceAndUnboxing(new Integer[] { 1 }));
        expectEquals(1, shortTypeVariableArrayToIntWideningReferenceAndUnboxingAndPrimitiveWidening(new Short[] { 1 }));
        expectEquals(1L, intArrayToLongPrimitiveWidening(new int[] { 1 }));
        expectEquals(1, integerArrayToIntUnboxing(new Integer[] { 1 }));
        expectEquals(1L, integerArrayToLongUnboxingAndPrimitiveWidening(new Integer[] { 1 }));
        expectEquals(Integer.valueOf(1), intArrayToIntegerBoxing(new int[] { 1 }));
        expectEquals(Integer.valueOf(1), intArrayToObjectBoxingAndWideningReference(new int[] { 1 }));
    }

    static void checkPrimitiveConversionsBytecode() throws Exception {
        String out = new JavapTask(new ToolBox())
                .options("-c", "-private", "-s")
                .classpath(System.getProperty("test.classes"))
                .classes("T8384131")
                .run()
                .getOutputLines(Task.OutputKind.DIRECT)
                .stream()
                .collect(Collectors.joining("\n"));

        expect(out, "intArrayToLongPrimitiveWidening",
                """
                    descriptor: ([I)J
                """,
                """
                        14: iaload
                        15: i2l
                """);
        expect(out, "integerArrayToIntUnboxing",
                """
                    descriptor: ([Ljava/lang/Integer;)I
                """,
                """
                        14: aaload
                        15: invokevirtual # // Method java/lang/Integer.intValue:()I
                """);
        expect(out, "integerArrayToLongUnboxingAndPrimitiveWidening",
                """
                    descriptor: ([Ljava/lang/Integer;)J
                """,
                """
                        14: aaload
                        15: invokevirtual # // Method java/lang/Integer.intValue:()I
                        18: i2l
                """);
        expect(out, "intArrayToIntegerBoxing",
                """
                    descriptor: ([I)Ljava/lang/Integer;
                """,
                """
                        14: iaload
                        15: invokestatic  # // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
                """);
        expect(out, "intArrayToObjectBoxingAndWideningReference",
                """
                    descriptor: ([I)Ljava/lang/Object;
                """,
                """
                        14: iaload
                        15: invokestatic  # // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
                """);
        expect(out, "integerTypeVariableArrayToIntWideningReferenceAndUnboxing",
                """
                    descriptor: ([Ljava/lang/Integer;)I
                """,
                """
                        14: aaload
                        15: invokevirtual # // Method java/lang/Integer.intValue:()I
                """);
        expect(out, "shortTypeVariableArrayToIntWideningReferenceAndUnboxingAndPrimitiveWidening",
                """
                    descriptor: ([Ljava/lang/Short;)I
                """,
                """
                        14: aaload
                        15: invokevirtual # // Method java/lang/Short.shortValue:()S
                """);
    }

    static void expect(String out, String methodName, String... expectedFragments) {
        int start = out.indexOf(methodName + "(");
        int end = out.indexOf("\n\n", start);
        String method = out
                .substring(start, end == -1 ? out.length() : end)
                .replaceAll("#\\d+\\s+//", "# //");
        for (String expected : expectedFragments) {
            String fragment = expected.stripTrailing();
            if (!method.contains(fragment)) {
                throw new AssertionError("Expected:\n" + fragment + "\nin " + methodName + ":\n" + method);
            }
        }
    }

    static void expectEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
