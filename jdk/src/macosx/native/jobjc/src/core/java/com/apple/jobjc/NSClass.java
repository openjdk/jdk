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


public class NSClass<T extends ID> extends ID {
    public static class NSClassNotFoundException extends RuntimeException{
        public NSClassNotFoundException(String m){ super(m); }
        public NSClassNotFoundException(String m, Throwable cause){ super(m, cause); }
    }

    static native long getNativeClassByName(String name);
    static native long getSuperClassOfClass(long classPtr);
    static native String getClassNameOfClass(long classPtr);
    static native long getClass(long objPtr);

    public NSClass(final long ptr, final JObjCRuntime runtime) {
        super(ptr, runtime);
    }

    public NSClass(final String name, final JObjCRuntime runtime) {
        this(getNativeClassByName(name), runtime);
        if(ptr == 0) throw new NSClassNotFoundException("NSClass pointer is 0. Found no class named " + name);
    }

    protected NSClass(final JObjCRuntime runtime){
        super(0, runtime);
        final String sn = getClass().getSimpleName();
        final String name = sn.substring(0, sn.lastIndexOf("Class"));
        ptr = getNativeClassByName(name);
        if(ptr == 0) throw new NSClassNotFoundException("NSClass pointer is 0. Found no class named " + name);
    }

    NSClass<? super T> getSuperClass() {
        return new NSClass<T>(getSuperClassOfClass(ptr), runtime);
    }

    String getClassName() { return getClassNameOfClass(ptr); }

    @Override protected NativeObjectLifecycleManager getNativeObjectLifecycleManager() {
        return NativeObjectLifecycleManager.Nothing.INST;
    }

    @Override public boolean equals(Object o){
        return (o instanceof NSClass) && (this.ptr == ((NSClass) o).ptr);
    }

    //

    static <T extends NSClass> T getObjCClassFor(final JObjCRuntime runtime, final long clsPtr){
        if (clsPtr == 0) return null;

        final WeakReference cachedObj = objectCache.get().get(clsPtr);
        if(cachedObj != null && cachedObj.get() != null) return (T) cachedObj.get();

        final T newObj = (T) createNewObjCClassFor(runtime, clsPtr);
        objectCache.get().put(clsPtr, new WeakReference(newObj));
        return newObj;
    }

    static <T extends NSClass> T createNewObjCClassFor(final JObjCRuntime runtime, final long clsPtr) {
        final Constructor<T> ctor = getNSClassConstructorForClassPtr(runtime, clsPtr);
        return (T) createNewObjCObjectForConstructor(ctor, clsPtr, runtime);
    }

    @SuppressWarnings("unchecked")
    static <T extends NSClass> Constructor<T> getNSClassConstructorForClassPtr(final JObjCRuntime runtime, final long clazzPtr){
        final Class<T> clazz = getNSClassForClassPtr(runtime, clazzPtr);
        Constructor<T> ctor;
        try {
            ctor = clazz.getDeclaredConstructor(CTOR_ARGS);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        ctor.setAccessible(true);
        return ctor;
    }

    @SuppressWarnings("unchecked")
    static <T extends ID> Class<T> getNSClassForClassPtr(final JObjCRuntime runtime, final long clazzPtr){
        final String className = NSClass.getClassNameOfClass(clazzPtr);
        final Class<T> clazz = (Class<T>) runtime.getClassForNativeClassName(className + "Class");
        if(clazz == null){
            final long superClazzPtr = NSClass.getSuperClassOfClass(clazzPtr);
            if(superClazzPtr != 0)
                return getNSClassForClassPtr(runtime, superClazzPtr);
        }
        return clazz;
    }
}
