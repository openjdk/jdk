/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

package handle.invoker;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MethodHandleInvoker {
    public void call(MethodHandle methodHandle) throws Throwable {
        try {
            Object[] args = makeArgs(methodHandle.type());
            methodHandle.invokeWithArguments(args);
            throw new AssertionError("Call to restricted method did not fail as expected!");
        } catch (IllegalCallerException ex) {
            if (!ex.getMessage().contains("lookup_module")) {
                throw new AssertionError("Caller module is not lookup_module!");
            }
        } catch (Throwable ex) {
            throw new AssertionError("Call to restricted method did not fail as expected!", ex);
        }
    }

    static final Map<Class<?>, Object> DEFAULT_VALUES = new HashMap<>();

    static void addDefaultMapping(Class<?> carrier, Object value) {
        DEFAULT_VALUES.put(carrier, value);
    }

    static {
        addDefaultMapping(Linker.class, Linker.nativeLinker());
        addDefaultMapping(Path.class, Path.of("nonExistent"));
        addDefaultMapping(String.class, "Hello!");
        addDefaultMapping(Runnable.class, (Runnable)() -> {});
        addDefaultMapping(MethodHandle.class, MethodHandles.identity(int.class));
        addDefaultMapping(Charset.class, Charset.defaultCharset());
        addDefaultMapping(MethodType.class, MethodType.methodType(void.class));
        addDefaultMapping(MemorySegment.class, MemorySegment.NULL);
        addDefaultMapping(MemoryLayout.class, ValueLayout.JAVA_INT);
        addDefaultMapping(FunctionDescriptor.class, FunctionDescriptor.ofVoid());
        addDefaultMapping(Arena.class, Arena.ofAuto());
        addDefaultMapping(MemorySegment.Scope.class, Arena.ofAuto().scope());
        addDefaultMapping(SegmentAllocator.class, SegmentAllocator.prefixAllocator(MemorySegment.ofArray(new byte[10])));
        addDefaultMapping(ValueLayout.OfByte.class, ValueLayout.JAVA_BYTE);
        addDefaultMapping(ValueLayout.OfBoolean.class, ValueLayout.JAVA_BOOLEAN);
        addDefaultMapping(ValueLayout.OfChar.class, ValueLayout.JAVA_CHAR);
        addDefaultMapping(ValueLayout.OfShort.class, ValueLayout.JAVA_SHORT);
        addDefaultMapping(ValueLayout.OfInt.class, ValueLayout.JAVA_INT);
        addDefaultMapping(ValueLayout.OfFloat.class, ValueLayout.JAVA_FLOAT);
        addDefaultMapping(ValueLayout.OfLong.class, ValueLayout.JAVA_LONG);
        addDefaultMapping(ValueLayout.OfDouble.class, ValueLayout.JAVA_DOUBLE);
        addDefaultMapping(AddressLayout.class, ValueLayout.ADDRESS);
        addDefaultMapping(SymbolLookup.class, SymbolLookup.loaderLookup());
        addDefaultMapping(Consumer.class, (Consumer<Object>)(Object o) -> {});
        addDefaultMapping(FunctionDescriptor.class, FunctionDescriptor.ofVoid());
        addDefaultMapping(Linker.Option[].class, null);
        addDefaultMapping(Runtime.class, Runtime.getRuntime());
        addDefaultMapping(byte.class, (byte)0);
        addDefaultMapping(boolean.class, true);
        addDefaultMapping(char.class, (char)0);
        addDefaultMapping(short.class, (short)0);
        addDefaultMapping(int.class, 0);
        addDefaultMapping(float.class, 0f);
        addDefaultMapping(long.class, 0L);
        addDefaultMapping(double.class, 0d);
    }

    static Object[] makeArgs(MethodType type) {
        return type.parameterList().stream()
                .map(MethodHandleInvoker::makeArg)
                .toArray();
    }

    static Object makeArg(Class<?> clazz) {
        if (!DEFAULT_VALUES.containsKey(clazz)) {
            throw new UnsupportedOperationException(clazz.getName());
        }
        return DEFAULT_VALUES.get(clazz);
    }
}
