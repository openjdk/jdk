/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include <objc/objc-runtime.h>
#import <Foundation/Foundation.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#include <JavaVM/jni.h>

#import <mach/mach.h>
#import <mach/mach_types.h>
#import <sys/sysctl.h>
#import <stdio.h>
#import <stdarg.h>
#import <stdlib.h>
#import <strings.h>
#import <dlfcn.h>
#import <limits.h>
#import <errno.h>
#import <sys/types.h>
#import <sys/ptrace.h>

jboolean debug = JNI_FALSE;

static jfieldID symbolicatorID = 0; // set in _init0
static jfieldID taskID = 0; // set in _init0

static void putSymbolicator(JNIEnv *env, jobject this_obj, id symbolicator) {
  (*env)->SetLongField(env, this_obj, symbolicatorID, (jlong)(intptr_t)symbolicator);
}

static id getSymbolicator(JNIEnv *env, jobject this_obj) {
  jlong ptr = (*env)->GetLongField(env, this_obj, symbolicatorID);
  return (id)(intptr_t)ptr;
}

static void putTask(JNIEnv *env, jobject this_obj, task_t task) {
  (*env)->SetLongField(env, this_obj, taskID, (jlong)task);
}

static task_t getTask(JNIEnv *env, jobject this_obj) {
  jlong ptr = (*env)->GetLongField(env, this_obj, taskID);
  return (task_t)ptr;
}

#define CHECK_EXCEPTION_(value) if ((*env)->ExceptionOccurred(env)) { return value; }
#define CHECK_EXCEPTION if ((*env)->ExceptionOccurred(env)) { return;}
#define THROW_NEW_DEBUGGER_EXCEPTION_(str, value) { throw_new_debugger_exception(env, str); return value; }
#define THROW_NEW_DEBUGGER_EXCEPTION(str) { throw_new_debugger_exception(env, str); return;}
#define CHECK_EXCEPTION_CLEAR if ((*env)->ExceptionOccurred(env)) { (*env)->ExceptionClear(env); } 
#define CHECK_EXCEPTION_CLEAR_VOID if ((*env)->ExceptionOccurred(env)) { (*env)->ExceptionClear(env); return; } 
#define CHECK_EXCEPTION_CLEAR_(value) if ((*env)->ExceptionOccurred(env)) { (*env)->ExceptionClear(env); return value; } 

static void throw_new_debugger_exception(JNIEnv* env, const char* errMsg) {
  (*env)->ThrowNew(env, (*env)->FindClass(env, "sun/jvm/hotspot/debugger/DebuggerException"), errMsg);
}

#if defined(__i386__)
    #define hsdb_thread_state_t     x86_thread_state32_t
    #define hsdb_float_state_t      x86_float_state32_t
    #define HSDB_THREAD_STATE       x86_THREAD_STATE32
    #define HSDB_FLOAT_STATE        x86_FLOAT_STATE32
    #define HSDB_THREAD_STATE_COUNT x86_THREAD_STATE32_COUNT
    #define HSDB_FLOAT_STATE_COUNT  x86_FLOAT_STATE32_COUNT
#elif defined(__x86_64__)
    #define hsdb_thread_state_t     x86_thread_state64_t
    #define hsdb_float_state_t      x86_float_state64_t
    #define HSDB_THREAD_STATE       x86_THREAD_STATE64
    #define HSDB_FLOAT_STATE        x86_FLOAT_STATE64
    #define HSDB_THREAD_STATE_COUNT x86_THREAD_STATE64_COUNT
    #define HSDB_FLOAT_STATE_COUNT  x86_FLOAT_STATE64_COUNT
#else
    #error "Unsupported architecture"
#endif

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    init0
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_init0(JNIEnv *env, jclass cls) {
  symbolicatorID = (*env)->GetFieldID(env, cls, "symbolicator", "J");
  taskID = (*env)->GetFieldID(env, cls, "task", "J");
  CHECK_EXCEPTION;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    lookupByName0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL 
Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_lookupByName0(
  JNIEnv *env, jobject this_obj, 
  jstring objectName, jstring symbolName) 
{
  jlong address = 0;

JNF_COCOA_ENTER(env);
  NSString *symbolNameString = JNFJavaToNSString(env, symbolName);

  if (debug) {
    printf("lookupInProcess called for %s\n", [symbolNameString UTF8String]);
  }

  id symbolicator = getSymbolicator(env, this_obj);
  if (symbolicator != nil) {
    uint64_t (*dynamicCall)(id, SEL, NSString *) = (uint64_t (*)(id, SEL, NSString *))&objc_msgSend;
    address = (jlong) dynamicCall(symbolicator, @selector(addressForSymbol:), symbolNameString);
  }

  if (debug) {
    printf("address of symbol %s = %llx\n", [symbolNameString UTF8String], address);
  }
JNF_COCOA_EXIT(env);

  return address;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    readBytesFromProcess0
 * Signature: (JJ)Lsun/jvm/hotspot/debugger/ReadResult;
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_readBytesFromProcess0(
  JNIEnv *env, jobject this_obj, 
  jlong addr, jlong numBytes) 
{
  if (debug) printf("readBytesFromProcess called. addr = %llx numBytes = %lld\n", addr, numBytes);

  // must allocate storage instead of using former parameter buf
  jboolean isCopy;
  jbyteArray array;
  jbyte *bufPtr;

  array = (*env)->NewByteArray(env, numBytes);
  CHECK_EXCEPTION_(0);

  unsigned long alignedAddress;
  unsigned long alignedLength = 0;
  kern_return_t result;
  vm_offset_t *pages;
  int *mapped;
  long pageCount;
  uint byteCount;
  int i;
  unsigned long remaining;

  alignedAddress = trunc_page(addr);
  if (addr != alignedAddress) {
    alignedLength += addr - alignedAddress;
  }
  alignedLength = round_page(numBytes);
  pageCount = alignedLength/vm_page_size;

  // Allocate storage for pages and flags.
  pages = malloc(pageCount * sizeof(vm_offset_t));
  mapped = calloc(pageCount, sizeof(int));

  task_t gTask = getTask(env, this_obj);
  // Try to read each of the pages.
  for (i = 0; i < pageCount; i++) {
    result = vm_read(gTask, alignedAddress + i*vm_page_size, vm_page_size, 
		     &pages[i], &byteCount);
    mapped[i] = (result == KERN_SUCCESS); 
    // assume all failures are unmapped pages
  }

  if (debug) fprintf(stderr, "%ld pages\n", pageCount);
	
  remaining = numBytes;
	
  for (i = 0; i < pageCount; i++) {
    unsigned long len = vm_page_size;
    unsigned long start = 0;

    if (i == 0) {
      start = addr - alignedAddress;
      len = vm_page_size - start;
    }

    if (i == (pageCount - 1)) {
      len = remaining;
    }

    if (mapped[i]) {
      if (debug) fprintf(stderr, "page %d mapped (len %ld start %ld)\n", i, len, start);
      (*env)->SetByteArrayRegion(env, array, 0, len, ((jbyte *) pages[i] + start));
      vm_deallocate(mach_task_self(), pages[i], vm_page_size);
    }

    remaining -= len;
  }

  free (pages);
  free (mapped);
  return array;
}


/*
 * Lookup the thread_t that corresponds to the given thread_id.
 * The thread_id should be the result from calling thread_info() with THREAD_IDENTIFIER_INFO
 * and reading the m_ident_info.thread_id returned.
 * The returned thread_t is the mach send right to the kernel port for the corresponding thread.
 *
 * We cannot simply use the OSThread._thread_id field in the JVM. This is set to ::mach_thread_self()
 * in the VM, but that thread port is not valid for a remote debugger to access the thread.
 */
thread_t
lookupThreadFromThreadId(task_t task, jlong thread_id) {
  if (debug) {
    printf("lookupThreadFromThreadId thread_id=0x%llx\n", thread_id);
  }
  
  thread_array_t thread_list = NULL;
  mach_msg_type_number_t thread_list_count = 0;
  thread_t result_thread = 0;
  int i;
  
  // get the list of all the send rights
  kern_return_t result = task_threads(task, &thread_list, &thread_list_count);
  if (result != KERN_SUCCESS) {
    if (debug) {
      printf("task_threads returned 0x%x\n", result);
    }
    return 0;
  }
  
  for(i = 0 ; i < thread_list_count; i++) {
    thread_identifier_info_data_t m_ident_info;
    mach_msg_type_number_t count = THREAD_IDENTIFIER_INFO_COUNT;

    // get the THREAD_IDENTIFIER_INFO for the send right
    result = thread_info(thread_list[i], THREAD_IDENTIFIER_INFO, (thread_info_t) &m_ident_info, &count);
    if (result != KERN_SUCCESS) {
      if (debug) {
        printf("thread_info returned 0x%x\n", result);
      }
      break;
    }
    
    // if this is the one we're looking for, return the send right
    if (thread_id == m_ident_info.thread_id)
    {
      result_thread = thread_list[i];
      break;
    }
  }
  
  vm_size_t thread_list_size = (vm_size_t) (thread_list_count * sizeof (thread_t));
  vm_deallocate(mach_task_self(), (vm_address_t) thread_list, thread_list_count);
  
  return result_thread;
}


/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    getThreadIntegerRegisterSet0
 * Signature: (J)[J
 */
JNIEXPORT jlongArray JNICALL 
Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_getThreadIntegerRegisterSet0(
  JNIEnv *env, jobject this_obj, 
  jlong thread_id) 
{
  if (debug)
    printf("getThreadRegisterSet0 called\n");

  kern_return_t result;
  thread_t tid;
  mach_msg_type_number_t count = HSDB_THREAD_STATE_COUNT;
  hsdb_thread_state_t state;
  unsigned int *r;
  int i;
  jlongArray registerArray;
  jlong *primitiveArray;
  task_t gTask = getTask(env, this_obj);

  tid = lookupThreadFromThreadId(gTask, thread_id);

  result = thread_get_state(tid, HSDB_THREAD_STATE, (thread_state_t)&state, &count);

  if (result != KERN_SUCCESS) {
    if (debug)
      printf("getregs: thread_get_state(%d) failed (%d)\n", tid, result);
    return NULL;
  }

  // 40 32-bit registers on ppc, 16 on x86. 
  // Output order is the same as the order in the ppc_thread_state/i386_thread_state struct.
#if defined(__i386__)
	r = (unsigned int *)&state;
	registerArray = (*env)->NewLongArray(env, 8);
	primitiveArray = (*env)->GetLongArrayElements(env, registerArray, NULL);
	primitiveArray[0] = r[0];  // eax
	primitiveArray[1] = r[2];  // ecx
	primitiveArray[2] = r[3];  // edx
	primitiveArray[3] = r[1];  // ebx
	primitiveArray[4] = r[7];  // esp
	primitiveArray[5] = r[6];  // ebp
	primitiveArray[6] = r[5];  // esi
	primitiveArray[7] = r[4];  // edi
	(*env)->ReleaseLongArrayElements(env, registerArray, primitiveArray, 0);
#elif defined(__x86_64__)
	/* From AMD64ThreadContext.java
	   public static final int R15 = 0;
	   public static final int R14 = 1;
	   public static final int R13 = 2;
	   public static final int R12 = 3;
	   public static final int R11 = 4;
	   public static final int R10 = 5;
	   public static final int R9  = 6;
	   public static final int R8  = 7;
	   public static final int RDI = 8;
	   public static final int RSI = 9;
	   public static final int RBP = 10;
	   public static final int RBX = 11;
	   public static final int RDX = 12;
	   public static final int RCX = 13;
	   public static final int RAX = 14;
	   public static final int TRAPNO = 15;
	   public static final int ERR = 16;
	   public static final int RIP = 17;
	   public static final int CS = 18;
	   public static final int RFL = 19;
	   public static final int RSP = 20;
	   public static final int SS = 21;
	   public static final int FS = 22;
	   public static final int GS = 23;
	   public static final int ES = 24;
	   public static final int DS = 25;
	   public static final int FSBASE = 26;
	   public static final int GSBASE = 27;
	 */
	// 64 bit
	if (debug) printf("Getting threads for a 64-bit process\n");
	registerArray = (*env)->NewLongArray(env, 28);
	primitiveArray = (*env)->GetLongArrayElements(env, registerArray, NULL);

	primitiveArray[0] = state.__r15;
	primitiveArray[1] = state.__r14;
	primitiveArray[2] = state.__r13;
	primitiveArray[3] = state.__r12;
	primitiveArray[4] = state.__r11;
	primitiveArray[5] = state.__r10;
	primitiveArray[6] = state.__r9;
	primitiveArray[7] = state.__r8;
	primitiveArray[8] = state.__rdi;
	primitiveArray[9] = state.__rsi;
	primitiveArray[10] = state.__rbp;
	primitiveArray[11] = state.__rbx;
	primitiveArray[12] = state.__rdx;
	primitiveArray[13] = state.__rcx;
	primitiveArray[14] = state.__rax;
	primitiveArray[15] = 0;             // trapno ?
	primitiveArray[16] = 0;             // err ?
	primitiveArray[17] = state.__rip;
	primitiveArray[18] = state.__cs;
	primitiveArray[19] = state.__rflags;
	primitiveArray[20] = state.__rsp;
	primitiveArray[21] = 0;            // We don't have SS
	primitiveArray[22] = state.__fs;
	primitiveArray[23] = state.__gs;
	primitiveArray[24] = 0;
	primitiveArray[25] = 0;
	primitiveArray[26] = 0;
	primitiveArray[27] = 0;

	if (debug) printf("set registers\n");

	(*env)->ReleaseLongArrayElements(env, registerArray, primitiveArray, 0);
#else
#error Unsupported architecture
#endif

  return registerArray;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    translateTID0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_sun_jvm_hotspot_debugger_macosx_MacOSXDebuggerLocal_translateTID0(
  JNIEnv *env, jobject this_obj, jint tid) 
{
  if (debug)
    printf("translateTID0 called on tid = 0x%x\n", (int)tid);

  kern_return_t result;
  thread_t foreign_tid, usable_tid;
  mach_msg_type_name_t type;
  
  foreign_tid = tid;
    
  task_t gTask = getTask(env, this_obj);
  result = mach_port_extract_right(gTask, foreign_tid, 
				   MACH_MSG_TYPE_COPY_SEND, 
				   &usable_tid, &type);
  if (result != KERN_SUCCESS)
    return -1;
    
  if (debug)
    printf("translateTID0: 0x%x -> 0x%x\n", foreign_tid, usable_tid);
    
  return (jint) usable_tid;
}


static bool ptrace_continue(pid_t pid, int signal) {
  // pass the signal to the process so we don't swallow it
  int res;
  if ((res = ptrace(PT_CONTINUE, pid, (caddr_t)1, signal)) < 0) {
    fprintf(stderr, "attach: ptrace(PT_CONTINUE, %d) failed with %d\n", pid, res);
    return false;
  }
  return true;
}

// waits until the ATTACH has stopped the process
// by signal SIGSTOP
static bool ptrace_waitpid(pid_t pid) {
  int ret;
  int status;
  while (true) {
    // Wait for debuggee to stop.
    ret = waitpid(pid, &status, 0);
    if (ret >= 0) {
      if (WIFSTOPPED(status)) {
        // Any signal will stop the thread, make sure it is SIGSTOP. Otherwise SIGSTOP
        // will still be pending and delivered when the process is DETACHED and the process
        // will go to sleep.
        if (WSTOPSIG(status) == SIGSTOP) {
          // Debuggee stopped by SIGSTOP.
          return true;
        }
        if (!ptrace_continue(pid, WSTOPSIG(status))) {
          fprintf(stderr, "attach: Failed to correctly attach to VM. VM might HANG! [PTRACE_CONT failed, stopped by %d]\n", WSTOPSIG(status));
          return false;
        }
      } else {
        fprintf(stderr, "attach: waitpid(): Child process exited/terminated (status = 0x%x)\n", status);
        return false;
      }
    } else {
      switch (errno) {
        case EINTR:
          continue;
          break;
        case ECHILD:
          fprintf(stderr, "attach: waitpid() failed. Child process pid (%d) does not exist \n", pid);
          break;
        case EINVAL:
          fprintf(stderr, "attach: waitpid() failed. Invalid options argument.\n");
          break;
        default:
          fprintf(stderr, "attach: waitpid() failed. Unexpected error %d\n",errno);
          break;
      }
      return false;
    }
  }
}

// attach to a process/thread specified by "pid"
static bool ptrace_attach(pid_t pid) {
  int res;
  if ((res = ptrace(PT_ATTACH, pid, 0, 0)) < 0) {
    fprintf(stderr, "ptrace(PT_ATTACH, %d) failed with %d\n", pid, res);
    return false;
  } else {
    return ptrace_waitpid(pid);
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    attach0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL 
Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_attach0__I(
  JNIEnv *env, jobject this_obj, jint jpid) 
{
JNF_COCOA_ENTER(env);
  if (getenv("JAVA_SAPROC_DEBUG") != NULL)
    debug = JNI_TRUE;
  else
    debug = JNI_FALSE;
  if (debug) printf("attach0 called for jpid=%d\n", (int)jpid);
  
  // get the task from the pid
  kern_return_t result;
  task_t gTask = 0;
  result = task_for_pid(mach_task_self(), jpid, &gTask);
  if (result != KERN_SUCCESS) {
    fprintf(stderr, "attach: task_for_pid(%d) failed (%d)\n", (int)jpid, result);
    THROW_NEW_DEBUGGER_EXCEPTION("Can't attach to the process");
  }
  putTask(env, this_obj, gTask);

  // use ptrace to stop the process
  // on os x, ptrace only needs to be called on the process, not the individual threads
  if (ptrace_attach(jpid) != true) {
    mach_port_deallocate(mach_task_self(), gTask);
    THROW_NEW_DEBUGGER_EXCEPTION("Can't attach to the process");
  }

  id symbolicator = nil;
  id jrsSymbolicator = objc_lookUpClass("JRSSymbolicator");
  if (jrsSymbolicator != nil) {
    id (*dynamicCall)(id, SEL, pid_t) = (id (*)(id, SEL, pid_t))&objc_msgSend;
    symbolicator = dynamicCall(jrsSymbolicator, @selector(symbolicatorForPid:), (pid_t)jpid);
  }
  if (symbolicator != nil) {
    CFRetain(symbolicator); // pin symbolicator while in java heap
  }

  putSymbolicator(env, this_obj, symbolicator);
  if (symbolicator == nil) {
    THROW_NEW_DEBUGGER_EXCEPTION("Can't attach symbolicator to the process");
  }

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    detach0
 * Signature: ()V
 */
JNIEXPORT void JNICALL 
Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_detach0(
  JNIEnv *env, jobject this_obj) 
{
JNF_COCOA_ENTER(env);
  if (debug) printf("detach0 called\n");

  task_t gTask = getTask(env, this_obj);

  // detach from the ptraced process causing it to resume execution
  int pid;
  kern_return_t k_res;
  k_res = pid_for_task(gTask, &pid);
  if (k_res != KERN_SUCCESS) {
    fprintf(stderr, "detach: pid_for_task(%d) failed (%d)\n", pid, k_res);
  }
  else {
    int res = ptrace(PT_DETACH, pid, 0, 0);
    if (res < 0) {
      fprintf(stderr, "detach: ptrace(PT_DETACH, %d) failed (%d)\n", pid, res);
    }
  }
  
  mach_port_deallocate(mach_task_self(), gTask);
  id symbolicator = getSymbolicator(env, this_obj);
  if (symbolicator != nil) {
    CFRelease(symbolicator);
  }
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_jvm_hotspot_asm_Disassembler
 * Method:    load_library
 * Signature: (Ljava/lang/String;)L
 */
JNIEXPORT jlong JNICALL
Java_sun_jvm_hotspot_asm_Disassembler_load_1library(
  JNIEnv * env, 
  jclass disclass,
  jstring jrepath_s,
  jstring libname_s) 
{
  uintptr_t func = 0;
  const char* error_message = NULL;
  const char* java_home;
  jboolean isCopy;
  uintptr_t *handle = NULL;

  const char * jrepath = (*env)->GetStringUTFChars(env, jrepath_s, &isCopy); // like $JAVA_HOME/jre/lib/sparc/
  const char * libname = (*env)->GetStringUTFChars(env, libname_s, &isCopy);
  char buffer[128];

  /* Load the hsdis library */
  void* hsdis_handle;
  hsdis_handle = dlopen(libname, RTLD_LAZY | RTLD_GLOBAL);
  if (hsdis_handle == NULL) {
    snprintf(buffer, sizeof(buffer), "%s%s", jrepath, libname);
    hsdis_handle = dlopen(buffer, RTLD_LAZY | RTLD_GLOBAL);
  }
  if (hsdis_handle != NULL) {
    func = (uintptr_t)dlsym(hsdis_handle, "decode_instructions_virtual");
  }
  if (func == 0) {
    error_message = dlerror();
    fprintf(stderr, "%s\n", error_message);
  }

  (*env)->ReleaseStringUTFChars(env, libname_s, libname);
  (*env)->ReleaseStringUTFChars(env, jrepath_s, jrepath);

  if (func == 0) {
    /* Couldn't find entry point.  error_message should contain some
     * platform dependent error message.
     */
    THROW_NEW_DEBUGGER_EXCEPTION_(error_message, (jlong)func);
  }
  return (jlong)func;
}

/* signature of decode_instructions_virtual from hsdis.h */
typedef void* (*decode_func)(uintptr_t start_va, uintptr_t end_va,
                             unsigned char* start, uintptr_t length,
                             void* (*event_callback)(void*, const char*, void*),
                             void* event_stream,
                             int (*printf_callback)(void*, const char*, ...),
                             void* printf_stream,
                             const char* options);

/* container for call back state when decoding instructions */
typedef struct {
  JNIEnv* env;
  jobject dis;
  jobject visitor;
  jmethodID handle_event;
  jmethodID raw_print;
  char buffer[4096];
} decode_env;


/* event callback binding to Disassembler.handleEvent */
static void* event_to_env(void* env_pv, const char* event, void* arg) {
  decode_env* denv = (decode_env*)env_pv;
  JNIEnv* env = denv->env;
  jstring event_string = (*env)->NewStringUTF(env, event);
  jlong result = (*env)->CallLongMethod(env, denv->dis, denv->handle_event, denv->visitor,
                                        event_string, (jlong) (uintptr_t)arg);
  /* ignore exceptions for now */
  CHECK_EXCEPTION_CLEAR_((void *)0);
  return (void*)(uintptr_t)result;
}

/* printing callback binding to Disassembler.rawPrint */
static int printf_to_env(void* env_pv, const char* format, ...) {
  jstring output;
  va_list ap;
  int cnt;
  decode_env* denv = (decode_env*)env_pv;
  JNIEnv* env = denv->env;
  size_t flen = strlen(format);
  const char* raw = NULL;

  if (flen == 0)  return 0;
  if (flen < 2 ||
      strchr(format, '%') == NULL) {
    raw = format;
  } else if (format[0] == '%' && format[1] == '%' &&
             strchr(format+2, '%') == NULL) {
    // happens a lot on machines with names like %foo
    flen--;
    raw = format+1;
  }
  if (raw != NULL) {
    jstring output = (*env)->NewStringUTF(env, raw);
    (*env)->CallVoidMethod(env, denv->dis, denv->raw_print, denv->visitor, output);
    CHECK_EXCEPTION_CLEAR;
    return (int) flen;
  }
  va_start(ap, format);
  cnt = vsnprintf(denv->buffer, sizeof(denv->buffer), format, ap);
  va_end(ap);

  output = (*env)->NewStringUTF(env, denv->buffer);
  (*env)->CallVoidMethod(env, denv->dis, denv->raw_print, denv->visitor, output);
  CHECK_EXCEPTION_CLEAR;
  return cnt;
}

/*
 * Class:     sun_jvm_hotspot_asm_Disassembler
 * Method:    decode
 * Signature: (Lsun/jvm/hotspot/asm/InstructionVisitor;J[BLjava/lang/String;J)V
 */
JNIEXPORT void JNICALL
Java_sun_jvm_hotspot_asm_Disassembler_decode(
   JNIEnv * env,
   jobject dis,
   jobject visitor,
   jlong startPc,
   jbyteArray code,
   jstring options_s,
   jlong decode_instructions_virtual) 
{
  jboolean isCopy;
  jbyte* start = (*env)->GetByteArrayElements(env, code, &isCopy);
  jbyte* end = start + (*env)->GetArrayLength(env, code);
  const char * options = (*env)->GetStringUTFChars(env, options_s, &isCopy);
  jclass disclass = (*env)->GetObjectClass(env, dis);

  decode_env denv;
  denv.env = env;
  denv.dis = dis;
  denv.visitor = visitor;

  /* find Disassembler.handleEvent callback */
  denv.handle_event = (*env)->GetMethodID(env, disclass, "handleEvent",
                                          "(Lsun/jvm/hotspot/asm/InstructionVisitor;Ljava/lang/String;J)J");
  CHECK_EXCEPTION_CLEAR_VOID

  /* find Disassembler.rawPrint callback */
  denv.raw_print = (*env)->GetMethodID(env, disclass, "rawPrint",
                                       "(Lsun/jvm/hotspot/asm/InstructionVisitor;Ljava/lang/String;)V");
  CHECK_EXCEPTION_CLEAR_VOID

  /* decode the buffer */
  (*(decode_func)(uintptr_t)decode_instructions_virtual)(startPc,
                                                         startPc + end - start,
                                                         (unsigned char*)start,
                                                         end - start,
                                                         &event_to_env,  (void*) &denv,
                                                         &printf_to_env, (void*) &denv,
                                                         options);

  /* cleanup */
  (*env)->ReleaseByteArrayElements(env, code, start, JNI_ABORT);
  (*env)->ReleaseStringUTFChars(env, options_s, options);
}
