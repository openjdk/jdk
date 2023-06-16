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

package jdk.internal.classfile.impl;

import java.lang.constant.MethodTypeDesc;
import java.util.function.Consumer;

import jdk.internal.classfile.BufWriter;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.MethodBuilder;
import jdk.internal.classfile.MethodElement;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.WritableElement;
import jdk.internal.classfile.constantpool.Utf8Entry;

public final class DirectMethodBuilder
        extends AbstractDirectBuilder<MethodModel>
        implements TerminalMethodBuilder, WritableElement<MethodModel>, MethodInfo {

    final Utf8Entry name;
    final Utf8Entry desc;
    int flags;
    int[] parameterSlots;
    MethodTypeDesc mDesc;

    public DirectMethodBuilder(SplitConstantPool constantPool,
                               Utf8Entry nameInfo,
                               Utf8Entry typeInfo,
                               int flags,
                               MethodModel original) {
        super(constantPool);
        setOriginal(original);
        this.name = nameInfo;
        this.desc = typeInfo;
        this.flags = flags;
    }

    void setFlags(int flags) {
        boolean wasStatic = (this.flags & Classfile.ACC_STATIC) != 0;
        boolean isStatic = (flags & Classfile.ACC_STATIC) != 0;
        if (wasStatic != isStatic)
            throw new IllegalArgumentException("Cannot change ACC_STATIC flag of method");
        this.flags = flags;
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
        return flags;
    }

    @Override
    public int parameterSlot(int paramNo) {
        if (parameterSlots == null)
            parameterSlots = Util.parseParameterSlots(methodFlags(), methodTypeSymbol());
        return parameterSlots[paramNo];
    }

    @Override
    public BufferedCodeBuilder bufferedCodeBuilder(CodeModel original) {
        return new BufferedCodeBuilder(this, constantPool, original);
    }

    @Override
    public MethodBuilder with(MethodElement element) {
        ((AbstractElement) element).writeTo(this);
        return this;
    }

    private MethodBuilder withCode(CodeModel original,
                                  Consumer<? super CodeBuilder> handler) {
        var cb = DirectCodeBuilder.build(this, handler, constantPool, original);
        writeAttribute(cb);
        return this;
    }

    @Override
    public MethodBuilder withCode(Consumer<? super CodeBuilder> handler) {
        return withCode(null, handler);
    }

    @Override
    public MethodBuilder transformCode(CodeModel code, CodeTransform transform) {
        return withCode(code, new Consumer<>() {
            @Override
            public void accept(CodeBuilder builder) {
                builder.transform(code, transform);
            }
        });
    }

    public DirectMethodBuilder run(Consumer<? super MethodBuilder> handler) {
        handler.accept(this);
        return this;
    }

    @Override
    public void writeTo(BufWriter b) {
        BufWriterImpl buf = (BufWriterImpl) b;
        buf.writeU2(flags);
        buf.writeIndex(name);
        buf.writeIndex(desc);
        attributes.writeTo(buf);
    }
}
