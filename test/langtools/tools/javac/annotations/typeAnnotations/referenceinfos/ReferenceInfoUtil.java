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

import java.io.Serial;
import java.util.*;

public class ReferenceInfoUtil {

    public static final int IGNORE_VALUE = -321;

    public static List<TypeAnnotation> extendedAnnotationsOf(ClassModel cm) {
        List<TypeAnnotation> annos = new ArrayList<>();
        findAnnotations(cm, annos);
        return annos;
    }

    /////////////////// Extract type annotations //////////////////
    private static void findAnnotations(ClassModel cm, List<TypeAnnotation> annos) {
        findAnnotations(cm, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, annos);
        findAnnotations(cm, Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, annos);

        for (FieldModel f : cm.fields()) {
            findAnnotations(cm, f, annos);
        }
        for (MethodModel m: cm.methods()) {
            findAnnotations(cm, m, annos);
        }
    }

    private static void findAnnotations(ClassModel cm, AttributedElement ae, List<TypeAnnotation> annos) {
        findAnnotations(cm, ae, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, annos);
        findAnnotations(cm, ae, Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, annos);
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    private static <T extends Attribute<T>> void findAnnotations(ClassModel cm, AttributeMapper<T> attrName, List<TypeAnnotation> annos) {
        Attribute<T> attr = cm.findAttribute(attrName).orElse(null);
        if (attr != null) {
            if (attr instanceof RuntimeVisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(tAttr.annotations());
            } else if (attr instanceof RuntimeInvisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(tAttr.annotations());
            } else throw new AssertionError();
        }
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    private static <T extends Attribute<T>> void findAnnotations(ClassModel cm, AttributedElement m, AttributeMapper<T> attrName, List<TypeAnnotation> annos) {
        Attribute<T> attr = m.findAttribute(attrName).orElse(null);
        if (attr != null) {
            if (attr instanceof RuntimeVisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(tAttr.annotations());
            } else if (attr instanceof RuntimeInvisibleTypeAnnotationsAttribute tAttr) {
                annos.addAll(tAttr.annotations());
            } else throw new AssertionError();
        }
        if (m instanceof MethodModel mm) {
            CodeAttribute cAttr = mm.findAttribute(Attributes.CODE).orElse(null);
            if (cAttr != null) {
                Attribute<T> attr2 = cAttr.findAttribute(attrName).orElse(null);;
                if (attr2 != null) {
                    if (attr2 instanceof RuntimeVisibleTypeAnnotationsAttribute tAttr2) {
                        annos.addAll(tAttr2.annotations());
                    } else if (attr2 instanceof RuntimeInvisibleTypeAnnotationsAttribute tAttr2) {
                        annos.addAll(tAttr2.annotations());
                    } else throw new AssertionError();
                }
            }
        }
    }

    /////////////////////// Equality testing /////////////////////
    private static boolean areEquals(int a, int b) {
        return a == b || a == IGNORE_VALUE || b == IGNORE_VALUE;
    }

    public static boolean areEquals(TypeAnnotation.TargetInfo p1Info, TypeAnnotation.TargetInfo p2Info) {
        if (p1Info == p2Info) return true;
        boolean equal = false;
        if (p1Info != null && p2Info != null && p1Info.targetType() == p2Info.targetType()) {
            switch (p1Info) {
                case TypeAnnotation.TypeArgumentTarget target -> {
                    TypeAnnotation.TypeArgumentTarget newP2Info = (TypeAnnotation.TypeArgumentTarget) p2Info;
                    equal = areEquals(target.typeArgumentIndex(), newP2Info.typeArgumentIndex());
                }
                case TypeAnnotation.SupertypeTarget target -> {
                    TypeAnnotation.SupertypeTarget newP2Info = (TypeAnnotation.SupertypeTarget) p2Info;
                    equal = areEquals(target.supertypeIndex(), newP2Info.supertypeIndex());
                }
                case TypeAnnotation.TypeParameterTarget target -> {
                    TypeAnnotation.TypeParameterTarget newP2Info = (TypeAnnotation.TypeParameterTarget) p2Info;
                    equal = areEquals(target.typeParameterIndex(), newP2Info.typeParameterIndex());
                }
                case TypeAnnotation.TypeParameterBoundTarget target -> {
                    TypeAnnotation.TypeParameterBoundTarget newP2Info = (TypeAnnotation.TypeParameterBoundTarget) p2Info;
                    equal = areEquals(target.typeParameterIndex(), newP2Info.typeParameterIndex())
                            && areEquals(target.boundIndex(), newP2Info.boundIndex());
                }
                case TypeAnnotation.CatchTarget target -> {
                    TypeAnnotation.CatchTarget newP2Info = (TypeAnnotation.CatchTarget) p2Info;
                    equal = areEquals(target.exceptionTableIndex(), newP2Info.exceptionTableIndex());
                }
                case TypeAnnotation.LocalVarTarget target -> {
                    TypeAnnotation.LocalVarTarget newP2Info = (TypeAnnotation.LocalVarTarget) p2Info;
                    equal = true;
                    for (int i = 0; i < target.table().size(); ++i) {
                        equal = equal & areEquals(target.table().get(i).index(), newP2Info.table().get(i).index());
                    }
                }
                case TypeAnnotation.FormalParameterTarget target -> {
                    TypeAnnotation.FormalParameterTarget newP2Info = (TypeAnnotation.FormalParameterTarget) p2Info;
                    equal = areEquals(target.formalParameterIndex(), newP2Info.formalParameterIndex());
                }
                case TypeAnnotation.ThrowsTarget target -> {
                    TypeAnnotation.ThrowsTarget newP2Info = (TypeAnnotation.ThrowsTarget) p2Info;
                    equal = areEquals(target.throwsTargetIndex(), newP2Info.throwsTargetIndex());
                }
                default -> equal = true; //EmptyTarget
            }
        }
        return equal;
    }

    private static TypeAnnotation findAnnotation(String name, List<TypeAnnotation> annotations) {
        String properName = "L" + name + ";";
        for (TypeAnnotation anno : annotations) {
            String actualName = anno.className().stringValue();
            if (properName.equals(actualName))
                return anno;
        }
        return null;
    }

    public static boolean compare(Map<String, TypeAnnotation> expectedAnnos,
            List<TypeAnnotation> actualAnnos) {
        if (actualAnnos.size() != expectedAnnos.size()) {
            throw new ComparisionException("Wrong number of annotations",
                    expectedAnnos,
                    actualAnnos);
        }

        for (Map.Entry<String, TypeAnnotation> expectedAno : expectedAnnos.entrySet()) {
            String aName = expectedAno.getKey();
            TypeAnnotation.TargetInfo expectedInfo = expectedAno.getValue().targetInfo();
            TypeAnnotation actualAno = findAnnotation(aName, actualAnnos);
            if (actualAno == null)
                throw new ComparisionException("Expected annotation not found: " + aName);

            if (!areEquals(expectedInfo, actualAno.targetInfo()) || !expectedAno.getValue().targetPath().equals(actualAno.targetPath())) {
                throw new ComparisionException("Unexpected position for annotation : " + aName +
                        "\n  Expected: " + expectedInfo.toString() +
                        "\n  Found: " + actualAno.targetInfo().toString());
            }
        }
        return true;
    }
}

class ComparisionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -3930499712333815821L;

    public final Map<String, TypeAnnotation> expected;
    public final List<TypeAnnotation> found;

    public ComparisionException(String message) {
        this(message, null, null);
    }

    public ComparisionException(String message, Map<String, TypeAnnotation> expected, List<TypeAnnotation> found) {
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
