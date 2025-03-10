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

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.util.List;

import jdk.internal.classfile.impl.AbstractInstruction;

/**
 * Models a {@link Opcode#LOOKUPSWITCH lookupswitch} instruction in the {@code
 * code} array of a {@code Code} attribute.  Delivered as a {@link CodeElement}
 * when traversing the elements of a {@link CodeModel}.
 * <p>
 * A lookup switch instruction is composite:
 * {@snippet lang=text :
 * // @link substring="LookupSwitchInstruction" target="#of" :
 * LookupSwitchInstruction(
 *     Label defaultTarget, // @link substring="defaultTarget" target="#defaultTarget"
 *     List<SwitchCase> cases // @link substring="cases" target="#cases()"
 * )
 * }
 * If elements in {@code cases} are not sorted ascending by their {@link
 * SwitchCase#caseValue caseValue}, a sorted version of the {@code cases} list
 * will be written instead.
 *
 * @see Opcode.Kind#LOOKUP_SWITCH
 * @see CodeBuilder#lookupswitch CodeBuilder::lookupswitch
 * @jvms 6.5.lookupswitch <em>lookupswitch</em>
 * @since 24
 */
public sealed interface LookupSwitchInstruction extends Instruction
        permits AbstractInstruction.BoundLookupSwitchInstruction,
                AbstractInstruction.UnboundLookupSwitchInstruction {
    /**
     * {@return the target of the default case}
     */
    Label defaultTarget();

    /**
     * {@return the cases of the switch}
     */
    List<SwitchCase> cases();

    /**
     * {@return a lookup switch instruction}
     *
     * @param defaultTarget the default target of the switch
     * @param cases the cases of the switch
     */
    static LookupSwitchInstruction of(Label defaultTarget, List<SwitchCase> cases) {
        return new AbstractInstruction.UnboundLookupSwitchInstruction(defaultTarget, cases);
    }
}
