/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.core.sparc;

import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringPhase.AddressLowering;

import jdk.vm.ci.meta.JavaConstant;

public class SPARCAddressLowering extends AddressLowering {

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        JavaConstant immBase = asImmediate(base);
        if (immBase != null && SPARCAssembler.isSimm13(immBase)) {
            return lower(offset, immBase.asLong());
        }

        JavaConstant immOffset = asImmediate(offset);
        if (immOffset != null && SPARCAssembler.isSimm13(immOffset)) {
            return lower(base, immOffset.asLong());
        }
        return base.graph().unique(new SPARCIndexedAddressNode(base, offset));
    }

    private AddressNode lower(ValueNode base, long displacement) {
        if (base instanceof AddNode) {
            AddNode add = (AddNode) base;

            JavaConstant immX = asImmediate(add.getX());
            if (immX != null && SPARCAssembler.isSimm13(displacement + immX.asLong())) {
                return lower(add.getY(), displacement + immX.asLong());
            }

            JavaConstant immY = asImmediate(add.getY());
            if (immY != null && SPARCAssembler.isSimm13(displacement + immY.asLong())) {
                return lower(add.getX(), displacement + immY.asLong());
            }

            if (displacement == 0) {
                return lower(add.getX(), add.getY());
            }
        }

        assert SPARCAssembler.isSimm13(displacement);
        return base.graph().unique(new SPARCImmediateAddressNode(base, (int) displacement));
    }

    private static JavaConstant asImmediate(ValueNode value) {
        JavaConstant c = value.asJavaConstant();
        if (c != null && c.getJavaKind().isNumericInteger()) {
            return c;
        } else {
            return null;
        }
    }
}
