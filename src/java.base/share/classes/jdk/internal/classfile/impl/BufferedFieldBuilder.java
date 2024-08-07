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

import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;

public final class BufferedFieldBuilder
        implements TerminalFieldBuilder {
    private final SplitConstantPool constantPool;
    private final ClassFileImpl context;
    private final Utf8Entry name;
    private final Utf8Entry desc;
    private final List<FieldElement> elements = new ArrayList<>();
    private AccessFlags flags;

    public BufferedFieldBuilder(SplitConstantPool constantPool,
                                ClassFileImpl context,
                                Utf8Entry name,
                                Utf8Entry type) {
        this.constantPool = constantPool;
        this.context = context;
        this.name = name;
        this.desc = type;
        this.flags = new AccessFlagsImpl(AccessFlag.Location.FIELD);
    }

    @Override
    public ConstantPoolBuilder constantPool() {
        return constantPool;
    }

    @Override
    public FieldBuilder with(FieldElement element) {
        elements.add(element);
        if (element instanceof AccessFlags f) this.flags = f;
        return this;
    }

    public BufferedFieldBuilder run(Consumer<? super FieldBuilder> handler) {
        handler.accept(this);
        return this;
    }

    public FieldModel toModel() {
        return new Model();
    }

    public final class Model
            extends AbstractUnboundModel<FieldElement>
            implements FieldModel {
        public Model() {
            super(BufferedFieldBuilder.this.elements);
        }

        @Override
        public Optional<ClassModel> parent() {
            return Optional.empty();
        }

        @Override
        public AccessFlags flags() {
            return flags;
        }

        @Override
        public Utf8Entry fieldName() {
            return name;
        }

        @Override
        public Utf8Entry fieldType() {
            return desc;
        }

        @Override
        public void writeTo(DirectClassBuilder builder) {
            builder.withField(name, desc, new Consumer<>() {
                @Override
                public void accept(FieldBuilder fieldBuilder) {
                    elements.forEach(fieldBuilder);
                }
            });
        }

        @Override
        public String toString() {
            return String.format("FieldModel[fieldName=%s, fieldType=%s, flags=%d]", name.stringValue(), desc.stringValue(), flags.flagsMask());
        }
    }
}
