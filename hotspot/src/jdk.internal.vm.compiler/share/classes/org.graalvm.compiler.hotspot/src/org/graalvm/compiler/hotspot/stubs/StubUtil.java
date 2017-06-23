/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.clearPendingException;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.getAndClearObjectResult;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadHubIntrinsic;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.verifyOops;
import static org.graalvm.compiler.replacements.nodes.CStringConstant.cstring;
import static org.graalvm.compiler.word.Word.unsigned;
import static jdk.vm.ci.meta.DeoptimizationReason.RuntimeConstraint;

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
import org.graalvm.compiler.hotspot.nodes.DeoptimizeCallerNode;
import org.graalvm.compiler.hotspot.nodes.SnippetAnchorNode;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.hotspot.nodes.VMErrorNode;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.replacements.Log;
import org.graalvm.compiler.word.Pointer;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.DeoptimizationAction;

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

    public static void handlePendingException(Word thread, boolean isObjectResult) {
        if (clearPendingException(thread) != null) {
            if (isObjectResult) {
                getAndClearObjectResult(thread);
            }
            DeoptimizeCallerNode.deopt(DeoptimizationAction.None, RuntimeConstraint);
        }
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
        vmMessageC(VM_MESSAGE_C, false, Word.zero(), value, 0L, 0L);
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
     * Verifies that a given object value is well formed if {@code -XX:+VerifyOops} is enabled.
     */
    public static Object verifyObject(Object object) {
        if (verifyOops(INJECTED_VMCONFIG)) {
            Word verifyOopCounter = Word.unsigned(verifyOopCounterAddress(INJECTED_VMCONFIG));
            verifyOopCounter.writeInt(0, verifyOopCounter.readInt(0) + 1);

            Pointer oop = Word.objectToTrackedPointer(object);
            if (object != null) {
                GuardingNode anchorNode = SnippetAnchorNode.anchor();
                // make sure object is 'reasonable'
                if (!oop.and(unsigned(verifyOopMask(INJECTED_VMCONFIG))).equal(unsigned(verifyOopBits(INJECTED_VMCONFIG)))) {
                    fatal("oop not in heap: %p", oop.rawValue());
                }

                KlassPointer klass = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
                if (klass.isNull()) {
                    fatal("klass for oop %p is null", oop.rawValue());
                }
            }
        }
        return object;
    }

    @Fold
    static long verifyOopCounterAddress(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopCounterAddress;
    }

    @Fold
    static long verifyOopMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopMask;
    }

    @Fold
    static long verifyOopBits(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopBits;
    }

    @Fold
    static int hubOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.hubOffset;
    }
}
