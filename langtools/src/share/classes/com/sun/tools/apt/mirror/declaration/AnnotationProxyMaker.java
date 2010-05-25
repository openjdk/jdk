/*
 * Copyright (c) 2004, 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.mirror.declaration;


import java.lang.annotation.*;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import sun.reflect.annotation.*;

import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.type.MirroredTypeException;
import com.sun.mirror.type.MirroredTypesException;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;


/**
 * A generator of dynamic proxy implementations of
 * java.lang.annotation.Annotation.
 *
 * <p> The "dynamic proxy return form" of an attribute element value is
 * the form used by sun.reflect.annotation.AnnotationInvocationHandler.
 */
@SuppressWarnings("deprecation")
class AnnotationProxyMaker {

    private final AptEnv env;
    private final Attribute.Compound attrs;
    private final Class<? extends Annotation> annoType;


    private AnnotationProxyMaker(AptEnv env,
                                 Attribute.Compound attrs,
                                 Class<? extends Annotation> annoType) {
        this.env = env;
        this.attrs = attrs;
        this.annoType = annoType;
    }


    /**
     * Returns a dynamic proxy for an annotation mirror.
     */
    public static <A extends Annotation> A generateAnnotation(
            AptEnv env, Attribute.Compound attrs, Class<A> annoType) {
        AnnotationProxyMaker apm = new AnnotationProxyMaker(env, attrs, annoType);
        return annoType.cast(apm.generateAnnotation());
    }


    /**
     * Returns a dynamic proxy for an annotation mirror.
     */
    private Annotation generateAnnotation() {
        return AnnotationParser.annotationForMap(annoType,
                                                 getAllReflectedValues());
    }

    /**
     * Returns a map from element names to their values in "dynamic
     * proxy return form".  Includes all elements, whether explicit or
     * defaulted.
     */
    private Map<String, Object> getAllReflectedValues() {
        Map<String, Object> res = new LinkedHashMap<String, Object>();

        for (Map.Entry<MethodSymbol, Attribute> entry :
                                                  getAllValues().entrySet()) {
            MethodSymbol meth = entry.getKey();
            Object value = generateValue(meth, entry.getValue());
            if (value != null) {
                res.put(meth.name.toString(), value);
            } else {
                // Ignore this element.  May lead to
                // IncompleteAnnotationException somewhere down the line.
            }
        }
        return res;
    }

    /**
     * Returns a map from element symbols to their values.
     * Includes all elements, whether explicit or defaulted.
     */
    private Map<MethodSymbol, Attribute> getAllValues() {
        Map<MethodSymbol, Attribute> res =
            new LinkedHashMap<MethodSymbol, Attribute>();

        // First find the default values.
        ClassSymbol sym = (ClassSymbol) attrs.type.tsym;
        for (Scope.Entry e = sym.members().elems; e != null; e = e.sibling) {
            if (e.sym.kind == Kinds.MTH) {
                MethodSymbol m = (MethodSymbol) e.sym;
                Attribute def = m.defaultValue;
                if (def != null) {
                    res.put(m, def);
                }
            }
        }
        // Next find the explicit values, possibly overriding defaults.
        for (Pair<MethodSymbol, Attribute> p : attrs.values) {
            res.put(p.fst, p.snd);
        }
        return res;
    }

    /**
     * Converts an element value to its "dynamic proxy return form".
     * Returns an exception proxy on some errors, but may return null if
     * a useful exception cannot or should not be generated at this point.
     */
    private Object generateValue(MethodSymbol meth, Attribute attr) {
        ValueVisitor vv = new ValueVisitor(meth);
        return vv.getValue(attr);
    }


    private class ValueVisitor implements Attribute.Visitor {

        private MethodSymbol meth;      // annotation element being visited
        private Class<?> runtimeType;   // runtime type of annotation element
        private Object value;           // value in "dynamic proxy return form"

        ValueVisitor(MethodSymbol meth) {
            this.meth = meth;
        }

        Object getValue(Attribute attr) {
            Method method;              // runtime method of annotation element
            try {
                method = annoType.getMethod(meth.name.toString());
            } catch (NoSuchMethodException e) {
                return null;
            }
            runtimeType = method.getReturnType();
            attr.accept(this);
            if (!(value instanceof ExceptionProxy) &&
                !AnnotationType.invocationHandlerReturnType(runtimeType)
                                                        .isInstance(value)) {
                typeMismatch(method, attr);
            }
            return value;
        }


        public void visitConstant(Attribute.Constant c) {
            value = Constants.decodeConstant(c.value, c.type);
        }

        public void visitClass(Attribute.Class c) {
            value = new MirroredTypeExceptionProxy(
                                env.typeMaker.getType(c.type));
        }

        public void visitArray(Attribute.Array a) {
            Type elemtype = env.jctypes.elemtype(a.type);

            if (elemtype.tsym == env.symtab.classType.tsym) {   // Class[]
                // Construct a proxy for a MirroredTypesException
                ArrayList<TypeMirror> elems = new ArrayList<TypeMirror>();
                for (int i = 0; i < a.values.length; i++) {
                    Type elem = ((Attribute.Class) a.values[i]).type;
                    elems.add(env.typeMaker.getType(elem));
                }
                value = new MirroredTypesExceptionProxy(elems);

            } else {
                int len = a.values.length;
                Class<?> runtimeTypeSaved = runtimeType;
                runtimeType = runtimeType.getComponentType();
                try {
                    Object res = Array.newInstance(runtimeType, len);
                    for (int i = 0; i < len; i++) {
                        a.values[i].accept(this);
                        if (value == null || value instanceof ExceptionProxy) {
                            return;
                        }
                        try {
                            Array.set(res, i, value);
                        } catch (IllegalArgumentException e) {
                            value = null;       // indicates a type mismatch
                            return;
                        }
                    }
                    value = res;
                } finally {
                    runtimeType = runtimeTypeSaved;
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitEnum(Attribute.Enum e) {
            if (runtimeType.isEnum()) {
                String constName = e.value.toString();
                try {
                    value = Enum.valueOf((Class)runtimeType, constName);
                } catch (IllegalArgumentException ex) {
                    value = new EnumConstantNotPresentExceptionProxy(
                                                        (Class<Enum<?>>)runtimeType, constName);
                }
            } else {
                value = null;   // indicates a type mismatch
            }
        }

        public void visitCompound(Attribute.Compound c) {
            try {
                Class<? extends Annotation> nested =
                    runtimeType.asSubclass(Annotation.class);
                value = generateAnnotation(env, c, nested);
            } catch (ClassCastException ex) {
                value = null;   // indicates a type mismatch
            }
        }

        public void visitError(Attribute.Error e) {
            value = null;       // indicates a type mismatch
        }


        /**
         * Sets "value" to an ExceptionProxy indicating a type mismatch.
         */
        private void typeMismatch(final Method method, final Attribute attr) {
            value = new ExceptionProxy() {
                private static final long serialVersionUID = 8473323277815075163L;
                public String toString() {
                    return "<error>";   // eg:  @Anno(value=<error>)
                }
                protected RuntimeException generateException() {
                    return new AnnotationTypeMismatchException(method,
                                attr.type.toString());
                }
            };
        }
    }


    /**
     * ExceptionProxy for MirroredTypeException.
     * The toString, hashCode, and equals methods foward to the underlying
     * type.
     */
    private static final class MirroredTypeExceptionProxy extends ExceptionProxy {
        private static final long serialVersionUID = 6662035281599933545L;

        private MirroredTypeException ex;

        MirroredTypeExceptionProxy(TypeMirror t) {
            // It would be safer if we could construct the exception in
            // generateException(), but there would be no way to do
            // that properly following deserialization.
            ex = new MirroredTypeException(t);
        }

        public String toString() {
            return ex.getQualifiedName();
        }

        public int hashCode() {
            TypeMirror t = ex.getTypeMirror();
            return (t != null)
                    ? t.hashCode()
                    : ex.getQualifiedName().hashCode();
        }

        public boolean equals(Object obj) {
            TypeMirror t = ex.getTypeMirror();
            return t != null &&
                   obj instanceof MirroredTypeExceptionProxy &&
                   t.equals(
                        ((MirroredTypeExceptionProxy) obj).ex.getTypeMirror());
        }

        protected RuntimeException generateException() {
            return (RuntimeException) ex.fillInStackTrace();
        }
    }


    /**
     * ExceptionProxy for MirroredTypesException.
     * The toString, hashCode, and equals methods foward to the underlying
     * types.
     */
    private static final class MirroredTypesExceptionProxy extends ExceptionProxy {
        private static final long serialVersionUID = -6670822532616693951L;

        private MirroredTypesException ex;

        MirroredTypesExceptionProxy(Collection<TypeMirror> ts) {
            // It would be safer if we could construct the exception in
            // generateException(), but there would be no way to do
            // that properly following deserialization.
            ex = new MirroredTypesException(ts);
        }

        public String toString() {
            return ex.getQualifiedNames().toString();
        }

        public int hashCode() {
            Collection<TypeMirror> ts = ex.getTypeMirrors();
            return (ts != null)
                    ? ts.hashCode()
                    : ex.getQualifiedNames().hashCode();
        }

        public boolean equals(Object obj) {
            Collection<TypeMirror> ts = ex.getTypeMirrors();
            return ts != null &&
                   obj instanceof MirroredTypesExceptionProxy &&
                   ts.equals(
                      ((MirroredTypesExceptionProxy) obj).ex.getTypeMirrors());
        }

        protected RuntimeException generateException() {
            return (RuntimeException) ex.fillInStackTrace();
        }
    }
}
