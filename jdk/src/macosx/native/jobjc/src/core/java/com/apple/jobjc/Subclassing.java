/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import com.apple.jobjc.Coder.PrimitivePointerCoder;
import com.apple.jobjc.Coder.VoidCoder;
import com.apple.jobjc.Invoke.MsgSend;

final class Subclassing {
    static native long allocateClassPair(long superClass, String name);
    static native boolean addIVarForJObj(long clazz);
    static native boolean patchAlloc(long classPtr);
    static native boolean addMethod(long cls, String name, Method jMethod, CIF cif, long cifPtr, String objCEncodedType);
    static native void registerClassPair(long clazz);

    static native <T extends ID> T getJObjectFromIVar(long objPtr);
    static native void initJObjectToIVar(long objPtr, ID object);

    final Set<Long> registeredUserSubclasses = new HashSet<Long>();
    final JObjCRuntime runtime;

    Subclassing(JObjCRuntime runtime){
        this.runtime = runtime;
    }

    boolean registerUserClass(final Class<? extends ID> clazz, final Class<? extends NSClass> clazzClazz) {
        final String nativeClassName = clazz.getSimpleName();
        // Is it already registered?
        if(0 != NSClass.getNativeClassByName(nativeClassName))
            return false;

        if(clazz.isAnonymousClass())
            throw new RuntimeException("JObjC cannot register anonymous classes.");

        // Verify superclass
        long superClass = NSClass.getNativeClassByName(clazz.getSuperclass().getSimpleName());
        if(0 == superClass)
            throw new RuntimeException(clazz.getSuperclass() + ", the superclass of " + clazz + ", must be a registered class.");

        runtime.registerPackage(clazz.getPackage().getName());

        // Create class
        long classPtr = Subclassing.allocateClassPair(superClass, nativeClassName);
        if(classPtr == 0) throw new RuntimeException("objc_allocateClassPair returned 0.");

        // Add ivar to hold jobject
        boolean addedI = Subclassing.addIVarForJObj(classPtr);
        if(!addedI) throw new RuntimeException("class_addIvar returned false.");

        // Verify constructor
        try {
            clazz.getConstructor(ID.CTOR_ARGS);
        } catch (Exception e) {
            throw new RuntimeException("Could not access required constructor: " + ID.CTOR_ARGS, e);
        }

        // Patch alloc to create corresponding jobject on invoke
        patchAlloc(classPtr);

        // Add methods
        Set<String> takenSelNames = new HashSet<String>();
        for(Method method : clazz.getDeclaredMethods()){
            // No overloading
            String selName = SEL.selectorName(method.getName(), method.getParameterTypes().length > 0);
            if(takenSelNames.contains(selName))
                throw new RuntimeException("Obj-C does not allow method overloading. The Objective-C selector '"
                        + selName + "' appears more than once in class " + clazz.getCanonicalName() + " / " + nativeClassName + ".");

            method.setAccessible(true);

            // Divine CIF
            Coder returnCoder = Coder.getCoderAtRuntimeForType(method.getReturnType());
            Class[] paramTypes = method.getParameterTypes();
            Coder[] argCoders = new Coder[paramTypes.length];
            for(int i = 0; i < paramTypes.length; i++)
                argCoders[i] = Coder.getCoderAtRuntimeForType(paramTypes[i]);

            CIF cif = new MsgSend(runtime, selName, returnCoder, argCoders).funCall.cif;

            // .. and objc encoding
            StringWriter encType = new StringWriter();
            encType.append(returnCoder.getObjCEncoding());
            encType.append("@:");
            for(int i = 0; i < argCoders.length; i++)
                encType.append(argCoders[i].getObjCEncoding());

            // Add it!
            boolean addedM = Subclassing.addMethod(classPtr, selName, method, cif, cif.cif.bufferPtr, encType.toString());
            if(!addedM) throw new RuntimeException("Failed to add method.");
            takenSelNames.add(selName);
        }

        // Seal it
        Subclassing.registerClassPair(classPtr);
        registeredUserSubclasses.add(classPtr);

        return true;
    }

    boolean isUserClass(long clsPtr) {
        return registeredUserSubclasses.contains(clsPtr);
    }

    // Called from JNI

    private static void initJObject(final long objPtr){
//        System.err.println("initJObject " + objPtr + " / " + Long.toHexString(objPtr));
        ID newObj = ID.createNewObjCObjectFor(JObjCRuntime.inst(), objPtr, NSClass.getClass(objPtr));
//        System.err.println("... " + newObj);
        initJObjectToIVar(objPtr, newObj);
    }

    private static void invokeFromJNI(ID obj, Method method, CIF cif, long result, long args){
        assert obj != null;
        assert obj.getClass().equals(method.getDeclaringClass()) :
            obj.getClass().toString() + " != " + method.getDeclaringClass().toString();

        final int argCount = method.getParameterTypes().length;

        // The first two args & coders are for objc id and sel. Skip them.
        final Object[] argObjects = new Object[argCount];
        for(int i = 0; i < argCount; i++){
            final long argAddrAddr = args + ((i+2) * JObjCRuntime.PTR_LEN);
            final long argAddr = PrimitivePointerCoder.INST.popPtr(obj.runtime, argAddrAddr);
            argObjects[i] = cif.argCoders[i + 2].pop(obj.runtime, argAddr);
        }

        Object retVal;
        try {
            retVal = method.invoke(obj, argObjects);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        if(!(cif.returnCoder instanceof VoidCoder))
            cif.returnCoder.push(obj.runtime, result, retVal);
    }
}
