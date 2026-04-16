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

package compiler.unsafe;

/*
 * @test
 * @bug 8385119
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 * @run main/bootclasspath/othervm ${test.main.class}
 */

import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

public class UnsafeBooleanTest {
    static final Class<UnsafeBooleanTest> THIS_CLASS = UnsafeBooleanTest.class;

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // int testPutBoolean(Unsafe unsafe, Object base, long offset, int value) {
    //   unsafe.putBoolean(base, offset, value);
    //   return unsafe.getByte(base, offset);
    // }
    static final MethodHandle TEST_PUT_BOOLEAN_MH = generateTestMethod(false, true);

    // int testGetBoolean(Unsafe unsafe, Object base, long offset, int value) {
    //   unsafe.putByte(base, offset, value);
    //   return unsafe.getBoolean(base, offset);
    // }
    static final MethodHandle TEST_GET_BOOLEAN_MH = generateTestMethod(true, false);

    // int testAllBoolean(Unsafe unsafe, Object base, long offset, int value) {
    //   unsafe.putBoolean(base, offset, value);
    //   return unsafe.getBoolean(base, offset);
    // }
    static final MethodHandle TEST_ALL_BOOLEAN_MH = generateTestMethod(true, true);

    static boolean B;
    static long J;
    static @Stable boolean stableB;
    static @Stable long stableJ;

    static final int[] INPUTS = new int[] {
            0, 1,
            2, 3, 4, 5, -1,
            Byte.MIN_VALUE, Byte.MAX_VALUE,
            Short.MIN_VALUE, Short.MAX_VALUE, Short.MIN_VALUE & ~0xFF, Short.MAX_VALUE & ~0xFF,
            Character.MIN_VALUE , Character.MAX_VALUE, Character.MIN_VALUE & ~0xFF, Character.MAX_VALUE & ~0xFF,
            Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE & ~0xFF, Integer.MAX_VALUE & ~0xFF };

    public static void main(String[] args) throws NoSuchFieldException {
        runTestsOn("boolean[0]", new boolean[1], UNSAFE.arrayBaseOffset(boolean[].class));

        // Mismatched array accesses.
        runTestsOn("byte[0]",    new byte[1], UNSAFE.arrayBaseOffset(byte[].class));
        runTestsOn("long[0]",    new long[1], UNSAFE.arrayBaseOffset(long[].class));
        runTestsOn("long[0]+1",  new long[1], UNSAFE.arrayBaseOffset(long[].class) + 1);

        runTestsOn("null",       null, UNSAFE.allocateMemory(1));

        Field booleanField = THIS_CLASS.getDeclaredField("B");
        runTestsOn("A.B", UNSAFE.staticFieldBase(booleanField), UNSAFE.staticFieldOffset(booleanField));

        // Mismatched field accesses.
        Field longField = THIS_CLASS.getDeclaredField("J");
        runTestsOn("A.J",   UNSAFE.staticFieldBase(longField), UNSAFE.staticFieldOffset(longField));
        runTestsOn("A.J+1", UNSAFE.staticFieldBase(longField), UNSAFE.staticFieldOffset(longField) + 1);

        Field booleanStableField = THIS_CLASS.getDeclaredField("stableB");
        runTestsOn("A.stableB", UNSAFE.staticFieldBase(booleanStableField), UNSAFE.staticFieldOffset(booleanStableField));

        Field longStableField = THIS_CLASS.getDeclaredField("stableJ");
        runTestsOn("A.stableJ", UNSAFE.staticFieldBase(longStableField), UNSAFE.staticFieldOffset(longStableField));

        if (!ERRORS.isEmpty()) {
            throw new AssertionError("TEST FAILED");
        }
        System.out.println("TEST PASSED");
    }

    static void runTestsOn(String name, Object base, long offset) {
        for (int value : INPUTS) {
            System.out.printf("Test: %s; input: 0x%08x\n", name, value);
            runTestsOn(prepare(name + " allBoolean", TEST_ALL_BOOLEAN_MH, base, offset), value, true);
            runTestsOn(prepare(name + " putBoolean", TEST_PUT_BOOLEAN_MH, base, offset), value, true);
            runTestsOn(prepare(name + " getBoolean", TEST_GET_BOOLEAN_MH, base, offset), value, false);
        }
    }

    static void runTestsOn(Test[] tests, int value, boolean normalizedOnStore) {
        for (Test t : tests) {
            runTest(t, value, normalizedOnStore);
        }
    }

    static void runTest(Test t, int value, boolean normalizedOnStore) {
        int expected = ((byte) value) & 1;
        for (int iter = 0; iter < 20_000; iter++) {
            try {
                int r = t.test(value);
                if (expected != r) {
                    if (!normalizedOnStore && (((byte) value) & 0xFF) == r) {
                        reportInterestingResult(t, value, r, expected, iter);
                    } else if (reportError(t, value)) {
                        System.out.printf("%s: 0x%08x: 0x%02x != %x\n",
                                          TEST_NAMES.get(t), value, r, expected);
                    }
                }
            } catch (Throwable e) {
                if (reportError(t, value)) {
                    System.out.printf("%s: 0x%08x: 0x%02x != %x\n",
                            TEST_NAMES.get(t), value, e.getMessage(), expected);
                }
            }
        }
    }

    static MethodHandle generateTestMethod(boolean isBooleanGetter, boolean isBooleanSetter) {
        if (!isBooleanGetter && !isBooleanSetter) {
            throw new InternalError("not supported");
        }

        final String name = !isBooleanGetter ? "testPutBoolean" :
                            !isBooleanSetter ? "testGetBoolean" :
                                               "testAllBoolean";
        MethodType mt = MethodType.methodType(int.class, Unsafe.class, Object.class, long.class, int.class);
            byte[] classFile = ClassFile.of().build(ClassDesc.of("compiler.unsafe.Helper"),
                            // static int test(Unsafe unsafe, Object base, long offset, int value) {
                            cb -> cb.withMethodBody(name,
                                        mt.describeConstable().get(),
                                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                        mb -> {
                                            mb.aload(0);
                                            mb.aload(1);
                                            mb.lload(2);
                                            mb.iload(4);
                                            if (isBooleanSetter) {
                                                // unsafe.putBoolean(base, offset, value);
                                                MethodType putBooleanMT = MethodType.methodType(void.class, Object.class, long.class, boolean.class);
                                                mb.invoke(Opcode.INVOKEVIRTUAL, Unsafe.class.describeConstable().get(),
                                                        "putBoolean", putBooleanMT.describeConstable().get(), false);
                                            } else {
                                                // unsafe.putByte(base, offset, value);
                                                MethodType putByteMT = MethodType.methodType(void.class, Object.class, long.class, byte.class);
                                                mb.invoke(Opcode.INVOKEVIRTUAL, Unsafe.class.describeConstable().get(),
                                                        "putByte", putByteMT.describeConstable().get(), false);
                                            }
                                            mb.aload(0);
                                            mb.aload(1);
                                            mb.lload(2);
                                            if (isBooleanGetter) {
                                                // boolean b = unsafe.getBoolean(base, offset);
                                                MethodType getBooleanMT = MethodType.methodType(boolean.class, Object.class, long.class);
                                                mb.invoke(Opcode.INVOKEVIRTUAL, Unsafe.class.describeConstable().get(),
                                                        "getBoolean", getBooleanMT.describeConstable().get(), false);
                                            } else {
                                                // byte b = unsafe.getByte(base, offset);
                                                MethodType getByteMT = MethodType.methodType(byte.class, Object.class, long.class);
                                                mb.invoke(Opcode.INVOKEVIRTUAL, Unsafe.class.describeConstable().get(),
                                                        "getByte", getByteMT.describeConstable().get(), false);
                                            }
                                            // return b;
                                            mb.ireturn();
                                        }));
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(classFile, true);
            return lookup.findStatic(lookup.lookupClass(), name, mt);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InternalError(e);
        }
    }

    static final HashMap<Test,HashMap<Integer,String>> INTERESTING = new HashMap<>();

    static void reportInterestingResult(Test t, int value, int result, int expected, int iter) {
        var s = INTERESTING.computeIfAbsent(t, _ -> new HashMap<>());
        boolean report = !s.containsKey(value);
        if (report) {
            var msg = String.format("%-40s: 0x%08x: 0x%02x != 0x%02x\n", TEST_NAMES.get(t), value, result, expected);
            s.put(value, msg);
            System.out.printf("INTERESTING: %s", msg);
        }
    }

    static final HashMap<Test,HashSet<Integer>> ERRORS = new HashMap<>();

    static boolean reportError(Test t, int i) {
        HashSet<Integer> s = ERRORS.computeIfAbsent(t, _ -> new HashSet<>());
        boolean report = !s.contains(i);
        if (report) {
            s.add(i);
        }
        return report;
    }

    public interface Test {
        int test(int i) throws Throwable;
    }

    static HashMap<Test,String> TEST_NAMES = new HashMap<>();

    static Test register(String name, String suffix, Test t) {
        TEST_NAMES.put(t, String.format("%s:%s", name, suffix));
        return t;
    }

    static final MethodHandle MH_NON_NULL;
    static {
        try {
            MH_NON_NULL = MethodHandles.lookup().findStatic(Objects.class, "requireNonNull",
                                                            MethodType.methodType(Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    static Test[] prepare(String id, MethodHandle test, Object base, long offset) {
        final MethodHandle mhBOI = MethodHandles.insertArguments(test, 0, UNSAFE);
        final MethodHandle mhOI  = MethodHandles.insertArguments(mhBOI, 0, base);
        final MethodHandle mhBI  = MethodHandles.insertArguments(mhBOI, 1, offset);
        final MethodHandle mhI   = MethodHandles.insertArguments(mhBOI, 0, base, offset);

        ArrayList<Test> tests = new ArrayList<>();
        tests.add(register(id, "base+offset=const", i -> (int) mhI.invokeExact(i)));
        tests.add(register(id, "offset=const",      i -> (int) mhBI.invokeExact(base, i)));
        tests.add(register(id, "base=const",        i -> (int) mhOI.invokeExact(offset, i)));
        tests.add(register(id, "noconst",           i -> (int) mhBOI.invokeExact(base, offset, i)));
        if (base != null) {
            final MethodHandle mhBOIN = MethodHandles.filterArguments(mhBOI, 0, MH_NON_NULL);
            tests.add(register(id, "base=nonnull",      i -> (int) mhBOIN.invokeExact(base, offset, i)));
        }
        return tests.toArray(new Test[0]);
    }
}