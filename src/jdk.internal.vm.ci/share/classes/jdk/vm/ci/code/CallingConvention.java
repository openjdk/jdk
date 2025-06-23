/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.code;

import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

import java.util.List;

/**
 * A calling convention describes the locations in which the arguments for a call are placed and the
 * location in which the return value is placed if the call is not void.
 *
 * @param stackSize         amount of stack space (in bytes) required for the stack-based arguments of the call
 * @param returnLocation    the location for the return value or {@link Value#ILLEGAL} if a void call
 * @param argumentLocations the ordered locations in which the arguments are placed
 */
public record CallingConvention(int                    stackSize,
                                AllocatableValue       returnLocation,
                                List<AllocatableValue> argumentLocations) {

    /**
     * Marker interface denoting the type of a call for which a calling convention is requested.
     */
    public interface Type {
    }

    /**
     * Creates a description of the registers and stack locations used by a call.
     *
     * @param stackSize         amount of stack space (in bytes) required for the stack-based arguments of the call
     * @param returnLocation    the location for the return value or {@link Value#ILLEGAL} if a void call
     * @param argumentLocations the ordered locations in which the arguments are placed. This array is now owned by this
     *                          object and should not be mutated by the caller.
     */
    public CallingConvention(int stackSize, AllocatableValue returnLocation, AllocatableValue... argumentLocations) {
        this(stackSize, returnLocation, CodeUtil.listFromTrustedArray(argumentLocations));
        assert argumentLocations != null;
        assert returnLocation != null;
        assert verify();
    }

    private boolean verify() {
        for (Value location : argumentLocations) {
            assert isStackSlot(location) || isAllocatableValue(location);
        }
        return true;
    }

    /**
     * Gets the location for the {@code index}'th argument.
     */
    public AllocatableValue getArgument(int index) {
        return argumentLocations.get(index);
    }

    /**
     * Gets the number of locations required for the arguments.
     */
    public int getArgumentCount() {
        return argumentLocations.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CallingConvention[");
        String sep = "";
        for (Value op : argumentLocations) {
            sb.append(sep).append(op);
            sep = ", ";
        }
        if (!returnLocation.equals(Value.ILLEGAL)) {
            sb.append(" -> ").append(returnLocation);
        }
        sb.append("]");
        return sb.toString();
    }
}
