/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ConstantDescs;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public final class DirectClassBuilder
        extends AbstractDirectBuilder<ClassModel>
        implements ClassBuilder {

    /** The value of default class access flags */
    static final int DEFAULT_CLASS_FLAGS = ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER;
    static final Util.Writable[] EMPTY_WRITABLE_ARRAY = {};
    static final ClassEntry[] EMPTY_CLASS_ENTRY_ARRAY = {};
    final ClassEntry thisClassEntry;
    private Util.Writable[] fields = EMPTY_WRITABLE_ARRAY;
    private Util.Writable[] methods = EMPTY_WRITABLE_ARRAY;
    private int fieldsCount = 0;
    private int methodsCount = 0;
    private ClassEntry superclassEntry;
    private List<ClassEntry> interfaceEntries;
    private int majorVersion;
    private int minorVersion;
    private int flags;
    private int sizeHint;

    public DirectClassBuilder(SplitConstantPool constantPool,
                              ClassFileImpl context,
                              ClassEntry thisClass) {
        super(constantPool, context);
        this.thisClassEntry = AbstractPoolEntry.maybeClone(constantPool, thisClass);
        this.flags = DEFAULT_CLASS_FLAGS;
        this.superclassEntry = null;
        this.interfaceEntries = Collections.emptyList();
        this.majorVersion = ClassFile.latestMajorVersion();
        this.minorVersion = ClassFile.latestMinorVersion();
    }

    @Override
    public ClassBuilder with(ClassElement element) {
        if (element instanceof AbstractElement ae) {
            ae.writeTo(this);
        } else {
            writeAttribute((CustomAttribute<?>) requireNonNull(element));
        }
        return this;
    }

    @Override
    public ClassBuilder withFlags(int flags) {
        setFlags(flags);
        return this;
    }

    @Override
    public ClassBuilder withField(Utf8Entry name,
                                  Utf8Entry descriptor,
                                  int flags) {
        return withField(new DirectFieldBuilder(constantPool, context, name, descriptor, flags, null));
    }

    @Override
    public ClassBuilder withField(Utf8Entry name,
                                  Utf8Entry descriptor,
                                  Consumer<? super FieldBuilder> handler) {
        return withField(new DirectFieldBuilder(constantPool, context, name, descriptor, 0, null)
                                 .run(handler));
    }

    @Override
    public ClassBuilder transformField(FieldModel field, FieldTransform transform) {
        DirectFieldBuilder builder = new DirectFieldBuilder(constantPool, context, field.fieldName(),
                                                            field.fieldType(), 0, field);
        builder.transform(field, transform);
        return withField(builder);
    }

    @Override
    public ClassBuilder withMethod(Utf8Entry name,
                                   Utf8Entry descriptor,
                                   int flags,
                                   Consumer<? super MethodBuilder> handler) {
        return withMethod(new DirectMethodBuilder(constantPool, context, name, descriptor, flags, null)
                                  .run(handler));
    }

    @Override
    public ClassBuilder transformMethod(MethodModel method, MethodTransform transform) {
        DirectMethodBuilder builder = new DirectMethodBuilder(constantPool, context, method.methodName(),
                                                              method.methodType(),
                                                              method.flags().flagsMask(),
                                                              method);
        builder.transform(method, transform);
        return withMethod(builder);
    }

    // internal / for use by elements

    ClassBuilder withField(Util.Writable field) {
        if (fieldsCount >= fields.length) {
            int newCapacity = fieldsCount + 8;
            this.fields = Arrays.copyOf(fields, newCapacity);
        }
        fields[fieldsCount++] = field;
        return this;
    }

    ClassBuilder withMethod(Util.Writable method) {
        if (methodsCount >= methods.length) {
            int newCapacity = methodsCount + 8;
            this.methods = Arrays.copyOf(methods, newCapacity);
        }
        methods[methodsCount++] = method;
        return this;
    }

    void setSuperclass(ClassEntry superclassEntry) {
        this.superclassEntry = superclassEntry;
    }

    void setInterfaces(List<ClassEntry> interfaces) {
        this.interfaceEntries = interfaces;
    }

    void setVersion(int major, int minor) {
        this.majorVersion = major;
        this.minorVersion = minor;
    }

    void setFlags(int flags) {
        this.flags = flags;
    }

    public void setSizeHint(int sizeHint) {
        this.sizeHint = sizeHint;
    }


    public byte[] build() {

        // The logic of this is very carefully ordered.  We want to avoid
        // repeated buffer copyings, so we accumulate lists of writers which
        // all get written later into the same buffer.  But, writing can often
        // trigger CP / BSM insertions, so we cannot run the CP writer or
        // BSM writers until everything else is written.

        // Do this early because it might trigger CP activity
        var constantPool = this.constantPool;
        ClassEntry superclass = superclassEntry;
        if (superclass != null)
            superclass = AbstractPoolEntry.maybeClone(constantPool, superclass);
        else if ((flags & ClassFile.ACC_MODULE) == 0 && !"java/lang/Object".equals(thisClassEntry.asInternalName()))
            superclass = constantPool.classEntry(ConstantDescs.CD_Object);
        int interfaceEntriesSize = interfaceEntries.size();
        ClassEntry[] ies = interfaceEntriesSize == 0 ? EMPTY_CLASS_ENTRY_ARRAY : buildInterfaceEnties(interfaceEntriesSize);

        // We maintain two writers, and then we join them at the end
        int size = sizeHint == 0 ? 256 : sizeHint;
        BufWriterImpl head = new BufWriterImpl(constantPool, context, size);
        BufWriterImpl tail = new BufWriterImpl(constantPool, context, size, thisClassEntry, majorVersion);

        // The tail consists of fields and methods, and attributes
        // This should trigger all the CP/BSM mutation
        Util.writeList(tail, fields, fieldsCount);
        Util.writeList(tail, methods, methodsCount);
        int attributesOffset = tail.size();
        attributes.writeTo(tail);

        // Now we have to append the BSM, if there is one
        if (constantPool.writeBootstrapMethods(tail)) {
            // Update attributes count
            tail.patchU2(attributesOffset, attributes.size() + 1);
        }

        // Now we can make the head
        head.writeInt(ClassFile.MAGIC_NUMBER);
        head.writeU2U2(minorVersion, majorVersion);
        constantPool.writeTo(head);
        head.writeU2U2U2(flags, head.cpIndex(thisClassEntry), head.cpIndexOrZero(superclass));
        head.writeU2(interfaceEntriesSize);
        for (int i = 0; i < interfaceEntriesSize; i++) {
            head.writeIndex(ies[i]);
        }

        // Join head and tail into an exact-size buffer
        return BufWriterImpl.join(head, tail);
    }

    private ClassEntry[] buildInterfaceEnties(int interfaceEntriesSize) {
        var ies = new ClassEntry[interfaceEntriesSize];
        for (int i = 0; i < interfaceEntriesSize; i++)
            ies[i] = AbstractPoolEntry.maybeClone(constantPool, interfaceEntries.get(i));
        return ies;
    }
}
