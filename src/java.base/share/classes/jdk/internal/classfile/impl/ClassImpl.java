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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attribute;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileVersion;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Interfaces;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Superclass;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleHashesAttribute;
import java.lang.classfile.attribute.ModuleMainClassAttribute;
import java.lang.classfile.attribute.ModulePackagesAttribute;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.lang.classfile.attribute.ModuleTargetAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import jdk.internal.access.SharedSecrets;

public final class ClassImpl
        extends AbstractElement
        implements ClassModel {

    final ClassReaderImpl reader;
    private final int attributesPos;
    private final List<MethodModel> methods;
    private final List<FieldModel> fields;
    private List<Attribute<?>> attributes;
    private List<ClassEntry> interfaces;

    public ClassImpl(byte[] cfbytes, ClassFileImpl context) {
        this.reader = new ClassReaderImpl(cfbytes, context);
        int p = reader.interfacesPos;
        int icnt = reader.readU2(p);
        p += 2 + icnt * 2;
        int fcnt = reader.readU2(p);
        FieldImpl[] fields = new FieldImpl[fcnt];
        p += 2;
        for (int i = 0; i < fcnt; ++i) {
            int startPos = p;
            int attrStart = p + 6;
            p = reader.skipAttributeHolder(attrStart);
            fields[i] = new FieldImpl(reader, startPos, p, attrStart);
        }
        this.fields = List.of(fields);
        int mcnt = reader.readU2(p);
        MethodImpl[] methods = new MethodImpl[mcnt];
        p += 2;
        for (int i = 0; i < mcnt; ++i) {
            int startPos = p;
            int attrStart = p + 6;
            p = reader.skipAttributeHolder(attrStart);
            methods[i] = new MethodImpl(reader, startPos, p, attrStart);
        }
        this.methods = List.of(methods);
        this.attributesPos = p;
        reader.setContainedClass(this);
    }

    public int classfileLength() {
        return reader.classfileLength();
    }

    @Override
    public AccessFlags flags() {
        return new AccessFlagsImpl(AccessFlag.Location.CLASS, reader.flags());
    }

    @Override
    public int majorVersion() {
        return reader.readU2(6);
    }

    @Override
    public int minorVersion() {
        return reader.readU2(4);
    }

    @Override
    public ConstantPool constantPool() {
        return reader;
    }

    @Override
    public ClassEntry thisClass() {
        return reader.thisClassEntry();
    }

    @Override
    public Optional<ClassEntry> superclass() {
        return reader.superclassEntry();
    }

    @Override
    public List<ClassEntry> interfaces() {
        if (interfaces == null) {
            int pos = reader.thisClassPos() + 4;
            int cnt = reader.readU2(pos);
            pos += 2;
            var arr = new Object[cnt];
            for (int i = 0; i < cnt; ++i) {
                arr[i] = reader.readEntry(pos, ClassEntry.class);
                pos += 2;
            }
            this.interfaces = SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(arr);
        }
        return interfaces;
    }

    @Override
    public List<Attribute<?>> attributes() {
        if (attributes == null) {
            attributes = BoundAttribute.readAttributes(this, reader, attributesPos, reader.customAttributes());
        }
        return attributes;
    }

    // ClassModel

    @Override
    public void forEach(Consumer<? super ClassElement> consumer) {
        consumer.accept(flags());
        consumer.accept(ClassFileVersion.of(majorVersion(), minorVersion()));
        superclass().ifPresent(new Consumer<ClassEntry>() {
            @Override
            public void accept(ClassEntry entry) {
                consumer.accept(Superclass.of(entry));
            }
        });
        consumer.accept(Interfaces.of(interfaces()));
        fields().forEach(consumer);
        methods().forEach(consumer);
        for (Attribute<?> attr : attributes()) {
            if (attr instanceof ClassElement e)
                consumer.accept(e);
        }
    }

    @Override
    public List<FieldModel> fields() {
        return fields;
    }

    @Override
    public List<MethodModel> methods() {
        return methods;
    }

    @Override
    public boolean isModuleInfo() {
        AccessFlags flags = flags();
        // move to where?
        return flags.has(AccessFlag.MODULE)
               && majorVersion() >= ClassFile.JAVA_9_VERSION
               && thisClass().asInternalName().equals("module-info")
               && (superclass().isEmpty())
               && interfaces().isEmpty()
               && fields().isEmpty()
               && methods().isEmpty()
               && verifyModuleAttributes();
    }

    @Override
    public String toString() {
        return String.format("ClassModel[thisClass=%s, flags=%d]", thisClass().name().stringValue(), flags().flagsMask());
    }

    private boolean verifyModuleAttributes() {
        if (findAttribute(Attributes.module()).isEmpty())
            return false;

        return attributes().stream().allMatch(a ->
                a instanceof ModuleAttribute
             || a instanceof ModulePackagesAttribute
             || a instanceof ModuleHashesAttribute
             || a instanceof ModuleMainClassAttribute
             || a instanceof ModuleResolutionAttribute
             || a instanceof ModuleTargetAttribute
             || a instanceof InnerClassesAttribute
             || a instanceof SourceFileAttribute
             || a instanceof SourceDebugExtensionAttribute
             || a instanceof RuntimeVisibleAnnotationsAttribute
             || a instanceof RuntimeInvisibleAnnotationsAttribute
             || a instanceof CustomAttribute);
    }
}
