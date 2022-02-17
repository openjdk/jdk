/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SymbolLookup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ThrowingUpcall extends NativeTestHelper {

    private static final MethodHandle downcallVoid;
    private static final MethodHandle downcallNonVoid;
    public static final MethodHandle MH_throwException;

    static {
        System.loadLibrary("TestUpcall");
        SymbolLookup lookup = SymbolLookup.loaderLookup();
        downcallVoid = CLinker.systemCLinker().downcallHandle(
            lookup.lookup("f0_V__").orElseThrow(),
                FunctionDescriptor.ofVoid(C_POINTER)
        );
        downcallNonVoid = CLinker.systemCLinker().downcallHandle(
            lookup.lookup("f10_I_I_").orElseThrow(),
                FunctionDescriptor.of(C_INT, C_INT, C_POINTER)
        );

        try {
            MH_throwException = MethodHandles.lookup().findStatic(ThrowingUpcall.class, "throwException",
                    MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void throwException() throws Throwable {
        throw new Throwable("Testing upcall exceptions");
    }

    public static void main(String[] args) throws Throwable {
        if (args[0].equals("void")) {
            testVoid();
        } else {
            testNonVoid();
        }
    }

    public static void testVoid() throws Throwable {
        MethodHandle handle = MH_throwException;
        MethodHandle invoker = MethodHandles.exactInvoker(MethodType.methodType(void.class));
        handle = MethodHandles.insertArguments(invoker, 0, handle);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            NativeSymbol stub = CLinker.systemCLinker().upcallStub(handle, FunctionDescriptor.ofVoid(), scope);

            downcallVoid.invoke(stub); // should call Shutdown.exit(1);
        }
    }

    public static void testNonVoid() throws Throwable {
        MethodHandle handle = MethodHandles.identity(int.class);
        handle = MethodHandles.collectArguments(handle, 0, MH_throwException);
        MethodHandle invoker = MethodHandles.exactInvoker(MethodType.methodType(int.class, int.class));
        handle = MethodHandles.insertArguments(invoker, 0, handle);

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            NativeSymbol stub = CLinker.systemCLinker().upcallStub(handle, FunctionDescriptor.of(C_INT, C_INT), scope);

            downcallNonVoid.invoke(42, stub); // should call Shutdown.exit(1);
        }
    }

}
