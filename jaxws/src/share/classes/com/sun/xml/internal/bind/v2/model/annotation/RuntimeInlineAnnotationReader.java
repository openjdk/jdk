/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.bind.v2.model.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link AnnotationReader} that uses {@code java.lang.reflect} to
 * read annotations from class files.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class RuntimeInlineAnnotationReader extends AbstractInlineAnnotationReaderImpl<Type,Class,Field,Method>
    implements RuntimeAnnotationReader {

    public <A extends Annotation> A getFieldAnnotation(Class<A> annotation, Field field, Locatable srcPos) {
        return LocatableAnnotation.create(field.getAnnotation(annotation),srcPos);
    }

    public boolean hasFieldAnnotation(Class<? extends Annotation> annotationType, Field field) {
        return field.isAnnotationPresent(annotationType);
    }

    public boolean hasClassAnnotation(Class clazz, Class<? extends Annotation> annotationType) {
        return clazz.isAnnotationPresent(annotationType);
    }

    public Annotation[] getAllFieldAnnotations(Field field, Locatable srcPos) {
        Annotation[] r = field.getAnnotations();
        for( int i=0; i<r.length; i++ ) {
            r[i] = LocatableAnnotation.create(r[i],srcPos);
        }
        return r;
    }

    public <A extends Annotation> A getMethodAnnotation(Class<A> annotation, Method method, Locatable srcPos) {
        return LocatableAnnotation.create(method.getAnnotation(annotation),srcPos);
    }

    public boolean hasMethodAnnotation(Class<? extends Annotation> annotation, Method method) {
        return method.isAnnotationPresent(annotation);
    }

    public Annotation[] getAllMethodAnnotations(Method method, Locatable srcPos) {
        Annotation[] r = method.getAnnotations();
        for( int i=0; i<r.length; i++ ) {
            r[i] = LocatableAnnotation.create(r[i],srcPos);
        }
        return r;
    }

    public <A extends Annotation> A getMethodParameterAnnotation(Class<A> annotation, Method method, int paramIndex, Locatable srcPos) {
        Annotation[] pa = method.getParameterAnnotations()[paramIndex];
        for( Annotation a : pa ) {
            if(a.annotationType()==annotation)
                return LocatableAnnotation.create((A)a,srcPos);
        }
        return null;
    }

    public <A extends Annotation> A getClassAnnotation(Class<A> a, Class clazz, Locatable srcPos) {
        return LocatableAnnotation.create(((Class<?>)clazz).getAnnotation(a),srcPos);
    }


    /**
     * Cache for package-level annotations.
     */
    private final Map<Class<? extends Annotation>,Map<Package,Annotation>> packageCache =
            new HashMap<Class<? extends Annotation>,Map<Package,Annotation>>();

    public <A extends Annotation> A getPackageAnnotation(Class<A> a, Class clazz, Locatable srcPos) {
        Package p = clazz.getPackage();
        if(p==null) return null;

        Map<Package,Annotation> cache = packageCache.get(a);
        if(cache==null) {
            cache = new HashMap<Package,Annotation>();
            packageCache.put(a,cache);
        }

        if(cache.containsKey(p))
            return (A)cache.get(p);
        else {
            A ann = LocatableAnnotation.create(p.getAnnotation(a),srcPos);
            cache.put(p,ann);
            return ann;
        }
    }

    public Class getClassValue(Annotation a, String name) {
        try {
            return (Class)a.annotationType().getMethod(name).invoke(a);
        } catch (IllegalAccessException e) {
            // impossible
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            // impossible
            throw new InternalError(e.getMessage());
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    public Class[] getClassArrayValue(Annotation a, String name) {
        try {
            return (Class[])a.annotationType().getMethod(name).invoke(a);
        } catch (IllegalAccessException e) {
            // impossible
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            // impossible
            throw new InternalError(e.getMessage());
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    protected String fullName(Method m) {
        return m.getDeclaringClass().getName()+'#'+m.getName();
    }
}
