/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @build TestUpcallException
 *
 * @run testng/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   TestUpcallException
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestUpcallException extends UpcallTestHelper {

    @Test(dataProvider = "exceptionCases")
    public void testException(Class<?> target, boolean useSpec) throws InterruptedException, IOException {
        runInNewProcess(target, useSpec)
                .assertStdErrContains("Testing upcall exceptions");
    }

    @DataProvider
    public static Object[][] exceptionCases() {
        return new Object[][]{
            { VoidUpcallRunner.class,    false },
            { NonVoidUpcallRunner.class, false },
            { VoidUpcallRunner.class,    true  },
            { NonVoidUpcallRunner.class, true  }
        };
    }

    public static class VoidUpcallRunner extends ExceptionRunnerBase {
        public static void main(String[] args) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment stub = Linker.nativeLinker().upcallStub(VOID_TARGET, FunctionDescriptor.ofVoid(), arena);
                downcallVoid.invoke(stub); // should call Shutdown.exit(1);
            }
        }
    }

    public static class NonVoidUpcallRunner extends ExceptionRunnerBase {
        public static void main(String[] args) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment stub = Linker.nativeLinker().upcallStub(INT_TARGET, FunctionDescriptor.of(C_INT, C_INT), arena);
                downcallNonVoid.invoke(42, stub); // should call Shutdown.exit(1);
            }
        }
    }

    // where

    private static class ExceptionRunnerBase {
        static final MethodHandle downcallVoid;
        static final MethodHandle downcallNonVoid;
        static final MethodHandle VOID_TARGET;
        static final MethodHandle INT_TARGET;

        static final Thread.UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER
                = (thread, throwable) -> System.out.println("From uncaught exception handler");

        static {
                System.loadLibrary("TestUpcall");
            downcallVoid = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("f0_V__"),
                    FunctionDescriptor.ofVoid(C_POINTER)
            );
            downcallNonVoid = Linker.nativeLinker().downcallHandle(
                    findNativeOrThrow("f10_I_I_"),
                    FunctionDescriptor.of(C_INT, C_INT, C_POINTER)
            );
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                VOID_TARGET = lookup.findStatic(ExceptionRunnerBase.class, "throwException",
                        MethodType.methodType(void.class));
                INT_TARGET = lookup.findStatic(ExceptionRunnerBase.class, "throwException",
                        MethodType.methodType(int.class, int.class));
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public static void throwException() {
            throw new RuntimeException("Testing upcall exceptions");
        }

        public static int throwException(int x) {
            throw new RuntimeException("Testing upcall exceptions");
        }
    }
}
