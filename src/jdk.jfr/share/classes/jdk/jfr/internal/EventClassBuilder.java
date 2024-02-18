/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import static jdk.jfr.internal.util.Bytecode.invokespecial;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.util.Bytecode;
import jdk.jfr.internal.util.Bytecode.MethodDesc;

// Helper class for building dynamic events
public final class EventClassBuilder {
    private static final ClassDesc TYPE_EVENT = Bytecode.classDesc(Event.class);
    private static final ClassDesc TYPE_IOBE = Bytecode.classDesc(IndexOutOfBoundsException.class);
    private static final MethodDesc DEFAULT_CONSTRUCTOR = MethodDesc.of("<init>", "()V");
    private static final MethodDesc SET_METHOD = MethodDesc.of("set", "(ILjava/lang/Object;)V");
    private static final AtomicLong idCounter = new AtomicLong();

    private final String fullClassName;
    private final ClassDesc type;
    private final List<ValueDescriptor> fields;
    private final List<AnnotationElement> annotationElements;

    public EventClassBuilder(List<AnnotationElement> annotationElements, List<ValueDescriptor> fields) {
        this.fullClassName = "jdk.jfr.DynamicEvent" + idCounter.incrementAndGet();
        this.type = ClassDesc.of(fullClassName);
        this.fields = fields;
        this.annotationElements = annotationElements;
    }

    public Class<? extends Event> build() {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(fullClassName), cb -> build(cb));
        Bytecode.log(fullClassName, bytes);
        return SecuritySupport.defineClass(Event.class, bytes).asSubclass(Event.class);
    }

    void build(ClassBuilder builder) {
        buildClassInfo(builder);
        buildConstructor(builder);
        buildFields(builder);
        buildSetMethod(builder);
    }

    private void buildSetMethod(ClassBuilder builder) {
        // void Event::set(int index, Object value);
        builder.withMethod(SET_METHOD.name(), SET_METHOD.descriptor(), ClassFile.ACC_PUBLIC, methodBuilder -> methodBuilder.withCode(codeBuilder -> {
            int index = 0;
            for (ValueDescriptor v : fields) {
                codeBuilder.iload(1);
                codeBuilder.ldc(index);
                Label notEqual = codeBuilder.newLabel();
                codeBuilder.if_icmpne(notEqual);
                codeBuilder.aload(0); // this
                codeBuilder.aload(2); // value
                ClassDesc cd = Bytecode.classDesc(v);
                Bytecode.unbox(codeBuilder, cd);
                codeBuilder.putfield(type, v.getName(), cd);
                codeBuilder.return_();
                codeBuilder.labelBinding(notEqual);
                index++;
            }
            Bytecode.throwException(codeBuilder, TYPE_IOBE, "Index must between 0 and " + fields.size());
        }));
    }

    private void buildConstructor(ClassBuilder builder) {
        builder.withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, methodBuilder -> methodBuilder.withCode(codeBuilder -> {
            codeBuilder.aload(0);
            invokespecial(codeBuilder, TYPE_EVENT, DEFAULT_CONSTRUCTOR);
            codeBuilder.return_();
        }));
    }

    private void buildClassInfo(ClassBuilder builder) {
        builder.withSuperclass(Bytecode.classDesc(Event.class));
        builder.withFlags(AccessFlag.FINAL, AccessFlag.PUBLIC, AccessFlag.SUPER);
        List<java.lang.classfile.Annotation> annotations = new ArrayList<>();
        for (jdk.jfr.AnnotationElement a : annotationElements) {
            List<java.lang.classfile.AnnotationElement> list = new ArrayList<>();
            for (ValueDescriptor v : a.getValueDescriptors()) {
                // ValueDescriptor can only hold primitive
                // No need to care about classes/enums
                var value = a.getValue(v.getName());
                var av = AnnotationValue.of(value);
                var ae = java.lang.classfile.AnnotationElement.of(v.getName(), av);
                list.add(ae);
            }
            ClassDesc cd = ClassDesc.of(a.getTypeName());
            annotations.add(java.lang.classfile.Annotation.of(cd, list));
        }
        builder.with(RuntimeVisibleAnnotationsAttribute.of(annotations));
    }

    private void buildFields(ClassBuilder builder) {
        for (ValueDescriptor v : fields) {
            builder.withField(v.getName(), Bytecode.classDesc(v), ClassFile.ACC_PRIVATE);
            // No need to store annotations on field since they will be replaced anyway.
        }
    }
}
