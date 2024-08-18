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
import java.lang.classfile.*;
import java.lang.classfile.constantpool.Utf8Entry;

import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class MethodImpl
        extends AbstractElement
        implements MethodModel, MethodInfo, Util.Writable {

    private final ClassReader reader;
    private final int startPos, endPos, attributesPos;
    private List<Attribute<?>> attributes;
    private int[] parameterSlots;
    private MethodTypeDesc mDesc;

    public MethodImpl(ClassReader reader, int startPos, int endPos, int attrStart) {
        this.reader = reader;
        this.startPos = startPos;
        this.endPos = endPos;
        this.attributesPos = attrStart;
    }

    @Override
    public AccessFlags flags() {
        return new AccessFlagsImpl(AccessFlag.Location.METHOD, reader.readU2(startPos));
    }

    @Override
    public Optional<ClassModel> parent() {
        if (reader instanceof ClassReaderImpl cri)
            return Optional.of(cri.getContainedClass());
        else
            return Optional.empty();
    }

    @Override
    public Utf8Entry methodName() {
        return reader.readEntry(startPos + 2, Utf8Entry.class);
    }

    @Override
    public Utf8Entry methodType() {
        return reader.readEntry(startPos + 4, Utf8Entry.class);
    }

    @Override
    public MethodTypeDesc methodTypeSymbol() {
        if (mDesc == null) {
            mDesc = MethodTypeDesc.ofDescriptor(methodType().stringValue());
        }
        return mDesc;
    }

    @Override
    public int methodFlags() {
        return reader.readU2(startPos);
    }

    @Override
    public int parameterSlot(int paramNo) {
        if (parameterSlots == null)
            parameterSlots = Util.parseParameterSlots(methodFlags(), methodTypeSymbol());
        return parameterSlots[paramNo];
    }

    @Override
    public List<Attribute<?>> attributes() {
        if (attributes == null) {
            attributes = BoundAttribute.readAttributes(this, reader, attributesPos, reader.customAttributes());
        }
        return attributes;
    }

    @Override
    public void writeTo(BufWriterImpl buf) {
        if (buf.canWriteDirect(reader)) {
            reader.copyBytesTo(buf, startPos, endPos - startPos);
        }
        else {
            buf.writeU2(flags().flagsMask());
            buf.writeIndex(methodName());
            buf.writeIndex(methodType());
            Util.writeAttributes(buf, attributes());
        }
    }

    // MethodModel

    @Override
    public Optional<CodeModel> code() {
        return findAttribute(Attributes.code()).map(a -> (CodeModel) a);
    }

    @Override
    public void forEach(Consumer<? super MethodElement> consumer) {
        consumer.accept(flags());
        for (Attribute<?> attr : attributes()) {
            if (attr instanceof MethodElement e)
                consumer.accept(e);
        }
    }

    @Override
    public void writeTo(DirectClassBuilder builder) {
        if (builder.canWriteDirect(reader)) {
            builder.withMethod(this);
        }
        else {
            builder.withMethod(methodName(), methodType(), methodFlags(),
                               new Consumer<>() {
                @Override
                public void accept(MethodBuilder mb) {
                    MethodImpl.this.forEach(mb);
                }
            });
        }
    }

    @Override
    public String toString() {
        return String.format("MethodModel[methodName=%s, methodType=%s, flags=%d]",
                methodName().stringValue(), methodType().stringValue(), flags().flagsMask());
    }
}
