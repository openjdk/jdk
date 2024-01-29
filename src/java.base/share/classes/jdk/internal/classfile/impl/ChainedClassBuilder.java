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

import java.util.Optional;
import java.util.function.Consumer;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;

public final class ChainedClassBuilder
        implements ClassBuilder, Consumer<ClassElement> {
    private final ClassBuilder downstream;
    private final DirectClassBuilder terminal;
    private final Consumer<ClassElement> consumer;

    public ChainedClassBuilder(ClassBuilder downstream,
                               Consumer<ClassElement> consumer) {
        this.downstream = downstream;
        this.consumer = consumer;
        this.terminal = switch (downstream) {
            case ChainedClassBuilder cb -> cb.terminal;
            case DirectClassBuilder db -> db;
        };
    }

    @Override
    public ClassBuilder with(ClassElement element) {
        consumer.accept(element);
        return this;
    }

    @Override
    public Optional<ClassModel> original() {
        return terminal.original();
    }

    @Override
    public ClassBuilder withField(Utf8Entry name, Utf8Entry descriptor, Consumer<? super FieldBuilder> handler) {
        return downstream.with(new BufferedFieldBuilder(terminal.constantPool, terminal.context,
                                                        name, descriptor, null)
                                       .run(handler)
                                       .toModel());
    }

    @Override
    public ClassBuilder transformField(FieldModel field, FieldTransform transform) {
        BufferedFieldBuilder builder = new BufferedFieldBuilder(terminal.constantPool, terminal.context,
                                                                field.fieldName(), field.fieldType(),
                                                                field);
        builder.transform(field, transform);
        return downstream.with(builder.toModel());
    }

    @Override
    public ClassBuilder withMethod(Utf8Entry name, Utf8Entry descriptor, int flags,
                                   Consumer<? super MethodBuilder> handler) {
        return downstream.with(new BufferedMethodBuilder(terminal.constantPool, terminal.context,
                                                         name, descriptor, null)
                                       .run(handler)
                                       .toModel());
    }

    @Override
    public ClassBuilder transformMethod(MethodModel method, MethodTransform transform) {
        BufferedMethodBuilder builder = new BufferedMethodBuilder(terminal.constantPool, terminal.context,
                                                                  method.methodName(), method.methodType(), method);
        builder.transform(method, transform);
        return downstream.with(builder.toModel());
    }

    @Override
    public ConstantPoolBuilder constantPool() {
        return terminal.constantPool();
    }

}
