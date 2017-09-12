/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/types.h>

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdarg.h>


#include <limits.h>

#include <com_sun_java_util_jar_pack_NativeUnpack.h>

#include "jni_util.h"

#include "defines.h"
#include "bytes.h"
#include "utils.h"
#include "coding.h"
#include "bands.h"
#include "constants.h"
#include "zip.h"
#include "unpack.h"


static jfieldID  unpackerPtrFID;
static jmethodID currentInstMID;
static jmethodID readInputMID;
static jclass    NIclazz;
static jmethodID getUnpackerPtrMID;

static char* dbg = null;

#define THROW_IOE(x) JNU_ThrowIOException(env,x)

#define CHECK_EXCEPTION_RETURN_VOID_THROW_IOE(CERVTI_exception, CERVTI_message) \
    do { \
        if ((env)->ExceptionOccurred()) { \
            THROW_IOE(CERVTI_message); \
            return; \
        } \
        if ((CERVTI_exception) == NULL) { \
                THROW_IOE(CERVTI_message); \
                return; \
        } \
    } while (JNI_FALSE)


#define CHECK_EXCEPTION_RETURN_VALUE(CERL_exception, CERL_return_value) \
    do { \
        if ((env)->ExceptionOccurred()) { \
            return CERL_return_value; \
        } \
        if ((CERL_exception) == NULL) { \
            return CERL_return_value; \
        } \
    } while (JNI_FALSE)


// If these useful macros aren't defined in jni_util.h then define them here
#ifndef CHECK_NULL_RETURN
#define CHECK_NULL_RETURN(x, y) \
    do { \
        if ((x) == NULL) return (y); \
    } while (JNI_FALSE)
#endif

#ifndef CHECK_EXCEPTION_RETURN
#define CHECK_EXCEPTION_RETURN(env, y) \
    do { \
        if ((*env)->ExceptionCheck(env)) return (y); \
    } while (JNI_FALSE)
#endif

/*
 * Declare library specific JNI_Onload entry if static build
 */
DEF_STATIC_JNI_OnLoad

static jlong read_input_via_jni(unpacker* self,
                                void* buf, jlong minlen, jlong maxlen);

static unpacker* get_unpacker(JNIEnv *env, jobject pObj, bool noCreate=false) {
  unpacker* uPtr;
  jlong p = env->CallLongMethod(pObj, getUnpackerPtrMID);
  uPtr = (unpacker*)jlong2ptr(p);
  if (uPtr == null) {
    if (noCreate)  return null;
    uPtr = new unpacker();
    if (uPtr == null) {
      THROW_IOE(ERROR_ENOMEM);
      return null;
    }
    //fprintf(stderr, "get_unpacker(%p) uPtr=%p initializing\n", pObj, uPtr);
    uPtr->init(read_input_via_jni);
    uPtr->jniobj = (void*) env->NewGlobalRef(pObj);
    env->SetLongField(pObj, unpackerPtrFID, ptr2jlong(uPtr));
  }
  uPtr->jnienv = env;  // keep refreshing this in case of MT access
  return uPtr;
}

// This is the harder trick:  Pull the current state out of mid-air.
static unpacker* get_unpacker() {
  //fprintf(stderr, "get_unpacker()\n");
  JavaVM* vm = null;
  jsize nVM = 0;
  jint retval = JNI_GetCreatedJavaVMs(&vm, 1, &nVM);
  // other VM implements may differ, thus for correctness, we need these checks
  if (retval != JNI_OK || nVM != 1)
    return null;
  void* envRaw = null;
  vm->GetEnv(&envRaw, JNI_VERSION_1_1);
  JNIEnv* env = (JNIEnv*) envRaw;
  //fprintf(stderr, "get_unpacker() env=%p\n", env);
  CHECK_NULL_RETURN(env, NULL);
  jobject pObj = env->CallStaticObjectMethod(NIclazz, currentInstMID);
  // We should check upon the known non-null variable because here we want to check
  // only for pending exceptions. If pObj is null we'll deal with it later.
  CHECK_EXCEPTION_RETURN_VALUE(env, NULL);
  //fprintf(stderr, "get_unpacker0() pObj=%p\n", pObj);
  if (pObj != null) {
    // Got pObj and env; now do it the easy way.
    return get_unpacker(env, pObj);
  }
  // this should really not happen, if it does something is seriously
  // wrong throw an exception
  THROW_IOE(ERROR_INTERNAL);
  return null;
}

static void free_unpacker(JNIEnv *env, jobject pObj, unpacker* uPtr) {
  if (uPtr != null) {
    //fprintf(stderr, "free_unpacker(%p) uPtr=%p\n", pObj, uPtr);
    env->DeleteGlobalRef((jobject) uPtr->jniobj);
    uPtr->jniobj = null;
    uPtr->free();
    delete uPtr;
    env->SetLongField(pObj, unpackerPtrFID, (jlong)null);
   }
}

unpacker* unpacker::current() {
  return get_unpacker();
}

// Callback for fetching data, Java style.  Calls NativeUnpack.readInputFn().
static jlong read_input_via_jni(unpacker* self,
                                void* buf, jlong minlen, jlong maxlen) {
  JNIEnv* env = (JNIEnv*) self->jnienv;
  jobject pbuf = env->NewDirectByteBuffer(buf, maxlen);
  return env->CallLongMethod((jobject) self->jniobj, readInputMID,
                             pbuf, minlen);
}

JNIEXPORT void JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_initIDs(JNIEnv *env, jclass clazz) {
#ifndef PRODUCT
  dbg = getenv("DEBUG_ATTACH");
  while( dbg != null) { sleep(10); }
#endif
  NIclazz = (jclass) env->NewGlobalRef(clazz);

  unpackerPtrFID = env->GetFieldID(clazz, "unpackerPtr", "J");
  CHECK_EXCEPTION_RETURN_VOID_THROW_IOE(unpackerPtrFID, ERROR_INIT);

  currentInstMID = env->GetStaticMethodID(clazz, "currentInstance",
                                          "()Ljava/lang/Object;");
  CHECK_EXCEPTION_RETURN_VOID_THROW_IOE(currentInstMID, ERROR_INIT);

  readInputMID = env->GetMethodID(clazz, "readInputFn",
                                  "(Ljava/nio/ByteBuffer;J)J");
  CHECK_EXCEPTION_RETURN_VOID_THROW_IOE(readInputMID, ERROR_INIT);

  getUnpackerPtrMID = env->GetMethodID(clazz, "getUnpackerPtr", "()J");
  CHECK_EXCEPTION_RETURN_VOID_THROW_IOE(getUnpackerPtrMID, ERROR_INIT);
}

JNIEXPORT jlong JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_start(JNIEnv *env, jobject pObj,
                                   jobject pBuf, jlong offset) {
  // try to get the unpacker pointer the hard way first, we do this to ensure
  // valid object pointers and env is intact, if not now is good time to bail.
  unpacker* uPtr = get_unpacker();
  //fprintf(stderr, "start(%p) uPtr=%p initializing\n", pObj, uPtr);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, -1);
  // redirect our io to the default log file or whatever.
  uPtr->redirect_stdio();

  void*  buf    = null;
  size_t buflen = 0;
  if (pBuf != null) {
    buf    = env->GetDirectBufferAddress(pBuf);
    buflen = (size_t)env->GetDirectBufferCapacity(pBuf);
    if (buflen == 0)  buf = null;
    if (buf == null) { THROW_IOE(ERROR_INTERNAL); return 0; }
    if ((size_t)offset >= buflen)
      { buf = null; buflen = 0; }
    else
      { buf = (char*)buf + (size_t)offset; buflen -= (size_t)offset; }
  }
  // before we start off we make sure there is no other error by the time we
  // get here
  if (uPtr->aborting()) {
    THROW_IOE(uPtr->get_abort_message());
    return 0;
  }
  uPtr->start(buf, buflen);
  if (uPtr->aborting()) {
    THROW_IOE(uPtr->get_abort_message());
    return 0;
  }

  return ((jlong)
          uPtr->get_segments_remaining() << 32)
    + uPtr->get_files_remaining();
}

JNIEXPORT jboolean JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_getNextFile(JNIEnv *env, jobject pObj,
                                         jobjectArray pParts) {

  unpacker* uPtr = get_unpacker(env, pObj);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, false);
  unpacker::file* filep = uPtr->get_next_file();

  if (uPtr->aborting()) {
    THROW_IOE(uPtr->get_abort_message());
    return false;
  }

  CHECK_NULL_RETURN(filep, false);
  assert(filep == &uPtr->cur_file);

  int pidx = 0, iidx = 0;
  jintArray pIntParts = (jintArray) env->GetObjectArrayElement(pParts, pidx++);
  CHECK_EXCEPTION_RETURN_VALUE(pIntParts, false);
  jint*     intParts  = env->GetIntArrayElements(pIntParts, null);
  intParts[iidx++] = (jint)( (julong)filep->size >> 32 );
  intParts[iidx++] = (jint)( (julong)filep->size >>  0 );
  intParts[iidx++] = filep->modtime;
  intParts[iidx++] = filep->deflate_hint() ? 1 : 0;
  env->ReleaseIntArrayElements(pIntParts, intParts, JNI_COMMIT);
  jstring filename = env->NewStringUTF(filep->name);
  CHECK_EXCEPTION_RETURN_VALUE(filename, false);
  env->SetObjectArrayElement(pParts, pidx++, filename);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, false);
  jobject pDataBuf = null;
  if (filep->data[0].len > 0) {
    pDataBuf = env->NewDirectByteBuffer(filep->data[0].ptr,
                                        filep->data[0].len);
    CHECK_EXCEPTION_RETURN_VALUE(pDataBuf, false);
  }
  env->SetObjectArrayElement(pParts, pidx++, pDataBuf);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, false);
  pDataBuf = null;
  if (filep->data[1].len > 0) {
    pDataBuf = env->NewDirectByteBuffer(filep->data[1].ptr,
                                        filep->data[1].len);
    CHECK_EXCEPTION_RETURN_VALUE(pDataBuf, false);
  }
  env->SetObjectArrayElement(pParts, pidx++, pDataBuf);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, false);

  return true;
}


JNIEXPORT jobject JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_getUnusedInput(JNIEnv *env, jobject pObj) {
  unpacker* uPtr = get_unpacker(env, pObj);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, NULL);
  unpacker::file* filep = &uPtr->cur_file;

  if (uPtr->aborting()) {
    THROW_IOE(uPtr->get_abort_message());
    return null;
  }

  // We have fetched all the files.
  // Now swallow up any remaining input.
  if (uPtr->input_remaining() == 0) {
    return null;
  } else {
    bytes remaining_bytes;
    remaining_bytes.malloc(uPtr->input_remaining());
    remaining_bytes.copyFrom(uPtr->input_scan(), uPtr->input_remaining());
    return env->NewDirectByteBuffer(remaining_bytes.ptr, remaining_bytes.len);
  }
}

JNIEXPORT jlong JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_finish(JNIEnv *env, jobject pObj) {
  unpacker* uPtr = get_unpacker(env, pObj, false);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, 0);
  size_t consumed = uPtr->input_consumed();
  free_unpacker(env, pObj, uPtr);
  return consumed;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_setOption(JNIEnv *env, jobject pObj,
                                       jstring pProp, jstring pValue) {
  unpacker*   uPtr  = get_unpacker(env, pObj);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, false);
  const char* prop  = env->GetStringUTFChars(pProp, JNI_FALSE);
  CHECK_EXCEPTION_RETURN_VALUE(prop, false);
  const char* value = env->GetStringUTFChars(pValue, JNI_FALSE);
  CHECK_EXCEPTION_RETURN_VALUE(value, false);
  jboolean   retval = uPtr->set_option(prop, value);
  env->ReleaseStringUTFChars(pProp,  prop);
  env->ReleaseStringUTFChars(pValue, value);
  return retval;
}

JNIEXPORT jstring JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_getOption(JNIEnv *env, jobject pObj,
                                       jstring pProp) {

  unpacker*   uPtr  = get_unpacker(env, pObj);
  CHECK_EXCEPTION_RETURN_VALUE(uPtr, NULL);
  const char* prop  = env->GetStringUTFChars(pProp, JNI_FALSE);
  CHECK_EXCEPTION_RETURN_VALUE(prop, NULL);
  const char* value = uPtr->get_option(prop);
  CHECK_EXCEPTION_RETURN_VALUE(value, NULL);
  env->ReleaseStringUTFChars(pProp, prop);
  return env->NewStringUTF(value);
}
