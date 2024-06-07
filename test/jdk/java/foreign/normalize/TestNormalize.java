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
 * @library ../
 * @run testng/othervm
 *   --enable-native-access=ALL-UNNAMED
 *   -Xbatch
 *   -XX:CompileCommand=dontinline,TestNormalize::doCall*
 *   TestNormalize
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.testng.Assert.assertEquals;

// test normalization of smaller than int primitive types
public class TestNormalize extends NativeTestHelper {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle SAVE_BOOLEAN_AS_INT;
    private static final MethodHandle SAVE_BYTE_AS_INT;
    private static final MethodHandle SAVE_SHORT_AS_INT;
    private static final MethodHandle SAVE_CHAR_AS_INT;

    private static final MethodHandle BOOLEAN_TO_INT;
    private static final MethodHandle BYTE_TO_INT;
    private static final MethodHandle SHORT_TO_INT;
    private static final MethodHandle CHAR_TO_INT;

    private static final MethodHandle NATIVE_BOOLEAN_TO_INT;

    private static final int BOOLEAN_HOB_MASK = ~0b1;
    private static final int BYTE_HOB_MASK    = ~0xFF;
    private static final int SHORT_HOB_MASK   = ~0xFFFF;
    private static final int CHAR_HOB_MASK    = ~0xFFFF;

    private static final MethodHandle SAVE_BOOLEAN;

    static {
        System.loadLibrary("Normalize");

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SAVE_BOOLEAN_AS_INT = lookup.findStatic(TestNormalize.class, "saveBooleanAsInt", MethodType.methodType(void.class, boolean.class, int[].class));
            SAVE_BYTE_AS_INT = lookup.findStatic(TestNormalize.class, "saveByteAsInt", MethodType.methodType(void.class, byte.class, int[].class));
            SAVE_SHORT_AS_INT = lookup.findStatic(TestNormalize.class, "saveShortAsInt", MethodType.methodType(void.class, short.class, int[].class));
            SAVE_CHAR_AS_INT = lookup.findStatic(TestNormalize.class, "saveCharAsInt", MethodType.methodType(void.class, char.class, int[].class));

            BOOLEAN_TO_INT = lookup.findStatic(TestNormalize.class, "booleanToInt", MethodType.methodType(int.class, boolean.class));
            BYTE_TO_INT = lookup.findStatic(TestNormalize.class, "byteToInt", MethodType.methodType(int.class, byte.class));
            SHORT_TO_INT = lookup.findStatic(TestNormalize.class, "shortToInt", MethodType.methodType(int.class, short.class));
            CHAR_TO_INT = lookup.findStatic(TestNormalize.class, "charToInt", MethodType.methodType(int.class, char.class));

            NATIVE_BOOLEAN_TO_INT = LINKER.downcallHandle(findNativeOrThrow("int_identity"), FunctionDescriptor.of(JAVA_INT, JAVA_BOOLEAN));

            SAVE_BOOLEAN = lookup.findStatic(TestNormalize.class, "saveBoolean", MethodType.methodType(void.class, boolean.class, boolean[].class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // The idea of this test is that we pass a 'dirty' int value down to native code, and then receive it back
    // as the argument to an upcall, as well as the result of the downcall, but with a sub-int type (boolean, byte, short, char).
    // When we do either of those, argument normalization should take place, so that the resulting value is sane (1).
    // After that we convert the value back to int again, the JVM can/will skip value normalization here.
    // We then check the high order bits of the resulting int. If argument normalization took place at (1), they should all be 0.
    @Test(dataProvider = "cases")
    public void testNormalize(ValueLayout layout, int testValue, int hobMask, MethodHandle toInt, MethodHandle saver) throws Throwable {
        // use actual type as parameter type to test upcall arg normalization
        FunctionDescriptor upcallDesc = FunctionDescriptor.ofVoid(layout);
        // use actual type as return type to test downcall return normalization
        FunctionDescriptor downcallDesc = FunctionDescriptor.of(layout, ADDRESS, JAVA_INT);

        MemorySegment target = findNativeOrThrow("test");
        MethodHandle downcallHandle = LINKER.downcallHandle(target, downcallDesc);
        downcallHandle = MethodHandles.filterReturnValue(downcallHandle, toInt);

        try (Arena arena = Arena.ofConfined()) {
            int[] box = new int[1];
            saver = MethodHandles.insertArguments(saver, 1, box);
            MemorySegment upcallStub = LINKER.upcallStub(saver, upcallDesc, arena);
            int dirtyValue = testValue | hobMask; // set all bits that should not be set

            // test after JIT as well
            for (int i = 0; i < 20_000; i++) {
                doCall(downcallHandle, upcallStub, box, dirtyValue, hobMask);
            }
        }
    }

    private static void doCall(MethodHandle downcallHandle, MemorySegment upcallStub,
                               int[] box, int dirtyValue, int hobMask) throws Throwable {
        int result = (int) downcallHandle.invokeExact(upcallStub, dirtyValue);
        assertEquals(box[0] & hobMask, 0); // check normalized upcall arg
        assertEquals(result & hobMask, 0); // check normalized downcall return value
    }

    public static void saveBooleanAsInt(boolean b, int[] box) {
        box[0] = booleanToInt(b);
    }
    public static void saveByteAsInt(byte b, int[] box) {
        box[0] = byteToInt(b);
    }
    public static void saveShortAsInt(short s, int[] box) {
        box[0] = shortToInt(s);
    }
    public static void saveCharAsInt(char c, int[] box) {
        box[0] = charToInt(c);
    }

    public static int booleanToInt(boolean b) {
        try {
            return (int) NATIVE_BOOLEAN_TO_INT.invokeExact(b); // FIXME do in pure Java?
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    public static int byteToInt(byte b) {
        return b;
    }
    public static int charToInt(char c) {
        return c;
    }
    public static int shortToInt(short s) {
        return s;
    }

    @DataProvider
    public static Object[][] cases() {
        return new Object[][] {
            { JAVA_BOOLEAN, booleanToInt(true),     BOOLEAN_HOB_MASK, BOOLEAN_TO_INT, SAVE_BOOLEAN_AS_INT },
            { JAVA_BYTE,    byteToInt((byte) 42),   BYTE_HOB_MASK,    BYTE_TO_INT,    SAVE_BYTE_AS_INT    },
            { JAVA_SHORT,   shortToInt((short) 42), SHORT_HOB_MASK,   SHORT_TO_INT,   SAVE_SHORT_AS_INT   },
            { JAVA_CHAR,    charToInt('a'),         CHAR_HOB_MASK,    CHAR_TO_INT,    SAVE_CHAR_AS_INT    }
        };
    }

    // test which int values are considered true and false
    // we currently convert any int with a non-zero first byte to true, otherwise false.
    @Test(dataProvider = "bools")
    public void testBool(int testValue, boolean expected) throws Throwable {
        MemorySegment addr = findNativeOrThrow("test");
        MethodHandle target = LINKER.downcallHandle(addr, FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT));

        boolean[] box = new boolean[1];
        MethodHandle upcallTarget = MethodHandles.insertArguments(SAVE_BOOLEAN, 1, box);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment callback = LINKER.upcallStub(upcallTarget, FunctionDescriptor.ofVoid(JAVA_BOOLEAN), arena);
            boolean result = (boolean) target.invokeExact(callback, testValue);
            assertEquals(box[0], expected);
            assertEquals(result, expected);
        }
    }

    private static void saveBoolean(boolean b, boolean[] box) {
        box[0] = b;
    }

    @DataProvider
    public static Object[][] bools() {
        return new Object[][]{
            { 0b10,          true  }, // zero least significant bit, but non-zero first byte
            { 0b1_0000_0000, false }  // zero first byte
        };
    }
}
