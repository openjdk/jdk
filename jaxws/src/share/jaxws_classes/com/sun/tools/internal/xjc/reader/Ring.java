/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.model.Model;

/**
 * Holds all the binding related singleton components in a "ring",
 * and let you access those components, creating them as necessary.
 *
 * <p>
 * A {@link Ring} is local to a thread,
 * and only one instanceof {@link Ring} can be active at any given time.
 *
 * Use {@link #begin()} and {@link #end(Ring)} to start/end a ring scope.
 * Inside a scope, use {@link #get()} to obtain the instance.
 *
 * <p>
 * When a {@link Model} is built by the reader, an active {@link Ring} scope
 * is assumed.
 *
 *
 * <h2>Components in Ring</h2>
 * <p>
 * Depending on the schema language we are dealing with, different
 * components are in the model. But at least the following components
 * are in the ring.
 *
 * <ul>
 *  <li>{@link ErrorReceiver}
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 */
public final class Ring {

    private final Map<Class,Object> components = new HashMap<Class,Object>();

    private static final ThreadLocal<Ring> instances = new ThreadLocal<Ring>();

    private Ring() {}

    public static <T> void add( Class<T> clazz, T instance ) {
        assert !get().components.containsKey(clazz);
        get().components.put(clazz,instance);
    }

    public static <T> void add( T o ) {
        add((Class<T>)o.getClass(),o);
    }

    public static <T> T get( Class<T> key ) {
        T t = (T)get().components.get(key);
        if(t==null) {
            try {
                Constructor<T> c = key.getDeclaredConstructor();
                c.setAccessible(true);
                t = c.newInstance();
                if(!get().components.containsKey(key))
                    // many components register themselves.
                    add(key,t);
            } catch (InstantiationException e) {
                throw new Error(e);
            } catch (IllegalAccessException e) {
                throw new Error(e);
            } catch (NoSuchMethodException e) {
                throw new Error(e);
            } catch (InvocationTargetException e) {
                throw new Error(e);
            }
        }

        assert t!=null;
        return t;
    }

    /**
     * A {@link Ring} instance is associated with a thread.
     */
    public static Ring get() {
        return instances.get();
    }

    /**
     * Starts a new scope.
     */
    public static Ring begin() {
        Ring r = null;
        synchronized (instances) {
            r = instances.get();
            instances.set(new Ring());
        }
        return r;
    }

    /**
     * Ends a scope.
     */
    public static void end(Ring old) {
        synchronized (instances) {
            instances.remove();
            instances.set(old);
        }
    }
}
