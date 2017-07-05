/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#include <stdlib.h>
#include <jni.h>
#include "libproc.h"

#if defined(x86_64) && !defined(amd64)
#define amd64 1
#endif

#ifdef i386
#include "sun_jvm_hotspot_debugger_x86_X86ThreadContext.h"
#endif

#ifdef amd64
#include "sun_jvm_hotspot_debugger_amd64_AMD64ThreadContext.h"
#endif

#if defined(sparc) || defined(sparcv9)
#include "sun_jvm_hotspot_debugger_sparc_SPARCThreadContext.h"
#endif

static jfieldID p_ps_prochandle_ID = 0;
static jfieldID threadList_ID = 0;
static jfieldID loadObjectList_ID = 0;

static jmethodID createClosestSymbol_ID = 0;
static jmethodID createLoadObject_ID = 0;
static jmethodID getThreadForThreadId_ID = 0;
static jmethodID listAdd_ID = 0;

#define CHECK_EXCEPTION_(value) if ((*env)->ExceptionOccurred(env)) { return value; }
#define CHECK_EXCEPTION if ((*env)->ExceptionOccurred(env)) { return;}
#define THROW_NEW_DEBUGGER_EXCEPTION_(str, value) { throw_new_debugger_exception(env, str); return value; }
#define THROW_NEW_DEBUGGER_EXCEPTION(str) { throw_new_debugger_exception(env, str); return;}

static void throw_new_debugger_exception(JNIEnv* env, const char* errMsg) {
  (*env)->ThrowNew(env, (*env)->FindClass(env, "sun/jvm/hotspot/debugger/DebuggerException"), errMsg);
}

static struct ps_prochandle* get_proc_handle(JNIEnv* env, jobject this_obj) {
  jlong ptr = (*env)->GetLongField(env, this_obj, p_ps_prochandle_ID);
  return (struct ps_prochandle*)(intptr_t)ptr;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    init0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_init0
  (JNIEnv *env, jclass cls) {
  jclass listClass;

  if (init_libproc(getenv("LIBSAPROC_DEBUG") != NULL) != true) {
     THROW_NEW_DEBUGGER_EXCEPTION("can't initialize libproc");
  }

  // fields we use
  p_ps_prochandle_ID = (*env)->GetFieldID(env, cls, "p_ps_prochandle", "J");
  CHECK_EXCEPTION;
  threadList_ID = (*env)->GetFieldID(env, cls, "threadList", "Ljava/util/List;");
  CHECK_EXCEPTION;
  loadObjectList_ID = (*env)->GetFieldID(env, cls, "loadObjectList", "Ljava/util/List;");
  CHECK_EXCEPTION;

  // methods we use
  createClosestSymbol_ID = (*env)->GetMethodID(env, cls, "createClosestSymbol",
                    "(Ljava/lang/String;J)Lsun/jvm/hotspot/debugger/cdbg/ClosestSymbol;");
  CHECK_EXCEPTION;
  createLoadObject_ID = (*env)->GetMethodID(env, cls, "createLoadObject",
                    "(Ljava/lang/String;JJ)Lsun/jvm/hotspot/debugger/cdbg/LoadObject;");
  CHECK_EXCEPTION;
  getThreadForThreadId_ID = (*env)->GetMethodID(env, cls, "getThreadForThreadId",
                                                     "(J)Lsun/jvm/hotspot/debugger/ThreadProxy;");
  CHECK_EXCEPTION;
  // java.util.List method we call
  listClass = (*env)->FindClass(env, "java/util/List");
  CHECK_EXCEPTION;
  listAdd_ID = (*env)->GetMethodID(env, listClass, "add", "(Ljava/lang/Object;)Z");
  CHECK_EXCEPTION;
}

JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_getAddressSize
  (JNIEnv *env, jclass cls)
{
#ifdef _LP64
 return 8;
#else
 return 4;
#endif

}


static void fillThreadsAndLoadObjects(JNIEnv* env, jobject this_obj, struct ps_prochandle* ph) {
  int n = 0, i = 0;

  // add threads
  n = get_num_threads(ph);
  for (i = 0; i < n; i++) {
    jobject thread;
    jobject threadList;
    lwpid_t lwpid;

    lwpid = get_lwp_id(ph, i);
    thread = (*env)->CallObjectMethod(env, this_obj, getThreadForThreadId_ID,
                                      (jlong)lwpid);
    CHECK_EXCEPTION;
    threadList = (*env)->GetObjectField(env, this_obj, threadList_ID);
    CHECK_EXCEPTION;
    (*env)->CallBooleanMethod(env, threadList, listAdd_ID, thread);
    CHECK_EXCEPTION;
  }

  // add load objects
  n = get_num_libs(ph);
  for (i = 0; i < n; i++) {
     uintptr_t base;
     const char* name;
     jobject loadObject;
     jobject loadObjectList;

     base = get_lib_base(ph, i);
     name = get_lib_name(ph, i);
     loadObject = (*env)->CallObjectMethod(env, this_obj, createLoadObject_ID,
                                   (*env)->NewStringUTF(env, name), (jlong)0, (jlong)base);
     CHECK_EXCEPTION;
     loadObjectList = (*env)->GetObjectField(env, this_obj, loadObjectList_ID);
     CHECK_EXCEPTION;
     (*env)->CallBooleanMethod(env, loadObjectList, listAdd_ID, loadObject);
     CHECK_EXCEPTION;
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    attach0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_attach0__I
  (JNIEnv *env, jobject this_obj, jint jpid) {

  struct ps_prochandle* ph;
  if ( (ph = Pgrab(jpid)) == NULL) {
    THROW_NEW_DEBUGGER_EXCEPTION("Can't attach to the process");
  }
  (*env)->SetLongField(env, this_obj, p_ps_prochandle_ID, (jlong)(intptr_t)ph);
  fillThreadsAndLoadObjects(env, this_obj, ph);
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    attach0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_attach0__Ljava_lang_String_2Ljava_lang_String_2
  (JNIEnv *env, jobject this_obj, jstring execName, jstring coreName) {
  const char *execName_cstr;
  const char *coreName_cstr;
  jboolean isCopy;
  struct ps_prochandle* ph;

  execName_cstr = (*env)->GetStringUTFChars(env, execName, &isCopy);
  CHECK_EXCEPTION;
  coreName_cstr = (*env)->GetStringUTFChars(env, coreName, &isCopy);
  CHECK_EXCEPTION;

  if ( (ph = Pgrab_core(execName_cstr, coreName_cstr)) == NULL) {
    (*env)->ReleaseStringUTFChars(env, execName, execName_cstr);
    (*env)->ReleaseStringUTFChars(env, coreName, coreName_cstr);
    THROW_NEW_DEBUGGER_EXCEPTION("Can't attach to the core file");
  }
  (*env)->SetLongField(env, this_obj, p_ps_prochandle_ID, (jlong)(intptr_t)ph);
  (*env)->ReleaseStringUTFChars(env, execName, execName_cstr);
  (*env)->ReleaseStringUTFChars(env, coreName, coreName_cstr);
  fillThreadsAndLoadObjects(env, this_obj, ph);
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    detach0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_detach0
  (JNIEnv *env, jobject this_obj) {
  struct ps_prochandle* ph = get_proc_handle(env, this_obj);
  if (ph != NULL) {
     Prelease(ph);
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    lookupByName0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_lookupByName0
  (JNIEnv *env, jobject this_obj, jstring objectName, jstring symbolName) {
  const char *objectName_cstr, *symbolName_cstr;
  jlong addr;
  jboolean isCopy;
  struct ps_prochandle* ph = get_proc_handle(env, this_obj);

  objectName_cstr = NULL;
  if (objectName != NULL) {
    objectName_cstr = (*env)->GetStringUTFChars(env, objectName, &isCopy);
    CHECK_EXCEPTION_(0);
  }
  symbolName_cstr = (*env)->GetStringUTFChars(env, symbolName, &isCopy);
  CHECK_EXCEPTION_(0);

  addr = (jlong) lookup_symbol(ph, objectName_cstr, symbolName_cstr);

  if (objectName_cstr != NULL) {
    (*env)->ReleaseStringUTFChars(env, objectName, objectName_cstr);
  }
  (*env)->ReleaseStringUTFChars(env, symbolName, symbolName_cstr);
  return addr;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    lookupByAddress0
 * Signature: (J)Lsun/jvm/hotspot/debugger/cdbg/ClosestSymbol;
 */
JNIEXPORT jobject JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_lookupByAddress0
  (JNIEnv *env, jobject this_obj, jlong addr) {
  uintptr_t offset;
  const char* sym = NULL;

  struct ps_prochandle* ph = get_proc_handle(env, this_obj);
  sym = symbol_for_pc(ph, (uintptr_t) addr, &offset);
  if (sym == NULL) return 0;
  return (*env)->CallObjectMethod(env, this_obj, createClosestSymbol_ID,
                          (*env)->NewStringUTF(env, sym), (jlong)offset);
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    readBytesFromProcess0
 * Signature: (JJ)Lsun/jvm/hotspot/debugger/ReadResult;
 */
JNIEXPORT jbyteArray JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_readBytesFromProcess0
  (JNIEnv *env, jobject this_obj, jlong addr, jlong numBytes) {

  jboolean isCopy;
  jbyteArray array;
  jbyte *bufPtr;
  ps_err_e err;

  array = (*env)->NewByteArray(env, numBytes);
  CHECK_EXCEPTION_(0);
  bufPtr = (*env)->GetByteArrayElements(env, array, &isCopy);
  CHECK_EXCEPTION_(0);

  err = ps_pread(get_proc_handle(env, this_obj), (psaddr_t) (uintptr_t)addr, bufPtr, numBytes);
  (*env)->ReleaseByteArrayElements(env, array, bufPtr, 0);
  return (err == PS_OK)? array : 0;
}

JNIEXPORT jlongArray JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_getThreadIntegerRegisterSet0
  (JNIEnv *env, jobject this_obj, jint lwp_id) {

  struct reg gregs;
  jboolean isCopy;
  jlongArray array;
  jlong *regs;

  struct ps_prochandle* ph = get_proc_handle(env, this_obj);
  if (get_lwp_regs(ph, lwp_id, &gregs) != true) {
     THROW_NEW_DEBUGGER_EXCEPTION_("get_thread_regs failed for a lwp", 0);
  }

#undef NPRGREG
#ifdef i386
#define NPRGREG sun_jvm_hotspot_debugger_x86_X86ThreadContext_NPRGREG
#endif
#ifdef ia64
#define NPRGREG IA64_REG_COUNT
#endif
#ifdef amd64
#define NPRGREG sun_jvm_hotspot_debugger_amd64_AMD64ThreadContext_NPRGREG
#endif
#if defined(sparc) || defined(sparcv9)
#define NPRGREG sun_jvm_hotspot_debugger_sparc_SPARCThreadContext_NPRGREG
#endif

  array = (*env)->NewLongArray(env, NPRGREG);
  CHECK_EXCEPTION_(0);
  regs = (*env)->GetLongArrayElements(env, array, &isCopy);

#undef REG_INDEX

#ifdef i386
#define REG_INDEX(reg) sun_jvm_hotspot_debugger_x86_X86ThreadContext_##reg

  regs[REG_INDEX(GS)]  = (uintptr_t) gregs.r_gs;
  regs[REG_INDEX(FS)]  = (uintptr_t) gregs.r_fs;
  regs[REG_INDEX(ES)]  = (uintptr_t) gregs.r_es;
  regs[REG_INDEX(DS)]  = (uintptr_t) gregs.r_ds;
  regs[REG_INDEX(EDI)] = (uintptr_t) gregs.r_edi;
  regs[REG_INDEX(ESI)] = (uintptr_t) gregs.r_esi;
  regs[REG_INDEX(FP)] = (uintptr_t) gregs.r_ebp;
  regs[REG_INDEX(SP)] = (uintptr_t) gregs.r_isp;
  regs[REG_INDEX(EBX)] = (uintptr_t) gregs.r_ebx;
  regs[REG_INDEX(EDX)] = (uintptr_t) gregs.r_edx;
  regs[REG_INDEX(ECX)] = (uintptr_t) gregs.r_ecx;
  regs[REG_INDEX(EAX)] = (uintptr_t) gregs.r_eax;
  regs[REG_INDEX(PC)] = (uintptr_t) gregs.r_eip;
  regs[REG_INDEX(CS)]  = (uintptr_t) gregs.r_cs;
  regs[REG_INDEX(SS)]  = (uintptr_t) gregs.r_ss;

#endif /* i386 */

#if ia64
  regs = (*env)->GetLongArrayElements(env, array, &isCopy);
  int i;
  for (i = 0; i < NPRGREG; i++ ) {
    regs[i] = 0xDEADDEAD;
  }
#endif /* ia64 */

#ifdef amd64
#define REG_INDEX(reg) sun_jvm_hotspot_debugger_amd64_AMD64ThreadContext_##reg

  regs[REG_INDEX(R15)] = gregs.r_r15;
  regs[REG_INDEX(R14)] = gregs.r_r14;
  regs[REG_INDEX(R13)] = gregs.r_r13;
  regs[REG_INDEX(R12)] = gregs.r_r12;
  regs[REG_INDEX(RBP)] = gregs.r_rbp;
  regs[REG_INDEX(RBX)] = gregs.r_rbx;
  regs[REG_INDEX(R11)] = gregs.r_r11;
  regs[REG_INDEX(R10)] = gregs.r_r10;
  regs[REG_INDEX(R9)] = gregs.r_r9;
  regs[REG_INDEX(R8)] = gregs.r_r8;
  regs[REG_INDEX(RAX)] = gregs.r_rax;
  regs[REG_INDEX(RCX)] = gregs.r_rcx;
  regs[REG_INDEX(RDX)] = gregs.r_rdx;
  regs[REG_INDEX(RSI)] = gregs.r_rsi;
  regs[REG_INDEX(RDI)] = gregs.r_rdi;
  regs[REG_INDEX(RIP)] = gregs.r_rip;
  regs[REG_INDEX(CS)] = gregs.r_cs;
  regs[REG_INDEX(RSP)] = gregs.r_rsp;
  regs[REG_INDEX(SS)] = gregs.r_ss;
//  regs[REG_INDEX(FSBASE)] = gregs.fs_base;
//  regs[REG_INDEX(GSBASE)] = gregs.gs_base;
//  regs[REG_INDEX(DS)] = gregs.ds;
//  regs[REG_INDEX(ES)] = gregs.es;
//  regs[REG_INDEX(FS)] = gregs.fs;
//  regs[REG_INDEX(GS)] = gregs.gs;

#endif /* amd64 */

#if defined(sparc) || defined(sparcv9)

#define REG_INDEX(reg) sun_jvm_hotspot_debugger_sparc_SPARCThreadContext_##reg

#ifdef _LP64
  regs[REG_INDEX(R_PSR)] = gregs.tstate;
  regs[REG_INDEX(R_PC)]  = gregs.tpc;
  regs[REG_INDEX(R_nPC)] = gregs.tnpc;
  regs[REG_INDEX(R_Y)]   = gregs.y;
#else
  regs[REG_INDEX(R_PSR)] = gregs.psr;
  regs[REG_INDEX(R_PC)]  = gregs.pc;
  regs[REG_INDEX(R_nPC)] = gregs.npc;
  regs[REG_INDEX(R_Y)]   = gregs.y;
#endif
  regs[REG_INDEX(R_G0)]  =            0 ;
  regs[REG_INDEX(R_G1)]  = gregs.u_regs[0];
  regs[REG_INDEX(R_G2)]  = gregs.u_regs[1];
  regs[REG_INDEX(R_G3)]  = gregs.u_regs[2];
  regs[REG_INDEX(R_G4)]  = gregs.u_regs[3];
  regs[REG_INDEX(R_G5)]  = gregs.u_regs[4];
  regs[REG_INDEX(R_G6)]  = gregs.u_regs[5];
  regs[REG_INDEX(R_G7)]  = gregs.u_regs[6];
  regs[REG_INDEX(R_O0)]  = gregs.u_regs[7];
  regs[REG_INDEX(R_O1)]  = gregs.u_regs[8];
  regs[REG_INDEX(R_O2)]  = gregs.u_regs[ 9];
  regs[REG_INDEX(R_O3)]  = gregs.u_regs[10];
  regs[REG_INDEX(R_O4)]  = gregs.u_regs[11];
  regs[REG_INDEX(R_O5)]  = gregs.u_regs[12];
  regs[REG_INDEX(R_O6)]  = gregs.u_regs[13];
  regs[REG_INDEX(R_O7)]  = gregs.u_regs[14];
#endif /* sparc */


  (*env)->ReleaseLongArrayElements(env, array, regs, JNI_COMMIT);
  return array;
}
