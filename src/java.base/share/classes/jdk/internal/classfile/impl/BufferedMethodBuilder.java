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
package jdk.internal.classfile.impl;

import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import java.lang.classfile.AccessFlags;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.Utf8Entry;

public final class BufferedMethodBuilder
        implements TerminalMethodBuilder {
    private final List<MethodElement> elements;
    private final SplitConstantPool constantPool;
    private final ClassFileImpl context;
    private final Utf8Entry name;
    private final Utf8Entry desc;
    private AccessFlags flags;
    private final MethodModel original;
    private int[] parameterSlots;
    MethodTypeDesc mDesc;

    public BufferedMethodBuilder(SplitConstantPool constantPool,
                                 ClassFileImpl context,
                                 Utf8Entry nameInfo,
                                 Utf8Entry typeInfo,
                                 int flags,
                                 MethodModel original) {
        this.elements = new ArrayList<>();
        this.constantPool = constantPool;
        this.context = context;
        this.name = nameInfo;
        this.desc = typeInfo;
        this.flags = new AccessFlagsImpl(AccessFlag.Location.METHOD, flags);
        this.original = original;
    }

    @Override
    public MethodBuilder with(MethodElement element) {
        elements.add(element);
        if (element instanceof AccessFlags f) this.flags = checkFlags(f);
        return this;
    }

    private AccessFlags checkFlags(AccessFlags updated) {
        boolean wasStatic = updated.has(AccessFlag.STATIC);
        boolean isStatic = flags.has(AccessFlag.STATIC);
        if (wasStatic != isStatic)
            throw new IllegalArgumentException("Cannot change ACC_STATIC flag of method");
        return updated;
    }

    @Override
    public ConstantPoolBuilder constantPool() {
        return constantPool;
    }

    @Override
    public Utf8Entry methodName() {
        return name;
    }

    @Override
    public Utf8Entry methodType() {
        return desc;
    }

    @Override
    public MethodTypeDesc methodTypeSymbol() {
        if (mDesc == null) {
            if (original instanceof MethodInfo mi) {
                mDesc = mi.methodTypeSymbol();
            } else {
                mDesc = MethodTypeDesc.ofDescriptor(methodType().stringValue());
            }
        }
        return mDesc;
    }

    @Override
    public int methodFlags() {
        return flags.flagsMask();
    }

    @Override
    public int parameterSlot(int paramNo) {
        if (parameterSlots == null)
            parameterSlots = Util.parseParameterSlots(methodFlags(), methodTypeSymbol());
        return parameterSlots[paramNo];
    }

    @Override
    public MethodBuilder withCode(Consumer<? super CodeBuilder> handler) {
        return with(new BufferedCodeBuilder(this, constantPool, context, null)
                            .run(handler)
                            .toModel());
    }

    @Override
    public MethodBuilder transformCode(CodeModel code, CodeTransform transform) {
        BufferedCodeBuilder builder = new BufferedCodeBuilder(this, constantPool, context, code);
        builder.transform(code, transform);
        return with(builder.toModel());
    }

    @Override
    public BufferedCodeBuilder bufferedCodeBuilder(CodeModel original) {
        return new BufferedCodeBuilder(this, constantPool, context, original);
    }

    public BufferedMethodBuilder run(Consumer<? super MethodBuilder> handler) {
        handler.accept(this);
        return this;
    }

    public MethodModel toModel() {
        return new Model();
    }

    public final class Model
            extends AbstractUnboundModel<MethodElement>
            implements MethodModel, MethodInfo {
        public Model() {
            super(BufferedMethodBuilder.this.elements);
        }

        @Override
        public AccessFlags flags() {
            return flags;
        }

        @Override
        public Optional<ClassModel> parent() {
            return Optional.empty();
        }

        @Override
        public Utf8Entry methodName() {
            return name;
        }

        @Override
        public Utf8Entry methodType() {
            return desc;
        }

        @Override
        public MethodTypeDesc methodTypeSymbol() {
            return BufferedMethodBuilder.this.methodTypeSymbol();
        }

        @Override
        public int methodFlags() {
            return flags.flagsMask();
        }

        @Override
        public int parameterSlot(int paramNo) {
            return BufferedMethodBuilder.this.parameterSlot(paramNo);
        }

        @Override
        public Optional<CodeModel> code() {
            return elements.stream().<CodeModel>mapMulti((e, sink) -> {
                if (e instanceof CodeModel cm) {
                    sink.accept(cm);
                }
            }).findFirst();
        }

        @Override
        public void writeTo(DirectClassBuilder builder) {
            builder.withMethod(methodName(), methodType(), methodFlags(), new Consumer<>() {
                @Override
                public void accept(MethodBuilder mb) {
                    forEach(mb);
                }
            });
        }

        @Override
        public String toString() {
            return String.format("MethodModel[methodName=%s, methodType=%s, flags=%d]",
                    name.stringValue(), desc.stringValue(), flags.flagsMask());
        }
    }
}
