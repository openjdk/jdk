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

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassReader;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.TypeAnnotation;
import static java.lang.classfile.ClassFile.*;
import static java.lang.classfile.TypeAnnotation.TargetInfo.*;

import java.util.List;
import java.lang.classfile.Label;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.access.SharedSecrets;

class AnnotationReader {
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
            case AEV_BYTE -> new AnnotationImpl.OfByteImpl(classReader.readEntry(p, IntegerEntry.class));
            case AEV_CHAR -> new AnnotationImpl.OfCharacterImpl(classReader.readEntry(p, IntegerEntry.class));
            case AEV_DOUBLE -> new AnnotationImpl.OfDoubleImpl(classReader.readEntry(p, DoubleEntry.class));
            case AEV_FLOAT -> new AnnotationImpl.OfFloatImpl(classReader.readEntry(p, FloatEntry.class));
            case AEV_INT -> new AnnotationImpl.OfIntegerImpl(classReader.readEntry(p, IntegerEntry.class));
            case AEV_LONG -> new AnnotationImpl.OfLongImpl(classReader.readEntry(p, LongEntry.class));
            case AEV_SHORT -> new AnnotationImpl.OfShortImpl(classReader.readEntry(p, IntegerEntry.class));
            case AEV_BOOLEAN -> new AnnotationImpl.OfBooleanImpl(classReader.readEntry(p, IntegerEntry.class));
            case AEV_STRING -> new AnnotationImpl.OfStringImpl(classReader.readUtf8Entry(p));
            case AEV_ENUM -> new AnnotationImpl.OfEnumImpl(classReader.readUtf8Entry(p), classReader.readUtf8Entry(p + 2));
            case AEV_CLASS -> new AnnotationImpl.OfClassImpl(classReader.readUtf8Entry(p));
            case AEV_ANNOTATION -> new AnnotationImpl.OfAnnotationImpl(readAnnotation(classReader, p));
            case AEV_ARRAY -> {
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
        Utf8Entry annotationClass = classReader.entryByIndex(classReader.readU2(p), Utf8Entry.class);
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
            Utf8Entry elementName = classReader.readUtf8Entry(p);
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
            case TAT_CLASS_TYPE_PARAMETER ->
                ofClassTypeParameter(classReader.readU1(p));
            case TAT_METHOD_TYPE_PARAMETER ->
                ofMethodTypeParameter(classReader.readU1(p));
            case TAT_CLASS_EXTENDS ->
                ofClassExtends(classReader.readU2(p));
            case TAT_CLASS_TYPE_PARAMETER_BOUND ->
                ofClassTypeParameterBound(classReader.readU1(p), classReader.readU1(p + 1));
            case TAT_METHOD_TYPE_PARAMETER_BOUND ->
                ofMethodTypeParameterBound(classReader.readU1(p), classReader.readU1(p + 1));
            case TAT_FIELD ->
                ofField();
            case TAT_METHOD_RETURN ->
                ofMethodReturn();
            case TAT_METHOD_RECEIVER ->
                ofMethodReceiver();
            case TAT_METHOD_FORMAL_PARAMETER ->
                ofMethodFormalParameter(classReader.readU1(p));
            case TAT_THROWS ->
                ofThrows(classReader.readU2(p));
            case TAT_LOCAL_VARIABLE ->
                ofLocalVariable(readLocalVarEntries(classReader, p, lc, targetType));
            case TAT_RESOURCE_VARIABLE ->
                ofResourceVariable(readLocalVarEntries(classReader, p, lc, targetType));
            case TAT_EXCEPTION_PARAMETER ->
                ofExceptionParameter(classReader.readU2(p));
            case TAT_INSTANCEOF ->
                ofInstanceofExpr(getLabel(lc, classReader.readU2(p), targetType, p));
            case TAT_NEW ->
                ofNewExpr(getLabel(lc, classReader.readU2(p), targetType, p));
            case TAT_CONSTRUCTOR_REFERENCE ->
                ofConstructorReference(getLabel(lc, classReader.readU2(p), targetType, p));
            case TAT_METHOD_REFERENCE ->
                ofMethodReference(getLabel(lc, classReader.readU2(p), targetType, p));
            case TAT_CAST ->
                ofCastExpr(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TAT_CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT ->
                ofConstructorInvocationTypeArgument(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TAT_METHOD_INVOCATION_TYPE_ARGUMENT ->
                ofMethodInvocationTypeArgument(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TAT_CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT ->
                ofConstructorReferenceTypeArgument(getLabel(lc, classReader.readU2(p), targetType, p), classReader.readU1(p + 2));
            case TAT_METHOD_REFERENCE_TYPE_ARGUMENT ->
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
        Utf8Entry type = classReader.readUtf8Entry(p);
        p += 2;
        return TypeAnnotation.of(targetInfo, List.of(typePath), type,
                                 readAnnotationElementValuePairs(classReader, p));
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
}
