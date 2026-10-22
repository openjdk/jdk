/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "ArrayTypeImpl.h"
#include "util.h"
#include "inStream.h"
#include "outStream.h"
#include "signature.h"


static jdwpError
findComponentClass(JNIEnv *env, jobject arrayClassLoader, char *componentSignature,
                   jclass *classes, jint count, jclass *componentClassPtr)
{
    int i;

    *componentClassPtr = NULL;
    for (i = 0; i < count; i++) {
        char *signature = NULL;
        jclass clazz = classes[i];
        jboolean match;
        jvmtiError error;

        /* signature must match */
        error = classSignature(clazz, &signature, NULL);
        if (error != JVMTI_ERROR_NONE) {
            return map2jdwpError(error);
        }
        match = strcmp(signature, componentSignature) == 0;
        jvmtiDeallocate(signature);

        /* if signature matches, get class loader to check if
         * it matches
         */
        if (match) {
            jobject loader;
            error = classLoader(clazz, &loader);
            if (error != JVMTI_ERROR_NONE) {
                return map2jdwpError(error);
            }
            match = isSameObject(env, loader, arrayClassLoader);
        }

        if (match) {
            *componentClassPtr = clazz;
            return JDWP_ERROR(NONE);
        }
    }
    return JDWP_ERROR(NONE);
}

/*
 * Determine the component class by first looking through the array's loader
 * classes, then all classes if necessary, for one with the component signature
 * and the same defining class loader as the array. See JVM spec 5.3.3:
 *     If the component type is a reference type, C is marked as having
 *     been defined by the defining class loader of the component type.
 */
static jdwpError
getComponentClass(JNIEnv *env, jclass arrayClass, char *componentSignature,
                  jclass *componentClassPtr)
{
    jobject arrayClassLoader;
    jclass *classes;
    jint count;
    jdwpError serror;
    jvmtiError error;

    *componentClassPtr = NULL;
    error = classLoader(arrayClass, &arrayClassLoader);
    if (error != JVMTI_ERROR_NONE) {
        return map2jdwpError(error);
    }

    /*
     * Hidden classes and their arrays have '.' in their signatures and are
     * not included in GetClassLoaderClasses.
     */
    if (strchr(componentSignature, '.') == NULL) {
        error = allClassLoaderClasses(arrayClassLoader, &classes, &count);
        if (error != JVMTI_ERROR_NONE) {
            return map2jdwpError(error);
        }
        serror = findComponentClass(env, arrayClassLoader, componentSignature,
                                   classes, count, componentClassPtr);
        jvmtiDeallocate(classes);
        if (serror != JDWP_ERROR(NONE) || *componentClassPtr != NULL) {
            return serror;
        }
    }

    error = allLoadedClasses(&classes, &count);
    if (error != JVMTI_ERROR_NONE) {
        return map2jdwpError(error);
    }
    serror = findComponentClass(env, arrayClassLoader, componentSignature,
                               classes, count, componentClassPtr);
    jvmtiDeallocate(classes);

    if (serror == JDWP_ERROR(NONE) && *componentClassPtr == NULL) {
        /* per JVM spec, component class is always loaded
         * before array class, so this should never occur.
         */
        serror = JDWP_ERROR(NOT_FOUND);
    }

    return serror;
}

static void
writeNewObjectArray(JNIEnv *env, PacketOutputStream *out,
                 jclass arrayClass, jint size, char *componentSignature)
{

    WITH_LOCAL_REFS(env, 1) {

        jarray array;
        jclass componentClass = NULL;
        jdwpError serror;

        serror = getComponentClass(env, arrayClass,
                                       componentSignature, &componentClass);
        if (serror != JDWP_ERROR(NONE)) {
            outStream_setError(out, serror);
        } else {

            array = JNI_FUNC_PTR(env,NewObjectArray)(env, size, componentClass, 0);
            if (JNI_FUNC_PTR(env,ExceptionCheck)(env)) {
                JNI_FUNC_PTR(env,ExceptionClear)(env);
                array = NULL;
            }

            if (array == NULL) {
                outStream_setError(out, JDWP_ERROR(OUT_OF_MEMORY));
            } else {
                (void)outStream_writeByte(out, specificTypeKey(env, array));
                (void)outStream_writeObjectRef(env, out, array);
            }

        }

    } END_WITH_LOCAL_REFS(env);
}

static void
writeNewPrimitiveArray(JNIEnv *env, PacketOutputStream *out,
                       jclass arrayClass, jint size, char *componentSignature)
{

    WITH_LOCAL_REFS(env, 1) {

        jarray array = NULL;

        switch (jdwpTag(componentSignature)) {
            case JDWP_TAG(BYTE):
                array = JNI_FUNC_PTR(env,NewByteArray)(env, size);
                break;

            case JDWP_TAG(CHAR):
                array = JNI_FUNC_PTR(env,NewCharArray)(env, size);
                break;

            case JDWP_TAG(FLOAT):
                array = JNI_FUNC_PTR(env,NewFloatArray)(env, size);
                break;

            case JDWP_TAG(DOUBLE):
                array = JNI_FUNC_PTR(env,NewDoubleArray)(env, size);
                break;

            case JDWP_TAG(INT):
                array = JNI_FUNC_PTR(env,NewIntArray)(env, size);
                break;

            case JDWP_TAG(LONG):
                array = JNI_FUNC_PTR(env,NewLongArray)(env, size);
                break;

            case JDWP_TAG(SHORT):
                array = JNI_FUNC_PTR(env,NewShortArray)(env, size);
                break;

            case JDWP_TAG(BOOLEAN):
                array = JNI_FUNC_PTR(env,NewBooleanArray)(env, size);
                break;

            default:
                outStream_setError(out, JDWP_ERROR(TYPE_MISMATCH));
                break;
        }

        if (JNI_FUNC_PTR(env,ExceptionCheck)(env)) {
            JNI_FUNC_PTR(env,ExceptionClear)(env);
            array = NULL;
        }

        if (array == NULL) {
            outStream_setError(out, JDWP_ERROR(OUT_OF_MEMORY));
        } else {
            (void)outStream_writeByte(out, specificTypeKey(env, array));
            (void)outStream_writeObjectRef(env, out, array);
        }

    } END_WITH_LOCAL_REFS(env);
}

static jboolean
newInstance(PacketInputStream *in, PacketOutputStream *out)
{
    JNIEnv *env;
    char *signature = NULL;
    char *componentSignature;
    jclass arrayClass;
    jint size;
    jvmtiError error;

    env = getEnv();

    arrayClass = inStream_readClassRef(env, in);
    if (inStream_error(in)) {
        return JNI_TRUE;
    }
    size = inStream_readInt(in);
    if (inStream_error(in)) {
        return JNI_TRUE;
    }

    error = classSignature(arrayClass, &signature, NULL);
    if ( error != JVMTI_ERROR_NONE ) {
        outStream_setError(out, map2jdwpError(error));
        return JNI_TRUE;
    }
    componentSignature = componentTypeSignature(signature);

    jbyte typeKey = jdwpTag(componentSignature);
    if (isReferenceTag(typeKey)) {
        writeNewObjectArray(env, out, arrayClass, size, componentSignature);
    } else {
        writeNewPrimitiveArray(env, out, arrayClass, size, componentSignature);
    }

    jvmtiDeallocate(signature);
    return JNI_TRUE;
}

Command ArrayType_Commands[] = {
    {newInstance, "NewInstance"}
};

DEBUG_DISPATCH_DEFINE_CMDSET(ArrayType)
