/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import jdk.internal.classfile.*;
import jdk.internal.classfile.constantpool.*;
import jdk.internal.classfile.java.lang.constant.*;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.ValueDescriptor;


// Helper class for building dynamic events
public final class EventClassBuilder {

    private static final ClassDesc CD_Event = ClassDesc.ofDescriptor(Event.class.descriptorString());
    private static final ClassDesc CD_IOBE = ClassDesc.ofDescriptor(IndexOutOfBoundsException.class.descriptorString());
    private static final String DEFAULT_CONSTRUCTOR = "<init>";
    private static final MethodTypeDesc DEFAULT_CONSTRUCTOR_DESC = MethodTypeDesc.of(CD_void);
    private static final String SET_METHOD = "set";
    private static final MethodTypeDesc SET_METHOD_DESC = MethodTypeDesc.of(CD_void, CD_int, CD_Object);
    private static final AtomicLong idCounter = new AtomicLong();
    private final String fullClassName;
    private final ClassDesc type;
    private final List<ValueDescriptor> fields;
    private final List<AnnotationElement> annotationElements;

    public EventClassBuilder(List<AnnotationElement> annotationElements, List<ValueDescriptor> fields) {
        this.fullClassName = "jdk.jfr.DynamicEvent" + idCounter.incrementAndGet();
        this.type = ClassDesc.ofDescriptor("L" + fullClassName.replace(".", "/") + ";");
        this.fields = fields;
        this.annotationElements = annotationElements;
    }

    public Class<? extends Event> build() {
        byte[] bytes = buildClassInfo(clb -> {
            buildConstructor(clb);
            buildFields(clb);
            buildSetMethod(clb);
        });
        ASMToolkit.logASM(fullClassName, bytes);
        return SecuritySupport.defineClass(Event.class, bytes).asSubclass(Event.class);
    }

    private void buildSetMethod(ClassBuilder clb) {
        clb.withMethod(SET_METHOD, SET_METHOD_DESC, Classfile.ACC_PUBLIC, mb -> mb.withFlags(Classfile.ACC_PUBLIC).withCode(cob -> {
            int index = 0;
            for (ValueDescriptor v : fields) {
                cob.iload(1);
                cob.constantInstruction(index);
                Label notEqual = cob.newLabel();
                cob.if_icmpne(notEqual);
                cob.aload(0);
                cob.aload(2);
                ClassDesc fieldType = ASMToolkit.toType(v);
                unbox(cob, fieldType);
                cob.putfield(type, v.getName(), fieldType);
                cob.return_();
                cob.labelBinding(notEqual);
                index++;
            }
            cob.newObjectInstruction(CD_IOBE);
            cob.dup();
            cob.constantInstruction("Index must between 0 and " + fields.size());
            cob.invokespecial(CD_IOBE, "<init>", MethodTypeDesc.of(CD_void, CD_String));
            cob.throwInstruction();
        }));
    }

    private void buildConstructor(ClassBuilder clb) {
        clb.withMethod(DEFAULT_CONSTRUCTOR, DEFAULT_CONSTRUCTOR_DESC, Classfile.ACC_PUBLIC, mb ->
                mb.withFlags(Classfile.ACC_PUBLIC).withCode(cob -> {
                    cob.aload(0);
                    cob.invokespecial(CD_Event, DEFAULT_CONSTRUCTOR, DEFAULT_CONSTRUCTOR_DESC);
                    cob.return_();
                }));
    }

    private byte[] buildClassInfo(Consumer<ClassBuilder> config) {
        String internalSuperName = ASMToolkit.getInternalName(Event.class.getName());
        return Classfile.build(type, clb -> {
            clb.withFlags(Classfile.ACC_PUBLIC + Classfile.ACC_FINAL + Classfile.ACC_SUPER);
            clb.withSuperclass(ClassDesc.ofInternalName(internalSuperName));
            if (annotationElements.isEmpty())
                return;
            List<Annotation> result = new ArrayList<>(annotationElements.size());
            ConstantPoolBuilder constantPoolBuilder = clb.constantPool();
            for (AnnotationElement a : annotationElements) {
                result.add(Annotation.of(
                        ASMToolkit.getDescriptor(a.getTypeName()),
                        a.getValueDescriptors().stream().map(v -> jdk.internal.classfile.AnnotationElement.of(
                                v.getName(),
                                AnnotationValue.of(a.getValue(v.getName())))).toList()));
            }
            clb.with(RuntimeVisibleAnnotationsAttribute.of(result));
            config.accept(clb);
        });
    }

    private void buildFields(ClassBuilder clb) {
        for (ValueDescriptor v : fields) {
            ClassDesc ftype = ASMToolkit.getDescriptor(v.getTypeName());
            clb.withField(v.getName(), ftype, Classfile.ACC_PRIVATE);
            // No need to store annotations on field since they will be replaced anyway.
        }
    }

    public static void unbox(CodeBuilder cob, final ClassDesc type) {
        ClassDesc boxedType = CD_Number;
        String unboxMethodName = null;
        MethodTypeDesc unboxMethodSig = null;
        switch (type.descriptorString()) {
            case "V":
                return;
            case "C":
                boxedType = CD_Character;
                unboxMethodName = "charValue";
                unboxMethodSig = MethodTypeDesc.of(CD_char);
                break;
            case "Z":
                boxedType = CD_Boolean;
                unboxMethodName = "booleanValue";
                unboxMethodSig = MethodTypeDesc.of(CD_boolean);
                break;
            case "D":
                unboxMethodName = "doubleValue";
                unboxMethodSig = MethodTypeDesc.of(CD_double);
                break;
            case "F":
                unboxMethodName = "floatValue";
                unboxMethodSig = MethodTypeDesc.of(CD_float);
                break;
            case "J":
                unboxMethodName = "longValue";
                unboxMethodSig = MethodTypeDesc.of(CD_long);
                break;
            case "I":
            case "S":
            case "B":
                unboxMethodName = "intValue";
                unboxMethodSig = MethodTypeDesc.of(CD_int);
                break;
        }
        if (unboxMethodName == null) {
            cob.typeCheckInstruction(Opcode.CHECKCAST, type);
        } else {
            cob.typeCheckInstruction(Opcode.CHECKCAST, boxedType);
            cob.invokevirtual(boxedType, unboxMethodName, unboxMethodSig);
        }
    }
}
