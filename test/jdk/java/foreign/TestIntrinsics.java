/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   --enable-native-access=ALL-UNNAMED
 *   -Xbatch
 *   TestIntrinsics
 */

import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import java.lang.foreign.MemoryLayout;
import org.testng.annotations.*;

import static java.lang.foreign.Linker.Option.firstVariadicArg;
import static java.lang.invoke.MethodType.methodType;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static org.testng.Assert.assertEquals;

public class TestIntrinsics extends NativeTestHelper {

    static final Linker abi = Linker.nativeLinker();
    static {
        System.loadLibrary("Intrinsics");
    }

    private interface RunnableX {
        void run() throws Throwable;
    }

    @Test(dataProvider = "tests")
    public void testIntrinsics(RunnableX test) throws Throwable {
        for (int i = 0; i < 20_000; i++) {
            test.run();
        }
    }

    @DataProvider
    public Object[][] tests() {
        List<RunnableX> testsList = new ArrayList<>();

        interface AddTest {
            void add(MethodHandle target, Object expectedResult, Object... args);
        }

        AddTest tests = (mh, expectedResult, args) -> testsList.add(() -> {
            Object actual = mh.invokeWithArguments(args);
            assertEquals(actual, expectedResult);
        });

        interface AddIdentity {
            void add(String name, Class<?> carrier, MemoryLayout layout, Object arg);
        }

        AddIdentity addIdentity = (name, carrier, layout, arg) -> {
            MemorySegment ma = findNativeOrThrow(name);
            FunctionDescriptor fd = FunctionDescriptor.of(layout, layout);

            tests.add(abi.downcallHandle(ma, fd), arg, arg);
            tests.add(abi.downcallHandle(fd), arg, ma, arg);
        };

        { // empty
            MemorySegment ma = findNativeOrThrow("empty");
            FunctionDescriptor fd = FunctionDescriptor.ofVoid();
            tests.add(abi.downcallHandle(ma, fd), null);
        }

        addIdentity.add("identity_bool",   boolean.class, C_BOOL,   true);
        addIdentity.add("identity_char",   byte.class,    C_CHAR,   (byte) 10);
        addIdentity.add("identity_short",  short.class,   C_SHORT, (short) 10);
        addIdentity.add("identity_int",    int.class,     C_INT,           10);
        addIdentity.add("identity_long",   long.class,    C_LONG_LONG,     10L);
        addIdentity.add("identity_float",  float.class,   C_FLOAT,         10F);
        addIdentity.add("identity_double", double.class,  C_DOUBLE,        10D);

        { // identity_va
            MemorySegment ma = findNativeOrThrow("identity_va");
            FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_INT,
                                                                 C_DOUBLE, C_INT, C_DOUBLE, C_LONG_LONG);
            tests.add(abi.downcallHandle(ma, fd, firstVariadicArg(1)), 1, 1, 10D, 2, 3D, 4L);
        }

        { // high_arity
            FunctionDescriptor baseFD = FunctionDescriptor.ofVoid(C_INT, C_DOUBLE, C_LONG_LONG, C_FLOAT, C_CHAR,
                    C_SHORT, JAVA_CHAR);
            Object[] args = {1, 10D, 2L, 3F, (byte) 0, (short) 13, 'a'};
            for (int i = 0; i < args.length; i++) {
                MemorySegment ma = findNativeOrThrow("invoke_high_arity" + i);
                FunctionDescriptor fd = baseFD.changeReturnLayout(baseFD.argumentLayouts().get(i));
                Object expected = args[i];
                tests.add(abi.downcallHandle(ma, fd), expected, args);
            }
        }

        return testsList.stream().map(rx -> new Object[]{ rx }).toArray(Object[][]::new);
    }
}
