/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.replacements.nodes.CStringConstant.cstring;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.hotspot.nodes.VMErrorNode;
import org.graalvm.compiler.replacements.Log;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.WordFactory;

//JaCoCo Exclude

/**
 * A collection of methods used in {@link Stub}s.
 */
public class StubUtil {

    public static final ForeignCallDescriptor VM_MESSAGE_C = newDescriptor(StubUtil.class, "vmMessageC", void.class, boolean.class, Word.class, long.class, long.class, long.class);

    public static ForeignCallDescriptor newDescriptor(Class<?> stubClass, String name, Class<?> resultType, Class<?>... argumentTypes) {
        ForeignCallDescriptor d = new ForeignCallDescriptor(name, resultType, argumentTypes);
        assert descriptorFor(stubClass, name).equals(d) : descriptorFor(stubClass, name) + " != " + d;
        return d;
    }

    /**
     * Looks for a {@link StubForeignCallNode} node intrinsic named {@code name} in
     * {@code stubClass} and returns a {@link ForeignCallDescriptor} based on its signature and the
     * value of {@code hasSideEffect}.
     */
    private static ForeignCallDescriptor descriptorFor(Class<?> stubClass, String name) {
        Method found = null;
        for (Method method : stubClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getAnnotation(NodeIntrinsic.class) != null && method.getName().equals(name)) {
                if (method.getAnnotation(NodeIntrinsic.class).value().equals(StubForeignCallNode.class)) {
                    assert found == null : "found more than one foreign call named " + name + " in " + stubClass;
                    assert method.getParameterTypes().length != 0 && method.getParameterTypes()[0] == ForeignCallDescriptor.class : "first parameter of foreign call '" + name + "' in " + stubClass +
                                    " must be of type " + ForeignCallDescriptor.class.getSimpleName();
                    found = method;
                }
            }
        }
        assert found != null : "could not find foreign call named " + name + " in " + stubClass;
        List<Class<?>> paramList = Arrays.asList(found.getParameterTypes());
        Class<?>[] cCallTypes = paramList.subList(1, paramList.size()).toArray(new Class<?>[paramList.size() - 1]);
        return new ForeignCallDescriptor(name, found.getReturnType(), cCallTypes);
    }

    /**
     * Determines if this is a HotSpot build where the ASSERT mechanism is enabled.
     */
    @Fold
    public static boolean cAssertionsEnabled(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.cAssertions;
    }

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native void vmMessageC(@ConstantNodeParameter ForeignCallDescriptor stubPrintfC, boolean vmError, Word format, long v1, long v2, long v3);

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     *
     * @param message a message string
     */
    public static void printf(String message) {
        vmMessageC(VM_MESSAGE_C, false, cstring(message), 0L, 0L, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param value the value associated with the first conversion specifier in {@code format}
     */
    public static void printf(String format, long value) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), value, 0L, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     */
    public static void printf(String format, long v1, long v2) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), v1, v2, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     * @param v3 the value associated with the third conversion specifier in {@code format}
     */
    public static void printf(String format, long v1, long v2, long v3) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), v1, v2, v3);
    }

    /**
     * Analyzes a given value and prints information about it to the log stream.
     */
    public static void decipher(long value) {
        vmMessageC(VM_MESSAGE_C, false, WordFactory.zero(), value, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link VMErrorNode#vmError(String, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param message an error message
     */
    public static void fatal(String message) {
        vmMessageC(VM_MESSAGE_C, true, cstring(message), 0L, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param value the value associated with the first conversion specifier in {@code format}
     */
    public static void fatal(String format, long value) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), value, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     */
    public static void fatal(String format, long v1, long v2) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), v1, v2, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     * @param v3 the value associated with the third conversion specifier in {@code format}
     */
    public static void fatal(String format, long v1, long v2, long v3) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), v1, v2, v3);
    }

    /**
     * Print {@code number} as decimal string to {@code buffer}.
     *
     * @param buffer
     * @param number
     * @return A pointer pointing one byte right after the last printed digit in {@code buffer}.
     */
    public static Word printNumber(Word buffer, long number) {
        long tmpNumber = number;
        int offset;
        if (tmpNumber <= 0) {
            tmpNumber = -tmpNumber;
            offset = 1;
        } else {
            offset = 0;
        }
        while (tmpNumber > 0) {
            tmpNumber /= 10;
            offset++;
        }
        tmpNumber = number < 0 ? -number : number;
        Word ptr = buffer.add(offset);
        do {
            long digit = tmpNumber % 10;
            tmpNumber /= 10;
            ptr = ptr.subtract(1);
            ptr.writeByte(0, (byte) ('0' + digit));
        } while (tmpNumber > 0);

        if (number < 0) {
            ptr = ptr.subtract(1);
            ptr.writeByte(0, (byte) '-');
        }
        return buffer.add(offset);
    }

    /**
     * Copy {@code javaString} bytes to the memory location {@code ptr}.
     *
     * @param buffer
     * @param javaString
     * @return A pointer pointing one byte right after the last byte copied from {@code javaString}
     *         to {@code ptr}
     */
    public static Word printString(Word buffer, String javaString) {
        Word string = cstring(javaString);
        int i = 0;
        byte b;
        while ((b = string.readByte(i)) != 0) {
            buffer.writeByte(i, b);
            i++;
        }
        return buffer.add(i);
    }
}
