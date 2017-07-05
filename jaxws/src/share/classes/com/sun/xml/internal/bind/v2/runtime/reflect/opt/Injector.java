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

package com.sun.xml.internal.bind.v2.runtime.reflect.opt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;

/**
 * A {@link ClassLoader} used to "inject" optimized accessor classes
 * into the VM.
 *
 * <p>
 * Its parent class loader needs to be set to the one that can see the user
 * class.
 *
 * @author Kohsuke Kawaguchi
 */
final class Injector {

    /**
     * {@link Injector}s keyed by their parent {@link ClassLoader}.
     *
     * We only need one injector per one user class loader.
     */
    private static final Map<ClassLoader,WeakReference<Injector>> injectors =
        Collections.synchronizedMap(new WeakHashMap<ClassLoader,WeakReference<Injector>>());

    private static final Logger logger = Util.getClassLogger();

    /**
     * Injects a new class into the given class loader.
     *
     * @return null
     *      if it fails to inject.
     */
    static Class inject( ClassLoader cl, String className, byte[] image ) {
        Injector injector = get(cl);
        if(injector!=null)
            return injector.inject(className,image);
        else
            return null;
    }

    /**
     * Returns the already injected class, or null.
     */
    static Class find( ClassLoader cl, String className ) {
        Injector injector = get(cl);
        if(injector!=null)
            return injector.find(className);
        else
            return null;
    }

    /**
     * Gets or creates an {@link Injector} for the given class loader.
     *
     * @return null
     *      if it fails.
     */
    private static Injector get(ClassLoader cl) {
        Injector injector = null;
        WeakReference<Injector> wr = injectors.get(cl);
        if(wr!=null)
            injector = wr.get();
        if(injector==null)
            try {
                injectors.put(cl,new WeakReference<Injector>(injector = new Injector(cl)));
            } catch (SecurityException e) {
                logger.log(Level.FINE,"Unable to set up a back-door for the injector",e);
                return null;
            }
        return injector;
    }

    /**
     * Injected classes keyed by their names.
     */
    private final Map<String,Class> classes = new HashMap<String,Class>();

    private final ClassLoader parent;

    /**
     * True if this injector is capable of injecting accessors.
     * False otherwise, which happens if this classloader can't see {@link Accessor}.
     */
    private final boolean loadable;

    private static final Method defineClass;
    private static final Method resolveClass;

    static {
        try {
            defineClass = ClassLoader.class.getDeclaredMethod("defineClass",String.class,byte[].class,Integer.TYPE,Integer.TYPE);
            resolveClass = ClassLoader.class.getDeclaredMethod("resolveClass",Class.class);
        } catch (NoSuchMethodException e) {
            // impossible
            throw new NoSuchMethodError(e.getMessage());
        }
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                // TODO: check security implication
                // do these setAccessible allow anyone to call these methods freely?s
                defineClass.setAccessible(true);
                resolveClass.setAccessible(true);
                return null;
            }
        });
    }

    private Injector(ClassLoader parent) {
        this.parent = parent;
        assert parent!=null;

        boolean loadable = false;

        try {
            loadable = parent.loadClass(Accessor.class.getName())==Accessor.class;
        } catch (ClassNotFoundException e) {
            ; // not loadable
        }

        this.loadable = loadable;
    }


    private synchronized Class inject(String className, byte[] image) {
        if(!loadable)   // this injector cannot inject anything
            return null;

        Class c = classes.get(className);
        if(c==null) {
            // we need to inject a class into the
            try {
                c = (Class)defineClass.invoke(parent,className.replace('/','.'),image,0,image.length);
                resolveClass.invoke(parent,c);
            } catch (IllegalAccessException e) {
                logger.log(Level.FINE,"Unable to inject "+className,e);
                return null;
            } catch (InvocationTargetException e) {
                logger.log(Level.FINE,"Unable to inject "+className,e);
                return null;
            } catch (SecurityException e) {
                logger.log(Level.FINE,"Unable to inject "+className,e);
                return null;
            } catch (LinkageError e) {
                logger.log(Level.FINE,"Unable to inject "+className,e);
                return null;
            }
            classes.put(className,c);
        }
        return c;
    }

    private synchronized Class find(String className) {
        return classes.get(className);
    }
}
