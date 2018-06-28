/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.spi;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.Value;

/**
 * The runtime specific details of a {@linkplain ForeignCallDescriptor foreign} call.
 */
public interface ForeignCallLinkage extends InvokeTarget {

    /**
     * Gets the details of where parameters are passed and value(s) are returned from the caller's
     * perspective.
     */
    CallingConvention getOutgoingCallingConvention();

    /**
     * Gets the details of where parameters are passed and value(s) are returned from the callee's
     * perspective.
     */
    CallingConvention getIncomingCallingConvention();

    /**
     * Returns the maximum absolute offset of a PC relative call to this stub from any position in
     * the code cache or -1 when not applicable. Intended for determining the required size of
     * address/offset fields.
     */
    long getMaxCallTargetOffset();

    ForeignCallDescriptor getDescriptor();

    /**
     * Gets the values used/killed by this foreign call.
     */
    Value[] getTemporaries();

    /**
     * Determines if the foreign call target destroys all registers.
     *
     * @return {@code true} if the register allocator must save all live registers around a call to
     *         this target
     */
    boolean destroysRegisters();

    /**
     * Determines if debug info needs to be associated with this call. Debug info is required if the
     * function can raise an exception, try to lock, trigger GC or do anything else that requires
     * the VM to be able to inspect the thread's execution state.
     */
    boolean needsDebugInfo();
}
