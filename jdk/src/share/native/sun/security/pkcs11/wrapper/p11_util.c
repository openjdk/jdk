/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
 */

/* Copyright  (c) 2002 Graz University of Technology. All rights reserved.
 *
 * Redistribution and use in  source and binary forms, with or without
 * modification, are permitted  provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in  binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following acknowledgment:
 *
 *    "This product includes software developed by IAIK of Graz University of
 *     Technology."
 *
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Graz University of Technology" and "IAIK of Graz University of
 *    Technology" must not be used to endorse or promote products derived from
 *    this software without prior written permission.
 *
 * 5. Products derived from this software may not be called
 *    "IAIK PKCS Wrapper", nor may "IAIK" appear in their name, without prior
 *    written permission of Graz University of Technology.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE LICENSOR BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 *  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY  OF SUCH DAMAGE.
 */

#include "pkcs11wrapper.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

/* declare file private functions */

ModuleData * getModuleEntry(JNIEnv *env, jobject pkcs11Implementation);
int isModulePresent(JNIEnv *env, jobject pkcs11Implementation);
void removeAllModuleEntries(JNIEnv *env);


/* ************************************************************************** */
/* Functions for keeping track of currently active and loaded modules         */
/* ************************************************************************** */


/*
 * Create a new object for locking.
 */
jobject createLockObject(JNIEnv *env) {
    jclass jObjectClass;
    jobject jLockObject;
    jmethodID jConstructor;

    jObjectClass = (*env)->FindClass(env, "java/lang/Object");
    if (jObjectClass == NULL) { return NULL; }
    jConstructor = (*env)->GetMethodID(env, jObjectClass, "<init>", "()V");
    if (jConstructor == NULL) { return NULL; }
    jLockObject = (*env)->NewObject(env, jObjectClass, jConstructor);
    if (jLockObject == NULL) { return NULL; }
    jLockObject = (*env)->NewGlobalRef(env, jLockObject);

    return jLockObject ;
}

/*
 * Create a new object for locking.
 */
void destroyLockObject(JNIEnv *env, jobject jLockObject) {
    if (jLockObject != NULL) {
        (*env)->DeleteGlobalRef(env, jLockObject);
    }
}

/*
 * Add the given pkcs11Implementation object to the list of present modules.
 * Attach the given data to the entry. If the given pkcs11Implementation is
 * already in the lsit, just override its old module data with the new one.
 * None of the arguments can be NULL. If one of the arguments is NULL, this
 * function does nothing.
 */
void putModuleEntry(JNIEnv *env, jobject pkcs11Implementation, ModuleData *moduleData) {
    if (pkcs11Implementation == NULL_PTR) {
        return ;
    }
    if (moduleData == NULL) {
        return ;
    }
    (*env)->SetLongField(env, pkcs11Implementation, pNativeDataID, ptr_to_jlong(moduleData));
}


/*
 * Get the module data of the entry for the given pkcs11Implementation. Returns
 * NULL, if the pkcs11Implementation is not in the list.
 */
ModuleData * getModuleEntry(JNIEnv *env, jobject pkcs11Implementation) {
    jlong jData;
    if (pkcs11Implementation == NULL) {
        return NULL;
    }
    jData = (*env)->GetLongField(env, pkcs11Implementation, pNativeDataID);
    return (ModuleData*)jlong_to_ptr(jData);
}

CK_FUNCTION_LIST_PTR getFunctionList(JNIEnv *env, jobject pkcs11Implementation) {
    ModuleData *moduleData;
    CK_FUNCTION_LIST_PTR ckpFunctions;

    moduleData = getModuleEntry(env, pkcs11Implementation);
    if (moduleData == NULL) {
        throwDisconnectedRuntimeException(env);
        return NULL;
    }
    ckpFunctions = moduleData->ckFunctionListPtr;
    return ckpFunctions;
}


/*
 * Returns 1, if the given pkcs11Implementation is in the list.
 * 0, otherwise.
 */
int isModulePresent(JNIEnv *env, jobject pkcs11Implementation) {
    int present;

    ModuleData *moduleData = getModuleEntry(env, pkcs11Implementation);

    present = (moduleData != NULL) ? 1 : 0;

    return present ;
}


/*
 * Removes the entry for the given pkcs11Implementation from the list. Returns
 * the module's data, after the node was removed. If this function returns NULL
 * the pkcs11Implementation was not in the list.
 */
ModuleData * removeModuleEntry(JNIEnv *env, jobject pkcs11Implementation) {
    ModuleData *moduleData = getModuleEntry(env, pkcs11Implementation);
    if (moduleData == NULL) {
        return NULL;
    }
    (*env)->SetLongField(env, pkcs11Implementation, pNativeDataID, 0);
    return moduleData;
}

/*
 * Removes all present entries from the list of modules and frees all
 * associated resources. This function is used for clean-up.
 */
void removeAllModuleEntries(JNIEnv *env) {
    /* XXX empty */
}

/* ************************************************************************** */
/* Below there follow the helper functions to support conversions between     */
/* Java and Cryptoki types                                                    */
/* ************************************************************************** */

/*
 * function to convert a PKCS#11 return value into a PKCS#11Exception
 *
 * This function generates a PKCS#11Exception with the returnValue as the errorcode
 * if the returnValue is not CKR_OK. The functin returns 0, if the returnValue is
 * CKR_OK. Otherwise, it returns the returnValue as a jLong.
 *
 * @param env - used to call JNI funktions and to get the Exception class
 * @param returnValue - of the PKCS#11 function
 */
jlong ckAssertReturnValueOK(JNIEnv *env, CK_RV returnValue)
{
    jclass jPKCS11ExceptionClass;
    jmethodID jConstructor;
    jthrowable jPKCS11Exception;
    jlong jErrorCode = 0L;

    if (returnValue != CKR_OK) {
        jErrorCode = ckULongToJLong(returnValue);
        jPKCS11ExceptionClass = (*env)->FindClass(env, CLASS_PKCS11EXCEPTION);
        if (jPKCS11ExceptionClass != NULL) {
            jConstructor = (*env)->GetMethodID(env, jPKCS11ExceptionClass, "<init>", "(J)V");
            if (jConstructor != NULL) {
                jPKCS11Exception = (jthrowable) (*env)->NewObject(env, jPKCS11ExceptionClass, jConstructor, jErrorCode);
                if (jPKCS11Exception != NULL) {
                    (*env)->Throw(env, jPKCS11Exception);
                }
            }
        }
        (*env)->DeleteLocalRef(env, jPKCS11ExceptionClass);
    }
    return jErrorCode ;
}


/*
 * Throws a Java Exception by name
 */
void throwByName(JNIEnv *env, const char *name, const char *msg)
{
    jclass cls = (*env)->FindClass(env, name);

    if (cls != 0) /* Otherwise an exception has already been thrown */
        (*env)->ThrowNew(env, cls, msg);
}

/*
 * Throws java.lang.OutOfMemoryError
 */
void throwOutOfMemoryError(JNIEnv *env, const char *msg)
{
    throwByName(env, "java/lang/OutOfMemoryError", msg);
}

/*
 * Throws java.lang.NullPointerException
 */
void throwNullPointerException(JNIEnv *env, const char *msg)
{
    throwByName(env, "java/lang/NullPointerException", msg);
}

/*
 * Throws java.io.IOException
 */
void throwIOException(JNIEnv *env, const char *msg)
{
    throwByName(env, "java/io/IOException", msg);
}

/*
 * This function simply throws a PKCS#11RuntimeException with the given
 * string as its message.
 *
 * @param env Used to call JNI funktions and to get the Exception class.
 * @param jmessage The message string of the Exception object.
 */
void throwPKCS11RuntimeException(JNIEnv *env, const char *message)
{
    throwByName(env, CLASS_PKCS11RUNTIMEEXCEPTION, message);
}

/*
 * This function simply throws a PKCS#11RuntimeException. The message says that
 * the object is not connected to the module.
 *
 * @param env Used to call JNI funktions and to get the Exception class.
 */
void throwDisconnectedRuntimeException(JNIEnv *env)
{
    throwPKCS11RuntimeException(env, "This object is not connected to a module.");
}

/* This function frees the specified CK_ATTRIBUTE array.
 *
 * @param attrPtr pointer to the to-be-freed CK_ATTRIBUTE array.
 * @param len the length of the array
 */
void freeCKAttributeArray(CK_ATTRIBUTE_PTR attrPtr, int len)
{
    int i;

    for (i=0; i<len; i++) {
        if (attrPtr[i].pValue != NULL_PTR) {
            free(attrPtr[i].pValue);
        }
    }
    free(attrPtr);
}

/*
 * the following functions convert Java arrays to PKCS#11 array pointers and
 * their array length and vice versa
 *
 * void j<Type>ArrayToCK<Type>Array(JNIEnv *env,
 *                                  const j<Type>Array jArray,
 *                                  CK_<Type>_PTR *ckpArray,
 *                                  CK_ULONG_PTR ckLength);
 *
 * j<Type>Array ck<Type>ArrayToJ<Type>Array(JNIEnv *env,
 *                                          const CK_<Type>_PTR ckpArray,
 *                                          CK_ULONG ckLength);
 *
 * PKCS#11 arrays consist always of a pointer to the beginning of the array and
 * the array length whereas Java arrays carry their array length.
 *
 * The Functions to convert a Java array to a PKCS#11 array are void functions.
 * Their arguments are the Java array object to convert, the reference to the
 * array pointer, where the new PKCS#11 array should be stored and the reference
 * to the array length where the PKCS#11 array length should be stored. These two
 * references must not be NULL_PTR.
 *
 * The functions first obtain the array length of the Java array and then allocate
 * the memory for the PKCS#11 array and set the array length. Then each element
 * gets converted depending on their type. After use the allocated memory of the
 * PKCS#11 array has to be explicitly freed.
 *
 * The Functions to convert a PKCS#11 array to a Java array get the PKCS#11 array
 * pointer and the array length and they return the new Java array object. The
 * Java array does not need to get freed after use.
 */

/*
 * converts a jbooleanArray to a CK_BBOOL array. The allocated memory has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java array to convert
 * @param ckpArray - the reference, where the pointer to the new CK_BBOOL array will be stored
 * @param ckpLength - the reference, where the array length will be stored
 */
void jBooleanArrayToCKBBoolArray(JNIEnv *env, const jbooleanArray jArray, CK_BBOOL **ckpArray, CK_ULONG_PTR ckpLength)
{
    jboolean* jpTemp;
    CK_ULONG i;

    if(jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }
    *ckpLength = (*env)->GetArrayLength(env, jArray);
    jpTemp = (jboolean*) malloc((*ckpLength) * sizeof(jboolean));
    if (jpTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return;
    }
    (*env)->GetBooleanArrayRegion(env, jArray, 0, *ckpLength, jpTemp);
    if ((*env)->ExceptionCheck(env)) {
        free(jpTemp);
        return;
    }

    *ckpArray = (CK_BBOOL*) malloc ((*ckpLength) * sizeof(CK_BBOOL));
    if (*ckpArray == NULL) {
        free(jpTemp);
        throwOutOfMemoryError(env, 0);
        return;
    }
    for (i=0; i<(*ckpLength); i++) {
        (*ckpArray)[i] = jBooleanToCKBBool(jpTemp[i]);
    }
    free(jpTemp);
}

/*
 * converts a jbyteArray to a CK_BYTE array. The allocated memory has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java array to convert
 * @param ckpArray - the reference, where the pointer to the new CK_BYTE array will be stored
 * @param ckpLength - the reference, where the array length will be stored
 */
void jByteArrayToCKByteArray(JNIEnv *env, const jbyteArray jArray, CK_BYTE_PTR *ckpArray, CK_ULONG_PTR ckpLength)
{
    jbyte* jpTemp;
    CK_ULONG i;

    if(jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }
    *ckpLength = (*env)->GetArrayLength(env, jArray);
    jpTemp = (jbyte*) malloc((*ckpLength) * sizeof(jbyte));
    if (jpTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return;
    }
    (*env)->GetByteArrayRegion(env, jArray, 0, *ckpLength, jpTemp);
    if ((*env)->ExceptionCheck(env)) {
        free(jpTemp);
        return;
    }

    /* if CK_BYTE is the same size as jbyte, we save an additional copy */
    if (sizeof(CK_BYTE) == sizeof(jbyte)) {
        *ckpArray = (CK_BYTE_PTR) jpTemp;
    } else {
        *ckpArray = (CK_BYTE_PTR) malloc ((*ckpLength) * sizeof(CK_BYTE));
        if (*ckpArray == NULL) {
            free(jpTemp);
            throwOutOfMemoryError(env, 0);
            return;
        }
        for (i=0; i<(*ckpLength); i++) {
            (*ckpArray)[i] = jByteToCKByte(jpTemp[i]);
        }
        free(jpTemp);
    }
}

/*
 * converts a jlongArray to a CK_ULONG array. The allocated memory has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java array to convert
 * @param ckpArray - the reference, where the pointer to the new CK_ULONG array will be stored
 * @param ckpLength - the reference, where the array length will be stored
 */
void jLongArrayToCKULongArray(JNIEnv *env, const jlongArray jArray, CK_ULONG_PTR *ckpArray, CK_ULONG_PTR ckpLength)
{
    jlong* jTemp;
    CK_ULONG i;

    if(jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }
    *ckpLength = (*env)->GetArrayLength(env, jArray);
    jTemp = (jlong*) malloc((*ckpLength) * sizeof(jlong));
    if (jTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return;
    }
    (*env)->GetLongArrayRegion(env, jArray, 0, *ckpLength, jTemp);
    if ((*env)->ExceptionCheck(env)) {
        free(jTemp);
        return;
    }

    *ckpArray = (CK_ULONG_PTR) malloc (*ckpLength * sizeof(CK_ULONG));
    if (*ckpArray == NULL) {
        free(jTemp);
        throwOutOfMemoryError(env, 0);
        return;
    }
    for (i=0; i<(*ckpLength); i++) {
        (*ckpArray)[i] = jLongToCKULong(jTemp[i]);
    }
    free(jTemp);
}

/*
 * converts a jcharArray to a CK_CHAR array. The allocated memory has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java array to convert
 * @param ckpArray - the reference, where the pointer to the new CK_CHAR array will be stored
 * @param ckpLength - the reference, where the array length will be stored
 */
void jCharArrayToCKCharArray(JNIEnv *env, const jcharArray jArray, CK_CHAR_PTR *ckpArray, CK_ULONG_PTR ckpLength)
{
    jchar* jpTemp;
    CK_ULONG i;

    if(jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }
    *ckpLength = (*env)->GetArrayLength(env, jArray);
    jpTemp = (jchar*) malloc((*ckpLength) * sizeof(jchar));
    if (jpTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return;
    }
    (*env)->GetCharArrayRegion(env, jArray, 0, *ckpLength, jpTemp);
    if ((*env)->ExceptionCheck(env)) {
        free(jpTemp);
        return;
    }

    *ckpArray = (CK_CHAR_PTR) malloc (*ckpLength * sizeof(CK_CHAR));
    if (*ckpArray == NULL) {
        free(jpTemp);
        throwOutOfMemoryError(env, 0);
        return;
    }
    for (i=0; i<(*ckpLength); i++) {
        (*ckpArray)[i] = jCharToCKChar(jpTemp[i]);
    }
    free(jpTemp);
}

/*
 * converts a jcharArray to a CK_UTF8CHAR array. The allocated memory has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java array to convert
 * @param ckpArray - the reference, where the pointer to the new CK_UTF8CHAR array will be stored
 * @param ckpLength - the reference, where the array length will be stored
 */
void jCharArrayToCKUTF8CharArray(JNIEnv *env, const jcharArray jArray, CK_UTF8CHAR_PTR *ckpArray, CK_ULONG_PTR ckpLength)
{
    jchar* jTemp;
    CK_ULONG i;

    if(jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }
    *ckpLength = (*env)->GetArrayLength(env, jArray);
    jTemp = (jchar*) malloc((*ckpLength) * sizeof(jchar));
    if (jTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return;
    }
    (*env)->GetCharArrayRegion(env, jArray, 0, *ckpLength, jTemp);
    if ((*env)->ExceptionCheck(env)) {
        free(jTemp);
        return;
    }

    *ckpArray = (CK_UTF8CHAR_PTR) malloc (*ckpLength * sizeof(CK_UTF8CHAR));
    if (*ckpArray == NULL) {
        free(jTemp);
        throwOutOfMemoryError(env, 0);
        return;
    }
    for (i=0; i<(*ckpLength); i++) {
        (*ckpArray)[i] = jCharToCKUTF8Char(jTemp[i]);
    }
    free(jTemp);
}

/*
 * converts a jstring to a CK_CHAR array. The allocated memory has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java array to convert
 * @param ckpArray - the reference, where the pointer to the new CK_CHAR array will be stored
 * @param ckpLength - the reference, where the array length will be stored
 */
void jStringToCKUTF8CharArray(JNIEnv *env, const jstring jArray, CK_UTF8CHAR_PTR *ckpArray, CK_ULONG_PTR ckpLength)
{
    const char* pCharArray;
    jboolean isCopy;

    if(jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }

    pCharArray = (*env)->GetStringUTFChars(env, jArray, &isCopy);
    if (pCharArray == NULL) { return; }

    *ckpLength = strlen(pCharArray);
    *ckpArray = (CK_UTF8CHAR_PTR) malloc((*ckpLength + 1) * sizeof(CK_UTF8CHAR));
    if (*ckpArray == NULL) {
        (*env)->ReleaseStringUTFChars(env, (jstring) jArray, pCharArray);
        throwOutOfMemoryError(env, 0);
        return;
    }
    strcpy((char*)*ckpArray, pCharArray);
    (*env)->ReleaseStringUTFChars(env, (jstring) jArray, pCharArray);
}

/*
 * converts a jobjectArray with Java Attributes to a CK_ATTRIBUTE array. The allocated memory
 * has to be freed after use!
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java Attribute array (template) to convert
 * @param ckpArray - the reference, where the pointer to the new CK_ATTRIBUTE array will be
 *                   stored
 * @param ckpLength - the reference, where the array length will be stored
 */
void jAttributeArrayToCKAttributeArray(JNIEnv *env, jobjectArray jArray, CK_ATTRIBUTE_PTR *ckpArray, CK_ULONG_PTR ckpLength)
{
    CK_ULONG i;
    jlong jLength;
    jobject jAttribute;

    TRACE0("\nDEBUG: jAttributeArrayToCKAttributeArray");
    if (jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }
    jLength = (*env)->GetArrayLength(env, jArray);
    *ckpLength = jLongToCKULong(jLength);
    *ckpArray = (CK_ATTRIBUTE_PTR) malloc(*ckpLength * sizeof(CK_ATTRIBUTE));
    if (*ckpArray == NULL) {
        throwOutOfMemoryError(env, 0);
        return;
    }
    TRACE1(", converting %d attributes", jLength);
    for (i=0; i<(*ckpLength); i++) {
        TRACE1(", getting %d. attribute", i);
        jAttribute = (*env)->GetObjectArrayElement(env, jArray, i);
        if ((*env)->ExceptionCheck(env)) {
            freeCKAttributeArray(*ckpArray, i);
            return;
        }
        TRACE1(", jAttribute = %d", jAttribute);
        TRACE1(", converting %d. attribute", i);
        (*ckpArray)[i] = jAttributeToCKAttribute(env, jAttribute);
        if ((*env)->ExceptionCheck(env)) {
            freeCKAttributeArray(*ckpArray, i);
            return;
        }
    }
    TRACE0("FINISHED\n");
}

/*
 * converts a CK_BYTE array and its length to a jbyteArray.
 *
 * @param env - used to call JNI funktions to create the new Java array
 * @param ckpArray - the pointer to the CK_BYTE array to convert
 * @param ckpLength - the length of the array to convert
 * @return - the new Java byte array or NULL if error occurred
 */
jbyteArray ckByteArrayToJByteArray(JNIEnv *env, const CK_BYTE_PTR ckpArray, CK_ULONG ckLength)
{
    CK_ULONG i;
    jbyte* jpTemp;
    jbyteArray jArray;

    /* if CK_BYTE is the same size as jbyte, we save an additional copy */
    if (sizeof(CK_BYTE) == sizeof(jbyte)) {
        jpTemp = (jbyte*) ckpArray;
    } else {
        jpTemp = (jbyte*) malloc((ckLength) * sizeof(jbyte));
        if (jpTemp == NULL) {
            throwOutOfMemoryError(env, 0);
            return NULL;
        }
        for (i=0; i<ckLength; i++) {
            jpTemp[i] = ckByteToJByte(ckpArray[i]);
        }
    }

    jArray = (*env)->NewByteArray(env, ckULongToJSize(ckLength));
    if (jArray != NULL) {
        (*env)->SetByteArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);
    }

    if (sizeof(CK_BYTE) != sizeof(jbyte)) { free(jpTemp); }

    return jArray ;
}

/*
 * converts a CK_ULONG array and its length to a jlongArray.
 *
 * @param env - used to call JNI funktions to create the new Java array
 * @param ckpArray - the pointer to the CK_ULONG array to convert
 * @param ckpLength - the length of the array to convert
 * @return - the new Java long array
 */
jlongArray ckULongArrayToJLongArray(JNIEnv *env, const CK_ULONG_PTR ckpArray, CK_ULONG ckLength)
{
    CK_ULONG i;
    jlong* jpTemp;
    jlongArray jArray;

    jpTemp = (jlong*) malloc((ckLength) * sizeof(jlong));
    if (jpTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    for (i=0; i<ckLength; i++) {
        jpTemp[i] = ckLongToJLong(ckpArray[i]);
    }
    jArray = (*env)->NewLongArray(env, ckULongToJSize(ckLength));
    if (jArray != NULL) {
        (*env)->SetLongArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);
    }
    free(jpTemp);

    return jArray ;
}

/*
 * converts a CK_CHAR array and its length to a jcharArray.
 *
 * @param env - used to call JNI funktions to create the new Java array
 * @param ckpArray - the pointer to the CK_CHAR array to convert
 * @param ckpLength - the length of the array to convert
 * @return - the new Java char array
 */
jcharArray ckCharArrayToJCharArray(JNIEnv *env, const CK_CHAR_PTR ckpArray, CK_ULONG ckLength)
{
    CK_ULONG i;
    jchar* jpTemp;
    jcharArray jArray;

    jpTemp = (jchar*) malloc(ckLength * sizeof(jchar));
    if (jpTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    for (i=0; i<ckLength; i++) {
        jpTemp[i] = ckCharToJChar(ckpArray[i]);
    }
    jArray = (*env)->NewCharArray(env, ckULongToJSize(ckLength));
    if (jArray != NULL) {
        (*env)->SetCharArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);
    }
    free(jpTemp);

    return jArray ;
}

/*
 * converts a CK_UTF8CHAR array and its length to a jcharArray.
 *
 * @param env - used to call JNI funktions to create the new Java array
 * @param ckpArray - the pointer to the CK_UTF8CHAR array to convert
 * @param ckpLength - the length of the array to convert
 * @return - the new Java char array
 */
jcharArray ckUTF8CharArrayToJCharArray(JNIEnv *env, const CK_UTF8CHAR_PTR ckpArray, CK_ULONG ckLength)
{
    CK_ULONG i;
    jchar* jpTemp;
    jcharArray jArray;

    jpTemp = (jchar*) malloc(ckLength * sizeof(jchar));
    if (jpTemp == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    for (i=0; i<ckLength; i++) {
        jpTemp[i] = ckUTF8CharToJChar(ckpArray[i]);
    }
    jArray = (*env)->NewCharArray(env, ckULongToJSize(ckLength));
    if (jArray != NULL) {
        (*env)->SetCharArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);
    }
    free(jpTemp);

    return jArray ;
}

/*
 * the following functions convert Java objects to PKCS#11 pointers and the
 * length in bytes and vice versa
 *
 * CK_<Type>_PTR j<Object>ToCK<Type>Ptr(JNIEnv *env, jobject jObject);
 *
 * jobject ck<Type>PtrToJ<Object>(JNIEnv *env, const CK_<Type>_PTR ckpValue);
 *
 * The functions that convert a Java object to a PKCS#11 pointer first allocate
 * the memory for the PKCS#11 pointer. Then they set each element corresponding
 * to the fields in the Java object to convert. After use the allocated memory of
 * the PKCS#11 pointer has to be explicitly freed.
 *
 * The functions to convert a PKCS#11 pointer to a Java object create a new Java
 * object first and than they set all fields in the object depending on the values
 * of the type or structure where the PKCS#11 pointer points to.
 */

/*
 * converts a CK_BBOOL pointer to a Java boolean Object.
 *
 * @param env - used to call JNI funktions to create the new Java object
 * @param ckpValue - the pointer to the CK_BBOOL value
 * @return - the new Java boolean object with the boolean value
 */
jobject ckBBoolPtrToJBooleanObject(JNIEnv *env, const CK_BBOOL *ckpValue)
{
    jclass jValueObjectClass;
    jmethodID jConstructor;
    jobject jValueObject;
    jboolean jValue;

    jValueObjectClass = (*env)->FindClass(env, "java/lang/Boolean");
    if (jValueObjectClass == NULL) { return NULL; }
    jConstructor = (*env)->GetMethodID(env, jValueObjectClass, "<init>", "(Z)V");
    if (jConstructor == NULL) { return NULL; }
    jValue = ckBBoolToJBoolean(*ckpValue);
    jValueObject = (*env)->NewObject(env, jValueObjectClass, jConstructor, jValue);

    return jValueObject ;
}

/*
 * converts a CK_ULONG pointer to a Java long Object.
 *
 * @param env - used to call JNI funktions to create the new Java object
 * @param ckpValue - the pointer to the CK_ULONG value
 * @return - the new Java long object with the long value
 */
jobject ckULongPtrToJLongObject(JNIEnv *env, const CK_ULONG_PTR ckpValue)
{
    jclass jValueObjectClass;
    jmethodID jConstructor;
    jobject jValueObject;
    jlong jValue;

    jValueObjectClass = (*env)->FindClass(env, "java/lang/Long");
    if (jValueObjectClass == NULL) { return NULL; }
    jConstructor = (*env)->GetMethodID(env, jValueObjectClass, "<init>", "(J)V");
    if (jConstructor == NULL) { return NULL; }
    jValue = ckULongToJLong(*ckpValue);
    jValueObject = (*env)->NewObject(env, jValueObjectClass, jConstructor, jValue);

    return jValueObject ;
}

/*
 * converts a Java boolean object into a pointer to a CK_BBOOL value. The memory has to be
 * freed after use!
 *
 * @param env - used to call JNI funktions to get the value out of the Java object
 * @param jObject - the "java/lang/Boolean" object to convert
 * @return - the pointer to the new CK_BBOOL value
 */
CK_BBOOL* jBooleanObjectToCKBBoolPtr(JNIEnv *env, jobject jObject)
{
    jclass jObjectClass;
    jmethodID jValueMethod;
    jboolean jValue;
    CK_BBOOL *ckpValue;

    jObjectClass = (*env)->FindClass(env, "java/lang/Boolean");
    if (jObjectClass == NULL) { return NULL; }
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "booleanValue", "()Z");
    if (jValueMethod == NULL) { return NULL; }
    jValue = (*env)->CallBooleanMethod(env, jObject, jValueMethod);
    ckpValue = (CK_BBOOL *) malloc(sizeof(CK_BBOOL));
    if (ckpValue == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    *ckpValue = jBooleanToCKBBool(jValue);

    return ckpValue ;
}

/*
 * converts a Java byte object into a pointer to a CK_BYTE value. The memory has to be
 * freed after use!
 *
 * @param env - used to call JNI funktions to get the value out of the Java object
 * @param jObject - the "java/lang/Byte" object to convert
 * @return - the pointer to the new CK_BYTE value
 */
CK_BYTE_PTR jByteObjectToCKBytePtr(JNIEnv *env, jobject jObject)
{
    jclass jObjectClass;
    jmethodID jValueMethod;
    jbyte jValue;
    CK_BYTE_PTR ckpValue;

    jObjectClass = (*env)->FindClass(env, "java/lang/Byte");
    if (jObjectClass == NULL) { return NULL; }
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "byteValue", "()B");
    if (jValueMethod == NULL) { return NULL; }
    jValue = (*env)->CallByteMethod(env, jObject, jValueMethod);
    ckpValue = (CK_BYTE_PTR) malloc(sizeof(CK_BYTE));
    if (ckpValue == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    *ckpValue = jByteToCKByte(jValue);
    return ckpValue ;
}

/*
 * converts a Java integer object into a pointer to a CK_ULONG value. The memory has to be
 * freed after use!
 *
 * @param env - used to call JNI funktions to get the value out of the Java object
 * @param jObject - the "java/lang/Integer" object to convert
 * @return - the pointer to the new CK_ULONG value
 */
CK_ULONG* jIntegerObjectToCKULongPtr(JNIEnv *env, jobject jObject)
{
    jclass jObjectClass;
    jmethodID jValueMethod;
    jint jValue;
    CK_ULONG *ckpValue;

    jObjectClass = (*env)->FindClass(env, "java/lang/Integer");
    if (jObjectClass == NULL) { return NULL; }
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "intValue", "()I");
    if (jValueMethod == NULL) { return NULL; }
    jValue = (*env)->CallIntMethod(env, jObject, jValueMethod);
    ckpValue = (CK_ULONG *) malloc(sizeof(CK_ULONG));
    if (ckpValue == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    *ckpValue = jLongToCKLong(jValue);
    return ckpValue ;
}

/*
 * converts a Java long object into a pointer to a CK_ULONG value. The memory has to be
 * freed after use!
 *
 * @param env - used to call JNI funktions to get the value out of the Java object
 * @param jObject - the "java/lang/Long" object to convert
 * @return - the pointer to the new CK_ULONG value
 */
CK_ULONG* jLongObjectToCKULongPtr(JNIEnv *env, jobject jObject)
{
    jclass jObjectClass;
    jmethodID jValueMethod;
    jlong jValue;
    CK_ULONG *ckpValue;

    jObjectClass = (*env)->FindClass(env, "java/lang/Long");
    if (jObjectClass == NULL) { return NULL; }
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "longValue", "()J");
    if (jValueMethod == NULL) { return NULL; }
    jValue = (*env)->CallLongMethod(env, jObject, jValueMethod);
    ckpValue = (CK_ULONG *) malloc(sizeof(CK_ULONG));
    if (ckpValue == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    *ckpValue = jLongToCKULong(jValue);

    return ckpValue ;
}

/*
 * converts a Java char object into a pointer to a CK_CHAR value. The memory has to be
 * freed after use!
 *
 * @param env - used to call JNI funktions to get the value out of the Java object
 * @param jObject - the "java/lang/Char" object to convert
 * @return - the pointer to the new CK_CHAR value
 */
CK_CHAR_PTR jCharObjectToCKCharPtr(JNIEnv *env, jobject jObject)
{
    jclass jObjectClass;
    jmethodID jValueMethod;
    jchar jValue;
    CK_CHAR_PTR ckpValue;

    jObjectClass = (*env)->FindClass(env, "java/lang/Char");
    if (jObjectClass == NULL) { return NULL; }
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "charValue", "()C");
    if (jValueMethod == NULL) { return NULL; }
    jValue = (*env)->CallCharMethod(env, jObject, jValueMethod);
    ckpValue = (CK_CHAR_PTR) malloc(sizeof(CK_CHAR));
    if (ckpValue == NULL) {
        throwOutOfMemoryError(env, 0);
        return NULL;
    }
    *ckpValue = jCharToCKChar(jValue);

    return ckpValue ;
}

/*
 * converts a Java object into a pointer to CK-type or a CK-structure with the length in Bytes.
 * The memory of *ckpObjectPtr to be freed after use! This function is only used by
 * jAttributeToCKAttribute by now.
 *
 * @param env - used to call JNI funktions to get the Java classes and objects
 * @param jObject - the Java object to convert
 * @param ckpObjectPtr - the reference of the new pointer to the new CK-value or CK-structure
 * @param ckpLength - the reference of the length in bytes of the new CK-value or CK-structure
 */
void jObjectToPrimitiveCKObjectPtrPtr(JNIEnv *env, jobject jObject, CK_VOID_PTR *ckpObjectPtr, CK_ULONG *ckpLength)
{
    jclass jLongClass, jBooleanClass, jByteArrayClass, jCharArrayClass;
    jclass jByteClass, jDateClass, jCharacterClass, jIntegerClass;
    jclass jBooleanArrayClass, jIntArrayClass, jLongArrayClass;
    jclass jStringClass;
    jclass jObjectClass, jClassClass;
    CK_VOID_PTR ckpVoid = *ckpObjectPtr;
    jmethodID jMethod;
    jobject jClassObject;
    jstring jClassNameString;
    char *classNameString, *exceptionMsgPrefix, *exceptionMsg;

    TRACE0("\nDEBUG: jObjectToPrimitiveCKObjectPtrPtr");
    if (jObject == NULL) {
        *ckpObjectPtr = NULL;
        *ckpLength = 0;
        return;
    }

    jLongClass = (*env)->FindClass(env, "java/lang/Long");
    if (jLongClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jLongClass)) {
        *ckpObjectPtr = jLongObjectToCKULongPtr(env, jObject);
        *ckpLength = sizeof(CK_ULONG);
        TRACE1("<converted long value %X>", *((CK_ULONG *) *ckpObjectPtr));
        return;
    }

    jBooleanClass = (*env)->FindClass(env, "java/lang/Boolean");
    if (jBooleanClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jBooleanClass)) {
        *ckpObjectPtr = jBooleanObjectToCKBBoolPtr(env, jObject);
        *ckpLength = sizeof(CK_BBOOL);
        TRACE0(" <converted boolean value ");
        TRACE0((*((CK_BBOOL *) *ckpObjectPtr) == TRUE) ? "TRUE>" : "FALSE>");
        return;
    }

    jByteArrayClass = (*env)->FindClass(env, "[B");
    if (jByteArrayClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jByteArrayClass)) {
        jByteArrayToCKByteArray(env, jObject, (CK_BYTE_PTR*)ckpObjectPtr, ckpLength);
        return;
    }

    jCharArrayClass = (*env)->FindClass(env, "[C");
    if (jCharArrayClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jCharArrayClass)) {
        jCharArrayToCKUTF8CharArray(env, jObject, (CK_UTF8CHAR_PTR*)ckpObjectPtr, ckpLength);
        return;
    }

    jByteClass = (*env)->FindClass(env, "java/lang/Byte");
    if (jByteClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jByteClass)) {
        *ckpObjectPtr = jByteObjectToCKBytePtr(env, jObject);
        *ckpLength = sizeof(CK_BYTE);
        TRACE1("<converted byte value %X>", *((CK_BYTE *) *ckpObjectPtr));
        return;
    }

    jDateClass = (*env)->FindClass(env, CLASS_DATE);
    if (jDateClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jDateClass)) {
        *ckpObjectPtr = jDateObjectPtrToCKDatePtr(env, jObject);
        *ckpLength = sizeof(CK_DATE);
        TRACE3("<converted date value %.4s-%.2s-%.2s>", (*((CK_DATE *) *ckpObjectPtr)).year, (*((CK_DATE *) *ckpObjectPtr)).month, (*((CK_DATE *) *ckpObjectPtr)).day);
        return;
    }

    jCharacterClass = (*env)->FindClass(env, "java/lang/Character");
    if (jCharacterClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jCharacterClass)) {
        *ckpObjectPtr = jCharObjectToCKCharPtr(env, jObject);
        *ckpLength = sizeof(CK_UTF8CHAR);
        TRACE1("<converted char value %c>", *((CK_CHAR *) *ckpObjectPtr));
        return;
    }

    jIntegerClass = (*env)->FindClass(env, "java/lang/Integer");
    if (jIntegerClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jIntegerClass)) {
        *ckpObjectPtr = jIntegerObjectToCKULongPtr(env, jObject);
        *ckpLength = sizeof(CK_ULONG);
        TRACE1("<converted integer value %X>", *((CK_ULONG *) *ckpObjectPtr));
        return;
    }

    jBooleanArrayClass = (*env)->FindClass(env, "[Z");
    if (jBooleanArrayClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jBooleanArrayClass)) {
        jBooleanArrayToCKBBoolArray(env, jObject, (CK_BBOOL**)ckpObjectPtr, ckpLength);
        return;
    }

    jIntArrayClass = (*env)->FindClass(env, "[I");
    if (jIntArrayClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jIntArrayClass)) {
        jLongArrayToCKULongArray(env, jObject, (CK_ULONG_PTR*)ckpObjectPtr, ckpLength);
        return;
    }

    jLongArrayClass = (*env)->FindClass(env, "[J");
    if (jLongArrayClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jLongArrayClass)) {
        jLongArrayToCKULongArray(env, jObject, (CK_ULONG_PTR*)ckpObjectPtr, ckpLength);
        return;
    }

    jStringClass = (*env)->FindClass(env, "java/lang/String");
    if (jStringClass == NULL) { return; }
    if ((*env)->IsInstanceOf(env, jObject, jStringClass)) {
        jStringToCKUTF8CharArray(env, jObject, (CK_UTF8CHAR_PTR*)ckpObjectPtr, ckpLength);
        return;
    }

    /* type of jObject unknown, throw PKCS11RuntimeException */
    jObjectClass = (*env)->FindClass(env, "java/lang/Object");
    if (jObjectClass == NULL) { return; }
    jMethod = (*env)->GetMethodID(env, jObjectClass, "getClass", "()Ljava/lang/Class;");
    if (jMethod == NULL) { return; }
    jClassObject = (*env)->CallObjectMethod(env, jObject, jMethod);
    assert(jClassObject != 0);
    jClassClass = (*env)->FindClass(env, "java/lang/Class");
    if (jClassClass == NULL) { return; }
    jMethod = (*env)->GetMethodID(env, jClassClass, "getName", "()Ljava/lang/String;");
    if (jMethod == NULL) { return; }
    jClassNameString = (jstring)
        (*env)->CallObjectMethod(env, jClassObject, jMethod);
    assert(jClassNameString != 0);
    classNameString = (char*)
        (*env)->GetStringUTFChars(env, jClassNameString, NULL);
    if (classNameString == NULL) { return; }
    exceptionMsgPrefix = "Java object of this class cannot be converted to native PKCS#11 type: ";
    exceptionMsg = (char *)
        malloc((strlen(exceptionMsgPrefix) + strlen(classNameString) + 1));
    if (exceptionMsg == NULL) {
        (*env)->ReleaseStringUTFChars(env, jClassNameString, classNameString);
        throwOutOfMemoryError(env, 0);
        return;
    }
    strcpy(exceptionMsg, exceptionMsgPrefix);
    strcat(exceptionMsg, classNameString);
    (*env)->ReleaseStringUTFChars(env, jClassNameString, classNameString);
    throwPKCS11RuntimeException(env, exceptionMsg);
    free(exceptionMsg);
    *ckpObjectPtr = NULL;
    *ckpLength = 0;

    TRACE0("FINISHED\n");
}

#ifdef P11_MEMORYDEBUG

#undef malloc
#undef free

void *p11malloc(size_t c, char *file, int line) {
    void *p = malloc(c);
    printf("malloc\t%08x\t%d\t%s:%d\n", p, c, file, line); fflush(stdout);
    return p;
}

void p11free(void *p, char *file, int line) {
    printf("free\t%08x\t\t%s:%d\n", p, file, line); fflush(stdout);
    free(p);
}

#endif

