/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8356126
 * @library ../ /test/lib
 * @run testng/othervm/native --enable-native-access=ALL-UNNAMED TestCaptureCallState
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.testng.Assert.*;

public class TestCaptureCallState extends NativeTestHelper {

    static {
        System.loadLibrary("CaptureCallState");
        if (IS_WINDOWS) {
            String system32 = System.getenv("SystemRoot") + "\\system32";
            System.load(system32 + "\\Kernel32.dll");
            System.load(system32 + "\\Ws2_32.dll");
        }
    }

    // Basic sanity tests around Java API contracts
    @Test
    public void testApiContracts() {
        assertThrows(IllegalArgumentException.class, () -> Linker.Option.captureCallState("Does not exist"));
        var duplicateOpt = Linker.Option.captureCallState("errno", "errno"); // duplicates
        var noDuplicateOpt = Linker.Option.captureCallState("errno");
        assertEquals(duplicateOpt, noDuplicateOpt, "auto deduplication");
        var display = duplicateOpt.toString();
        assertTrue(display.contains("errno"), "toString should contain state name 'errno': " + display);
    }

    private record SaveValuesCase(String nativeTarget, FunctionDescriptor nativeDesc, String threadLocalName,
                                  Consumer<Object> resultCheck, boolean critical) {}

    @Test(dataProvider = "cases")
    public void testSavedThreadLocal(SaveValuesCase testCase) throws Throwable {
        List<Linker.Option> options = new ArrayList<>();
        options.add(Linker.Option.captureCallState(testCase.threadLocalName()));
        if (testCase.critical()) {
            options.add(Linker.Option.critical(false));
        }
        MethodHandle handle = downcallHandle(testCase.nativeTarget(), testCase.nativeDesc(),
                options.toArray(Linker.Option[]::new));

        StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
        VarHandle errnoHandle = capturedStateLayout.varHandle(groupElement(testCase.threadLocalName()));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment saveSeg = arena.allocate(capturedStateLayout);
            int testValue = 42;
            boolean needsAllocator = testCase.nativeDesc().returnLayout().map(StructLayout.class::isInstance).orElse(false);
            Object result = needsAllocator
                ? handle.invoke(arena, saveSeg, testValue)
                : handle.invoke(saveSeg, testValue);
            testCase.resultCheck().accept(result);
            int savedErrno = (int) errnoHandle.get(saveSeg, 0L);
            assertEquals(savedErrno, testValue);
        }
    }

    @Test(dataProvider = "invalidCaptureSegmentCases")
    public void testInvalidCaptureSegment(MemorySegment captureSegment,
                                          Class<?> expectedExceptionType, String expectedExceptionMessage,
                                          Linker.Option[] extraOptions) {
        List<Linker.Option> options = new ArrayList<>();
        options.add(Linker.Option.captureCallState("errno"));
        for (Linker.Option extra : extraOptions) {
            options.add(extra);
        }
        MethodHandle handle = downcallHandle("set_errno_V", FunctionDescriptor.ofVoid(C_INT), options.toArray(Linker.Option[]::new));

        try {
            int testValue = 42;
            handle.invoke(captureSegment, testValue); // should throw
        } catch (Throwable t) {
            assertTrue(expectedExceptionType.isInstance(t));
            assertTrue(t.getMessage().matches(expectedExceptionMessage));
        }
    }

    @DataProvider
    public static Object[][] cases() {
        List<SaveValuesCase> cases = new ArrayList<>();

        for (boolean critical : new boolean[]{ true, false }) {
            cases.add(new SaveValuesCase("set_errno_V", FunctionDescriptor.ofVoid(JAVA_INT),
                    "errno", o -> {}, critical));
            cases.add(new SaveValuesCase("set_errno_I", FunctionDescriptor.of(JAVA_INT, JAVA_INT),
                    "errno", o -> assertEquals((int) o, 42), critical));
            cases.add(new SaveValuesCase("set_errno_D", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_INT),
                    "errno", o -> assertEquals((double) o, 42.0), critical));

            cases.add(structCase("SL", Map.of(JAVA_LONG.withName("x"), 42L), critical));
            cases.add(structCase("SLL", Map.of(JAVA_LONG.withName("x"), 42L,
                    JAVA_LONG.withName("y"), 42L), critical));
            cases.add(structCase("SLLL", Map.of(JAVA_LONG.withName("x"), 42L,
                    JAVA_LONG.withName("y"), 42L,
                    JAVA_LONG.withName("z"), 42L), critical));
            cases.add(structCase("SD", Map.of(JAVA_DOUBLE.withName("x"), 42D), critical));
            cases.add(structCase("SDD", Map.of(JAVA_DOUBLE.withName("x"), 42D,
                    JAVA_DOUBLE.withName("y"), 42D), critical));
            cases.add(structCase("SDDD", Map.of(JAVA_DOUBLE.withName("x"), 42D,
                    JAVA_DOUBLE.withName("y"), 42D,
                    JAVA_DOUBLE.withName("z"), 42D), critical));

            if (IS_WINDOWS) {
                cases.add(new SaveValuesCase("SetLastError", FunctionDescriptor.ofVoid(JAVA_INT),
                        "GetLastError", o -> {}, critical));
                cases.add(new SaveValuesCase("WSASetLastError", FunctionDescriptor.ofVoid(JAVA_INT),
                        "WSAGetLastError", o -> {}, critical));
            }
        }

        return cases.stream().map(tc -> new Object[] {tc}).toArray(Object[][]::new);
    }

    static SaveValuesCase structCase(String name, Map<MemoryLayout, Object> fields, boolean critical) {
        StructLayout layout = MemoryLayout.structLayout(fields.keySet().toArray(MemoryLayout[]::new));

        Consumer<Object> check = o -> {};
        for (var field : fields.entrySet()) {
            MemoryLayout fieldLayout = field.getKey();
            VarHandle fieldHandle = layout.varHandle(MemoryLayout.PathElement.groupElement(fieldLayout.name().get()));
            Object value = field.getValue();
            check = check.andThen(o -> assertEquals(fieldHandle.get(o, 0L), value));
        }

        return new SaveValuesCase("set_errno_" + name, FunctionDescriptor.of(layout, JAVA_INT),
                "errno", check, critical);
    }

    @DataProvider
    public static Object[][] invalidCaptureSegmentCases() {
        return new Object[][]{
            {Arena.ofAuto().allocate(1), IndexOutOfBoundsException.class, ".*Out of bound access on segment.*", new Linker.Option[0]},
            {MemorySegment.NULL, IllegalArgumentException.class, ".*Capture segment is NULL.*", new Linker.Option[0]},
            {Arena.ofAuto().allocate(Linker.Option.captureStateLayout().byteSize() + 3).asSlice(3), // misaligned
                    IllegalArgumentException.class, ".*Target offset incompatible with alignment constraints.*", new Linker.Option[0]},
            {MemorySegment.ofArray(new byte[(int) Linker.Option.captureStateLayout().byteSize()]), // misaligned
                    IllegalArgumentException.class, ".*Target offset incompatible with alignment constraints.*", new Linker.Option[0]},
        };
    }
}
