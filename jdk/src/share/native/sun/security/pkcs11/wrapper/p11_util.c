/*
 * Portions Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
    assert(jObjectClass != 0);
    jConstructor = (*env)->GetMethodID(env, jObjectClass, "<init>", "()V");
    assert(jConstructor != 0);
    jLockObject = (*env)->NewObject(env, jObjectClass, jConstructor);
    assert(jLockObject != 0);
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
    (*env)->SetLongField(env, pkcs11Implementation, pNativeDataID, (jlong)moduleData);
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
    return (ModuleData*)jData;
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
    jlong jErrorCode;

    if (returnValue == CKR_OK) {
        return 0L ;
    } else {
        jPKCS11ExceptionClass = (*env)->FindClass(env, CLASS_PKCS11EXCEPTION);
        assert(jPKCS11ExceptionClass != 0);
        jConstructor = (*env)->GetMethodID(env, jPKCS11ExceptionClass, "<init>", "(J)V");
        assert(jConstructor != 0);
        jErrorCode = ckULongToJLong(returnValue);
        jPKCS11Exception = (jthrowable) (*env)->NewObject(env, jPKCS11ExceptionClass, jConstructor, jErrorCode);
        (*env)->Throw(env, jPKCS11Exception);
        return jErrorCode ;
    }
}

/*
 * this function simply throws a FileNotFoundException
 *
 * @param env Used to call JNI funktions and to get the Exception class.
 * @param jmessage The message string of the Exception object.
 */
void throwFileNotFoundException(JNIEnv *env, jstring jmessage)
{
    jclass jFileNotFoundExceptionClass;
    jmethodID jConstructor;
    jthrowable jFileNotFoundException;

    jFileNotFoundExceptionClass = (*env)->FindClass(env, CLASS_FILE_NOT_FOUND_EXCEPTION);
    assert(jFileNotFoundExceptionClass != 0);

    jConstructor = (*env)->GetMethodID(env, jFileNotFoundExceptionClass, "<init>", "(Ljava/lang/String;)V");
    assert(jConstructor != 0);
    jFileNotFoundException = (jthrowable) (*env)->NewObject(env, jFileNotFoundExceptionClass, jConstructor, jmessage);
    (*env)->Throw(env, jFileNotFoundException);
}

/*
 * this function simply throws an IOException
 *
 * @param env Used to call JNI funktions and to get the Exception class.
 * @param message The message string of the Exception object.
 */
void throwIOException(JNIEnv *env, const char * message)
{
    jclass jIOExceptionClass;

    jIOExceptionClass = (*env)->FindClass(env, CLASS_IO_EXCEPTION);
    assert(jIOExceptionClass != 0);

    (*env)->ThrowNew(env, jIOExceptionClass, message);
}

/*
 * this function simply throws an IOException and takes a unicode
 * messge.
 *
 * @param env Used to call JNI funktions and to get the Exception class.
 * @param message The unicode message string of the Exception object.
 */
void throwIOExceptionUnicodeMessage(JNIEnv *env, const short *message)
{
    jclass jIOExceptionClass;
    jmethodID jConstructor;
    jthrowable jIOException;
    jstring jmessage;
    jsize length;
    short *currentCharacter;

    jIOExceptionClass = (*env)->FindClass(env, CLASS_IO_EXCEPTION);
    assert(jIOExceptionClass != 0);

    length = 0;
    if (message != NULL) {
        currentCharacter = (short *) message;
        while (*(currentCharacter++) != 0) length++;
    }

    jmessage = (*env)->NewString(env, (const jchar *)message, length);

    jConstructor = (*env)->GetMethodID(env, jIOExceptionClass, "<init>", "(Ljava/lang/String;)V");
    assert(jConstructor != 0);
    jIOException = (jthrowable) (*env)->NewObject(env, jIOExceptionClass, jConstructor, jmessage);
    (*env)->Throw(env, jIOException);
}

/*
 * This function simply throws a PKCS#11RuntimeException with the given
 * string as its message. If the message is NULL, the exception is created
 * using the default constructor.
 *
 * @param env Used to call JNI funktions and to get the Exception class.
 * @param jmessage The message string of the Exception object.
 */
void throwPKCS11RuntimeException(JNIEnv *env, jstring jmessage)
{
    jclass jPKCS11RuntimeExceptionClass;
    jmethodID jConstructor;
    jthrowable jPKCS11RuntimeException;

    jPKCS11RuntimeExceptionClass = (*env)->FindClass(env, CLASS_PKCS11RUNTIMEEXCEPTION);
    assert(jPKCS11RuntimeExceptionClass != 0);

    if (jmessage == NULL) {
        jConstructor = (*env)->GetMethodID(env, jPKCS11RuntimeExceptionClass, "<init>", "()V");
        assert(jConstructor != 0);
        jPKCS11RuntimeException = (jthrowable) (*env)->NewObject(env, jPKCS11RuntimeExceptionClass, jConstructor);
        (*env)->Throw(env, jPKCS11RuntimeException);
    } else {
        jConstructor = (*env)->GetMethodID(env, jPKCS11RuntimeExceptionClass, "<init>", "(Ljava/lang/String;)V");
        assert(jConstructor != 0);
        jPKCS11RuntimeException = (jthrowable) (*env)->NewObject(env, jPKCS11RuntimeExceptionClass, jConstructor, jmessage);
        (*env)->Throw(env, jPKCS11RuntimeException);
    }
}

/*
 * This function simply throws a PKCS#11RuntimeException. The message says that
 * the object is not connected to the module.
 *
 * @param env Used to call JNI funktions and to get the Exception class.
 */
void throwDisconnectedRuntimeException(JNIEnv *env)
{
    jstring jExceptionMessage = (*env)->NewStringUTF(env, "This object is not connected to a module.");

    throwPKCS11RuntimeException(env, jExceptionMessage);
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
    (*env)->GetBooleanArrayRegion(env, jArray, 0, *ckpLength, jpTemp);
    *ckpArray = (CK_BBOOL*) malloc ((*ckpLength) * sizeof(CK_BBOOL));
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
    (*env)->GetByteArrayRegion(env, jArray, 0, *ckpLength, jpTemp);

    /* if CK_BYTE is the same size as jbyte, we save an additional copy */
    if (sizeof(CK_BYTE) == sizeof(jbyte)) {
        *ckpArray = (CK_BYTE_PTR) jpTemp;
    } else {
        *ckpArray = (CK_BYTE_PTR) malloc ((*ckpLength) * sizeof(CK_BYTE));
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
    (*env)->GetLongArrayRegion(env, jArray, 0, *ckpLength, jTemp);
    *ckpArray = (CK_ULONG_PTR) malloc (*ckpLength * sizeof(CK_ULONG));
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
    (*env)->GetCharArrayRegion(env, jArray, 0, *ckpLength, jpTemp);
    *ckpArray = (CK_CHAR_PTR) malloc (*ckpLength * sizeof(CK_CHAR));
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
    (*env)->GetCharArrayRegion(env, jArray, 0, *ckpLength, jTemp);
    *ckpArray = (CK_UTF8CHAR_PTR) malloc (*ckpLength * sizeof(CK_UTF8CHAR));
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
    *ckpLength = strlen(pCharArray);
    *ckpArray = (CK_UTF8CHAR_PTR) malloc((*ckpLength + 1) * sizeof(CK_UTF8CHAR));
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
    TRACE1(", converting %d attibutes", jLength);
    for (i=0; i<(*ckpLength); i++) {
        TRACE1(", getting %d. attibute", i);
        jAttribute = (*env)->GetObjectArrayElement(env, jArray, i);
        TRACE1(", jAttribute = %d", jAttribute);
        TRACE1(", converting %d. attibute", i);
        (*ckpArray)[i] = jAttributeToCKAttribute(env, jAttribute);
    }
    TRACE0("FINISHED\n");
}

/*
 * converts a jobjectArray to a CK_VOID_PTR array. The allocated memory has to be freed after
 * use!
 * NOTE: this function does not work and is not used yet
 *
 * @param env - used to call JNI funktions to get the array informtaion
 * @param jArray - the Java object array to convert
 * @param ckpArray - the reference, where the pointer to the new CK_VOID_PTR array will be stored
 * @param ckpLength - the reference, where the array length will be stored
 */
/*
void jObjectArrayToCKVoidPtrArray(JNIEnv *env, const jobjectArray jArray, CK_VOID_PTR_PTR *ckpArray, CK_ULONG_PTR ckpLength)
{
    jobject jTemp;
    CK_ULONG i;

    if(jArray == NULL) {
        *ckpArray = NULL_PTR;
        *ckpLength = 0L;
        return;
    }
    *ckpLength = (*env)->GetArrayLength(env, jArray);
    *ckpArray = (CK_VOID_PTR_PTR) malloc (*ckpLength * sizeof(CK_VOID_PTR));
    for (i=0; i<(*ckpLength); i++) {
        jTemp = (*env)->GetObjectArrayElement(env, jArray, i);
        (*ckpArray)[i] = jObjectToCKVoidPtr(jTemp);
    }
    free(jTemp);
}
*/

/*
 * converts a CK_BYTE array and its length to a jbyteArray.
 *
 * @param env - used to call JNI funktions to create the new Java array
 * @param ckpArray - the pointer to the CK_BYTE array to convert
 * @param ckpLength - the length of the array to convert
 * @return - the new Java byte array
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
        for (i=0; i<ckLength; i++) {
            jpTemp[i] = ckByteToJByte(ckpArray[i]);
        }
    }

    jArray = (*env)->NewByteArray(env, ckULongToJSize(ckLength));
    (*env)->SetByteArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);

    if (sizeof(CK_BYTE) != sizeof(jbyte)) {
        free(jpTemp);
    }

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
    for (i=0; i<ckLength; i++) {
        jpTemp[i] = ckLongToJLong(ckpArray[i]);
    }
    jArray = (*env)->NewLongArray(env, ckULongToJSize(ckLength));
    (*env)->SetLongArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);
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
    for (i=0; i<ckLength; i++) {
        jpTemp[i] = ckCharToJChar(ckpArray[i]);
    }
    jArray = (*env)->NewCharArray(env, ckULongToJSize(ckLength));
    (*env)->SetCharArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);
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
    for (i=0; i<ckLength; i++) {
        jpTemp[i] = ckUTF8CharToJChar(ckpArray[i]);
    }
    jArray = (*env)->NewCharArray(env, ckULongToJSize(ckLength));
    (*env)->SetCharArrayRegion(env, jArray, 0, ckULongToJSize(ckLength), jpTemp);
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
    assert(jValueObjectClass != 0);
    jConstructor = (*env)->GetMethodID(env, jValueObjectClass, "<init>", "(Z)V");
    assert(jConstructor != 0);
    jValue = ckBBoolToJBoolean(*ckpValue);
    jValueObject = (*env)->NewObject(env, jValueObjectClass, jConstructor, jValue);
    assert(jValueObject != 0);

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
    assert(jValueObjectClass != 0);
    jConstructor = (*env)->GetMethodID(env, jValueObjectClass, "<init>", "(J)V");
    assert(jConstructor != 0);
    jValue = ckULongToJLong(*ckpValue);
    jValueObject = (*env)->NewObject(env, jValueObjectClass, jConstructor, jValue);
    assert(jValueObject != 0);

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
    assert(jObjectClass != 0);
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "booleanValue", "()Z");
    assert(jValueMethod != 0);
    jValue = (*env)->CallBooleanMethod(env, jObject, jValueMethod);
    ckpValue = (CK_BBOOL *) malloc(sizeof(CK_BBOOL));
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
    assert(jObjectClass != 0);
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "byteValue", "()B");
    assert(jValueMethod != 0);
    jValue = (*env)->CallByteMethod(env, jObject, jValueMethod);
    ckpValue = (CK_BYTE_PTR) malloc(sizeof(CK_BYTE));
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
    assert(jObjectClass != 0);
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "intValue", "()I");
    assert(jValueMethod != 0);
    jValue = (*env)->CallIntMethod(env, jObject, jValueMethod);
    ckpValue = (CK_ULONG *) malloc(sizeof(CK_ULONG));
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
    assert(jObjectClass != 0);
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "longValue", "()J");
    assert(jValueMethod != 0);
    jValue = (*env)->CallLongMethod(env, jObject, jValueMethod);
    ckpValue = (CK_ULONG *) malloc(sizeof(CK_ULONG));
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
    assert(jObjectClass != 0);
    jValueMethod = (*env)->GetMethodID(env, jObjectClass, "charValue", "()C");
    assert(jValueMethod != 0);
    jValue = (*env)->CallCharMethod(env, jObject, jValueMethod);
    ckpValue = (CK_CHAR_PTR) malloc(sizeof(CK_CHAR));
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
    jclass jBooleanClass     = (*env)->FindClass(env, "java/lang/Boolean");
    jclass jByteClass        = (*env)->FindClass(env, "java/lang/Byte");
    jclass jCharacterClass   = (*env)->FindClass(env, "java/lang/Character");
    jclass jClassClass = (*env)->FindClass(env, "java/lang/Class");
    /* jclass jShortClass       = (*env)->FindClass(env, "java/lang/Short"); */
    jclass jIntegerClass     = (*env)->FindClass(env, "java/lang/Integer");
    jclass jLongClass        = (*env)->FindClass(env, "java/lang/Long");
    /* jclass jFloatClass       = (*env)->FindClass(env, "java/lang/Float"); */
    /* jclass jDoubleClass      = (*env)->FindClass(env, "java/lang/Double"); */
    jclass jDateClass      = (*env)->FindClass(env, CLASS_DATE);
    jclass jStringClass      = (*env)->FindClass(env, "java/lang/String");
    jclass jStringBufferClass      = (*env)->FindClass(env, "java/lang/StringBuffer");
    jclass jBooleanArrayClass = (*env)->FindClass(env, "[Z");
    jclass jByteArrayClass    = (*env)->FindClass(env, "[B");
    jclass jCharArrayClass    = (*env)->FindClass(env, "[C");
    /* jclass jShortArrayClass   = (*env)->FindClass(env, "[S"); */
    jclass jIntArrayClass     = (*env)->FindClass(env, "[I");
    jclass jLongArrayClass    = (*env)->FindClass(env, "[J");
    /* jclass jFloatArrayClass   = (*env)->FindClass(env, "[F"); */
    /* jclass jDoubleArrayClass  = (*env)->FindClass(env, "[D"); */
    jclass jObjectClass = (*env)->FindClass(env, "java/lang/Object");
    /* jclass jObjectArrayClass = (*env)->FindClass(env, "[java/lang/Object"); */
    /* ATTENTION: jObjectArrayClass is always NULL !! */
    /* CK_ULONG ckArrayLength; */
    /* CK_VOID_PTR *ckpElementObject; */
    /* CK_ULONG ckElementLength; */
    /* CK_ULONG i; */
    CK_VOID_PTR ckpVoid = *ckpObjectPtr;
    jmethodID jMethod;
    jobject jClassObject;
    jstring jClassNameString;
    jstring jExceptionMessagePrefix;
    jobject jExceptionMessageStringBuffer;
    jstring jExceptionMessage;

    TRACE0("\nDEBUG: jObjectToPrimitiveCKObjectPtrPtr");
    if (jObject == NULL) {
        *ckpObjectPtr = NULL;
        *ckpLength = 0;
    } else if ((*env)->IsInstanceOf(env, jObject, jLongClass)) {
        *ckpObjectPtr = jLongObjectToCKULongPtr(env, jObject);
        *ckpLength = sizeof(CK_ULONG);
        TRACE1("<converted long value %X>", *((CK_ULONG *) *ckpObjectPtr));
    } else if ((*env)->IsInstanceOf(env, jObject, jBooleanClass)) {
        *ckpObjectPtr = jBooleanObjectToCKBBoolPtr(env, jObject);
        *ckpLength = sizeof(CK_BBOOL);
        TRACE0(" <converted boolean value ");
        TRACE0((*((CK_BBOOL *) *ckpObjectPtr) == TRUE) ? "TRUE>" : "FALSE>");
    } else if ((*env)->IsInstanceOf(env, jObject, jByteArrayClass)) {
        jByteArrayToCKByteArray(env, jObject, (CK_BYTE_PTR*)ckpObjectPtr, ckpLength);
    } else if ((*env)->IsInstanceOf(env, jObject, jCharArrayClass)) {
        jCharArrayToCKUTF8CharArray(env, jObject, (CK_UTF8CHAR_PTR*)ckpObjectPtr, ckpLength);
    } else if ((*env)->IsInstanceOf(env, jObject, jByteClass)) {
        *ckpObjectPtr = jByteObjectToCKBytePtr(env, jObject);
        *ckpLength = sizeof(CK_BYTE);
        TRACE1("<converted byte value %X>", *((CK_BYTE *) *ckpObjectPtr));
    } else if ((*env)->IsInstanceOf(env, jObject, jDateClass)) {
        *ckpObjectPtr = jDateObjectPtrToCKDatePtr(env, jObject);
        *ckpLength = sizeof(CK_DATE);
        TRACE3("<converted date value %.4s-%.2s-%.2s>", (*((CK_DATE *) *ckpObjectPtr)).year,
                                                    (*((CK_DATE *) *ckpObjectPtr)).month,
                                                    (*((CK_DATE *) *ckpObjectPtr)).day);
    } else if ((*env)->IsInstanceOf(env, jObject, jCharacterClass)) {
        *ckpObjectPtr = jCharObjectToCKCharPtr(env, jObject);
        *ckpLength = sizeof(CK_UTF8CHAR);
        TRACE1("<converted char value %c>", *((CK_CHAR *) *ckpObjectPtr));
    } else if ((*env)->IsInstanceOf(env, jObject, jIntegerClass)) {
        *ckpObjectPtr = jIntegerObjectToCKULongPtr(env, jObject);
        *ckpLength = sizeof(CK_ULONG);
        TRACE1("<converted integer value %X>", *((CK_ULONG *) *ckpObjectPtr));
    } else if ((*env)->IsInstanceOf(env, jObject, jBooleanArrayClass)) {
        jBooleanArrayToCKBBoolArray(env, jObject, (CK_BBOOL**)ckpObjectPtr, ckpLength);
    } else if ((*env)->IsInstanceOf(env, jObject, jIntArrayClass)) {
        jLongArrayToCKULongArray(env, jObject, (CK_ULONG_PTR*)ckpObjectPtr, ckpLength);
    } else if ((*env)->IsInstanceOf(env, jObject, jLongArrayClass)) {
        jLongArrayToCKULongArray(env, jObject, (CK_ULONG_PTR*)ckpObjectPtr, ckpLength);
    } else if ((*env)->IsInstanceOf(env, jObject, jStringClass)) {
        jStringToCKUTF8CharArray(env, jObject, (CK_UTF8CHAR_PTR*)ckpObjectPtr, ckpLength);

        /* a Java object array is not used by CK_ATTRIBUTE by now... */
/*  } else if ((*env)->IsInstanceOf(env, jObject, jObjectArrayClass)) {
        ckArrayLength = (*env)->GetArrayLength(env, (jarray) jObject);
        ckpObjectPtr = (CK_VOID_PTR_PTR) malloc(sizeof(CK_VOID_PTR) * ckArrayLength);
        *ckpLength = 0;
        for (i = 0; i < ckArrayLength; i++) {
            jObjectToPrimitiveCKObjectPtrPtr(env, (*env)->GetObjectArrayElement(env, (jarray) jObject, i),
                     ckpElementObject, &ckElementLength);
            (*ckpObjectPtr)[i] = *ckpElementObject;
            *ckpLength += ckElementLength;
        }
*/
    } else {
        /* type of jObject unknown, throw PKCS11RuntimeException */
        jMethod = (*env)->GetMethodID(env, jObjectClass, "getClass", "()Ljava/lang/Class;");
        assert(jMethod != 0);
        jClassObject = (*env)->CallObjectMethod(env, jObject, jMethod);
        assert(jClassObject != 0);
        jMethod = (*env)->GetMethodID(env, jClassClass, "getName", "()Ljava/lang/String;");
        assert(jMethod != 0);
        jClassNameString = (jstring)
                (*env)->CallObjectMethod(env, jClassObject, jMethod);
        assert(jClassNameString != 0);
        jExceptionMessagePrefix = (*env)->NewStringUTF(env, "Java object of this class cannot be converted to native PKCS#11 type: ");
        jMethod = (*env)->GetMethodID(env, jStringBufferClass, "<init>", "(Ljava/lang/String;)V");
        assert(jMethod != 0);
        jExceptionMessageStringBuffer = (*env)->NewObject(env, jStringBufferClass, jMethod, jExceptionMessagePrefix);
        assert(jClassNameString != 0);
        jMethod = (*env)->GetMethodID(env, jStringBufferClass, "append", "(Ljava/lang/String;)Ljava/lang/StringBuffer;");
        assert(jMethod != 0);
        jExceptionMessage = (jstring)
                 (*env)->CallObjectMethod(env, jExceptionMessageStringBuffer, jMethod, jClassNameString);
        assert(jExceptionMessage != 0);

        throwPKCS11RuntimeException(env, jExceptionMessage);

        *ckpObjectPtr = NULL;
        *ckpLength = 0;
    }

    TRACE0("FINISHED\n");
}
