/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.InvocationTargetException;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.HashMap;

/**
 * Dynamically implements the typed annotation writer interfaces.
 *
 * @author Kohsuke Kawaguchi
 */
class TypedAnnotationWriter<A extends Annotation,W extends JAnnotationWriter<A>>
    implements InvocationHandler, JAnnotationWriter<A> {
    /**
     * This is what we are writing to.
     */
    private final JAnnotationUse use;

    /**
     * The annotation that we are writing.
     */
    private final Class<A> annotation;

    /**
     * The type of the writer.
     */
    private final Class<W> writerType;

    /**
     * Keeps track of writers for array members.
     * Lazily created.
     */
    private Map<String,JAnnotationArrayMember> arrays;

    public TypedAnnotationWriter(Class<A> annotation, Class<W> writer, JAnnotationUse use) {
        this.annotation = annotation;
        this.writerType = writer;
        this.use = use;
    }

    public JAnnotationUse getAnnotationUse() {
        return use;
    }

    public Class<A> getAnnotationType() {
        return annotation;
    }

    @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if(method.getDeclaringClass()==JAnnotationWriter.class) {
            try {
                return method.invoke(this,args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        String name = method.getName();
        Object arg=null;
        if(args!=null && args.length>0)
            arg = args[0];

        // check how it's defined on the annotation
        Method m = annotation.getDeclaredMethod(name);
        Class<?> rt = m.getReturnType();

        // array value
        if(rt.isArray()) {
            return addArrayValue(proxy,name,rt.getComponentType(),method.getReturnType(),arg);
        }

        // sub annotation
        if(Annotation.class.isAssignableFrom(rt)) {
            Class<? extends Annotation> r = (Class<? extends Annotation>)rt;
            return new TypedAnnotationWriter(
                r,method.getReturnType(),use.annotationParam(name,r)).createProxy();
        }

        // scalar value

        if(arg instanceof JType) {
            JType targ = (JType) arg;
            checkType(Class.class,rt);
            if(m.getDefaultValue()!=null) {
                // check the default
                if(targ.equals(targ.owner().ref((Class)m.getDefaultValue())))
                    return proxy;   // defaulted
            }
            use.param(name,targ);
            return proxy;
        }

        // other Java built-in types
        checkType(arg.getClass(),rt);
        if(m.getDefaultValue()!=null && m.getDefaultValue().equals(arg))
            // defaulted. no need to write out.
            return proxy;

        if(arg instanceof String) {
            use.param(name,(String)arg);
            return proxy;
        }
        if(arg instanceof Boolean) {
            use.param(name,(Boolean)arg);
            return proxy;
        }
        if(arg instanceof Integer) {
            use.param(name,(Integer)arg);
            return proxy;
        }
        if(arg instanceof Class) {
            use.param(name,(Class)arg);
            return proxy;
        }
        if(arg instanceof Enum) {
            use.param(name,(Enum)arg);
            return proxy;
        }

        throw new IllegalArgumentException("Unable to handle this method call "+method.toString());
    }

    @SuppressWarnings("unchecked")
        private Object addArrayValue(Object proxy,String name, Class itemType, Class expectedReturnType, Object arg) {
        if(arrays==null)
            arrays = new HashMap<String,JAnnotationArrayMember>();
        JAnnotationArrayMember m = arrays.get(name);
        if(m==null) {
            m = use.paramArray(name);
            arrays.put(name,m);
        }

        // sub annotation
        if(Annotation.class.isAssignableFrom(itemType)) {
            Class<? extends Annotation> r = (Class<? extends Annotation>)itemType;
            if(!JAnnotationWriter.class.isAssignableFrom(expectedReturnType))
                throw new IllegalArgumentException("Unexpected return type "+expectedReturnType);
            return new TypedAnnotationWriter(r,expectedReturnType,m.annotate(r)).createProxy();
        }

        // primitive
        if(arg instanceof JType) {
            checkType(Class.class,itemType);
            m.param((JType)arg);
            return proxy;
        }
        checkType(arg.getClass(),itemType);
        if(arg instanceof String) {
            m.param((String)arg);
            return proxy;
        }
        if(arg instanceof Boolean) {
            m.param((Boolean)arg);
            return proxy;
        }
        if(arg instanceof Integer) {
            m.param((Integer)arg);
            return proxy;
        }
        if(arg instanceof Class) {
            m.param((Class)arg);
            return proxy;
        }
        // TODO: enum constant. how should we handle it?

        throw new IllegalArgumentException("Unable to handle this method call ");
    }


    /**
     * Check if the type of the argument matches our expectation.
     * If not, report an error.
     */
    private void checkType(Class<?> actual, Class<?> expected) {
        if(expected==actual || expected.isAssignableFrom(actual))
            return; // no problem

        if( expected==JCodeModel.boxToPrimitive.get(actual) )
            return; // no problem

        throw new IllegalArgumentException("Expected "+expected+" but found "+actual);
    }

    /**
     * Creates a proxy and returns it.
     */
    @SuppressWarnings("unchecked")
        private W createProxy() {
        return (W)Proxy.newProxyInstance(
            SecureLoader.getClassClassLoader(writerType),new Class[]{writerType},this);
    }

    /**
     * Creates a new typed annotation writer.
     */
    @SuppressWarnings("unchecked")
        static <W extends JAnnotationWriter<?>> W create(Class<W> w, JAnnotatable annotatable) {
        Class<? extends Annotation> a = findAnnotationType(w);
        return (W)new TypedAnnotationWriter(a,w,annotatable.annotate(a)).createProxy();
    }

    private static Class<? extends Annotation> findAnnotationType(Class<?> clazz) {
        for( Type t : clazz.getGenericInterfaces()) {
            if(t instanceof ParameterizedType) {
                ParameterizedType p = (ParameterizedType) t;
                if(p.getRawType()==JAnnotationWriter.class)
                    return (Class<? extends Annotation>)p.getActualTypeArguments()[0];
            }
            if(t instanceof Class<?>) {
                // recursive search
                Class<? extends Annotation> r = findAnnotationType((Class<?>)t);
                if(r!=null)     return r;
            }
        }
        return null;
    }
}
