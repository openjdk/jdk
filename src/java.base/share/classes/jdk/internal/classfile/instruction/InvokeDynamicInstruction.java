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
package jdk.internal.classfile.instruction;

import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Function;

import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.Instruction;
import jdk.internal.classfile.constantpool.InvokeDynamicEntry;
import jdk.internal.classfile.constantpool.LoadableConstantEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.Util;

/**
 * Models an {@code invokedynamic} instruction in the {@code code} array of a
 * {@code Code} attribute.  Delivered as a {@link CodeElement} when traversing
 * the elements of a {@link CodeModel}.
 */
public sealed interface InvokeDynamicInstruction extends Instruction
        permits AbstractInstruction.BoundInvokeDynamicInstruction, AbstractInstruction.UnboundInvokeDynamicInstruction {
    /**
     * {@return an {@link InvokeDynamicEntry} describing the call site}
     */
    InvokeDynamicEntry invokedynamic();

    /**
     * {@return the invocation name of the call site}
     */
    default Utf8Entry name() {
        return invokedynamic().name();
    }

    /**
     * {@return the invocation type of the call site}
     */
    default Utf8Entry type() {
        return invokedynamic().type();
    }

    /**
     * {@return the invocation type of the call site, as a symbolic descriptor}
     */
    default MethodTypeDesc typeSymbol() {
        return invokedynamic().typeSymbol();
    }

    /**
     * {@return the bootstrap method of the call site}
     */
    default DirectMethodHandleDesc bootstrapMethod() {
        return invokedynamic().bootstrap()
                              .bootstrapMethod()
                              .asSymbol();
    }

    /**
     * {@return the bootstrap arguments of the call site}
     */
    default List<ConstantDesc> bootstrapArgs() {
        return Util.mappedList(invokedynamic().bootstrap().arguments(), new Function<>() {
            @Override
            public ConstantDesc apply(LoadableConstantEntry loadableConstantEntry) {
                return loadableConstantEntry.constantValue();
            }
        });
    }

    /**
     * {@return an invokedynamic instruction}
     *
     * @param invokedynamic the constant pool entry describing the call site
     */
    static InvokeDynamicInstruction of(InvokeDynamicEntry invokedynamic) {
        return new AbstractInstruction.UnboundInvokeDynamicInstruction(invokedynamic);
    }
}
