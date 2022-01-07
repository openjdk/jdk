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

package handle.invoker;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.SymbolLookup;
import jdk.incubator.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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

    static <Z> void addDefaultMapping(Class<Z> carrier, Z value) {
        DEFAULT_VALUES.put(carrier, value);
    }

    static {
        addDefaultMapping(CLinker.class, CLinker.systemCLinker());
        addDefaultMapping(Path.class, Path.of("nonExistent"));
        addDefaultMapping(String.class, "Hello!");
        addDefaultMapping(Runnable.class, () -> {});
        addDefaultMapping(MethodHandle.class, MethodHandles.identity(int.class));
        addDefaultMapping(Charset.class, Charset.defaultCharset());
        addDefaultMapping(MethodType.class, MethodType.methodType(void.class));
        addDefaultMapping(MemoryAddress.class, MemoryAddress.NULL);
        addDefaultMapping(Addressable.class, MemoryAddress.NULL);
        addDefaultMapping(MemoryLayout.class, ValueLayout.JAVA_INT);
        addDefaultMapping(FunctionDescriptor.class, FunctionDescriptor.ofVoid());
        addDefaultMapping(SymbolLookup.class, SymbolLookup.loaderLookup());
        addDefaultMapping(ResourceScope.class, ResourceScope.newImplicitScope());
        addDefaultMapping(SegmentAllocator.class, SegmentAllocator.prefixAllocator(MemorySegment.ofArray(new byte[10])));
        addDefaultMapping(ValueLayout.OfByte.class, ValueLayout.JAVA_BYTE);
        addDefaultMapping(ValueLayout.OfBoolean.class, ValueLayout.JAVA_BOOLEAN);
        addDefaultMapping(ValueLayout.OfChar.class, ValueLayout.JAVA_CHAR);
        addDefaultMapping(ValueLayout.OfShort.class, ValueLayout.JAVA_SHORT);
        addDefaultMapping(ValueLayout.OfInt.class, ValueLayout.JAVA_INT);
        addDefaultMapping(ValueLayout.OfFloat.class, ValueLayout.JAVA_FLOAT);
        addDefaultMapping(ValueLayout.OfLong.class, ValueLayout.JAVA_LONG);
        addDefaultMapping(ValueLayout.OfDouble.class, ValueLayout.JAVA_DOUBLE);
        addDefaultMapping(ValueLayout.OfAddress.class, ValueLayout.ADDRESS);
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
        Object value = DEFAULT_VALUES.get(clazz);
        if (value == null) {
            throw new UnsupportedOperationException(clazz.getName());
        }
        return value;
    }
}
