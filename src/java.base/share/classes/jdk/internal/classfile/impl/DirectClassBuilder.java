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

import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.FieldBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.FieldTransform;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.WritableElement;
import java.lang.classfile.constantpool.Utf8Entry;

public final class DirectClassBuilder
        extends AbstractDirectBuilder<ClassModel>
        implements ClassBuilder {

    final ClassEntry thisClassEntry;
    private final List<WritableElement<FieldModel>> fields = new ArrayList<>();
    private final List<WritableElement<MethodModel>> methods = new ArrayList<>();
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
        this.flags = ClassFile.DEFAULT_CLASS_FLAGS;
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
            writeAttribute((CustomAttribute)element);
        }
        return this;
    }

    @Override
    public ClassBuilder withField(Utf8Entry name,
                                  Utf8Entry descriptor,
                                  Consumer<? super FieldBuilder> handler) {
        return withField(new DirectFieldBuilder(constantPool, context, name, descriptor, null)
                                 .run(handler));
    }

    @Override
    public ClassBuilder transformField(FieldModel field, FieldTransform transform) {
        DirectFieldBuilder builder = new DirectFieldBuilder(constantPool, context, field.fieldName(),
                                                            field.fieldType(), field);
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

    public ClassBuilder withField(WritableElement<FieldModel> field) {
        fields.add(field);
        return this;
    }

    public ClassBuilder withMethod(WritableElement<MethodModel> method) {
        methods.add(method);
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
        ClassEntry superclass = superclassEntry;
        if (superclass != null)
            superclass = AbstractPoolEntry.maybeClone(constantPool, superclass);
        else if ((flags & ClassFile.ACC_MODULE) == 0 && !"java/lang/Object".equals(thisClassEntry.asInternalName()))
            superclass = constantPool.classEntry(ConstantDescs.CD_Object);
        List<ClassEntry> ies = new ArrayList<>(interfaceEntries.size());
        for (ClassEntry ce : interfaceEntries)
            ies.add(AbstractPoolEntry.maybeClone(constantPool, ce));

        // We maintain two writers, and then we join them at the end
        int size = sizeHint == 0 ? 256 : sizeHint;
        BufWriter head = new BufWriterImpl(constantPool, context, size);
        BufWriterImpl tail = new BufWriterImpl(constantPool, context, size, thisClassEntry, majorVersion);

        // The tail consists of fields and methods, and attributes
        // This should trigger all the CP/BSM mutation
        tail.writeList(fields);
        tail.writeList(methods);
        int attributesOffset = tail.size();
        attributes.writeTo(tail);

        // Now we have to append the BSM, if there is one
        boolean written = constantPool.writeBootstrapMethods(tail);
        if (written) {
            // Update attributes count
            tail.patchInt(attributesOffset, 2, attributes.size() + 1);
        }

        // Now we can make the head
        head.writeInt(ClassFile.MAGIC_NUMBER);
        head.writeU2(minorVersion);
        head.writeU2(majorVersion);
        constantPool.writeTo(head);
        head.writeU2(flags);
        head.writeIndex(thisClassEntry);
        head.writeIndexOrZero(superclass);
        head.writeListIndices(ies);

        // Join head and tail into an exact-size buffer
        byte[] result = new byte[head.size() + tail.size()];
        head.copyTo(result, 0);
        tail.copyTo(result, head.size());
        return result;
    }
}
