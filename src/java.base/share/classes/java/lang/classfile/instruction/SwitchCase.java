/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.lang.classfile.instruction;

import java.lang.classfile.Label;

import jdk.internal.classfile.impl.AbstractInstruction;

/**
 * Models a single case in a {@link LookupSwitchInstruction lookupswitch} or
 * {@link TableSwitchInstruction tableswitch} instruction.
 * <p>
 * Conceptually, a switch case is a record:
 * {@snippet lang=text :
 * // @link region substring="SwitchCase" target="#of"
 * // @link substring="Label target" target="#target" :
 * SwitchCase(int caseValue, Label target) // @link substring="int caseValue" target="#caseValue"
 * // @end
 * }
 * <p>
 * Physically, a switch case is represented differently in a {@code lookupswitch}
 * versus in a {@code tableswitch}.  In a {@code lookupswitch}, a switch case
 * is as its conceptual representation, a tuple of lookup key and jump target.
 * A {@code tableswitch} instead knows a {@link TableSwitchInstruction#lowValue
 * lowValue}, and the lookup value of the case is implicitly {@code lowValue
 * + index}, where {@code index} is the index of the case into the array of
 * cases.
 *
 * @see LookupSwitchInstruction
 * @see TableSwitchInstruction
 * @since 24
 */
public sealed interface SwitchCase
        permits AbstractInstruction.SwitchCaseImpl {

    /** {@return the integer value corresponding to this case} */
    int caseValue();

    /** {@return the branch target corresponding to this case} */
    Label target();

    /**
     * {@return a new switch case}
     *
     * @param caseValue the integer value for the case
     * @param target the branch target for the case
     */
    static SwitchCase of(int caseValue, Label target) {
        return new AbstractInstruction.SwitchCaseImpl(caseValue, target);
    }
}
