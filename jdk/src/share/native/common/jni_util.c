/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <stdlib.h>
#include <string.h>

#include "jvm.h"
#include "jni.h"
#include "jni_util.h"

/* Due to a bug in the win32 C runtime library strings
 * such as "z:" need to be appended with a "." so we
 * must allocate at least 4 bytes to allow room for
 * this expansion. See 4235353 for details.
 */
#define MALLOC_MIN4(len) ((char *)malloc((len) + 1 < 4 ? 4 : (len) + 1))

/**
 * Throw a Java exception by name. Similar to SignalError.
 */
JNIEXPORT void JNICALL
JNU_ThrowByName(JNIEnv *env, const char *name, const char *msg)
{
    jclass cls = (*env)->FindClass(env, name);

    if (cls != 0) /* Otherwise an exception has already been thrown */
        (*env)->ThrowNew(env, cls, msg);
}

/* JNU_Throw common exceptions */

JNIEXPORT void JNICALL
JNU_ThrowNullPointerException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/NullPointerException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowArrayIndexOutOfBoundsException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/ArrayIndexOutOfBoundsException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowOutOfMemoryError(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/OutOfMemoryError", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowIllegalArgumentException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/IllegalArgumentException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowIllegalAccessError(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/IllegalAccessError", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowIllegalAccessException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/IllegalAccessException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowInternalError(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/InternalError", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowNoSuchFieldException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/NoSuchFieldException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowNoSuchMethodException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/NoSuchMethodException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowClassNotFoundException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/ClassNotFoundException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowNumberFormatException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/NumberFormatException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowIOException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/io/IOException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowNoSuchFieldError(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/NoSuchFieldError", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowNoSuchMethodError(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/NoSuchMethodError", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowStringIndexOutOfBoundsException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/StringIndexOutOfBoundsException", msg);
}

JNIEXPORT void JNICALL
JNU_ThrowInstantiationException(JNIEnv *env, const char *msg)
{
    JNU_ThrowByName(env, "java/lang/InstantiationException", msg);
}


/* Throw an exception by name, using the string returned by
 * JVM_LastErrorString for the detail string.  If the last-error
 * string is NULL, use the given default detail string.
 */
JNIEXPORT void JNICALL
JNU_ThrowByNameWithLastError(JNIEnv *env, const char *name,
                             const char *defaultDetail)
{
    char buf[256];
    int n = JVM_GetLastErrorString(buf, sizeof(buf));

    if (n > 0) {
        jstring s = JNU_NewStringPlatform(env, buf);
        if (s != NULL) {
            jobject x = JNU_NewObjectByName(env, name,
                                            "(Ljava/lang/String;)V", s);
            if (x != NULL) {
                (*env)->Throw(env, x);
            }
        }
    }
    if (!(*env)->ExceptionOccurred(env)) {
        JNU_ThrowByName(env, name, defaultDetail);
    }
}

/* Throw an IOException, using the last-error string for the detail
 * string.  If the last-error string is NULL, use the given default
 * detail string.
 */
JNIEXPORT void JNICALL
JNU_ThrowIOExceptionWithLastError(JNIEnv *env, const char *defaultDetail)
{
    JNU_ThrowByNameWithLastError(env, "java/io/IOException", defaultDetail);
}


JNIEXPORT jvalue JNICALL
JNU_CallStaticMethodByName(JNIEnv *env,
                           jboolean *hasException,
                           const char *class_name,
                           const char *name,
                           const char *signature,
                           ...)
{
    jclass clazz;
    jmethodID mid;
    va_list args;
    jvalue result;
    const char *p = signature;

    /* find out the return type */
    while (*p && *p != ')')
        p++;
    p++;

    result.i = 0;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        goto done2;

    clazz = (*env)->FindClass(env, class_name);
    if (clazz == 0)
        goto done2;
    mid = (*env)->GetStaticMethodID(env, clazz, name, signature);
    if (mid == 0)
        goto done1;
    va_start(args, signature);
    switch (*p) {
    case 'V':
        (*env)->CallStaticVoidMethodV(env, clazz, mid, args);
        break;
    case '[':
    case 'L':
        result.l = (*env)->CallStaticObjectMethodV(env, clazz, mid, args);
        break;
    case 'Z':
        result.z = (*env)->CallStaticBooleanMethodV(env, clazz, mid, args);
        break;
    case 'B':
        result.b = (*env)->CallStaticByteMethodV(env, clazz, mid, args);
        break;
    case 'C':
        result.c = (*env)->CallStaticCharMethodV(env, clazz, mid, args);
        break;
    case 'S':
        result.s = (*env)->CallStaticShortMethodV(env, clazz, mid, args);
        break;
    case 'I':
        result.i = (*env)->CallStaticIntMethodV(env, clazz, mid, args);
        break;
    case 'J':
        result.j = (*env)->CallStaticLongMethodV(env, clazz, mid, args);
        break;
    case 'F':
        result.f = (*env)->CallStaticFloatMethodV(env, clazz, mid, args);
        break;
    case 'D':
        result.d = (*env)->CallStaticDoubleMethodV(env, clazz, mid, args);
        break;
    default:
        (*env)->FatalError(env, "JNU_CallStaticMethodByName: illegal signature");
    }
    va_end(args);

 done1:
    (*env)->DeleteLocalRef(env, clazz);
 done2:
    if (hasException) {
        *hasException = (*env)->ExceptionCheck(env);
    }
    return result;
}

JNIEXPORT jvalue JNICALL
JNU_CallMethodByName(JNIEnv *env,
                     jboolean *hasException,
                     jobject obj,
                     const char *name,
                     const char *signature,
                     ...)
{
    jvalue result;
    va_list args;

    va_start(args, signature);
    result = JNU_CallMethodByNameV(env, hasException, obj, name, signature,
                                   args);
    va_end(args);

    return result;
}


JNIEXPORT jvalue JNICALL
JNU_CallMethodByNameV(JNIEnv *env,
                      jboolean *hasException,
                      jobject obj,
                      const char *name,
                      const char *signature,
                      va_list args)
{
    jclass clazz;
    jmethodID mid;
    jvalue result;
    const char *p = signature;

    /* find out the return type */
    while (*p && *p != ')')
        p++;
    p++;

    result.i = 0;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        goto done2;

    clazz = (*env)->GetObjectClass(env, obj);
    mid = (*env)->GetMethodID(env, clazz, name, signature);
    if (mid == 0)
        goto done1;

    switch (*p) {
    case 'V':
        (*env)->CallVoidMethodV(env, obj, mid, args);
        break;
    case '[':
    case 'L':
        result.l = (*env)->CallObjectMethodV(env, obj, mid, args);
        break;
    case 'Z':
        result.z = (*env)->CallBooleanMethodV(env, obj, mid, args);
        break;
    case 'B':
        result.b = (*env)->CallByteMethodV(env, obj, mid, args);
        break;
    case 'C':
        result.c = (*env)->CallCharMethodV(env, obj, mid, args);
        break;
    case 'S':
        result.s = (*env)->CallShortMethodV(env, obj, mid, args);
        break;
    case 'I':
        result.i = (*env)->CallIntMethodV(env, obj, mid, args);
        break;
    case 'J':
        result.j = (*env)->CallLongMethodV(env, obj, mid, args);
        break;
    case 'F':
        result.f = (*env)->CallFloatMethodV(env, obj, mid, args);
        break;
    case 'D':
        result.d = (*env)->CallDoubleMethodV(env, obj, mid, args);
        break;
    default:
        (*env)->FatalError(env, "JNU_CallMethodByNameV: illegal signature");
    }
 done1:
    (*env)->DeleteLocalRef(env, clazz);
 done2:
    if (hasException) {
        *hasException = (*env)->ExceptionCheck(env);
    }
    return result;
}

JNIEXPORT jobject JNICALL
JNU_NewObjectByName(JNIEnv *env, const char *class_name,
                    const char *constructor_sig, ...)
{
    jobject obj = NULL;

    jclass cls = 0;
    jmethodID cls_initMID;
    va_list args;

    if ((*env)->EnsureLocalCapacity(env, 2) < 0)
        goto done;

    cls = (*env)->FindClass(env, class_name);
    if (cls == 0) {
        goto done;
    }
    cls_initMID  = (*env)->GetMethodID(env, cls,
                                       "<init>", constructor_sig);
    if (cls_initMID == NULL) {
        goto done;
    }
    va_start(args, constructor_sig);
    obj = (*env)->NewObjectV(env, cls, cls_initMID, args);
    va_end(args);

 done:
    (*env)->DeleteLocalRef(env, cls);
    return obj;
}

/* Optimized for char set ISO_8559_1 */
static jstring
newString8859_1(JNIEnv *env, const char *str)
{
    int len = (int)strlen(str);
    jchar buf[512];
    jchar *str1;
    jstring result;
    int i;

    if (len > 512) {
        str1 = (jchar *)malloc(len * sizeof(jchar));
        if (str1 == 0) {
            JNU_ThrowOutOfMemoryError(env, 0);
            return 0;
        }
    } else
        str1 = buf;

    for (i=0;i<len;i++)
        str1[i] = (unsigned char)str[i];
    result = (*env)->NewString(env, str1, len);
    if (str1 != buf)
        free(str1);
    return result;
}

static const char*
getString8859_1Chars(JNIEnv *env, jstring jstr)
{
    int i;
    char *result;
    jint len = (*env)->GetStringLength(env, jstr);
    const jchar *str = (*env)->GetStringCritical(env, jstr, 0);
    if (str == 0) {
        return 0;
    }

    result = MALLOC_MIN4(len);
    if (result == 0) {
        (*env)->ReleaseStringCritical(env, jstr, str);
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    }

    for (i=0; i<len; i++) {
        jchar unicode = str[i];
        if (unicode <= 0x00ff)
            result[i] = unicode;
        else
            result[i] = '?';
    }

    result[len] = 0;
    (*env)->ReleaseStringCritical(env, jstr, str);
    return result;
}


/* Optimized for char set ISO646-US (us-ascii) */
static jstring
newString646_US(JNIEnv *env, const char *str)
{
    int len = strlen(str);
    jchar buf[512];
    jchar *str1;
    jstring result;
    int i;

    if (len > 512) {
        str1 = (jchar *)malloc(len * sizeof(jchar));
        if (str1 == 0) {
            JNU_ThrowOutOfMemoryError(env, 0);
            return 0;
        }
    } else
        str1 = buf;

    for (i=0; i<len; i++) {
        unsigned char c = (unsigned char)str[i];
        if (c <= 0x7f)
            str1[i] = c;
        else
            str1[i] = '?';
    }

    result = (*env)->NewString(env, str1, len);
    if (str1 != buf)
        free(str1);
    return result;
}

static const char*
getString646_USChars(JNIEnv *env, jstring jstr)
{
    int i;
    char *result;
    jint len = (*env)->GetStringLength(env, jstr);
    const jchar *str = (*env)->GetStringCritical(env, jstr, 0);
    if (str == 0) {
        return 0;
    }

    result = MALLOC_MIN4(len);
    if (result == 0) {
        (*env)->ReleaseStringCritical(env, jstr, str);
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    }

    for (i=0; i<len; i++) {
        jchar unicode = str[i];
        if (unicode <= 0x007f )
            result[i] = unicode;
        else
            result[i] = '?';
    }

    result[len] = 0;
    (*env)->ReleaseStringCritical(env, jstr, str);
    return result;
}

/* enumeration of c1 row from Cp1252 */
static int cp1252c1chars[32] = {
    0x20AC,0xFFFD,0x201A,0x0192,0x201E,0x2026,0x2020,0x2021,
    0x02C6,0x2030,0x0160,0x2039,0x0152,0xFFFD,0x017D,0xFFFD,
    0xFFFD,0x2018,0x2019,0x201C,0x201D,0x2022,0x2013,0x2014,
    0x02Dc,0x2122,0x0161,0x203A,0x0153,0xFFFD,0x017E,0x0178
};

/* Optimized for char set Cp1252 */
static jstring
newStringCp1252(JNIEnv *env, const char *str)
{
    int len = (int) strlen(str);
    jchar buf[512];
    jchar *str1;
    jstring result;
    int i;
    if (len > 512) {
        str1 = (jchar *)malloc(len * sizeof(jchar));
        if (str1 == 0) {
            JNU_ThrowOutOfMemoryError(env, 0);
            return 0;
        }
    } else
        str1 = buf;

    for (i=0; i<len; i++) {
        unsigned char c = (unsigned char)str[i];
        if ((c >= 0x80) && (c <= 0x9f))
            str1[i] = cp1252c1chars[c-128];
        else
            str1[i] = c;
    }

    result = (*env)->NewString(env, str1, len);
    if (str1 != buf)
        free(str1);
    return result;
}

static const char*
getStringCp1252Chars(JNIEnv *env, jstring jstr)
{
    int i;
    char *result;
    jint len = (*env)->GetStringLength(env, jstr);
    const jchar *str = (*env)->GetStringCritical(env, jstr, 0);
    if (str == 0) {
        return 0;
    }

    result = MALLOC_MIN4(len);
    if (result == 0) {
        (*env)->ReleaseStringCritical(env, jstr, str);
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    }

    for (i=0; i<len; i++) {
        jchar c = str[i];
        if (c < 256)
            result[i] = c;
        else switch(c) {
            case 0x20AC: result[i] = (char)0x80; break;
            case 0x201A: result[i] = (char)0x82; break;
            case 0x0192: result[i] = (char)0x83; break;
            case 0x201E: result[i] = (char)0x84; break;
            case 0x2026: result[i] = (char)0x85; break;
            case 0x2020: result[i] = (char)0x86; break;
            case 0x2021: result[i] = (char)0x87; break;
            case 0x02C6: result[i] = (char)0x88; break;
            case 0x2030: result[i] = (char)0x89; break;
            case 0x0160: result[i] = (char)0x8A; break;
            case 0x2039: result[i] = (char)0x8B; break;
            case 0x0152: result[i] = (char)0x8C; break;
            case 0x017D: result[i] = (char)0x8E; break;
            case 0x2018: result[i] = (char)0x91; break;
            case 0x2019: result[i] = (char)0x92; break;
            case 0x201C: result[i] = (char)0x93; break;
            case 0x201D: result[i] = (char)0x94; break;
            case 0x2022: result[i] = (char)0x95; break;
            case 0x2013: result[i] = (char)0x96; break;
            case 0x2014: result[i] = (char)0x97; break;
            case 0x02DC: result[i] = (char)0x98; break;
            case 0x2122: result[i] = (char)0x99; break;
            case 0x0161: result[i] = (char)0x9A; break;
            case 0x203A: result[i] = (char)0x9B; break;
            case 0x0153: result[i] = (char)0x9C; break;
            case 0x017E: result[i] = (char)0x9E; break;
            case 0x0178: result[i] = (char)0x9F; break;
            default:     result[i] = '?';  break;
        }
    }

    result[len] = 0;
    (*env)->ReleaseStringCritical(env, jstr, str);
    return result;
}

static int fastEncoding = NO_ENCODING_YET;
static jstring jnuEncoding = NULL;

/* Cached method IDs */
static jmethodID String_init_ID;        /* String(byte[], enc) */
static jmethodID String_getBytes_ID;    /* String.getBytes(enc) */

int getFastEncoding() {
    return fastEncoding;
}

/* Initialize the fast encoding.  If the "sun.jnu.encoding" property
 * has not yet been set, we leave fastEncoding == NO_ENCODING_YET.
 */
void
initializeEncoding(JNIEnv *env)
{
    jstring propname = 0;
    jstring enc = 0;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        return;

    propname = (*env)->NewStringUTF(env, "sun.jnu.encoding");
    if (propname) {
        jboolean exc;
        enc = JNU_CallStaticMethodByName
                       (env,
                        &exc,
                        "java/lang/System",
                        "getProperty",
                        "(Ljava/lang/String;)Ljava/lang/String;",
                        propname).l;
        if (!exc) {
            if (enc) {
                const char* encname = (*env)->GetStringUTFChars(env, enc, 0);
                if (encname) {
           /*
            * On Solaris with nl_langinfo() called in GetJavaProperties():
            *
            *   locale undefined -> NULL -> hardcoded default
            *   "C" locale       -> "" -> hardcoded default     (on 2.6)
            *   "C" locale       -> "ISO646-US"                 (on Sol 7/8)
            *   "en_US" locale -> "ISO8859-1"
            *   "en_GB" locale -> "ISO8859-1"                   (on Sol 7/8)
            *   "en_UK" locale -> "ISO8859-1"                   (on 2.6)
            */
                    if ((strcmp(encname, "8859_1") == 0) ||
                        (strcmp(encname, "ISO8859-1") == 0) ||
                        (strcmp(encname, "ISO8859_1") == 0))
                        fastEncoding = FAST_8859_1;
                    else if (strcmp(encname, "ISO646-US") == 0)
                        fastEncoding = FAST_646_US;
                    else if (strcmp(encname, "Cp1252") == 0 ||
                             /* This is a temporary fix until we move */
                             /* to wide character versions of all Windows */
                             /* calls. */
                             strcmp(encname, "utf-16le") == 0)
                        fastEncoding = FAST_CP1252;
                    else {
                        fastEncoding = NO_FAST_ENCODING;
                        jnuEncoding = (jstring)(*env)->NewGlobalRef(env, enc);
                    }
                    (*env)->ReleaseStringUTFChars(env, enc, encname);
                }
            }
        } else {
            (*env)->ExceptionClear(env);
        }
    } else {
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, propname);
    (*env)->DeleteLocalRef(env, enc);

    /* Initialize method-id cache */
    String_getBytes_ID = (*env)->GetMethodID(env, JNU_ClassString(env),
                                             "getBytes", "(Ljava/lang/String;)[B");
    String_init_ID = (*env)->GetMethodID(env, JNU_ClassString(env),
                                         "<init>", "([BLjava/lang/String;)V");
}

static jboolean isJNUEncodingSupported = JNI_FALSE;
static jboolean jnuEncodingSupported(JNIEnv *env) {
    jboolean exe;
    if (isJNUEncodingSupported == JNI_TRUE) {
        return JNI_TRUE;
    }
    isJNUEncodingSupported = (jboolean) JNU_CallStaticMethodByName (
                                    env, &exe,
                                    "java/nio/charset/Charset",
                                    "isSupported",
                                    "(Ljava/lang/String;)Z",
                                    jnuEncoding).z;
    return isJNUEncodingSupported;
}


JNIEXPORT jstring
NewStringPlatform(JNIEnv *env, const char *str)
{
    return JNU_NewStringPlatform(env, str);
}

JNIEXPORT jstring JNICALL
JNU_NewStringPlatform(JNIEnv *env, const char *str)
{
    jstring result;
    result = nativeNewStringPlatform(env, str);
    if (result == NULL) {
        jbyteArray hab = 0;
        int len;

        if (fastEncoding == NO_ENCODING_YET)
            initializeEncoding(env);

        if ((fastEncoding == FAST_8859_1) || (fastEncoding == NO_ENCODING_YET))
            return newString8859_1(env, str);
        if (fastEncoding == FAST_646_US)
            return newString646_US(env, str);
        if (fastEncoding == FAST_CP1252)
            return newStringCp1252(env, str);

        if ((*env)->EnsureLocalCapacity(env, 2) < 0)
            return NULL;

        len = (int)strlen(str);
        hab = (*env)->NewByteArray(env, len);
        if (hab != 0) {
            (*env)->SetByteArrayRegion(env, hab, 0, len, (jbyte *)str);
            if (jnuEncodingSupported(env)) {
                result = (*env)->NewObject(env, JNU_ClassString(env),
                                           String_init_ID, hab, jnuEncoding);
            } else {
                /*If the encoding specified in sun.jnu.encoding is not endorsed
                  by "Charset.isSupported" we have to fall back to use String(byte[])
                  explicitly here without specifying the encoding name, in which the
                  StringCoding class will pickup the iso-8859-1 as the fallback
                  converter for us.
                 */
                jmethodID mid = (*env)->GetMethodID(env, JNU_ClassString(env),
                                                    "<init>", "([B)V");
                result = (*env)->NewObject(env, JNU_ClassString(env), mid, hab);
            }
            (*env)->DeleteLocalRef(env, hab);
            return result;
        }
    }
    return NULL;
}

JNIEXPORT const char *
GetStringPlatformChars(JNIEnv *env, jstring jstr, jboolean *isCopy)
{
    return JNU_GetStringPlatformChars(env, jstr, isCopy);
}

JNIEXPORT const char * JNICALL
JNU_GetStringPlatformChars(JNIEnv *env, jstring jstr, jboolean *isCopy)
{
    char *result = nativeGetStringPlatformChars(env, jstr, isCopy);
    if (result == NULL) {

        jbyteArray hab = 0;

        if (isCopy)
            *isCopy = JNI_TRUE;

        if (fastEncoding == NO_ENCODING_YET)
            initializeEncoding(env);

        if ((fastEncoding == FAST_8859_1) || (fastEncoding == NO_ENCODING_YET))
            return getString8859_1Chars(env, jstr);
        if (fastEncoding == FAST_646_US)
            return getString646_USChars(env, jstr);
        if (fastEncoding == FAST_CP1252)
            return getStringCp1252Chars(env, jstr);

        if ((*env)->EnsureLocalCapacity(env, 2) < 0)
            return 0;

        if (jnuEncodingSupported(env)) {
            hab = (*env)->CallObjectMethod(env, jstr, String_getBytes_ID, jnuEncoding);
        } else {
            jmethodID mid = (*env)->GetMethodID(env, JNU_ClassString(env),
                                                "getBytes", "()[B");
            hab = (*env)->CallObjectMethod(env, jstr, mid);
        }

        if (!(*env)->ExceptionCheck(env)) {
            jint len = (*env)->GetArrayLength(env, hab);
            result = MALLOC_MIN4(len);
            if (result == 0) {
                JNU_ThrowOutOfMemoryError(env, 0);
                (*env)->DeleteLocalRef(env, hab);
                return 0;
            }
            (*env)->GetByteArrayRegion(env, hab, 0, len, (jbyte *)result);
            result[len] = 0; /* NULL-terminate */
        }

        (*env)->DeleteLocalRef(env, hab);
    }
    return result;
}

JNIEXPORT void JNICALL
JNU_ReleaseStringPlatformChars(JNIEnv *env, jstring jstr, const char *str)
{
    free((void *)str);
}

/*
 * Export the platform dependent path canonicalization so that
 * VM can find it when loading system classes.
 *
 */
extern int canonicalize(char *path, const char *out, int len);

JNIEXPORT int
Canonicalize(JNIEnv *env, char *orig, char *out, int len)
{
    /* canonicalize an already natived path */
    return canonicalize(orig, out, len);
}

JNIEXPORT jclass JNICALL
JNU_ClassString(JNIEnv *env)
{
    static jclass cls = 0;
    if (cls == 0) {
        jclass c;
        if ((*env)->EnsureLocalCapacity(env, 1) < 0)
            return 0;
        c = (*env)->FindClass(env, "java/lang/String");
        cls = (*env)->NewGlobalRef(env, c);
        (*env)->DeleteLocalRef(env, c);
    }
    return cls;
}

JNIEXPORT jclass JNICALL
JNU_ClassClass(JNIEnv *env)
{
    static jclass cls = 0;
    if (cls == 0) {
        jclass c;
        if ((*env)->EnsureLocalCapacity(env, 1) < 0)
            return 0;
        c = (*env)->FindClass(env, "java/lang/Class");
        cls = (*env)->NewGlobalRef(env, c);
        (*env)->DeleteLocalRef(env, c);
    }
    return cls;
}

JNIEXPORT jclass JNICALL
JNU_ClassObject(JNIEnv *env)
{
    static jclass cls = 0;
    if (cls == 0) {
        jclass c;
        if ((*env)->EnsureLocalCapacity(env, 1) < 0)
            return 0;
        c = (*env)->FindClass(env, "java/lang/Object");
        cls = (*env)->NewGlobalRef(env, c);
        (*env)->DeleteLocalRef(env, c);
    }
    return cls;
}

JNIEXPORT jclass JNICALL
JNU_ClassThrowable(JNIEnv *env)
{
    static jclass cls = 0;
    if (cls == 0) {
        jclass c;
        if ((*env)->EnsureLocalCapacity(env, 1) < 0)
            return 0;
        c = (*env)->FindClass(env, "java/lang/Throwable");
        cls = (*env)->NewGlobalRef(env, c);
        (*env)->DeleteLocalRef(env, c);
    }
    return cls;
}

JNIEXPORT jint JNICALL
JNU_CopyObjectArray(JNIEnv *env, jobjectArray dst, jobjectArray src,
                         jint count)
{
    int i;
    if ((*env)->EnsureLocalCapacity(env, 1) < 0)
        return -1;
    for (i=0; i<count; i++) {
        jstring p = (*env)->GetObjectArrayElement(env, src, i);
        (*env)->SetObjectArrayElement(env, dst, i, p);
        (*env)->DeleteLocalRef(env, p);
    }
    return 0;
}

JNIEXPORT void * JNICALL
JNU_GetEnv(JavaVM *vm, jint version)
{
    void *env;
    (*vm)->GetEnv(vm, &env, version);
    return env;
}

JNIEXPORT jint JNICALL
JNU_IsInstanceOfByName(JNIEnv *env, jobject object, char* classname)
{
    jclass cls;
    if ((*env)->EnsureLocalCapacity(env, 1) < 0)
        return JNI_ERR;
    cls = (*env)->FindClass(env, classname);
    if (cls != NULL) {
        jint result = (*env)->IsInstanceOf(env, object, cls);
        (*env)->DeleteLocalRef(env, cls);
        return result;
    }
    return JNI_ERR;
}

JNIEXPORT jboolean JNICALL
JNU_Equals(JNIEnv *env, jobject object1, jobject object2)
{
    static jmethodID mid = NULL;
    if (mid == NULL) {
        mid = (*env)->GetMethodID(env, JNU_ClassObject(env), "equals",
                                  "(Ljava/lang/Object;)Z");
    }
    return (*env)->CallBooleanMethod(env, object1, mid, object2);
}


/************************************************************************
 * Thread calls
 */

static jmethodID Object_waitMID;
static jmethodID Object_notifyMID;
static jmethodID Object_notifyAllMID;

JNIEXPORT void JNICALL
JNU_MonitorWait(JNIEnv *env, jobject object, jlong timeout)
{
    if (object == NULL) {
        JNU_ThrowNullPointerException(env, "JNU_MonitorWait argument");
        return;
    }
    if (Object_waitMID == NULL) {
        jclass cls = JNU_ClassObject(env);
        if (cls == NULL) {
            return;
        }
        Object_waitMID = (*env)->GetMethodID(env, cls, "wait", "(J)V");
        if (Object_waitMID == NULL) {
            return;
        }
    }
    (*env)->CallVoidMethod(env, object, Object_waitMID, timeout);
}

JNIEXPORT void JNICALL
JNU_Notify(JNIEnv *env, jobject object)
{
    if (object == NULL) {
        JNU_ThrowNullPointerException(env, "JNU_Notify argument");
        return;
    }
    if (Object_notifyMID == NULL) {
        jclass cls = JNU_ClassObject(env);
        if (cls == NULL) {
            return;
        }
        Object_notifyMID = (*env)->GetMethodID(env, cls, "notify", "()V");
        if (Object_notifyMID == NULL) {
            return;
        }
    }
    (*env)->CallVoidMethod(env, object, Object_notifyMID);
}

JNIEXPORT void JNICALL
JNU_NotifyAll(JNIEnv *env, jobject object)
{
    if (object == NULL) {
        JNU_ThrowNullPointerException(env, "JNU_NotifyAll argument");
        return;
    }
    if (Object_notifyAllMID == NULL) {
        jclass cls = JNU_ClassObject(env);
        if (cls == NULL) {
            return;
        }
        Object_notifyAllMID = (*env)->GetMethodID(env, cls,"notifyAll", "()V");
        if (Object_notifyAllMID == NULL) {
            return;
        }
    }
    (*env)->CallVoidMethod(env, object, Object_notifyAllMID);
}


/************************************************************************
 * Debugging utilities
 */

JNIEXPORT void JNICALL
JNU_PrintString(JNIEnv *env, char *hdr, jstring string)
{
    if (string == NULL) {
        fprintf(stderr, "%s: is NULL\n", hdr);
    } else {
        const char *stringPtr = JNU_GetStringPlatformChars(env, string, 0);
        if (stringPtr == 0)
            return;
        fprintf(stderr, "%s: %s\n", hdr, stringPtr);
        JNU_ReleaseStringPlatformChars(env, string, stringPtr);
    }
}

JNIEXPORT void JNICALL
JNU_PrintClass(JNIEnv *env, char* hdr, jobject object)
{
    if (object == NULL) {
        fprintf(stderr, "%s: object is NULL\n", hdr);
        return;
    } else {
        jclass cls = (*env)->GetObjectClass(env, object);
        jstring clsName = JNU_ToString(env, cls);
        JNU_PrintString(env, hdr, clsName);
        (*env)->DeleteLocalRef(env, cls);
        (*env)->DeleteLocalRef(env, clsName);
    }
}

JNIEXPORT jstring JNICALL
JNU_ToString(JNIEnv *env, jobject object)
{
    if (object == NULL) {
        return (*env)->NewStringUTF(env, "NULL");
    } else {
        return (jstring)JNU_CallMethodByName(env,
                                             NULL,
                                             object,
                                             "toString",
                                             "()Ljava/lang/String;").l;
    }
}

JNIEXPORT jvalue JNICALL
JNU_GetFieldByName(JNIEnv *env,
                   jboolean *hasException,
                   jobject obj,
                   const char *name,
                   const char *signature)
{
    jclass cls;
    jfieldID fid;
    jvalue result;

    result.i = 0;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        goto done2;

    cls = (*env)->GetObjectClass(env, obj);
    fid = (*env)->GetFieldID(env, cls, name, signature);
    if (fid == 0)
        goto done1;

    switch (*signature) {
    case '[':
    case 'L':
        result.l = (*env)->GetObjectField(env, obj, fid);
        break;
    case 'Z':
        result.z = (*env)->GetBooleanField(env, obj, fid);
        break;
    case 'B':
        result.b = (*env)->GetByteField(env, obj, fid);
        break;
    case 'C':
        result.c = (*env)->GetCharField(env, obj, fid);
        break;
    case 'S':
        result.s = (*env)->GetShortField(env, obj, fid);
        break;
    case 'I':
        result.i = (*env)->GetIntField(env, obj, fid);
        break;
    case 'J':
        result.j = (*env)->GetLongField(env, obj, fid);
        break;
    case 'F':
        result.f = (*env)->GetFloatField(env, obj, fid);
        break;
    case 'D':
        result.d = (*env)->GetDoubleField(env, obj, fid);
        break;

    default:
        (*env)->FatalError(env, "JNU_GetFieldByName: illegal signature");
    }

 done1:
    (*env)->DeleteLocalRef(env, cls);
 done2:
    if (hasException) {
        *hasException = (*env)->ExceptionCheck(env);
    }
    return result;
}

JNIEXPORT void JNICALL
JNU_SetFieldByName(JNIEnv *env,
                   jboolean *hasException,
                   jobject obj,
                   const char *name,
                   const char *signature,
                   ...)
{
    jclass cls;
    jfieldID fid;
    va_list args;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        goto done2;

    cls = (*env)->GetObjectClass(env, obj);
    fid = (*env)->GetFieldID(env, cls, name, signature);
    if (fid == 0)
        goto done1;

    va_start(args, signature);
    switch (*signature) {
    case '[':
    case 'L':
        (*env)->SetObjectField(env, obj, fid, va_arg(args, jobject));
        break;
    case 'Z':
        (*env)->SetBooleanField(env, obj, fid, (jboolean)va_arg(args, int));
        break;
    case 'B':
        (*env)->SetByteField(env, obj, fid, (jbyte)va_arg(args, int));
        break;
    case 'C':
        (*env)->SetCharField(env, obj, fid, (jchar)va_arg(args, int));
        break;
    case 'S':
        (*env)->SetShortField(env, obj, fid, (jshort)va_arg(args, int));
        break;
    case 'I':
        (*env)->SetIntField(env, obj, fid, va_arg(args, jint));
        break;
    case 'J':
        (*env)->SetLongField(env, obj, fid, va_arg(args, jlong));
        break;
    case 'F':
        (*env)->SetFloatField(env, obj, fid, (jfloat)va_arg(args, jdouble));
        break;
    case 'D':
        (*env)->SetDoubleField(env, obj, fid, va_arg(args, jdouble));
        break;

    default:
        (*env)->FatalError(env, "JNU_SetFieldByName: illegal signature");
    }
    va_end(args);

 done1:
    (*env)->DeleteLocalRef(env, cls);
 done2:
    if (hasException) {
        *hasException = (*env)->ExceptionCheck(env);
    }
}

JNIEXPORT jvalue JNICALL
JNU_GetStaticFieldByName(JNIEnv *env,
                         jboolean *hasException,
                         const char *classname,
                         const char *name,
                         const char *signature)
{
    jclass cls;
    jfieldID fid;
    jvalue result;

    result.i = 0;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        goto done2;

    cls = (*env)->FindClass(env, classname);
    if (cls == 0)
        goto done2;

    fid = (*env)->GetStaticFieldID(env, cls, name, signature);
    if (fid == 0)
        goto done1;

    switch (*signature) {
    case '[':
    case 'L':
        result.l = (*env)->GetStaticObjectField(env, cls, fid);
        break;
    case 'Z':
        result.z = (*env)->GetStaticBooleanField(env, cls, fid);
        break;
    case 'B':
        result.b = (*env)->GetStaticByteField(env, cls, fid);
        break;
    case 'C':
        result.c = (*env)->GetStaticCharField(env, cls, fid);
        break;
    case 'S':
        result.s = (*env)->GetStaticShortField(env, cls, fid);
        break;
    case 'I':
        result.i = (*env)->GetStaticIntField(env, cls, fid);
        break;
    case 'J':
        result.j = (*env)->GetStaticLongField(env, cls, fid);
        break;
    case 'F':
        result.f = (*env)->GetStaticFloatField(env, cls, fid);
        break;
    case 'D':
        result.d = (*env)->GetStaticDoubleField(env, cls, fid);
        break;

    default:
        (*env)->FatalError(env, "JNU_GetStaticFieldByName: illegal signature");
    }

 done1:
    (*env)->DeleteLocalRef(env, cls);
 done2:
    if (hasException) {
        *hasException = (*env)->ExceptionCheck(env);
    }
    return result;
}

JNIEXPORT void JNICALL
JNU_SetStaticFieldByName(JNIEnv *env,
                         jboolean *hasException,
                         const char *classname,
                         const char *name,
                         const char *signature,
                         ...)
{
    jclass cls;
    jfieldID fid;
    va_list args;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0)
        goto done2;

    cls = (*env)->FindClass(env, classname);
    if (cls == 0)
        goto done2;

    fid = (*env)->GetStaticFieldID(env, cls, name, signature);
    if (fid == 0)
        goto done1;

    va_start(args, signature);
    switch (*signature) {
    case '[':
    case 'L':
        (*env)->SetStaticObjectField(env, cls, fid, va_arg(args, jobject));
        break;
    case 'Z':
        (*env)->SetStaticBooleanField(env, cls, fid, (jboolean)va_arg(args, int));
        break;
    case 'B':
        (*env)->SetStaticByteField(env, cls, fid, (jbyte)va_arg(args, int));
        break;
    case 'C':
        (*env)->SetStaticCharField(env, cls, fid, (jchar)va_arg(args, int));
        break;
    case 'S':
        (*env)->SetStaticShortField(env, cls, fid, (jshort)va_arg(args, int));
        break;
    case 'I':
        (*env)->SetStaticIntField(env, cls, fid, va_arg(args, jint));
        break;
    case 'J':
        (*env)->SetStaticLongField(env, cls, fid, va_arg(args, jlong));
        break;
    case 'F':
        (*env)->SetStaticFloatField(env, cls, fid, (jfloat)va_arg(args, jdouble));
        break;
    case 'D':
        (*env)->SetStaticDoubleField(env, cls, fid, va_arg(args, jdouble));
        break;

    default:
        (*env)->FatalError(env, "JNU_SetStaticFieldByName: illegal signature");
    }
    va_end(args);

 done1:
    (*env)->DeleteLocalRef(env, cls);
 done2:
    if (hasException) {
        *hasException = (*env)->ExceptionCheck(env);
    }
}
