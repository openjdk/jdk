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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.TypeAnnotation;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.RuntimeTypeAnnotations_attribute;
import com.sun.tools.classfile.ConstantPool.InvalidIndex;
import com.sun.tools.classfile.ConstantPool.UnexpectedEntry;

public class ReferenceInfoUtil {

    public static final int IGNORE_VALUE = -321;

    public static List<TypeAnnotation> extendedAnnotationsOf(ClassFile cf) {
        List<TypeAnnotation> annos = new ArrayList<TypeAnnotation>();
        findAnnotations(cf, annos);
        return annos;
    }

    /////////////////// Extract type annotations //////////////////
    private static void findAnnotations(ClassFile cf, List<TypeAnnotation> annos) {
        findAnnotations(cf, Attribute.RuntimeVisibleTypeAnnotations, annos);
        findAnnotations(cf, Attribute.RuntimeInvisibleTypeAnnotations, annos);

        for (Field f : cf.fields) {
            findAnnotations(cf, f, annos);
        }
        for (Method m: cf.methods) {
            findAnnotations(cf, m, annos);
        }
    }

    private static void findAnnotations(ClassFile cf, Method m, List<TypeAnnotation> annos) {
        findAnnotations(cf, m, Attribute.RuntimeVisibleTypeAnnotations, annos);
        findAnnotations(cf, m, Attribute.RuntimeInvisibleTypeAnnotations, annos);
    }

    private static void findAnnotations(ClassFile cf, Field m, List<TypeAnnotation> annos) {
        findAnnotations(cf, m, Attribute.RuntimeVisibleTypeAnnotations, annos);
        findAnnotations(cf, m, Attribute.RuntimeInvisibleTypeAnnotations, annos);
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    private static void findAnnotations(ClassFile cf, String name, List<TypeAnnotation> annos) {
        int index = cf.attributes.getIndex(cf.constant_pool, name);
        if (index != -1) {
            Attribute attr = cf.attributes.get(index);
            assert attr instanceof RuntimeTypeAnnotations_attribute;
            RuntimeTypeAnnotations_attribute tAttr = (RuntimeTypeAnnotations_attribute)attr;
            annos.addAll(Arrays.asList(tAttr.annotations));
        }
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    private static void findAnnotations(ClassFile cf, Method m, String name, List<TypeAnnotation> annos) {
        int index = m.attributes.getIndex(cf.constant_pool, name);
        if (index != -1) {
            Attribute attr = m.attributes.get(index);
            assert attr instanceof RuntimeTypeAnnotations_attribute;
            RuntimeTypeAnnotations_attribute tAttr = (RuntimeTypeAnnotations_attribute)attr;
            annos.addAll(Arrays.asList(tAttr.annotations));
        }

        int cindex = m.attributes.getIndex(cf.constant_pool, Attribute.Code);
        if (cindex != -1) {
            Attribute cattr = m.attributes.get(cindex);
            assert cattr instanceof Code_attribute;
            Code_attribute cAttr = (Code_attribute)cattr;
            index = cAttr.attributes.getIndex(cf.constant_pool, name);
            if (index != -1) {
                Attribute attr = cAttr.attributes.get(index);
                assert attr instanceof RuntimeTypeAnnotations_attribute;
                RuntimeTypeAnnotations_attribute tAttr = (RuntimeTypeAnnotations_attribute)attr;
                annos.addAll(Arrays.asList(tAttr.annotations));
            }
        }
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    private static void findAnnotations(ClassFile cf, Field m, String name, List<TypeAnnotation> annos) {
        int index = m.attributes.getIndex(cf.constant_pool, name);
        if (index != -1) {
            Attribute attr = m.attributes.get(index);
            assert attr instanceof RuntimeTypeAnnotations_attribute;
            RuntimeTypeAnnotations_attribute tAttr = (RuntimeTypeAnnotations_attribute)attr;
            annos.addAll(Arrays.asList(tAttr.annotations));
        }
    }

    /////////////////// TA Position Builder ///////////////////////
    /* TODO: comment out this dead code. Was this unfinished code that was
     * supposed to be used somewhere? The tests pass without this.
    private static class TAPositionBuilder {
        private TypeAnnotation.Position pos = new TypeAnnotation.Position();

        private TAPositionBuilder() { }

        public TypeAnnotation.Position build() { return pos; }

        public static TAPositionBuilder ofType(TypeAnnotation.TargetType type) {
            TAPositionBuilder builder = new TAPositionBuilder();
            builder.pos.type = type;
            return builder;
        }

        public TAPositionBuilder atOffset(int offset) {
            switch (pos.type) {
            // type cast
            case TYPECAST:
            // instanceof
            case INSTANCEOF:
            // new expression
            case NEW:
                pos.offset = offset;
                break;
            default:
                throw new IllegalArgumentException("invalid field for given type: " + pos.type);
            }
            return this;
        }

        public TAPositionBuilder atLocalPosition(int offset, int length, int index) {
            switch (pos.type) {
            // local variable
            case LOCAL_VARIABLE:
                pos.lvarOffset = new int[] { offset };
                pos.lvarLength = new int[] { length };
                pos.lvarIndex  = new int[] { index  };
                break;
            default:
                throw new IllegalArgumentException("invalid field for given type: " + pos.type);
            }
            return this;
        }

        public TAPositionBuilder atParameterIndex(int index) {
            switch (pos.type) {
            // type parameters
            case CLASS_TYPE_PARAMETER:
            case METHOD_TYPE_PARAMETER:
            // method parameter
            case METHOD_FORMAL_PARAMETER:
                pos.parameter_index = index;
                break;
            default:
                throw new IllegalArgumentException("invalid field for given type: " + pos.type);
            }
            return this;
        }

        public TAPositionBuilder atParamBound(int param, int bound) {
            switch (pos.type) {
            // type parameters bounds
            case CLASS_TYPE_PARAMETER_BOUND:
            case METHOD_TYPE_PARAMETER_BOUND:
                pos.parameter_index = param;
                pos.bound_index = bound;
                break;
            default:
                throw new IllegalArgumentException("invalid field for given type: " + pos.type);
            }
            return this;
        }

        public TAPositionBuilder atWildcardPosition(TypeAnnotation.Position pos) {
            switch (pos.type) {
            // wildcards
            case WILDCARD_BOUND:
                pos.wildcard_position = pos;
                break;
            default:
                throw new IllegalArgumentException("invalid field for given type: " + pos.type);
            }
            return this;
        }

        public TAPositionBuilder atTypeIndex(int index) {
            switch (pos.type) {
            // class extends or implements clauses
            case CLASS_EXTENDS:
            // throws
            case THROWS:
                pos.type_index = index;
                break;
            default:
                throw new IllegalArgumentException("invalid field for given type: " + pos.type);
            }
            return this;
        }

        public TAPositionBuilder atOffsetWithIndex(int offset, int index) {
            switch (pos.type) {
            // method type argument: wasn't specified
            case NEW_TYPE_ARGUMENT:
            case METHOD_TYPE_ARGUMENT:
                pos.offset = offset;
                pos.type_index = index;
                break;
            default:
                throw new IllegalArgumentException("invalid field for given type: " + pos.type);
            }
            return this;
        }

        public TAPositionBuilder atGenericLocation(Integer ...loc) {
            pos.location = Arrays.asList(loc);
            pos.type = pos.type.getGenericComplement();
            return this;
        }
    }*/

    /////////////////////// Equality testing /////////////////////
    private static boolean areEquals(int a, int b) {
        return a == b || a == IGNORE_VALUE || b == IGNORE_VALUE;
    }

    private static boolean areEquals(int[] a, int[] a2) {
        if (a==a2)
            return true;
        if (a==null || a2==null)
            return false;

        int length = a.length;
        if (a2.length != length)
            return false;

        for (int i=0; i<length; i++)
            if (a[i] != a2[i] && a[i] != IGNORE_VALUE && a2[i] != IGNORE_VALUE)
                return false;

        return true;
    }

    public static boolean areEquals(TypeAnnotation.Position p1, TypeAnnotation.Position p2) {
        if (p1 == p2)
            return true;
        if (p1 == null || p2 == null)
            return false;

        return ((p1.type == p2.type)
                && (p1.location.equals(p2.location))
                && areEquals(p1.offset, p2.offset)
                && areEquals(p1.lvarOffset, p2.lvarOffset)
                && areEquals(p1.lvarLength, p2.lvarLength)
                && areEquals(p1.lvarIndex, p2.lvarIndex)
                && areEquals(p1.bound_index, p2.bound_index)
                && areEquals(p1.parameter_index, p2.parameter_index)
                && areEquals(p1.type_index, p2.type_index)
                && areEquals(p1.exception_index, p2.exception_index));
    }

    private static TypeAnnotation findAnnotation(String name, List<TypeAnnotation> annotations, ClassFile cf) throws InvalidIndex, UnexpectedEntry {
        String properName = "L" + name + ";";
        for (TypeAnnotation anno : annotations) {
            String actualName = cf.constant_pool.getUTF8Value(anno.annotation.type_index);
            if (properName.equals(actualName))
                return anno;
        }
        return null;
    }

    public static boolean compare(Map<String, TypeAnnotation.Position> expectedAnnos,
            List<TypeAnnotation> actualAnnos, ClassFile cf) throws InvalidIndex, UnexpectedEntry {
        if (actualAnnos.size() != expectedAnnos.size()) {
            throw new ComparisionException("Wrong number of annotations",
                    expectedAnnos,
                    actualAnnos);
        }

        for (Map.Entry<String, TypeAnnotation.Position> e : expectedAnnos.entrySet()) {
            String aName = e.getKey();
            TypeAnnotation.Position expected = e.getValue();
            TypeAnnotation actual = findAnnotation(aName, actualAnnos, cf);
            if (actual == null)
                throw new ComparisionException("Expected annotation not found: " + aName);

            // TODO: you currently get an exception if the test case does not use all necessary
            // annotation attributes, e.g. forgetting the offset for a local variable.
            // It would be nicer to give an understandable warning instead.
            if (!areEquals(expected, actual.position)) {
                throw new ComparisionException("Unexpected position for annotation : " + aName +
                        "\n  Expected: " + expected.toString() +
                        "\n  Found: " + actual.position.toString());
            }
        }
        return true;
    }
}

class ComparisionException extends RuntimeException {
    private static final long serialVersionUID = -3930499712333815821L;

    public final Map<String, TypeAnnotation.Position> expected;
    public final List<TypeAnnotation> found;

    public ComparisionException(String message) {
        this(message, null, null);
    }

    public ComparisionException(String message, Map<String, TypeAnnotation.Position> expected, List<TypeAnnotation> found) {
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
