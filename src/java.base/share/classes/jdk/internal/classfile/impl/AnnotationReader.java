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

import java.lang.classfile.*;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.List;

import jdk.internal.access.SharedSecrets;

import static java.lang.classfile.AnnotationValue.*;
import static java.lang.classfile.TypeAnnotation.TargetInfo.*;

public final class AnnotationReader {
    private AnnotationReader() { }

    public static List<Annotation> readAnnotations(ClassReader classReader, int p) {
        int pos = p;
        int numAnnotations = classReader.readU2(pos);
        var annos = new Object[numAnnotations];
        pos += 2;
        for (int i = 0; i < numAnnotations; ++i) {
            annos[i] = readAnnotation(classReader, pos);
            pos = skipAnnotation(classReader, pos);
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(annos);
    }

    public static AnnotationValue readElementValue(ClassReader classReader, int p) {
        char tag = (char) classReader.readU1(p);
        ++p;
        return switch (tag) {
            case TAG_BYTE -> new AnnotationImpl.OfByteImpl(classReader.readEntry(p, IntegerEntry.class));
            case TAG_CHAR -> new AnnotationImpl.OfCharImpl(classReader.readEntry(p, IntegerEntry.class));
            case TAG_DOUBLE -> new AnnotationImpl.OfDoubleImpl(classReader.readEntry(p, DoubleEntry.class));
            case TAG_FLOAT -> new AnnotationImpl.OfFloatImpl(classReader.readEntry(p, FloatEntry.class));
            case TAG_INT -> new AnnotationImpl.OfIntImpl(classReader.readEntry(p, IntegerEntry.class));
            case TAG_LONG -> new AnnotationImpl.OfLongImpl(classReader.readEntry(p, LongEntry.class));
            case TAG_SHORT -> new AnnotationImpl.OfShortImpl(classReader.readEntry(p, IntegerEntry.class));
            case TAG_BOOLEAN -> new AnnotationImpl.OfBooleanImpl(classReader.readEntry(p, IntegerEntry.class));
            case TAG_STRING -> new AnnotationImpl.OfStringImpl(classReader.readEntry(p, Utf8Entry.class));
            case TAG_ENUM -> new AnnotationImpl.OfEnumImpl(classReader.readEntry(p, Utf8Entry.class),
                    classReader.readEntry(p + 2, Utf8Entry.class));
            case TAG_CLASS -> new AnnotationImpl.OfClassImpl(classReader.readEntry(p, Utf8Entry.class));
            case TAG_ANNOTATION -> new AnnotationImpl.OfAnnotationImpl(readAnnotation(classReader, p));
            case TAG_ARRAY -> {
                int numValues = classReader.readU2(p);
                p += 2;
                var values = new Object[numValues];
                for (int i = 0; i < numValues; ++i) {
                    values[i] = readElementValue(classReader, p);
                    p = skipElementValue(classReader, p);
                }
                yield new AnnotationImpl.OfArrayImpl(SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(values));
            }
            default -> throw new IllegalArgumentException(
                    "Unexpected tag '%s' in AnnotationValue, pos = %d".formatted(tag, p - 1));
        };
    }

    public static List<TypeAnnotation> readTypeAnnotations(ClassReader classReader, int p, LabelContext lc) {
        int numTypeAnnotations = classReader.readU2(p);
        p += 2;
        var annotations = new Object[numTypeAnnotations];
        for (int i = 0; i < numTypeAnnotations; ++i) {
            annotations[i] = readTypeAnnotation(classReader, p, lc);
            p = skipTypeAnnotation(classReader, p);
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(annotations);
    }

    public static List<List<Annotation>> readParameterAnnotations(ClassReader classReader, int p) {
        int cnt = classReader.readU1(p++);
        var pas = new Object[cnt];
        for (int i = 0; i < cnt; ++i) {
            pas[i] = readAnnotations(classReader, p);
            p = skipAnnotations(classReader, p);
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(pas);
    }

    private static int skipElementValue(ClassReader classReader, int p) {
        char tag = (char) classReader.readU1(p);
        ++p;
        return switch (tag) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's', 'c' -> p + 2;
            case 'e' -> p + 4;
            case '@' -> skipAnnotation(classReader, p);
            case '[' -> {
                int numValues = classReader.readU2(p);
                p += 2;
                for (int i = 0; i < numValues; ++i) {
                    p = skipElementValue(classReader, p);
                }
                yield p;
            }
            default -> throw new IllegalArgumentException(
                    "Unexpected tag '%s' in AnnotationValue, pos = %d".formatted(tag, p - 1));
        };
    }

    private static Annotation readAnnotation(ClassReader classReader, int p) {
        Utf8Entry annotationClass = classReader.readEntry(p, Utf8Entry.class);
        p += 2;
        List<AnnotationElement> elems = readAnnotationElementValuePairs(classReader, p);
        return new AnnotationImpl(annotationClass, elems);
    }

    private static int skipAnnotations(ClassReader classReader, int p) {
        int numAnnotations = classReader.readU2(p);
        p += 2;
        for (int i = 0; i < numAnnotations; ++i)
            p = skipAnnotation(classReader, p);
        return p;
    }

    private static int skipAnnotation(ClassReader classReader, int p) {
        return skipElementValuePairs(classReader, p + 2);
    }

    private static List<AnnotationElement> readAnnotationElementValuePairs(ClassReader classReader, int p) {
        int numElementValuePairs = classReader.readU2(p);
        p += 2;
        var annotationElements = new Object[numElementValuePairs];
        for (int i = 0; i < numElementValuePairs; ++i) {
            Utf8Entry elementName = classReader.readEntry(p, Utf8Entry.class);
            p += 2;
            AnnotationValue value = readElementValue(classReader, p);
            annotationElements[i] = new AnnotationImpl.AnnotationElementImpl(elementName, value);
            p = skipElementValue(classReader, p);
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(annotationElements);
    }

    private static int skipElementValuePairs(ClassReader classReader, int p) {
        int numElementValuePairs = classReader.readU2(p);
        p += 2;
        for (int i = 0; i < numElementValuePairs; ++i) {
            p = skipElementValue(classReader, p + 2);
        }
        return p;
    }

    private static Label getLabel(LabelContext lc, int bciOffset, int targetType, int p) {
        //helper method to avoid NPE
        if (lc == null) throw new IllegalArgumentException("Unexpected targetType '%d' in TypeAnnotation outside of Code attribute, pos = %d".formatted(targetType, p - 1));
        return lc.getLabel(bciOffset);
    }

    private static TypeAnnotation readTypeAnnotation(ClassReader classReader, int p, LabelContext lc) {
        int targetType = classReader.readU1(p++);
        var targetInfo = switch (targetType) {
            case TARGET_CLASS_TYPE_PARAMETER ->
                ofClassTypeParameter(classReader.readU1(p));
            case TARGET_METHOD_TYPE_PARAMETER ->
                ofMethodTypeParameter(classReader.readU1(p));
            case TARGET_CLASS_EXTENDS ->
                ofClassExtends(classReader.readU2(p));
            case TARGET_CLASS_TYPE_PARAMETER_BOUND ->
                ofClassTypeParameterBound(classReader.readU1(p), classReader.readU1(p + 1));
            case TARGET_METHOD_TYPE_PARAMETER_BOUND ->
                ofMethodTypeParameterBound(classReader.readU1(p), classReader.readU1(p + 1));
            case TARGET_FIELD ->
                ofField();
            case TARGET_METHOD_RETURN ->
                ofMethodReturn();
            case TARGET_METHOD_RECEIVER ->
                ofMethodReceiver();
            case TARGET_METHOD_FORMAL_PARAMETER ->
                ofMethodFormalParameter(classReader.readU1(p));
            case TARGET_THROWS ->
                ofThrows(classReader.readU2(p));
            case TARGET_LOCAL_VARIABLE ->
                ofLocalVariable(readLocalVarEntries(classReader, p, lc, targetType));
            case TARGET_RESOURCE_VARIABLE ->
                ofResourceVariable(readLocalVarEntries(classReader, p, lc, targetType));
            case TARGET_EXCEPTION_PARAMETER ->
                ofExceptionParameter(classReader.readU2(p));
            case TARGET_INSTANCEOF ->
                ofInstanceofExpr(getLabel(lc, classReader.readU2(p), targetType, p));
            case TARGET_NEW ->
                ofNewExpr(getLabel(lc, classReader.readU2(p), targetType, p));
            case TARGET_CONSTRUCTOR_REFERENCE ->
                ofConstructorReference(getLabel(lc, classReader.readU2(p), targetType, p));
            case TARGET_METHOD_REFERENCE ->
                ofMethodReference(getLabel(lc, classReader.readU2(p), targetType, p));
            case TARGET_CAST ->
                ofCastExpr(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TARGET_CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT ->
                ofConstructorInvocationTypeArgument(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TARGET_METHOD_INVOCATION_TYPE_ARGUMENT ->
                ofMethodInvocationTypeArgument(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TARGET_CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT ->
                ofConstructorReferenceTypeArgument(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TARGET_METHOD_REFERENCE_TYPE_ARGUMENT ->
                ofMethodReferenceTypeArgument(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            default ->
                throw new IllegalArgumentException("Unexpected targetType '%d' in TypeAnnotation, pos = %d".formatted(targetType, p - 1));
        };
        p += targetInfo.size();
        int pathLength = classReader.readU1(p++);
        TypeAnnotation.TypePathComponent[] typePath = new TypeAnnotation.TypePathComponent[pathLength];
        for (int i = 0; i < pathLength; ++i) {
            int typePathKindTag = classReader.readU1(p++);
            int typeArgumentIndex = classReader.readU1(p++);
            typePath[i] = switch (typePathKindTag) {
                case 0 -> TypeAnnotation.TypePathComponent.ARRAY;
                case 1 -> TypeAnnotation.TypePathComponent.INNER_TYPE;
                case 2 -> TypeAnnotation.TypePathComponent.WILDCARD;
                case 3 -> new UnboundAttribute.TypePathComponentImpl(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT, typeArgumentIndex);
                default -> throw new IllegalArgumentException("Unknown type annotation path component kind: " + typePathKindTag);
            };
        }
        // the annotation info for this annotation
        var anno = readAnnotation(classReader, p);
        return TypeAnnotation.of(targetInfo, List.of(typePath), anno);
    }

    private static List<TypeAnnotation.LocalVarTargetInfo> readLocalVarEntries(ClassReader classReader, int p, LabelContext lc, int targetType) {
        int tableLength = classReader.readU2(p);
        p += 2;
        var entries = new Object[tableLength];
        for (int i = 0; i < tableLength; ++i) {
            int startPc = classReader.readU2(p);
            entries[i] = TypeAnnotation.LocalVarTargetInfo.of(
                    getLabel(lc, startPc, targetType, p),
                    getLabel(lc, startPc + classReader.readU2(p + 2), targetType, p - 2),
                    classReader.readU2(p + 4));
            p += 6;
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(entries);
    }

    private static int skipTypeAnnotation(ClassReader classReader, int p) {
        int targetType = classReader.readU1(p++);
        p += switch (targetType) {
            case 0x13, 0x14, 0x15 -> 0;
            case 0x00, 0x01, 0x16 -> 1;
            case 0x10, 0x11, 0x12, 0x17, 0x42, 0x43, 0x44, 0x45, 0x46 -> 2;
            case 0x47, 0x48, 0x49, 0x4A, 0x4B -> 3;
            case 0x40, 0x41 -> 2 + classReader.readU2(p) * 6;
            default -> throw new IllegalArgumentException(
                    "Unexpected targetType '%d' in TypeAnnotation, pos = %d".formatted(targetType, p - 1));
        };
        int pathLength = classReader.readU1(p++);
        p += pathLength * 2;

        // the annotation info for this annotation
        p += 2;
        p = skipElementValuePairs(classReader, p);
        return p;
    }

    public static void writeAnnotation(BufWriterImpl buf, Annotation annotation) {
        var elements = annotation.elements();
        buf.writeU2U2(buf.cpIndex(annotation.className()), elements.size());
        for (var e : elements) {
            buf.writeIndex(e.name());
            AnnotationReader.writeAnnotationValue(buf, e.value());
        }
    }

    public static void writeAnnotations(BufWriter buf, List<Annotation> list) {
        var internalBuf = (BufWriterImpl) buf;
        internalBuf.writeU2(list.size());
        for (var e : list) {
            writeAnnotation(internalBuf, e);
        }
    }

    private static int labelToBci(LabelContext lr, Label label, TypeAnnotation ta) {
        //helper method to avoid NPE
        if (lr == null) throw new IllegalArgumentException("Illegal targetType '%s' in TypeAnnotation outside of Code attribute".formatted(ta.targetInfo().targetType()));
        return lr.labelToBci(label);
    }

    public static void writeTypeAnnotation(BufWriterImpl buf, TypeAnnotation ta) {
        LabelContext lr = buf.labelContext();
        // target_type
        buf.writeU1(ta.targetInfo().targetType().targetTypeValue());

        // target_info
        switch (ta.targetInfo()) {
            case TypeAnnotation.TypeParameterTarget tpt -> buf.writeU1(tpt.typeParameterIndex());
            case TypeAnnotation.SupertypeTarget st -> buf.writeU2(st.supertypeIndex());
            case TypeAnnotation.TypeParameterBoundTarget tpbt -> {
                buf.writeU1U1(tpbt.typeParameterIndex(), tpbt.boundIndex());
            }
            case TypeAnnotation.EmptyTarget _ -> {
                // nothing to write
            }
            case TypeAnnotation.FormalParameterTarget fpt -> buf.writeU1(fpt.formalParameterIndex());
            case TypeAnnotation.ThrowsTarget tt -> buf.writeU2(tt.throwsTargetIndex());
            case TypeAnnotation.LocalVarTarget lvt -> {
                buf.writeU2(lvt.table().size());
                for (var e : lvt.table()) {
                    int startPc = labelToBci(lr, e.startLabel(), ta);
                    buf.writeU2U2U2(startPc, labelToBci(lr, e.endLabel(), ta) - startPc, e.index());
                }
            }
            case TypeAnnotation.CatchTarget ct -> buf.writeU2(ct.exceptionTableIndex());
            case TypeAnnotation.OffsetTarget ot -> buf.writeU2(labelToBci(lr, ot.target(), ta));
            case TypeAnnotation.TypeArgumentTarget tat -> {
                buf.writeU2U1(labelToBci(lr, tat.target(), ta),
                        tat.typeArgumentIndex());
            }
        }

        // target_path
        buf.writeU1(ta.targetPath().size());
        for (TypeAnnotation.TypePathComponent component : ta.targetPath()) {
            buf.writeU1U1(component.typePathKind().tag(), component.typeArgumentIndex());
        }

        // annotation data
        writeAnnotation(buf, ta.annotation());
    }

    public static void writeTypeAnnotations(BufWriter buf, List<TypeAnnotation> list) {
        var internalBuf = (BufWriterImpl) buf;
        internalBuf.writeU2(list.size());
        for (var e : list) {
            writeTypeAnnotation(internalBuf, e);
        }
    }

    public static void writeAnnotationValue(BufWriterImpl buf, AnnotationValue value) {
        var tag = value.tag();
        buf.writeU1(tag);
        switch (tag) {
            case TAG_BOOLEAN, TAG_BYTE, TAG_CHAR, TAG_DOUBLE, TAG_FLOAT, TAG_INT, TAG_LONG, TAG_SHORT, TAG_STRING ->
                    buf.writeIndex(((AnnotationValue.OfConstant) value).constant());
            case TAG_CLASS -> buf.writeIndex(((AnnotationValue.OfClass) value).className());
            case TAG_ENUM -> {
                var enumValue = (AnnotationValue.OfEnum) value;
                buf.writeIndex(enumValue.className());
                buf.writeIndex(enumValue.constantName());
            }
            case TAG_ANNOTATION -> writeAnnotation(buf, ((AnnotationValue.OfAnnotation) value).annotation());
            case TAG_ARRAY -> {
                var array = ((AnnotationValue.OfArray) value).values();
                buf.writeU2(array.size());
                for (var e : array) {
                    writeAnnotationValue(buf, e);
                }
            }
            default -> throw new InternalError("Unknown value " + value);
        }
    }
}
