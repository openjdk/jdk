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

        void testArrayEagerCCE(Z[] arr) {
            for (I i : arr) {
                throw new AssertionError("reached beyond for-header");
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
        expectCCE(() -> new Test().testArrayEagerCCE(new C[] { new C() }));
        expectCCE(() -> new Test().testList(List.of(new C())));

        // pos tests
        new Test<CI>().testArray(new CI[] { new CI() });
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
    static <T extends Integer> void integerTypeVariableArrayToIntWideningReferenceAndUnboxing(T[] values) {
        for (int i : values) { }
    }

    static <T extends Short> void shortTypeVariableArrayToIntWideningReferenceAndUnboxingAndPrimitiveWidening(T[] values) {
        for (int i : values) { }
    }

    static void intArrayToLongPrimitiveWidening() {
        for (long l : new int[] { 1 }) { }
    }

    static void integerArrayToIntUnboxing() {
        for (int i : new Integer[] { 1 }) { }
    }

    static void integerArrayToLongUnboxingAndPrimitiveWidening() {
        for (long l : new Integer[] { 1 }) { }
    }

    static void intArrayToIntegerBoxing() {
        for (Integer i : new int[] { 1 }) { }
    }

    static void intArrayToObjectBoxingAndWideningReference() {
        for (Object o : new int[] { 1 }) { }
    }
    // </editor-fold>

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
                        20: iaload
                        21: i2l
                """);
        expect(out, "integerArrayToIntUnboxing",
                """
                        24: aaload
                        25: invokevirtual # // Method java/lang/Integer.intValue:()I
                """);
        expect(out, "integerArrayToLongUnboxingAndPrimitiveWidening",
                """
                        24: aaload
                        25: invokevirtual # // Method java/lang/Integer.intValue:()I
                        28: i2l
                """);
        expect(out, "intArrayToIntegerBoxing",
                """
                        20: iaload
                        21: invokestatic  # // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
                """);
        expect(out, "intArrayToObjectBoxingAndWideningReference",
                """
                        20: iaload
                        21: invokestatic  # // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
                """);
        expect(out, "integerTypeVariableArrayToIntWideningReferenceAndUnboxing",
                """
                    descriptor: ([Ljava/lang/Integer;)V
                """,
                """
                        14: aaload
                        15: invokevirtual # // Method java/lang/Integer.intValue:()I
                """);
        expect(out, "shortTypeVariableArrayToIntWideningReferenceAndUnboxingAndPrimitiveWidening",
                """
                    descriptor: ([Ljava/lang/Short;)V
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
}
