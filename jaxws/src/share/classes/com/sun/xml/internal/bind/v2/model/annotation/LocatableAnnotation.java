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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * {@link Annotation} that also implements {@link Locatable}.
 *
 * @author Kohsuke Kawaguchi
 */
public class LocatableAnnotation implements InvocationHandler, Locatable, Location {
    private final Annotation core;

    private final Locatable upstream;

    /**
     * Wraps the annotation into a proxy so that the returned object will also implement
     * {@link Locatable}.
     */
    public static <A extends Annotation> A create( A annotation, Locatable parentSourcePos ) {
        if(annotation==null)    return null;
        Class<? extends Annotation> type = annotation.annotationType();
        if(quicks.containsKey(type)) {
            // use the existing proxy implementation if available
            return (A)quicks.get(type).newInstance(parentSourcePos,annotation);
        }

        // otherwise take the slow route

        ClassLoader cl = LocatableAnnotation.class.getClassLoader();

        try {
            Class loadableT = Class.forName(type.getName(), false, cl);
            if(loadableT !=type)
                return annotation;  // annotation type not loadable from this class loader

            return (A)Proxy.newProxyInstance(cl,
                    new Class[]{ type, Locatable.class },
                    new LocatableAnnotation(annotation,parentSourcePos));
        } catch (ClassNotFoundException e) {
            // annotation not loadable
            return annotation;
        } catch (IllegalArgumentException e) {
            // Proxy.newProxyInstance throws this if it cannot resolve this annotation
            // in this classloader
            return annotation;
        }

    }

    LocatableAnnotation(Annotation core, Locatable upstream) {
        this.core = core;
        this.upstream = upstream;
    }

    public Locatable getUpstream() {
        return upstream;
    }

    public Location getLocation() {
        return this;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if(method.getDeclaringClass()==Locatable.class)
                return method.invoke(this,args);
            else
                return method.invoke(core,args);
        } catch (InvocationTargetException e) {
            if(e.getTargetException()!=null)
                throw e.getTargetException();
            throw e;
        }
    }

    public String toString() {
        return core.toString();
    }


    /**
     * List of {@link Quick} implementations keyed by their annotation type.
     */
    private static final Map<Class,Quick> quicks = new HashMap<Class, Quick>();

    static {
        for( Quick q : Init.getAll() ) {
            quicks.put(q.annotationType(),q);
        }
    }
}
