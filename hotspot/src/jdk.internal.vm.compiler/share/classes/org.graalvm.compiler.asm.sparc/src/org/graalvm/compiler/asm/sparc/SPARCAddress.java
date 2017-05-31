/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.sparc;

import static jdk.vm.ci.sparc.SPARC.STACK_BIAS;
import static jdk.vm.ci.sparc.SPARC.fp;
import static jdk.vm.ci.sparc.SPARC.sp;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.sparc.SPARC;

import org.graalvm.compiler.asm.AbstractAddress;

public class SPARCAddress extends AbstractAddress {

    private final Register base;
    private final Register index;
    private final int displacement;

    /**
     * Creates an {@link SPARCAddress} with given base register, no scaling and a given
     * displacement.
     *
     * @param base the base register
     * @param displacement the displacement
     */
    public SPARCAddress(Register base, int displacement) {
        this.base = base;
        this.index = Register.None;
        this.displacement = displacement;
    }

    /**
     * Creates an {@link SPARCAddress} with given base register, no scaling and a given index.
     *
     * @param base the base register
     * @param index the index register
     */
    public SPARCAddress(Register base, Register index) {
        this.base = base;
        this.index = index;
        this.displacement = 0;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("[");
        String sep = "";
        if (!getBase().equals(Register.None)) {
            s.append(getBase());
            sep = " + ";
        }
        if (!getIndex().equals(Register.None)) {
            s.append(sep).append(getIndex());
            sep = " + ";
        } else {
            if (getDisplacement() < 0) {
                s.append(" - ").append(-getDisplacement());
            } else if (getDisplacement() > 0) {
                s.append(sep).append(getDisplacement());
            }
        }
        s.append("]");
        return s.toString();
    }

    /**
     * @return Base register that defines the start of the address computation. If not present, is
     *         denoted by {@link Register#None}.
     */
    public Register getBase() {
        return base;
    }

    /**
     * @return Index register, the value of which is added to {@link #getBase}. If not present, is
     *         denoted by {@link Register#None}.
     */
    public Register getIndex() {
        return index;
    }

    /**
     * @return true if this address has an index register
     */
    public boolean hasIndex() {
        return !getIndex().equals(Register.None);
    }

    /**
     * This method adds the stack-bias to the displacement if the base register is either
     * {@link SPARC#sp} or {@link SPARC#fp}.
     *
     * @return Optional additive displacement.
     */
    public int getDisplacement() {
        if (hasIndex()) {
            throw new InternalError("address has index register");
        }
        // TODO Should we also hide the register save area size here?
        if (getBase().equals(sp) || getBase().equals(fp)) {
            return displacement + STACK_BIAS;
        }
        return displacement;
    }
}
