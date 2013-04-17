/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;


public class ID extends Pointer<Void>{
    static native String getNativeDescription(final long objPtr);

    final JObjCRuntime runtime;

    static final Class[] CTOR_ARGS = { long.class, JObjCRuntime.class };
    protected ID(final long objPtr, final JObjCRuntime runtime) {
        super(objPtr);
        runtime.assertOK();
        this.runtime = runtime;
    }

    protected ID(final ID obj, final JObjCRuntime runtime) {
        this(obj.ptr, runtime);
    }

    @Override protected NativeObjectLifecycleManager getNativeObjectLifecycleManager() {
        return NativeObjectLifecycleManager.CFRetainRelease.INST;
    }

    protected final JObjCRuntime getRuntime() { return runtime; }

    @Override public String toString(){
        String s = super.toString();
        return s + " (ObjC: " + ptr + " / " + Long.toHexString(ptr) + ")";
    }

    //

    public static <T extends ID> T getInstance(final long ptr, final JObjCRuntime runtime){
        return (T) getObjCObjectFor(runtime, ptr);
    }

    static <T extends ID> T getObjCObjectFor(final JObjCRuntime runtime, final long objPtr){
        if (objPtr == 0) return null;

        final WeakReference cachedObj = objectCache.get().get(objPtr);
        if(cachedObj != null && cachedObj.get() != null) return (T) cachedObj.get();

        final long clsPtr = NSClass.getClass(objPtr);

        final T newObj = (T) (runtime.subclassing.isUserClass(clsPtr) ?
                Subclassing.getJObjectFromIVar(objPtr)
                : createNewObjCObjectFor(runtime, objPtr, clsPtr));

        objectCache.get().put(objPtr, new WeakReference(newObj));
        return newObj;
    }

    static <T extends ID> T createNewObjCObjectFor(final JObjCRuntime runtime, final long objPtr, final long clsPtr) {
        final Constructor<T> ctor = getConstructorForClassPtr(runtime, clsPtr);
        return (T) createNewObjCObjectForConstructor(ctor, objPtr, runtime);
    }

    @SuppressWarnings("unchecked")
    static <T extends ID> Constructor<T> getConstructorForClassPtr(final JObjCRuntime runtime, final long clazzPtr){
        final Constructor<T> cachedCtor = (Constructor<T>) constructorCache.get().get(clazzPtr);
        if(cachedCtor != null) return cachedCtor;

        final Class<T> clazz = getClassForClassPtr(runtime, clazzPtr);
        Constructor<T> ctor;
        try {
            ctor = clazz.getDeclaredConstructor(CTOR_ARGS);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        ctor.setAccessible(true);
        constructorCache.get().put(clazzPtr, (Constructor<ID>) ctor);
        return ctor;
    }

    @SuppressWarnings("unchecked")
    static <T extends ID> Class<T> getClassForClassPtr(final JObjCRuntime runtime, final long clazzPtr){
        final String className = NSClass.getClassNameOfClass(clazzPtr);
        final Class<T> clazz = (Class<T>) runtime.getClassForNativeClassName(className);
        if(clazz == null){
            final long superClazzPtr = NSClass.getSuperClassOfClass(clazzPtr);
            if(superClazzPtr != 0)
                return getClassForClassPtr(runtime, superClazzPtr);
        }
        return clazz;
    }

    static <T extends ID> T createNewObjCObjectForConstructor(final Constructor ctor, final long objPtr, final JObjCRuntime runtime) {
        try {
            final T newInstance = (T) ctor.newInstance(new Object[] { Long.valueOf(objPtr), runtime });
            objectCache.get().put(objPtr, new WeakReference(newInstance));
            return newInstance;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    static <T extends ID> T createNewObjCObjectForClass(final Class<T> clazz, final long objPtr, final JObjCRuntime runtime) {
        try {
            final Constructor<T> constructor = clazz.getDeclaredConstructor(CTOR_ARGS);
            constructor.setAccessible(true);
            return (T) createNewObjCObjectForConstructor(constructor, objPtr, runtime);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    //

    static final ThreadLocal<LinkedHashMap<Long, Constructor>> constructorCache = new ThreadLocal<LinkedHashMap<Long, Constructor>>(){
        @Override protected LinkedHashMap<Long, Constructor> initialValue(){
            final int MAX_ENTRIES = 1000;
            final float LOAD_FACTOR = 0.75f;
            return new LinkedHashMap<Long, Constructor>((int) (MAX_ENTRIES/LOAD_FACTOR), LOAD_FACTOR, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Constructor> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };
        }
    };

    static final ThreadLocal<LinkedHashMap<Long, WeakReference>> objectCache = new ThreadLocal<LinkedHashMap<Long, WeakReference>>(){
        @Override protected LinkedHashMap<Long, WeakReference> initialValue(){
            final int MAX_ENTRIES = 1000;
            final float LOAD_FACTOR = 0.75f;
            return new LinkedHashMap<Long, WeakReference>((int) (MAX_ENTRIES/LOAD_FACTOR), LOAD_FACTOR, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, WeakReference> eldest) {
                    return size() > MAX_ENTRIES || eldest.getValue().get() == null;
                }
            };
        }
    };
}
