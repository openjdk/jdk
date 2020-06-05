/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;

import java.util.Arrays;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import jdk.internal.vm.compiler.word.LocationIdentity;

public class HotSpotForeignCallDescriptor extends ForeignCallDescriptor {

    /**
     * Constants for specifying whether a call is a leaf or not and whether a
     * {@code JavaFrameAnchor} prologue and epilogue is required around the call. A leaf function
     * does not lock, GC or throw exceptions.
     */
    public enum Transition {
        /**
         * A call to a leaf function that is guaranteed to not use floating point registers.
         * Consequently, floating point registers cleanup will be waived. On AMD64, this means the
         * compiler will no longer emit vzeroupper instruction around the foreign call, which it
         * normally does for unknown foreign calls to avoid potential SSE-AVX transition penalty.
         * Besides, this foreign call will never have its caller stack inspected by the VM. That is,
         * {@code JavaFrameAnchor} management around the call can be omitted.
         */
        LEAF_NO_VZERO,

        /**
         * A call to a leaf function that might use floating point registers but will never have its
         * caller stack inspected. That is, {@code JavaFrameAnchor} management around the call can
         * be omitted.
         */
        LEAF,

        /**
         * A call to a leaf function that might use floating point registers and may have its caller
         * stack inspected. That is, {@code JavaFrameAnchor} management code around the call is
         * required.
         */
        STACK_INSPECTABLE_LEAF,

        /**
         * A function that may lock, GC or raise an exception and thus requires debug info to be
         * associated with a call site to the function. The execution stack may be inspected while
         * in the called function. That is, {@code JavaFrameAnchor} management code around the call
         * is required.
         */
        SAFEPOINT,
    }

    /**
     * Constants specifying when a foreign call or stub call is re-executable.
     */
    public enum Reexecutability {
        /**
         * Denotes a call that cannot be re-executed. If an exception is raised, the call is
         * deoptimized and the exception is passed on to be dispatched. If the call can throw an
         * exception it needs to have a precise frame state.
         */
        NOT_REEXECUTABLE,

        /**
         * Denotes a call that can always be re-executed. If an exception is raised by the call it
         * may be cleared, compiled code deoptimized and reexecuted. Since the call has no side
         * effects it is assumed that the same exception will be thrown.
         */
        REEXECUTABLE
    }

    private final Transition transition;
    private final Reexecutability reexecutability;

    public HotSpotForeignCallDescriptor(Transition transition, Reexecutability reexecutability, LocationIdentity[] killedLocations, String name, Class<?> resultType, Class<?>... argumentTypes) {
        super(name, resultType, argumentTypes, reexecutability == Reexecutability.REEXECUTABLE, killedLocations, transition == SAFEPOINT, transition == SAFEPOINT);
        this.transition = transition;
        this.reexecutability = reexecutability;
    }

    public HotSpotForeignCallDescriptor(Transition transition, Reexecutability reexecutability, LocationIdentity killedLocation, String name, Class<?> resultType, Class<?>... argumentTypes) {
        this(transition, reexecutability, killedLocation == null ? HotSpotForeignCallsProviderImpl.NO_LOCATIONS : new LocationIdentity[]{killedLocation}, name, resultType, argumentTypes);
    }

    public HotSpotForeignCallDescriptor(ForeignCallSignature signature, Transition transition, Reexecutability reexecutability, LocationIdentity[] killedLocations) {
        this(transition, reexecutability, killedLocations, signature.getName(), signature.getResultType(), signature.getArgumentTypes());
    }

    public Transition getTransition() {
        return transition;
    }

    public Reexecutability getReexecutability() {
        return reexecutability;
    }

    @Override
    public String toString() {
        return "HotSpotForeignCallDescriptor{" + signature +
                        ", isReexecutable=" + isReexecutable +
                        ", canDeoptimize=" + canDeoptimize +
                        ", isGuaranteedSafepoint=" + isGuaranteedSafepoint +
                        ", killedLocations=" + Arrays.toString(killedLocations) +
                        ", transition=" + transition +
                        ", reexecutability=" + reexecutability +
                        '}';
    }
}
