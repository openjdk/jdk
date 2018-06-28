/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.ValueKind;

/**
 * Represents a value that is yet to be bound to a machine location (such as a {@link RegisterValue}
 * or {@link StackSlot}) by a register allocator.
 */
public final class Variable extends AllocatableValue {

    /**
     * The identifier of the variable. This is a non-zero index in a contiguous 0-based name space.
     */
    public final int index;

    private String name;

    /**
     * Creates a new variable.
     *
     * @param kind
     * @param index
     */
    public Variable(ValueKind<?> kind, int index) {
        super(kind);
        assert index >= 0;
        this.index = index;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        if (name != null) {
            return name;
        } else {
            return "v" + index + getKindSuffix();
        }
    }

    @Override
    public int hashCode() {
        return 71 * super.hashCode() + index;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Variable) {
            Variable other = (Variable) obj;
            return super.equals(other) && index == other.index;
        }
        return false;
    }
}
