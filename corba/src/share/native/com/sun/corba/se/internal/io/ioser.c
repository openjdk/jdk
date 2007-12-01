/*
 * Copyright 1998-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "jni.h"

#include "com_sun_corba_se_internal_io_IIOPInputStream.h"
#include "com_sun_corba_se_internal_io_IIOPOutputStream.h"
#include "com_sun_corba_se_internal_io_ObjectStreamClass.h"
#include "com_sun_corba_se_internal_io_LibraryManager.h"

#define MAJOR_VERSION   1
#define MINOR_VERSION   11  /*sun.4296963  ibm.11861*/

static char *copyright[] = {
    "Licensed Materials - Property of IBM and Sun",
    "RMI-IIOP v1.0",
    "Copyright IBM Corp. 1998 1999  All Rights Reserved",
    "Copyright 1998-1999 Sun Microsystems, Inc. 901 San Antonio Road,",
    "Palo Alto, CA  94303, U.S.A.  All rights reserved."
};

/*
 * Class:     com_sun_corba_se_internal_io_LibraryManager
 * Method:    getMajorVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_sun_corba_se_internal_io_LibraryManager_getMajorVersion
  (JNIEnv *env, jclass this)
{
    return MAJOR_VERSION;
}

/*
 * Class:     com_sun_corba_se_internal_io_LibraryManager
 * Method:    getMinorVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_sun_corba_se_internal_io_LibraryManager_getMinorVersion
  (JNIEnv *env, jclass this)
{
    return MINOR_VERSION;
}

/*
 * Class:     com_sun_corba_se_internal_io_LibraryManager
 * Method:    setEnableOverride
 * Signature: (Ljava/lang/Class;Ljava/lang/Object;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_sun_corba_se_internal_io_LibraryManager_setEnableOverride
  (JNIEnv *env, jclass this, jclass targetClass, jobject instance)
{
    jfieldID fieldID = (*env)->GetFieldID(env, targetClass,
        "enableSubclassImplementation",
        "Z");
    (*env)->SetBooleanField(env, instance, fieldID, JNI_TRUE);

    return (*env)->GetBooleanField(env, instance, fieldID);

}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    throwExceptionType
 * Signature: (Ljava/lang/Class;Ljava/lang/String;)V
 *
 * Construct and throw the given exception using the given message.
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_throwExceptionType
  (JNIEnv *env, jobject obj, jclass c, jstring mssg)
{
    const char* strMsg = (*env)->GetStringUTFChars(env, mssg, 0L);
    (*env)->ThrowNew(env, c, strMsg);
    (*env)->ReleaseStringUTFChars(env, mssg, strMsg);
    return;
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    throwExceptionType
 * Signature: (Ljava/lang/Class;Ljava/lang/String;)V
 *
 * Construct and throw the given exception using the given message.
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_throwExceptionType
  (JNIEnv *env, jobject obj, jclass c, jstring mssg)
{
    const char* strMsg = (*env)->GetStringUTFChars(env, mssg, 0L);
    (*env)->ThrowNew(env, c, strMsg);
    (*env)->ReleaseStringUTFChars(env, mssg, strMsg);
    return;

}

JNIEXPORT jobject JNICALL
Java_com_sun_corba_se_internal_io_IIOPInputStream_allocateNewObject (JNIEnv * env,
                                                  jclass this,
                                                  jclass aclass,
                                                  jclass initclass)
{
    jmethodID cid;

    /**
     * Get the method ID of the default constructor of
     * initclass, which is the first non-Serializable
     * superclass.
     */
    cid = (*env)->GetMethodID(env, initclass, "<init>", "()V");

    if (cid == NULL) {
        /* exception thrown */
        return NULL;
    }

    /**
     * Allocates an object of type aclass and calls the
     * initclass default constructor (found above)
     */
    return (*env)->NewObject(env, aclass, cid);
}


/* DEPRECATED - This is no longer used.
 *
 * Find the first class loader up the stack and use its class to call
 * FindClassFromClass to resolve the specified class
 * name.  The code is similar to that of java.lang.currentClassLoader
 */
JNIEXPORT jclass JNICALL
Java_com_sun_corba_se_internal_io_IIOPInputStream_loadClass (JNIEnv * env,
                                           jobject this,
                                           jclass curClass,
                                           jstring currClassName)
{
    return 0L;
}

#include "com_sun_corba_se_internal_io_ObjectStreamClass.h"

/*
 * Class:     com_sun_corba_se_internal_io_ObjectStreamClass
 * Method:    hasStaticInitializer
 * Signature: (Ljava/lang/Class;)Z
 *
 * If the method <clinit> ()V is defined true is returned.
 * Otherwise, false is returned.
 */
JNIEXPORT jboolean JNICALL
Java_com_sun_corba_se_internal_io_ObjectStreamClass_hasStaticInitializer(JNIEnv *env, jclass this,
                                                    jclass clazz)
{
    jclass superclazz = NULL;
    jmethodID superclinit = NULL;

    jmethodID clinit = (*env)->GetStaticMethodID(env, clazz,
                                                 "<clinit>", "()V");
    if (clinit == NULL || (*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }

    /* Ask the superclass the same question
     * If the answer is the same then the constructor is from a superclass.
     * If different, it's really defined on the subclass.
     */
    superclazz = (*env)->GetSuperclass(env, clazz);
    if ((*env)->ExceptionOccurred(env)) {
        return 0;
    }

    if (superclazz == NULL)
        return 1;

    superclinit = (*env)->GetStaticMethodID(env, superclazz,
                                            "<clinit>", "()V");
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionClear(env);
        superclinit = NULL;
    }

    return (superclinit != clinit);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    readObject
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_readObject
  (JNIEnv *env, jobject this, jobject obj, jclass cls, jobject ois)
{
    jthrowable exc;
    jclass newExcCls;
    jmethodID mid = (*env)->GetMethodID(env, cls, "readObject", "(Ljava/io/ObjectInputStream;)V");
    if (mid == 0)
                return;
    (*env)->CallNonvirtualVoidMethod(env, obj, cls, mid, ois);

    exc = (*env)->ExceptionOccurred(env);
    if (exc) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);

        newExcCls = (*env)->FindClass(env, "java/io/IOException");
        if (newExcCls == 0) /* Unable to find the new exception class, give up. */
          return;
        (*env)->ThrowNew(env, newExcCls, "Serializable readObject method failed internally");
        return;
    }

    return;
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    writeObject
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_writeObject
  (JNIEnv *env, jobject this, jobject obj, jclass cls, jobject oos)
{
    jthrowable exc;
    jclass newExcCls;
    jmethodID mid = (*env)->GetMethodID(env, cls, "writeObject", "(Ljava/io/ObjectOutputStream;)V");
    if (mid == 0)
                return;
    (*env)->CallNonvirtualVoidMethod(env, obj, cls, mid, oos);

    exc = (*env)->ExceptionOccurred(env);
    if (exc) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);

        newExcCls = (*env)->FindClass(env, "java/io/IOException");
        if (newExcCls == 0) /* Unable to find the new exception class, give up. */
          return;
        (*env)->ThrowNew(env, newExcCls, "Serializable readObject method failed internally");
        return;
    }

    return;

}


/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getObjectField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getObjectField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char *strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char *strFieldSig  = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetObjectField(env, obj, fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getBooleanField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getBooleanField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetBooleanField(env, obj, fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getByteField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)B
 */
JNIEXPORT jbyte JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getByteField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetByteField(env, obj, fieldID);

}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getCharField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)C
 */
JNIEXPORT jchar JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getCharField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetCharField(env, obj, fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getShortField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)S
 */
JNIEXPORT jshort JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getShortField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetShortField(env, obj, fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getIntField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getIntField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetIntField(env, obj, fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getLongField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getLongField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetLongField(env, obj, fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getFloatField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)F
 */
JNIEXPORT jfloat JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getFloatField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetFloatField(env, obj, fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getDoubleField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)D
 */
JNIEXPORT jdouble JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getDoubleField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (*env)->GetDoubleField(env, obj, fieldID);
}



/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setObjectField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setObjectField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jobject v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetObjectField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setBooleanField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Z)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setBooleanField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jboolean v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetBooleanField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setByteField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;B)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setByteField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jbyte v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetByteField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setCharField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;C)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setCharField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jchar v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetCharField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setShortField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;S)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setShortField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jshort v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetShortField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setIntField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setIntField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jint v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetIntField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setLongField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;J)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setLongField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jlong v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetLongField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setFloatField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;F)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setFloatField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jfloat v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetFloatField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setDoubleField
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;D)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setDoubleField
  (JNIEnv *env, jobject this, jobject obj, jclass clazz, jstring fieldName, jstring fieldSig, jdouble v)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    (*env)->SetDoubleField(env, obj, fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_util_JDKClassLoader
 * Method:    specialLoadClass
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Class;
 */
JNIEXPORT jclass JNICALL Java_com_sun_corba_se_internal_util_JDKClassLoader_specialLoadClass
  (JNIEnv *env, jclass this, jobject target, jclass cls, jstring clsName)
{
    jthrowable exc;
        jclass streamTargetCls;
    jmethodID mid;
        jclass result;
        streamTargetCls = (*env)->FindClass(env, "java/io/ObjectInputStream");
        mid = (*env)->GetMethodID(env, streamTargetCls, "loadClass0", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Class;");
    if (mid == 0)
                return 0L;
    result = (jclass) (*env)->CallNonvirtualObjectMethod(env, target, streamTargetCls, mid, cls, clsName);

    exc = (*env)->ExceptionOccurred(env);
    if (exc) {
        return 0L;
    }

        return result;
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getObjectFieldOpt
 * Signature: (Ljava/lang/Object;J)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getObjectFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetObjectField(env, obj, (jfieldID)fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getBooleanFieldOpt
 * Signature: (Ljava/lang/Object;J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getBooleanFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetBooleanField(env, obj, (jfieldID)fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getByteFieldOpt
 * Signature: (Ljava/lang/Object;J)B
 */
JNIEXPORT jbyte JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getByteFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetByteField(env, obj, (jfieldID)fieldID);

}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getCharFieldOpt
 * Signature: (Ljava/lang/Object;J)C
 */
JNIEXPORT jchar JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getCharFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetCharField(env, obj, (jfieldID)fieldID);

}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getShortFieldOpt
 * Signature: (Ljava/lang/Object;J)S
 */
JNIEXPORT jshort JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getShortFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetShortField(env, obj, (jfieldID)fieldID);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getIntFieldOpt
 * Signature: (Ljava/lang/Object;J)I
 */
JNIEXPORT jint JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getIntFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetIntField(env, obj, (jfieldID)fieldID);

}


/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getLongFieldOpt
 * Signature: (Ljava/lang/Object;J)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getLongFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetLongField(env, obj, (jfieldID)fieldID);

}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getFloatFieldOpt
 * Signature: (Ljava/lang/Object;J)F
 */
JNIEXPORT jfloat JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getFloatFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetFloatField(env, obj, (jfieldID)fieldID);

}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPOutputStream
 * Method:    getDoubleFieldOpt
 * Signature: (Ljava/lang/Object;J)D
 */
JNIEXPORT jdouble JNICALL Java_com_sun_corba_se_internal_io_IIOPOutputStream_getDoubleFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID)
{
    return (*env)->GetDoubleField(env, obj, (jfieldID)fieldID);

}



/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setObjectFieldOpt
 * Signature: (Ljava/lang/Object;JLjava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setObjectFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jobject v)
{
    (*env)->SetObjectField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setBooleanFieldOpt
 * Signature: (Ljava/lang/Object;JZ)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setBooleanFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jboolean v)
{
    (*env)->SetBooleanField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setByteFieldOpt
 * Signature: (Ljava/lang/Object;JB)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setByteFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jbyte v)
{
    (*env)->SetByteField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setCharFieldOpt
 * Signature: (Ljava/lang/Object;JC)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setCharFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jchar v)
{
    (*env)->SetCharField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setShortFieldOpt
 * Signature: (Ljava/lang/Object;JS)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setShortFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jshort v)
{
    (*env)->SetShortField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setIntFieldOpt
 * Signature: (Ljava/lang/Object;JI)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setIntFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jint v)
{
  (*env)->SetIntField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setLongFieldOpt
 * Signature: (Ljava/lang/Object;JJ)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setLongFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jlong v)
{
    (*env)->SetLongField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setFloatFieldOpt
 * Signature: (Ljava/lang/Object;JF)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setFloatFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jfloat v)
{
    (*env)->SetFloatField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPInputStream
 * Method:    setDoubleFieldOpt
 * Signature: (Ljava/lang/Object;JD)V
 */
JNIEXPORT void JNICALL Java_com_sun_corba_se_internal_io_IIOPInputStream_setDoubleFieldOpt
  (JNIEnv *env, jobject this, jobject obj, jlong fieldID, jdouble v)
{
    (*env)->SetDoubleField(env, obj, (jfieldID)fieldID, v);
}

/*
 * Class:     com_sun_corba_se_internal_io_IIOPObjectStreamField
 * Method:    getFieldID
 * Signature: (Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)X
 */
JNIEXPORT jlong JNICALL Java_com_sun_corba_se_internal_io_ObjectStreamField_getFieldIDNative
  (JNIEnv *env, jobject this, jclass clazz, jstring fieldName, jstring fieldSig)
{
    const char* strFieldName = (*env)->GetStringUTFChars(env, fieldName, 0L);
    const char* strFieldSig = (*env)->GetStringUTFChars(env, fieldSig, 0L);

    jfieldID fieldID = (*env)->GetFieldID(env, clazz, strFieldName, strFieldSig);

    (*env)->ReleaseStringUTFChars(env, fieldName, strFieldName);
    (*env)->ReleaseStringUTFChars(env, fieldSig, strFieldSig);

    return (jlong)fieldID;
}
