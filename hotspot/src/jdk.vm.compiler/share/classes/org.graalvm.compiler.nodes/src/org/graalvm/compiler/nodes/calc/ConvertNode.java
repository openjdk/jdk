/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeInterface;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Represents a conversion between primitive types.
 */
public interface ConvertNode extends ValueNodeInterface {

    ValueNode getValue();

    Constant convert(Constant c, ConstantReflectionProvider constantReflection);

    Constant reverse(Constant c, ConstantReflectionProvider constantReflection);

    /**
     * Check whether a conversion is lossless.
     *
     * @return true iff reverse(convert(c)) == c for all c
     */
    boolean isLossless();

    /**
     * Check whether a conversion preserves comparison order.
     *
     * @param op a comparison operator
     * @return true iff (c1 op c2) == (convert(c1) op convert(c2)) for all c1, c2
     */
    default boolean preservesOrder(Condition op) {
        return isLossless();
    }

    /**
     * Check whether a conversion preserves comparison order against a particular constant value.
     *
     * @param op a comparison operator
     * @param value
     * @param constantReflection
     * @return true iff (c1 op value) == (convert(c1) op convert(value)) for value and all c1
     */
    default boolean preservesOrder(Condition op, Constant value, ConstantReflectionProvider constantReflection) {
        return preservesOrder(op);
    }
}
