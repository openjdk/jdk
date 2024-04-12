/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../ /test/lib
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestAddressDereference
 */

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class TestAddressDereference extends UpcallTestHelper {

    static final Linker LINKER = Linker.nativeLinker();
    static final MemorySegment GET_ADDR_SYM;
    static final MethodHandle GET_ADDR_CB_HANDLE, TEST_ARG_HANDLE;

    static {
        System.loadLibrary("AddressDereference");
        GET_ADDR_SYM = SymbolLookup.loaderLookup().find("get_addr").get();
        GET_ADDR_CB_HANDLE = LINKER.downcallHandle(
                SymbolLookup.loaderLookup().find("get_addr_cb").get(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        try {
            TEST_ARG_HANDLE = MethodHandles.lookup().findStatic(TestAddressDereference.class, "testArg",
                    MethodType.methodType(void.class, MemorySegment.class, long.class));
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    @Test(dataProvider = "layoutsAndAlignments")
    public void testGetAddress(long alignment, ValueLayout layout) {
        boolean badAlign = layout.byteAlignment() > alignment;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(ValueLayout.ADDRESS);
            segment.set(ValueLayout.ADDRESS, 0, MemorySegment.ofAddress(alignment));
            MemorySegment deref = segment.get(ValueLayout.ADDRESS.withTargetLayout(layout), 0);
            assertFalse(badAlign);
            assertEquals(deref.byteSize(), layout.byteSize());
        } catch (IllegalArgumentException ex) {
            assertTrue(badAlign);
            assertTrue(ex.getMessage().contains("alignment constraint for address"));
        }
    }

    @Test(dataProvider = "layoutsAndAlignments")
    public void testGetAddressIndex(long alignment, ValueLayout layout) {
        boolean badAlign = layout.byteAlignment() > alignment;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(ValueLayout.ADDRESS);
            segment.set(ValueLayout.ADDRESS, 0, MemorySegment.ofAddress(alignment));
            MemorySegment deref = segment.getAtIndex(ValueLayout.ADDRESS.withTargetLayout(layout), 0);
            assertFalse(badAlign);
            assertEquals(deref.byteSize(), layout.byteSize());
        } catch (IllegalArgumentException ex) {
            assertTrue(badAlign);
            assertTrue(ex.getMessage().contains("alignment constraint for address"));
        }
    }

    @Test(dataProvider = "layoutsAndAlignments")
    public void testNativeReturn(long alignment, ValueLayout layout) throws Throwable {
        boolean badAlign = layout.byteAlignment() > alignment;
        try {
            MethodHandle get_addr_handle = LINKER.downcallHandle(GET_ADDR_SYM,
                    FunctionDescriptor.of(ValueLayout.ADDRESS.withTargetLayout(layout), ValueLayout.ADDRESS));
            MemorySegment deref = (MemorySegment)get_addr_handle.invokeExact(MemorySegment.ofAddress(alignment));
            assertFalse(badAlign);
            assertEquals(deref.byteSize(), layout.byteSize());
        } catch (IllegalArgumentException ex) {
            assertTrue(badAlign);
            assertTrue(ex.getMessage().contains("alignment constraint for address"));
        }
    }

    @Test(dataProvider = "layoutsAndAlignments")
    public void testNativeUpcallArgPos(long alignment, ValueLayout layout) throws Throwable {
        boolean badAlign = layout.byteAlignment() > alignment;
        if (badAlign) return; // this will crash the JVM (exception occurs when going into the upcall stub)
        try (Arena arena = Arena.ofConfined()) {
            FunctionDescriptor testDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS.withTargetLayout(layout));
            MethodHandle upcallHandle = MethodHandles.insertArguments(TEST_ARG_HANDLE, 1, layout.byteSize());
            MemorySegment testStub = LINKER.upcallStub(upcallHandle, testDesc, arena);
            GET_ADDR_CB_HANDLE.invokeExact(MemorySegment.ofAddress(alignment), testStub);
        }
    }

    @Test(dataProvider = "layoutsAndAlignments")
    public void testNativeUpcallArgNeg(long alignment, ValueLayout layout) throws Throwable {
        boolean badAlign = layout.byteAlignment() > alignment;
        if (!badAlign) return;
        runInNewProcess(UpcallTestRunner.class, true,
                new String[] {Long.toString(alignment), layout.toString() })
                .shouldNotHaveExitValue(0)
                .stderrShouldContain("alignment constraint for address");
    }

    public static class UpcallTestRunner {
        public static void main(String[] args) throws Throwable {
            long alignment = parseAlignment(args[0]);
            ValueLayout layout = parseLayout(args[1]);
            try (Arena arena = Arena.ofConfined()) {
                FunctionDescriptor testDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS.withTargetLayout(layout));
                MethodHandle upcallHandle = MethodHandles.insertArguments(TEST_ARG_HANDLE, 1, layout.byteSize());
                MemorySegment testStub = LINKER.upcallStub(upcallHandle, testDesc, arena);
                GET_ADDR_CB_HANDLE.invokeExact(MemorySegment.ofAddress(alignment), testStub);
            }
        }

        static long parseAlignment(String s) {
            return Long.parseLong(s);
        }

        static ValueLayout parseLayout(String s) {
            return LayoutKind.parse(s).layout;
        }
    }

    static void testArg(MemorySegment deref, long expectedSize) {
        assertEquals(deref.byteSize(), expectedSize);
    }

    @DataProvider(name = "layoutsAndAlignments")
    static Object[][] layoutsAndAlignments() {
        List<Object[]> layoutsAndAlignments = new ArrayList<>();
        for (LayoutKind lk : LayoutKind.values()) {
            for (int align : new int[]{ 1, 2, 4, 8 }) {
                layoutsAndAlignments.add(new Object[] { align, lk.layout });
            }
        }
        return layoutsAndAlignments.toArray(Object[][]::new);
    }

    enum LayoutKind {
        BOOL(ValueLayout.JAVA_BOOLEAN),
        CHAR(ValueLayout.JAVA_CHAR),
        SHORT(ValueLayout.JAVA_SHORT),
        INT(ValueLayout.JAVA_INT),
        FLOAT(ValueLayout.JAVA_FLOAT),
        LONG(ValueLayout.JAVA_LONG),
        DOUBLE(ValueLayout.JAVA_DOUBLE),
        ADDRESS(ValueLayout.ADDRESS);


        final ValueLayout layout;

        LayoutKind(ValueLayout segment) {
            this.layout = segment;
        }

        private static final Pattern LAYOUT_PATTERN = Pattern.compile("^(?<align>\\d+%)?(?<char>[azcsifjdAZCSIFJD])\\d+$");

        static LayoutKind parse(String layoutString) {
            Matcher matcher = LAYOUT_PATTERN.matcher(layoutString);
            if (matcher.matches()) {
                switch (matcher.group("char")) {
                    case "A","a": return ADDRESS;
                    case "z","Z": return BOOL;
                    case "c","C": return CHAR;
                    case "s","S": return SHORT;
                    case "i","I": return INT;
                    case "f","F": return FLOAT;
                    case "j","J": return LONG;
                    case "d","D": return DOUBLE;
                };
            }
            throw new AssertionError("Invalid layout string: " + layoutString);
        }
    }
}
