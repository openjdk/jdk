/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import java.util.*;

public class ReferenceInfoUtil {

    public static final int IGNORE_VALUE = Integer.MIN_VALUE;

    public static List<TAD> extendedAnnotationsOf(ClassModel cm) {
        List<TAD> annos = new ArrayList<>();
        findAnnotations(cm, annos);
        return annos;
    }

    /////////////////// Extract type annotations //////////////////
    private static void findAnnotations(ClassModel cm, List<TAD> annos) {
        findAnnotations(cm, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, annos);
        findAnnotations(cm, Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, annos);

        for (FieldModel f : cm.fields()) {
            findAnnotations(f, annos);
        }
        for (MethodModel m: cm.methods()) {
            findAnnotations(m, annos);
        }
    }

    private static void findAnnotations(AttributedElement ae, List<TAD> annos) {
        findAnnotations(ae, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, annos);
        findAnnotations(ae, Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, annos);
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    private static <T extends Attribute<T>> void findAnnotations(ClassModel cm, AttributeMapper<T> attrName, List<TAD> annos) {
        Attribute<T> attr = cm.findAttribute(attrName).orElse(null);
        if (attr != null) {
            if (attr instanceof RuntimeVisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(Objects.requireNonNull(generateTADList(tAttr.annotations(), null)));
            } else if (attr instanceof RuntimeInvisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(Objects.requireNonNull(generateTADList(tAttr.annotations(), null)));
            } else throw new AssertionError();
        }
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    private static <T extends Attribute<T>> void findAnnotations(AttributedElement m, AttributeMapper<T> attrName, List<TAD> annos) {
        Attribute<T> attr = m.findAttribute(attrName).orElse(null);
        if (attr != null) {
            if (attr instanceof RuntimeVisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(Objects.requireNonNull(generateTADList(tAttr.annotations(), null)));
            } else if (attr instanceof RuntimeInvisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(Objects.requireNonNull(generateTADList(tAttr.annotations(), null)));
            } else throw new AssertionError();
        }
        if (m instanceof MethodModel mm) {
            CodeAttribute cAttr = mm.findAttribute(Attributes.CODE).orElse(null);
            if (cAttr != null) {
                Attribute<T> attr2 = cAttr.findAttribute(attrName).orElse(null);;
                if (attr2 != null) {
                    if (attr2 instanceof RuntimeVisibleTypeAnnotationsAttribute tAttr2) {
                        annos.addAll(Objects.requireNonNull(generateTADList(tAttr2.annotations(), cAttr)));
                    } else if (attr2 instanceof RuntimeInvisibleTypeAnnotationsAttribute tAttr2) {
                        annos.addAll(Objects.requireNonNull(generateTADList(tAttr2.annotations(), cAttr)));
                    } else throw new AssertionError();
                }
            }
        }
    }

    // get each target information and wrap with TAD (corresponding with TADescription in driver class)
    private static List<TAD> generateTADList(List<TypeAnnotation> annos, CodeAttribute cAttr) {
        List<TAD> result = new ArrayList<>();
        for (TypeAnnotation anno: annos) {
            TAD tad = new TAD();
            tad.annotation = anno.className().stringValue();
            tad.type = anno.targetInfo().targetType();
            switch (anno.targetInfo().targetType()) {
                case CAST, CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, METHOD_INVOCATION_TYPE_ARGUMENT -> {
                    if (cAttr == null) throw new AssertionError("Invalid Annotation");
                    tad.typeIndex = ((TypeAnnotation.TypeArgumentTarget) anno.targetInfo()).typeArgumentIndex();
                    tad.offset = cAttr.labelToBci(((TypeAnnotation.TypeArgumentTarget) anno.targetInfo()).target());
                }
                case CLASS_EXTENDS -> tad.typeIndex = ((TypeAnnotation.SupertypeTarget) anno.targetInfo()).supertypeIndex();
                case CLASS_TYPE_PARAMETER, METHOD_TYPE_PARAMETER -> tad.paramIndex = ((TypeAnnotation.TypeParameterTarget) anno.targetInfo()).typeParameterIndex();
                case CLASS_TYPE_PARAMETER_BOUND, METHOD_TYPE_PARAMETER_BOUND -> {
                    tad.paramIndex = ((TypeAnnotation.TypeParameterBoundTarget) anno.targetInfo()).typeParameterIndex();
                    tad.boundIndex = ((TypeAnnotation.TypeParameterBoundTarget) anno.targetInfo()).boundIndex();
                }
                case EXCEPTION_PARAMETER -> tad.exceptionIndex = ((TypeAnnotation.CatchTarget) anno.targetInfo()).exceptionTableIndex();
                case INSTANCEOF, NEW -> {
                    if (cAttr == null) throw new AssertionError("Invalid Annotation");
                    tad.offset = cAttr.labelToBci(((TypeAnnotation.OffsetTarget) anno.targetInfo()).target());
                }
                case LOCAL_VARIABLE, RESOURCE_VARIABLE -> {
                    if (cAttr == null) throw new AssertionError("Invalid Annotation");
                    TypeAnnotation.LocalVarTarget localTarget = (TypeAnnotation.LocalVarTarget) anno.targetInfo();
                    for (TypeAnnotation.LocalVarTargetInfo localInfo : localTarget.table()) {
                        tad.lvarIndex.add(localInfo.index());
                        tad.lvarOffset.add(cAttr.labelToBci(localInfo.startLabel()));
                        tad.lvarLength.add(cAttr.labelToBci(localInfo.endLabel()) - cAttr.labelToBci(localInfo.startLabel()));
                    }
                }
                case METHOD_FORMAL_PARAMETER -> tad.paramIndex = ((TypeAnnotation.FormalParameterTarget) anno.targetInfo()).formalParameterIndex();
                case THROWS -> tad.typeIndex = ((TypeAnnotation.ThrowsTarget) anno.targetInfo()).throwsTargetIndex();
                default -> {}
            }
            for (TypeAnnotation.TypePathComponent pathComponent : anno.targetPath()) {
                switch (pathComponent.typePathKind()) {
                    case ARRAY -> tad.genericLocation.add(0);
                    case INNER_TYPE -> tad.genericLocation.add(1);
                    case WILDCARD -> tad.genericLocation.add(2);
                    case TYPE_ARGUMENT -> tad.genericLocation.add(3);
                }
                tad.genericLocation.add(pathComponent.typeArgumentIndex());
            }
            result.add(tad);
        }
        return result;
    }

    /////////////////////// Equality testing /////////////////////
    private static boolean areEquals(int a, int b) {
        return a == b || a == IGNORE_VALUE || b == IGNORE_VALUE;
    }

    private static boolean areEquals(List<Integer> a, List<Integer> a2) {
        if (a==a2)
            return true;
        if (a==null || a2==null)
            return false;

        int length = a.size();
        if (a2.size() != length)
            return false;

        for (int i=0; i<length; i++)
            if (!Objects.equals(a.get(i), a2.get(i)) && a.get(i) != IGNORE_VALUE && a2.get(i) != IGNORE_VALUE)
                return false;

        return true;
    }

    public static boolean areEquals(TAD p1, TAD p2) {
        return p1 == p2 || !(p1 == null || p2 == null) &&
                p1.type == p2.type &&
                areEquals(p1.genericLocation, p2.genericLocation) &&
                areEquals(p1.offset, p2.offset) &&
                areEquals(p1.lvarOffset, p2.lvarOffset) &&
                areEquals(p1.lvarLength, p2.lvarLength) &&
                areEquals(p1.lvarIndex, p2.lvarIndex) &&
                areEquals(p1.boundIndex, p2.boundIndex) &&
                areEquals(p1.paramIndex, p2.paramIndex) &&
                areEquals(p1.typeIndex, p2.typeIndex) &&
                areEquals(p1.exceptionIndex, p2.exceptionIndex);

    }

    private static TAD findAnnotation(String name, List<TAD> annotations) {
        String properName = "L" + name + ";";
        for (TAD anno : annotations) {
            String actualName = anno.annotation;
            if (properName.equals(actualName))
                return anno;
        }
        return null;
    }

    public static boolean compare(Map<String, TAD> expectedAnnos,
            List<TAD> actualAnnos) {
        if (actualAnnos.size() != expectedAnnos.size()) {
            throw new ComparisionException("Wrong number of annotations",
                    expectedAnnos,
                    actualAnnos);
        }

        for (Map.Entry<String, TAD> expectedAno : expectedAnnos.entrySet()) {
            String aName = expectedAno.getKey();
            TAD expectedTAD = expectedAno.getValue();
            TAD actualTAD = findAnnotation(aName, actualAnnos);
            if (actualTAD == null)
                throw new ComparisionException("Expected annotation not found: " + aName);

            if (!areEquals(expectedTAD, actualTAD)) {
                throw new ComparisionException("Unexpected position for annotation : " + aName +
                        "\n  Expected: " + expectedTAD +
                        "\n  Found: " + actualTAD);
            }
        }
        return true;
    }
    public static class TAD {
        String annotation;
        TypeAnnotation.TargetType type;
        int typeIndex = IGNORE_VALUE, paramIndex = IGNORE_VALUE, boundIndex = IGNORE_VALUE, exceptionIndex = IGNORE_VALUE, offset = IGNORE_VALUE;
        List<Integer> lvarOffset = new ArrayList<>(), lvarLength = new ArrayList<>(), lvarIndex = new ArrayList<>(), genericLocation = new ArrayList<>();
        public TAD(String a, TypeAnnotation.TargetType t, int tIdx, int pIndx, int bIdx, int eIdx,
                   int ofs, List<Integer> lvarOfs, List<Integer> lvarLen, List<Integer> lvarIdx, List<Integer> genericLoc) {
            annotation = a; type = t; typeIndex = tIdx;
            paramIndex = pIndx; boundIndex = bIdx; exceptionIndex = eIdx;
            offset = ofs; lvarOffset = lvarOfs; lvarLength = lvarLen; lvarIndex = lvarIdx;
            genericLocation = genericLoc;
        }
        public TAD() {}
    }
}

class ComparisionException extends RuntimeException {
    private static final long serialVersionUID = -3930499712333815821L;

    public final Map<String, ReferenceInfoUtil.TAD> expected;
    public final List<ReferenceInfoUtil.TAD> found;

    public ComparisionException(String message) {
        this(message, null, null);
    }

    public ComparisionException(String message, Map<String, ReferenceInfoUtil.TAD> expected, List<ReferenceInfoUtil.TAD> found) {
        super(message);
        this.expected = expected;
        this.found = found;
    }

    public String toString() {
        String str = super.toString();
        if (expected != null && found != null) {
            str += "\n\tExpected: " + expected.size() + " annotations; but found: " + found.size() + " annotations\n" +
                   "  Expected: " + expected +
                   "\n  Found: " + found;
        }
        return str;
    }
}
