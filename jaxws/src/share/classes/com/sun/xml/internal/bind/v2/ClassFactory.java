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
package com.sun.xml.internal.bind.v2;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.bind.Util;

/**
 * Creates new instances of classes.
 *
 * <p>
 * This code handles the case where the class is not public or the constructor is
 * not public.
 *
 * @since 2.0
 * @author Kohsuke Kawaguchi
 */
public final class ClassFactory {
    private static final Class[] emptyClass = new Class[0];
    private static final Object[] emptyObject = new Object[0];

    private static final Logger logger = Util.getClassLogger();

    /**
     * Cache from a class to its default constructor.
     *
     * To avoid synchronization among threads, we use {@link ThreadLocal}.
     */
    private static final ThreadLocal<Map<Class,Constructor>> tls = new ThreadLocal<Map<Class,Constructor>>() {
        public Map<Class, Constructor> initialValue() {
            return new WeakHashMap<Class,Constructor>();
        }
    };

    /**
     * Creates a new instance of the class but throw exceptions without catching it.
     */
    public static <T> T create0( final Class<T> clazz ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        Map<Class,Constructor> m = tls.get();
        Constructor<T> cons = m.get(clazz);
        if(cons==null) {
            try {
                cons = clazz.getDeclaredConstructor(emptyClass);
            } catch (NoSuchMethodException e) {
                logger.log(Level.INFO,"No default constructor found on "+clazz,e);
                NoSuchMethodError exp;
                if(clazz.getDeclaringClass()!=null && !Modifier.isStatic(clazz.getModifiers())) {
                    exp = new NoSuchMethodError(Messages.NO_DEFAULT_CONSTRUCTOR_IN_INNER_CLASS.format(clazz.getName()));
                } else {
                    exp = new NoSuchMethodError(e.getMessage());
                }
                exp.initCause(e);
                throw exp;
            }

            int classMod = clazz.getModifiers();

            if(!Modifier.isPublic(classMod) || !Modifier.isPublic(cons.getModifiers())) {
                // attempt to make it work even if the constructor is not accessible
                try {
                    cons.setAccessible(true);
                } catch(SecurityException e) {
                    // but if we don't have a permission to do so, work gracefully.
                    logger.log(Level.FINE,"Unable to make the constructor of "+clazz+" accessible",e);
                    throw e;
                }
            }

            m.put(clazz,cons);
        }

        return cons.newInstance(emptyObject);
    }

    /**
     * The same as {@link #create0} but with an error handling to make
     * the instanciation error fatal.
     */
    public static <T> T create( Class<T> clazz ) {
        try {
            return create0(clazz);
        } catch (InstantiationException e) {
            logger.log(Level.INFO,"failed to create a new instance of "+clazz,e);
            throw new InstantiationError(e.toString());
        } catch (IllegalAccessException e) {
            logger.log(Level.INFO,"failed to create a new instance of "+clazz,e);
            throw new IllegalAccessError(e.toString());
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();

            // most likely an error on the user's code.
            // just let it through for the ease of debugging
            if(target instanceof RuntimeException)
                throw (RuntimeException)target;

            // error. just forward it for the ease of debugging
            if(target instanceof Error)
                throw (Error)target;

            // a checked exception.
            // not sure how we should report this error,
            // but for now we just forward it by wrapping it into a runtime exception
            throw new IllegalStateException(target);
        }
    }

    /**
     *  Call a method in the factory class to get the object.
     */
    public static Object create(final Method method) {
        Object cons = null;
        Throwable errorMsg = null;
        try {
            cons = method.invoke(null, emptyObject);
        } catch (InvocationTargetException ive) {
            Throwable target = ive.getTargetException();

            if(target instanceof RuntimeException)
                throw (RuntimeException)target;

            if(target instanceof Error)
                throw (Error)target;

            throw new IllegalStateException(target);
        } catch (IllegalAccessException e) {
            logger.log(Level.INFO,"failed to create a new instance of "+method.getReturnType().getName(),e);
            throw new IllegalAccessError(e.toString());
        } catch (IllegalArgumentException iae){
            logger.log(Level.INFO,"failed to create a new instance of "+method.getReturnType().getName(),iae);
            errorMsg = iae;
        } catch (NullPointerException npe){
            logger.log(Level.INFO,"failed to create a new instance of "+method.getReturnType().getName(),npe);
            errorMsg = npe;
        } catch (ExceptionInInitializerError eie){
            logger.log(Level.INFO,"failed to create a new instance of "+method.getReturnType().getName(),eie);
            errorMsg = eie;
        }
        if (errorMsg != null){
            NoSuchMethodError exp;
            exp = new NoSuchMethodError(errorMsg.getMessage());
            exp.initCause(errorMsg);
            throw exp;
        }
        return cons;
    }

    /**
     * Infers the instanciable implementation class that can be assigned to the given field type.
     *
     * @return null
     *      if inference fails.
     */
    public static <T> Class<? extends T> inferImplClass(Class<T> fieldType, Class[] knownImplClasses) {
        if(!fieldType.isInterface())
            // instanciable class?
            // TODO: check if it has a default constructor
            return fieldType;

        for( Class<?> impl : knownImplClasses ) {
            if(fieldType.isAssignableFrom(impl))
                return impl.asSubclass(fieldType);
        }

        // if we can't find an implementation class,
        // let's just hope that we will never need to create a new object,
        // and returns null
        return null;
    }

    public static final Class[] COLLECTION_IMPL_CLASSES = new Class[] {
        ArrayList.class,
        LinkedList.class,
        HashSet.class,
        TreeSet.class,
        Stack.class,
    };
}
