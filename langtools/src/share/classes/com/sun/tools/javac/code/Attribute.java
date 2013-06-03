/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.type.DeclaredType;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.*;

/** An annotation value.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Attribute implements AnnotationValue {

    /** The type of the annotation element. */
    public Type type;

    public Attribute(Type type) {
        this.type = type;
    }

    public abstract void accept(Visitor v);

    public Object getValue() {
        throw new UnsupportedOperationException();
    }

    public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
        throw new UnsupportedOperationException();
    }

    public boolean isSynthesized() {
        return false;
    }

    /** The value for an annotation element of primitive type or String. */
    public static class Constant extends Attribute {
        public final Object value;
        public void accept(Visitor v) { v.visitConstant(this); }
        public Constant(Type type, Object value) {
            super(type);
            this.value = value;
        }
        public String toString() {
            return Constants.format(value, type);
        }
        public Object getValue() {
            return Constants.decode(value, type);
        }
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            if (value instanceof String)
                return v.visitString((String) value, p);
            if (value instanceof Integer) {
                int i = (Integer) value;
                switch (type.tag) {
                case BOOLEAN:   return v.visitBoolean(i != 0, p);
                case CHAR:      return v.visitChar((char) i, p);
                case BYTE:      return v.visitByte((byte) i, p);
                case SHORT:     return v.visitShort((short) i, p);
                case INT:       return v.visitInt(i, p);
                }
            }
            switch (type.tag) {
            case LONG:          return v.visitLong((Long) value, p);
            case FLOAT:         return v.visitFloat((Float) value, p);
            case DOUBLE:        return v.visitDouble((Double) value, p);
            }
            throw new AssertionError("Bad annotation element value: " + value);
        }
    }

    /** The value for an annotation element of type java.lang.Class,
     *  represented as a ClassSymbol.
     */
    public static class Class extends Attribute {
        public final Type classType;
        public void accept(Visitor v) { v.visitClass(this); }
        public Class(Types types, Type type) {
            super(makeClassType(types, type));
            this.classType = type;
        }
        static Type makeClassType(Types types, Type type) {
            Type arg = type.isPrimitive()
                ? types.boxedClass(type).type
                : types.erasure(type);
            return new Type.ClassType(types.syms.classType.getEnclosingType(),
                                      List.of(arg),
                                      types.syms.classType.tsym);
        }
        public String toString() {
            return classType + ".class";
        }
        public Type getValue() {
            return classType;
        }
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitType(classType, p);
        }
    }

    /** A compound annotation element value, the type of which is an
     *  attribute interface.
     */
    public static class Compound extends Attribute implements AnnotationMirror {
        /** The attributes values, as pairs.  Each pair contains a
         *  reference to the accessing method in the attribute interface
         *  and the value to be returned when that method is called to
         *  access this attribute.
         */
        public final List<Pair<MethodSymbol,Attribute>> values;

        private boolean synthesized = false;

        @Override
        public boolean isSynthesized() {
            return synthesized;
        }

        public void setSynthesized(boolean synthesized) {
            this.synthesized = synthesized;
        }

        public Compound(Type type,
                        List<Pair<MethodSymbol,Attribute>> values) {
            super(type);
            this.values = values;
        }
        public void accept(Visitor v) { v.visitCompound(this); }

        /**
         * Returns a string representation of this annotation.
         * String is of one of the forms:
         *     @com.example.foo(name1=val1, name2=val2)
         *     @com.example.foo(val)
         *     @com.example.foo
         * Omit parens for marker annotations, and omit "value=" when allowed.
         */
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("@");
            buf.append(type);
            int len = values.length();
            if (len > 0) {
                buf.append('(');
                boolean first = true;
                for (Pair<MethodSymbol, Attribute> value : values) {
                    if (!first) buf.append(", ");
                    first = false;

                    Name name = value.fst.name;
                    if (len > 1 || name != name.table.names.value) {
                        buf.append(name);
                        buf.append('=');
                    }
                    buf.append(value.snd);
                }
                buf.append(')');
            }
            return buf.toString();
        }

        public Attribute member(Name member) {
            for (Pair<MethodSymbol,Attribute> pair : values)
                if (pair.fst.name == member) return pair.snd;
            return null;
        }

        public Attribute.Compound getValue() {
            return this;
        }

        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitAnnotation(this, p);
        }

        public DeclaredType getAnnotationType() {
            return (DeclaredType) type;
        }

        public Map<MethodSymbol, Attribute> getElementValues() {
            Map<MethodSymbol, Attribute> valmap =
                new LinkedHashMap<MethodSymbol, Attribute>();
            for (Pair<MethodSymbol, Attribute> value : values)
                valmap.put(value.fst, value.snd);
            return valmap;
        }
    }

    public static class TypeCompound extends Compound {
        public TypeAnnotationPosition position;
        public TypeCompound(Compound compound,
                TypeAnnotationPosition position) {
            this(compound.type, compound.values, position);
        }
        public TypeCompound(Type type,
                List<Pair<MethodSymbol, Attribute>> values,
                TypeAnnotationPosition position) {
            super(type, values);
            this.position = position;
        }

        public boolean hasUnknownPosition() {
            return position == null || position.type == TargetType.UNKNOWN;
        }

        public boolean isContainerTypeCompound() {
            if (isSynthesized() && values.size() == 1)
                return getFirstEmbeddedTC() != null;
            return false;
        }

        private TypeCompound getFirstEmbeddedTC() {
            if (values.size() == 1) {
                Pair<MethodSymbol, Attribute> val = values.get(0);
                if (val.fst.getSimpleName().contentEquals("value")
                        && val.snd instanceof Array) {
                    Array arr = (Array) val.snd;
                    if (arr.values.length != 0
                            && arr.values[0] instanceof Attribute.TypeCompound)
                        return (Attribute.TypeCompound) arr.values[0];
                }
            }
            return null;
        }

        public boolean tryFixPosition() {
            if (!isContainerTypeCompound())
                return false;

            TypeCompound from = getFirstEmbeddedTC();
            if (from != null && from.position != null &&
                    from.position.type != TargetType.UNKNOWN) {
                position = from.position;
                return true;
            }
            return false;
        }
    }

    /** The value for an annotation element of an array type.
     */
    public static class Array extends Attribute {
        public final Attribute[] values;
        public Array(Type type, Attribute[] values) {
            super(type);
            this.values = values;
        }

        public Array(Type type, List<Attribute> values) {
            super(type);
            this.values = values.toArray(new Attribute[values.size()]);
        }

        public void accept(Visitor v) { v.visitArray(this); }
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append('{');
            boolean first = true;
            for (Attribute value : values) {
                if (!first)
                    buf.append(", ");
                first = false;
                buf.append(value);
            }
            buf.append('}');
            return buf.toString();
        }
        public List<Attribute> getValue() {
            return List.from(values);
        }
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitArray(getValue(), p);
        }
    }

    /** The value for an annotation element of an enum type.
     */
    public static class Enum extends Attribute {
        public VarSymbol value;
        public Enum(Type type, VarSymbol value) {
            super(type);
            this.value = Assert.checkNonNull(value);
        }
        public void accept(Visitor v) { v.visitEnum(this); }
        public String toString() {
            return value.enclClass() + "." + value;     // qualified name
        }
        public VarSymbol getValue() {
            return value;
        }
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitEnumConstant(value, p);
        }
    }

    public static class Error extends Attribute {
        public Error(Type type) {
            super(type);
        }
        public void accept(Visitor v) { v.visitError(this); }
        public String toString() {
            return "<error>";
        }
        public String getValue() {
            return toString();
        }
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitString(toString(), p);
        }
    }

    /** A visitor type for dynamic dispatch on the kind of attribute value. */
    public static interface Visitor {
        void visitConstant(Attribute.Constant value);
        void visitClass(Attribute.Class clazz);
        void visitCompound(Attribute.Compound compound);
        void visitArray(Attribute.Array array);
        void visitEnum(Attribute.Enum e);
        void visitError(Attribute.Error e);
    }

    /** A mirror of java.lang.annotation.RetentionPolicy. */
    public static enum RetentionPolicy {
        SOURCE,
        CLASS,
        RUNTIME
    }
}
