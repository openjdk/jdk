/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import jdk.vm.ci.meta.InvokeTarget;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.word.LocationIdentity;

/**
 * The details required to link a HotSpot runtime or stub call.
 */
public interface HotSpotForeignCallLinkage extends ForeignCallLinkage, InvokeTarget {

    /**
     * Constants for specifying whether a foreign call destroys or preserves registers. A foreign
     * call will always destroy {@link HotSpotForeignCallLinkage#getOutgoingCallingConvention() its}
     * {@linkplain ForeignCallLinkage#getTemporaries() temporary} registers.
     */
    enum RegisterEffect {
        DESTROYS_REGISTERS,
        PRESERVES_REGISTERS
    }

    /**
     * Constants for specifying whether a call is a leaf or not and whether a
     * {@code JavaFrameAnchor} prologue and epilogue is required around the call. A leaf function
     * does not lock, GC or throw exceptions.
     */
    enum Transition {
        /**
         * A call to a leaf function that is guaranteed to not use floating point registers and will
         * never have its caller stack inspected by the VM. That is, {@code JavaFrameAnchor}
         * management around the call can be omitted.
         */
        LEAF_NOFP,

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
     * Sentinel marker for a computed jump address.
     */
    long JUMP_ADDRESS = 0xDEADDEADBEEFBEEFL;

    boolean isReexecutable();

    LocationIdentity[] getKilledLocations();

    void setCompiledStub(Stub stub);

    /**
     * Determines if this is a call to a compiled {@linkplain Stub stub}.
     */
    boolean isCompiledStub();

    /**
     * Gets the stub, if any, this foreign call links to.
     */
    Stub getStub();

    void finalizeAddress(Backend backend);

    long getAddress();

    /**
     * Determines if the runtime function or stub might use floating point registers. If the answer
     * is no, then no FPU state management prologue or epilogue needs to be emitted around the call.
     */
    boolean mayContainFP();

    /**
     * Determines if a {@code JavaFrameAnchor} needs to be set up and torn down around this call.
     */
    boolean needsJavaFrameAnchor();

    /**
     * Gets the VM symbol associated with the target {@linkplain #getAddress() address} of the call.
     */
    String getSymbol();

    /**
     * Identifies foreign calls which are guaranteed to include a safepoint check.
     */
    boolean isGuaranteedSafepoint();
}
