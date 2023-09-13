/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a {@code tableswitch} instruction in the {@code code} array of a
 * {@code Code} attribute.  Delivered as a {@link CodeElement} when traversing
 * the elements of a {@link CodeModel}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface TableSwitchInstruction extends Instruction
        permits AbstractInstruction.BoundTableSwitchInstruction, AbstractInstruction.UnboundTableSwitchInstruction {
    /**
     * {@return the low value of the switch target range, inclusive}
     */
    int lowValue();

    /**
     * {@return the high value of the switch target range, inclusive}
     */
    int highValue();

    /**
     * {@return the default target of the switch}
     */
    Label defaultTarget();

    /**
     * {@return the cases of the switch}
     */
    List<SwitchCase> cases();

    /**
     * {@return a table switch instruction}
     *
     * @param lowValue the low value of the switch target range, inclusive
     * @param highValue the high value of the switch target range, inclusive
     * @param defaultTarget the default target of the switch
     * @param cases the cases of the switch
     */
    static TableSwitchInstruction of(int lowValue, int highValue, Label defaultTarget, List<SwitchCase> cases) {
        return new AbstractInstruction.UnboundTableSwitchInstruction(lowValue, highValue, defaultTarget, cases);
    }
}
