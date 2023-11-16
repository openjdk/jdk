/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "jdk_internal_foreign_abi_fallback_LibFallback.h"

#include <ffi.h>

#include <errno.h>
#include <malloc.h>
#include <stdint.h>
#include <wchar.h>
#ifdef _WIN64
#include <Windows.h>
#include <Winsock2.h>
#endif

#include "jlong.h"

static JavaVM* VM;
static jclass LibFallback_class;
static jmethodID LibFallback_doUpcall_ID;
static const char* LibFallback_doUpcall_sig = "(JJLjava/lang/invoke/MethodHandle;)V";

#define CHECK_NULL(expr) \
  if (expr == NULL) { \
    return JNI_FALSE; \
  }

JNIEXPORT jboolean JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_init(JNIEnv* env, jclass cls) {
  jint result = (*env)->GetJavaVM(env, &VM);
  if (result != 0) {
    return JNI_FALSE;
  }

  jclass LibFallback_class_local = (*env)->FindClass(env, "jdk/internal/foreign/abi/fallback/LibFallback");
  CHECK_NULL(LibFallback_class_local)

  LibFallback_class = (*env)->NewGlobalRef(env, LibFallback_class_local);
  CHECK_NULL(LibFallback_class)

  LibFallback_doUpcall_ID = (*env)->GetStaticMethodID(env,
    LibFallback_class, "doUpcall", LibFallback_doUpcall_sig);
  CHECK_NULL(LibFallback_doUpcall_ID)

  return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_sizeofCif(JNIEnv* env, jclass cls) {
  return sizeof(ffi_cif);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1prep_1cif(JNIEnv* env, jclass cls, jlong cif, jint abi, jint nargs, jlong rtype, jlong atypes) {
  return ffi_prep_cif(jlong_to_ptr(cif), (ffi_abi) abi, (unsigned int) nargs, jlong_to_ptr(rtype), jlong_to_ptr(atypes));
}
JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1prep_1cif_1var(JNIEnv* env, jclass cls, jlong cif, jint abi, jint nfixedargs, jint ntotalargs, jlong rtype, jlong atypes) {
  return ffi_prep_cif_var(jlong_to_ptr(cif), (ffi_abi) abi, (unsigned int) nfixedargs, (unsigned int) ntotalargs, jlong_to_ptr(rtype), jlong_to_ptr(atypes));
}
JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1get_1struct_1offsets(JNIEnv* env, jclass cls, jint abi, jlong type, jlong offsets) {
  return ffi_get_struct_offsets((ffi_abi) abi, jlong_to_ptr(type), jlong_to_ptr(offsets));
}

static void do_capture_state(int32_t* value_ptr, int captured_state_mask) {
    // keep in synch with jdk.internal.foreign.abi.CapturableState
  enum PreservableValues {
    NONE = 0,
    GET_LAST_ERROR = 1,
    WSA_GET_LAST_ERROR = 1 << 1,
    ERRNO = 1 << 2
  };
#ifdef _WIN64
  if (captured_state_mask & GET_LAST_ERROR) {
    *value_ptr = GetLastError();
  }
  value_ptr++;
  if (captured_state_mask & WSA_GET_LAST_ERROR) {
    *value_ptr = WSAGetLastError();
  }
  value_ptr++;
#endif
  if (captured_state_mask & ERRNO) {
    *value_ptr = errno;
  }
}

JNIEXPORT void JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_doDowncall(JNIEnv* env, jclass cls, jlong cif, jlong fn, jlong rvalue,
                                                              jlong avalues, jlong jcaptured_state, jint captured_state_mask,
                                                              jarray heapBases, jint numArgs) {
  void** carrays;
  if (heapBases != NULL) {
    void** aptrs = jlong_to_ptr(avalues);
    carrays = malloc(sizeof(void*) * numArgs);
    for (int i = 0; i < numArgs; i++) {
      jarray hb = (jarray) (*env)->GetObjectArrayElement(env, heapBases, i);
      if (hb != NULL) {
        // *(aptrs[i]) is the offset into the segment (from MS::address)
        // we add the base address of the array to it here
        jboolean isCopy;
        jbyte* arrayPtr = (*env)->GetPrimitiveArrayCritical(env, hb, &isCopy);
        carrays[i] = arrayPtr;
        int offset = *((int*)aptrs[i]);
        *((void**)aptrs[i]) = arrayPtr + offset;
      }
    }
  }

  ffi_call(jlong_to_ptr(cif), jlong_to_ptr(fn), jlong_to_ptr(rvalue), jlong_to_ptr(avalues));

  if (heapBases != NULL) {
    for (int i = 0; i < numArgs; i++) {
      jarray hb = (jarray) (*env)->GetObjectArrayElement(env, heapBases, i);
      if (hb != NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, hb, carrays[i], JNI_COMMIT);
      }
    }
    free(carrays);
  }

  if (captured_state_mask != 0) {
    int32_t* captured_state = jlong_to_ptr(jcaptured_state);
    do_capture_state(captured_state, captured_state_mask);
  }
}

static void do_upcall(ffi_cif* cif, void* ret, void** args, void* user_data) {
  // attach thread
  JNIEnv* env;
  jint result = (*VM)->AttachCurrentThreadAsDaemon(VM, (void**) &env, NULL);

  // call into doUpcall in LibFallback
  jobject upcall_data = (jobject) user_data;
  (*env)->CallStaticVoidMethod(env, LibFallback_class, LibFallback_doUpcall_ID,
    ptr_to_jlong(ret), ptr_to_jlong(args), upcall_data);

  // always detach for now
  (*VM)->DetachCurrentThread(VM);
}

static void free_closure(JNIEnv* env, void* closure, jobject upcall_data) {
  ffi_closure_free(closure);
  (*env)->DeleteGlobalRef(env, upcall_data);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_createClosure(JNIEnv* env, jclass cls, jlong cif, jobject upcall_data, jlongArray jptrs) {
  void* code;
  void* closure = ffi_closure_alloc(sizeof(ffi_closure), &code);

  jobject global_upcall_data = (*env)->NewGlobalRef(env, upcall_data);

  ffi_status status = ffi_prep_closure_loc(closure, jlong_to_ptr(cif), &do_upcall, (void*) global_upcall_data, code);

  if (status != FFI_OK) {
    free_closure(env,closure, global_upcall_data);
    return status;
  }

  jlong* ptrs = (*env)->GetLongArrayElements(env, jptrs, NULL);
  ptrs[0] = ptr_to_jlong(closure);
  ptrs[1] = ptr_to_jlong(code);
  ptrs[2] = ptr_to_jlong(global_upcall_data);
  (*env)->ReleaseLongArrayElements(env, jptrs, ptrs, JNI_COMMIT);

  return status;
}

JNIEXPORT void JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_freeClosure(JNIEnv* env, jclass cls, jlong closure, jlong upcall_data) {
  free_closure(env, jlong_to_ptr(closure), jlong_to_ptr(upcall_data));
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1default_1abi(JNIEnv* env, jclass cls) {
  return (jint) FFI_DEFAULT_ABI;
}

JNIEXPORT jshort JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1struct(JNIEnv* env, jclass cls) {
  return (jshort) FFI_TYPE_STRUCT;
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1void(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_void);
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1uint8(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_uint8);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1sint8(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_sint8);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1uint16(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_uint16);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1sint16(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_sint16);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1uint32(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_uint32);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1sint32(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_sint32);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1uint64(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_uint64);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1sint64(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_sint64);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1float(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_float);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1double(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_double);
}
JNIEXPORT jlong JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1type_1pointer(JNIEnv* env, jclass cls) {
  return ptr_to_jlong(&ffi_type_pointer);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1sizeof_1short(JNIEnv* env, jclass cls) {
  return sizeof(short);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1sizeof_1int(JNIEnv* env, jclass cls) {
  return sizeof(int);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1sizeof_1long(JNIEnv* env, jclass cls) {
  return sizeof(long);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_foreign_abi_fallback_LibFallback_ffi_1sizeof_1wchar(JNIEnv* env, jclass cls) {
  return sizeof(wchar_t);
}
