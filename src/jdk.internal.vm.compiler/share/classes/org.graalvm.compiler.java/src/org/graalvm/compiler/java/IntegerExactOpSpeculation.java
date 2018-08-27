/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.java;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

public final class IntegerExactOpSpeculation implements SpeculationReason {

    public enum IntegerExactOp {
        INTEGER_ADD_EXACT,
        INTEGER_INCREMENT_EXACT,
        INTEGER_SUBTRACT_EXACT,
        INTEGER_DECREMENT_EXACT,
        INTEGER_MULTIPLY_EXACT
    }

    protected final String methodDescriptor;
    protected final IntegerExactOp op;

    public IntegerExactOpSpeculation(ResolvedJavaMethod method, IntegerExactOp op) {
        this.methodDescriptor = method.format("%H.%n(%p)%R");
        this.op = op;
    }

    @Override
    public int hashCode() {
        return methodDescriptor.hashCode() * 31 + op.ordinal();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IntegerExactOpSpeculation) {
            IntegerExactOpSpeculation other = (IntegerExactOpSpeculation) obj;
            return op.equals(other.op) && methodDescriptor.equals(other.methodDescriptor);
        }
        return false;
    }

}
