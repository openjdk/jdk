/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

static char* dbg = null;

#define THROW_IOE(x) JNU_ThrowIOException(env,x)

static jlong read_input_via_jni(unpacker* self,
                                void* buf, jlong minlen, jlong maxlen);

static unpacker* get_unpacker(JNIEnv *env, jobject pObj, bool noCreate=false) {
  unpacker* uPtr;
  uPtr = (unpacker*)jlong2ptr(env->GetLongField(pObj, unpackerPtrFID));
  //fprintf(stderr, "get_unpacker(%p) uPtr=%p\n", pObj, uPtr);
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
  JNI_GetCreatedJavaVMs(&vm, 1, null);
  void* envRaw = null;
  vm->GetEnv(&envRaw, JNI_VERSION_1_1);
  JNIEnv* env = (JNIEnv*) envRaw;
  //fprintf(stderr, "get_unpacker() env=%p\n", env);
  if (env == null)
    return null;
  jobject pObj = env->CallStaticObjectMethod(NIclazz, currentInstMID);
  //fprintf(stderr, "get_unpacker() pObj=%p\n", pObj);
  if (pObj == null)
    return null;
  // Got pObj and env; now do it the easy way.
  return get_unpacker(env, pObj);
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
  dbg = getenv("DEBUG_ATTACH");
  while( dbg != null) { sleep(10); }
  NIclazz = (jclass) env->NewGlobalRef(clazz);
  unpackerPtrFID = env->GetFieldID(clazz, "unpackerPtr", "J");
  currentInstMID = env->GetStaticMethodID(clazz, "currentInstance",
                                          "()Ljava/lang/Object;");
  readInputMID = env->GetMethodID(clazz, "readInputFn",
                                  "(Ljava/nio/ByteBuffer;J)J");
  if (unpackerPtrFID == null ||
      currentInstMID == null ||
      readInputMID == null ||
      NIclazz == null) {
    THROW_IOE("cannot init class members");
  }
}

JNIEXPORT jlong JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_start(JNIEnv *env, jobject pObj,
                                   jobject pBuf, jlong offset) {
  unpacker* uPtr = get_unpacker(env, pObj);

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
  unpacker::file* filep = uPtr->get_next_file();

  if (uPtr->aborting()) {
    THROW_IOE(uPtr->get_abort_message());
    return false;
  }

  if (filep == null) {
    return false;   // end of the sequence
  }
  assert(filep == &uPtr->cur_file);

  int pidx = 0, iidx = 0;
  jintArray pIntParts = (jintArray) env->GetObjectArrayElement(pParts, pidx++);
  jint*     intParts  = env->GetIntArrayElements(pIntParts, null);
  intParts[iidx++] = (jint)( (julong)filep->size >> 32 );
  intParts[iidx++] = (jint)( (julong)filep->size >>  0 );
  intParts[iidx++] = filep->modtime;
  intParts[iidx++] = filep->deflate_hint() ? 1 : 0;
  env->ReleaseIntArrayElements(pIntParts, intParts, JNI_COMMIT);

  env->SetObjectArrayElement(pParts, pidx++, env->NewStringUTF(filep->name));

  jobject pDataBuf = null;
  if (filep->data[0].len > 0)
    pDataBuf = env->NewDirectByteBuffer(filep->data[0].ptr,
                                        filep->data[0].len);
  env->SetObjectArrayElement(pParts, pidx++, pDataBuf);
  pDataBuf = null;
  if (filep->data[1].len > 0)
    pDataBuf = env->NewDirectByteBuffer(filep->data[1].ptr,
                                        filep->data[1].len);
  env->SetObjectArrayElement(pParts, pidx++, pDataBuf);

  return true;
}


JNIEXPORT jobject JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_getUnusedInput(JNIEnv *env, jobject pObj) {
  unpacker* uPtr = get_unpacker(env, pObj);
  unpacker::file* filep = &uPtr->cur_file;

  if (uPtr->aborting()) {
    THROW_IOE(uPtr->get_abort_message());
    return false;
  }

  // We have fetched all the files.
  // Now swallow up any remaining input.
  if (uPtr->input_remaining() == 0)
    return null;
  else
    return env->NewDirectByteBuffer(uPtr->input_scan(),
                                    uPtr->input_remaining());
}

JNIEXPORT jlong JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_finish(JNIEnv *env, jobject pObj) {
  unpacker* uPtr = get_unpacker(env, pObj, false);
  if (uPtr == null)  return 0;
  size_t consumed = uPtr->input_consumed();
  free_unpacker(env, pObj, uPtr);
  return consumed;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_setOption(JNIEnv *env, jobject pObj,
                                       jstring pProp, jstring pValue) {
  unpacker*   uPtr  = get_unpacker(env, pObj);
  const char* prop  = env->GetStringUTFChars(pProp, JNI_FALSE);
  const char* value = env->GetStringUTFChars(pValue, JNI_FALSE);
  jboolean   retval = uPtr->set_option(prop, value);
  env->ReleaseStringUTFChars(pProp,  prop);
  env->ReleaseStringUTFChars(pValue, value);
  return retval;
}

JNIEXPORT jstring JNICALL
Java_com_sun_java_util_jar_pack_NativeUnpack_getOption(JNIEnv *env, jobject pObj,
                                       jstring pProp) {

  unpacker*   uPtr  = get_unpacker(env, pObj);
  const char* prop  = env->GetStringUTFChars(pProp, JNI_FALSE);
  const char* value = uPtr->get_option(prop);
  env->ReleaseStringUTFChars(pProp, prop);
  if (value == null)  return null;
  return env->NewStringUTF(value);
}
