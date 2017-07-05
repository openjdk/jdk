/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <vector>
#include "sa.hpp"
#include "jni.h"
#include "jvmdi.h"

#ifndef WIN32
 #include <inttypes.h>
#else
 typedef int int32_t;
#endif

#ifdef WIN32
 #include <windows.h>
 #define YIELD() Sleep(0)
 #define SLEEP() Sleep(10)
 #define vsnprintf _vsnprintf
#else
 Error: please port YIELD() and SLEEP() macros to your platform
#endif

using namespace std;

//////////////////////////////////////////////////////////////////////
//                                                                  //
// Exported "interface" for Java language-level interaction between //
// the SA and the VM. Note that the SA knows about the layout of    //
// certain VM data structures and that knowledge is taken advantage //
// of in this code, although this interfaces with the VM via JVMDI. //
//                                                                  //
//////////////////////////////////////////////////////////////////////

extern "C" {
  /////////////////////////////////////
  //                                 //
  // Events sent by the VM to the SA //
  //                                 //
  /////////////////////////////////////

  // Set by the SA when it attaches. Indicates that events should be
  // posted via these exported variables, and that the VM should wait
  // for those events to be acknowledged by the SA (via its setting
  // saEventPending to 0).
  JNIEXPORT volatile int32_t saAttached     = 0;

  // Set to nonzero value by the VM when an event has been posted; set
  // back to 0 by the SA when it has processed that event.
  JNIEXPORT volatile int32_t saEventPending = 0;

  // Kind of the event (from jvmdi.h)
  JNIEXPORT volatile int32_t saEventKind    = 0;

  //
  // Exception events
  //
  JNIEXPORT jthread   saExceptionThread;
  JNIEXPORT jclass    saExceptionClass;
  JNIEXPORT jmethodID saExceptionMethod;
  JNIEXPORT int32_t   saExceptionLocation;
  JNIEXPORT jobject   saExceptionException;
  JNIEXPORT jclass    saExceptionCatchClass;
  JNIEXPORT jmethodID saExceptionCatchMethod;
  JNIEXPORT int32_t   saExceptionCatchLocation;

  //
  // Breakpoint events
  //
  JNIEXPORT jthread   saBreakpointThread;
  JNIEXPORT jclass    saBreakpointClass;
  JNIEXPORT jmethodID saBreakpointMethod;
  JNIEXPORT jlocation saBreakpointLocation;

  ///////////////////////////////////////
  //                                   //
  // Commands sent by the SA to the VM //
  //                                   //
  ///////////////////////////////////////

  extern JNIEXPORT const int32_t SA_CMD_SUSPEND_ALL       = 0;
  extern JNIEXPORT const int32_t SA_CMD_RESUME_ALL        = 1;
  extern JNIEXPORT const int32_t SA_CMD_TOGGLE_BREAKPOINT = 2;
  extern JNIEXPORT const int32_t SA_CMD_BUF_SIZE          = 1024;

  // SA sets this to a nonzero value when it is requesting a command
  // to be processed; VM sets it back to 0 when the command has been
  // executed
  JNIEXPORT volatile int32_t saCmdPending   = 0;

  // SA sets this to one of the manifest constants above to indicate
  // the kind of command to be executed
  JNIEXPORT volatile int32_t saCmdType      = 0;

  // VM sets this to 0 if the last command succeeded or a nonzero
  // value if it failed
  JNIEXPORT volatile int32_t saCmdResult    = 0;

  // If last command failed, this buffer will contain a descriptive
  // error message
  JNIEXPORT char             saCmdResultErrMsg[SA_CMD_BUF_SIZE];

  //
  // Toggling of breakpoint command arguments.
  //
  // Originally there were separate set/clear breakpoint commands
  // taking a class name, method name and signature, and the iteration
  // through the debug information was done in the SA. It turns out
  // that doing this work in the target VM is significantly faster,
  // and since interactivity when setting and clearing breakpoints is
  // important, the solution which resulted in more C/C++ code was used.
  //

  // Source file name
  JNIEXPORT char    saCmdBkptSrcFileName[SA_CMD_BUF_SIZE];

  // Package name ('/' as separator instead of '.')
  JNIEXPORT char    saCmdBkptPkgName[SA_CMD_BUF_SIZE];

  // Line number
  JNIEXPORT int32_t saCmdBkptLineNumber;

  // Output back to SA: indicator whether the last failure of a
  // breakpoint toggle command was really an error or just a lack of
  // debug information covering the requested line. 0 if not error.
  // Valid only if saCmdResult != 0.
  JNIEXPORT int32_t saCmdBkptResWasError;

  // Output back to SA: resulting line number at which the breakpoint
  // was set or cleared (valid only if saCmdResult == 0)
  JNIEXPORT int32_t saCmdBkptResLineNumber;

  // Output back to SA: resulting byte code index at which the
  // breakpoint was set or cleared (valid only if saCmdResult == 0)
  JNIEXPORT int32_t saCmdBkptResBCI;

  // Output back to SA: indicator whether the breakpoint operation
  // resulted in a set or cleared breakpoint; nonzero if set, zero if
  // cleared (valid only if saCmdResult == 0)
  JNIEXPORT int32_t saCmdBkptResWasSet;

  // Output back to SA: method name the breakpoint was set in (valid
  // only if saCmdResult == 0)
  JNIEXPORT char    saCmdBkptResMethodName[SA_CMD_BUF_SIZE];

  // Output back to SA: method signature (JNI style) the breakpoint
  // was set in (valid only if saCmdResult == 0)
  JNIEXPORT char    saCmdBkptResMethodSig[SA_CMD_BUF_SIZE];
}

// Internal state
static JavaVM* jvm = NULL;
static JVMDI_Interface_1* jvmdi = NULL;
static jthread debugThreadObj = NULL;
static bool suspended = false;
static vector<jthread> suspendedThreads;
static JVMDI_RawMonitor eventLock = NULL;

class MonitorLocker {
private:
  JVMDI_RawMonitor lock;
public:
  MonitorLocker(JVMDI_RawMonitor lock) {
    this->lock = lock;
    if (lock != NULL) {
      jvmdi->RawMonitorEnter(lock);
    }
  }
  ~MonitorLocker() {
    if (lock != NULL) {
      jvmdi->RawMonitorExit(lock);
    }
  }
};

class JvmdiDeallocator {
private:
  void* ptr;
public:
  JvmdiDeallocator(void* ptr) {
    this->ptr = ptr;
  }
  ~JvmdiDeallocator() {
    jvmdi->Deallocate((jbyte*) ptr);
  }
};

class JvmdiRefListDeallocator {
private:
  JNIEnv* env;
  jobject* refList;
  jint refCount;
public:
  JvmdiRefListDeallocator(JNIEnv* env, jobject* refList, jint refCount) {
    this->env = env;
    this->refList = refList;
    this->refCount = refCount;
  }
  ~JvmdiRefListDeallocator() {
    for (int i = 0; i < refCount; i++) {
      env->DeleteGlobalRef(refList[i]);
    }
    jvmdi->Deallocate((jbyte*) refList);
  }
};

static void
stop(char* msg) {
  fprintf(stderr, "%s", msg);
  fprintf(stderr, "\n");
  exit(1);
}

// This fills in the command result error message, sets the command
// result to -1, and clears the pending command flag
static void
reportErrorToSA(const char* str, ...) {
  va_list varargs;
  va_start(varargs, str);
  vsnprintf(saCmdResultErrMsg, sizeof(saCmdResultErrMsg), str, varargs);
  va_end(varargs);
  saCmdResult = -1;
  saCmdPending = 0;
}

static bool
packageNameMatches(char* clazzName, char* pkg) {
  int pkgLen = strlen(pkg);
  int clazzNameLen = strlen(clazzName);

  if (pkgLen >= clazzNameLen + 1) {
    return false;
  }

  if (strncmp(clazzName, pkg, pkgLen)) {
    return false;
  }

  // Ensure that '/' is the next character if non-empty package name
  int l = pkgLen;
  if (l > 0) {
    if (clazzName[l] != '/') {
      return false;
    }
    l++;
  }
  // Ensure that there are no more trailing slashes
  while (l < clazzNameLen) {
    if (clazzName[l++] == '/') {
      return false;
    }
  }
  return true;
}

static void
executeOneCommand(JNIEnv* env) {
  switch (saCmdType) {
  case SA_CMD_SUSPEND_ALL: {
    if (suspended) {
      reportErrorToSA("Target process already suspended");
      return;
    }

    // We implement this by getting all of the threads and calling
    // SuspendThread on each one, except for the thread object
    // corresponding to this thread. Each thread for which the call
    // succeeded (i.e., did not return JVMDI_ERROR_INVALID_THREAD)
    // is added to a list which is remembered for later resumption.
    // Note that this currently has race conditions since a thread
    // might be started after we call GetAllThreads and since a
    // thread for which we got an error earlier might be resumed by
    // the VM while we are busy suspending other threads. We could
    // solve this by looping until there are no more threads we can
    // suspend, but a more robust and scalable solution is to add
    // this functionality to the JVMDI interface (i.e.,
    // "suspendAll"). Probably need to provide an exclude list for
    // such a routine.
    jint threadCount;
    jthread* threads;
    if (jvmdi->GetAllThreads(&threadCount, &threads) != JVMDI_ERROR_NONE) {
      reportErrorToSA("Error while getting thread list");
      return;
    }


    for (int i = 0; i < threadCount; i++) {
      jthread thr = threads[i];
      if (!env->IsSameObject(thr, debugThreadObj)) {
        jvmdiError err = jvmdi->SuspendThread(thr);
        if (err == JVMDI_ERROR_NONE) {
          // Remember this thread and do not free it
          suspendedThreads.push_back(thr);
          continue;
        } else {
          fprintf(stderr, " SA: Error %d while suspending thread\n", err);
          // FIXME: stop, resume all threads, report error
        }
      }
      env->DeleteGlobalRef(thr);
    }

    // Free up threads
    jvmdi->Deallocate((jbyte*) threads);

    // Suspension is complete
    suspended = true;
    break;
  }

  case SA_CMD_RESUME_ALL: {
    if (!suspended) {
      reportErrorToSA("Target process already suspended");
      return;
    }

    saCmdResult = 0;
    bool errorOccurred = false;
    jvmdiError firstError;
    for (int i = 0; i < suspendedThreads.size(); i++) {
      jthread thr = suspendedThreads[i];
      jvmdiError err = jvmdi->ResumeThread(thr);
      env->DeleteGlobalRef(thr);
      if (err != JVMDI_ERROR_NONE) {
        if (!errorOccurred) {
          errorOccurred = true;
          firstError = err;
        }
      }
    }
    suspendedThreads.clear();
    suspended = false;
    if (errorOccurred) {
      reportErrorToSA("Error %d while resuming threads", firstError);
      return;
    }
    break;
  }

  case SA_CMD_TOGGLE_BREAKPOINT: {
    saCmdBkptResWasError = 1;

    // Search line number info for all loaded classes
    jint classCount;
    jclass* classes;

    jvmdiError glcRes = jvmdi->GetLoadedClasses(&classCount, &classes);
    if (glcRes != JVMDI_ERROR_NONE) {
      reportErrorToSA("Error %d while getting loaded classes", glcRes);
      return;
    }
    JvmdiRefListDeallocator rld(env, (jobject*) classes, classCount);

    bool done = false;
    bool gotOne = false;
    jclass targetClass;
    jmethodID targetMethod;
    jlocation targetLocation;
    jint targetLineNumber;

    for (int i = 0; i < classCount && !done; i++) {
      fflush(stderr);
      jclass clazz = classes[i];
      char* srcName;
      jvmdiError sfnRes = jvmdi->GetSourceFileName(clazz, &srcName);
      if (sfnRes == JVMDI_ERROR_NONE) {
        JvmdiDeallocator de1(srcName);
        if (!strcmp(srcName, saCmdBkptSrcFileName)) {
          // Got a match. Now see whether the package name of the class also matches
          char* clazzName;
          jvmdiError sigRes = jvmdi->GetClassSignature(clazz, &clazzName);
          if (sigRes != JVMDI_ERROR_NONE) {
            reportErrorToSA("Error %d while getting a class's signature", sigRes);
            return;
          }
          JvmdiDeallocator de2(clazzName);
          if (packageNameMatches(clazzName + 1, saCmdBkptPkgName)) {
            // Iterate through all methods
            jint methodCount;
            jmethodID* methods;
            if (jvmdi->GetClassMethods(clazz, &methodCount, &methods) != JVMDI_ERROR_NONE) {
              reportErrorToSA("Error while getting methods of class %s", clazzName);
              return;
            }
            JvmdiDeallocator de3(methods);
            for (int j = 0; j < methodCount && !done; j++) {
              jmethodID m = methods[j];
              jint entryCount;
              JVMDI_line_number_entry* table;
              jvmdiError lnRes = jvmdi->GetLineNumberTable(clazz, m, &entryCount, &table);
              if (lnRes == JVMDI_ERROR_NONE) {
                JvmdiDeallocator de4(table);
                // Look for line number greater than or equal to requested line
                for (int k = 0; k < entryCount && !done; k++) {
                  JVMDI_line_number_entry& entry = table[k];
                  if (entry.line_number >= saCmdBkptLineNumber &&
                      (!gotOne || entry.line_number < targetLineNumber)) {
                    gotOne = true;
                    targetClass = clazz;
                    targetMethod = m;
                    targetLocation = entry.start_location;
                    targetLineNumber = entry.line_number;
                    done = (targetLineNumber == saCmdBkptLineNumber);
                  }
                }
              } else if (lnRes != JVMDI_ERROR_ABSENT_INFORMATION) {
                reportErrorToSA("Unexpected error %d while fetching line number table", lnRes);
                return;
              }
            }
          }
        }
      } else if (sfnRes != JVMDI_ERROR_ABSENT_INFORMATION) {
        reportErrorToSA("Unexpected error %d while fetching source file name", sfnRes);
        return;
      }
    }

    bool wasSet = true;
    if (gotOne) {
      // Really toggle this breakpoint
      jvmdiError bpRes;
      bpRes = jvmdi->SetBreakpoint(targetClass, targetMethod, targetLocation);
      if (bpRes == JVMDI_ERROR_DUPLICATE) {
        bpRes = jvmdi->ClearBreakpoint(targetClass, targetMethod, targetLocation);
        wasSet = false;
      }
      if (bpRes != JVMDI_ERROR_NONE) {
        reportErrorToSA("Unexpected error %d while setting or clearing breakpoint at bci %d, line %d",
                        bpRes, targetLocation, targetLineNumber);
        return;
      }
    } else {
      saCmdBkptResWasError = 0;
      reportErrorToSA("No debug information found covering this line");
      return;
    }

    // Provide result
    saCmdBkptResLineNumber = targetLineNumber;
    saCmdBkptResBCI        = targetLocation;
    saCmdBkptResWasSet     = (wasSet ? 1 : 0);
    {
      char* methodName;
      char* methodSig;
      if (jvmdi->GetMethodName(targetClass, targetMethod, &methodName, &methodSig)
          == JVMDI_ERROR_NONE) {
        JvmdiDeallocator mnd(methodName);
        JvmdiDeallocator msd(methodSig);
        strncpy(saCmdBkptResMethodName, methodName, SA_CMD_BUF_SIZE);
        strncpy(saCmdBkptResMethodSig,  methodSig, SA_CMD_BUF_SIZE);
      } else {
        strncpy(saCmdBkptResMethodName, "<error>", SA_CMD_BUF_SIZE);
        strncpy(saCmdBkptResMethodSig,  "<error>", SA_CMD_BUF_SIZE);
      }
    }
    break;
  }

  default:
    reportErrorToSA("Command %d not yet supported", saCmdType);
    return;
  }

  // Successful command execution
  saCmdResult = 0;
  saCmdPending = 0;
}

static void
saCommandThread(void *arg) {
  JNIEnv* env = NULL;
  if (jvm->GetEnv((void **) &env, JNI_VERSION_1_2) != JNI_OK) {
    stop("Error while starting Serviceability Agent "
         "command thread: could not get JNI environment");
  }

  while (1) {
    // Wait for command
    while (!saCmdPending) {
      SLEEP();
    }

    executeOneCommand(env);
  }
}

static void
saEventHook(JNIEnv *env, JVMDI_Event *event)
{
  MonitorLocker ml(eventLock);

  saEventKind = event->kind;

  if (event->kind == JVMDI_EVENT_VM_INIT) {
    // Create event lock
    if (jvmdi->CreateRawMonitor("Serviceability Agent Event Lock", &eventLock)
        != JVMDI_ERROR_NONE) {
      stop("Unable to create Serviceability Agent's event lock");
    }
    // Start thread which receives commands from the SA.
    jclass threadClass = env->FindClass("java/lang/Thread");
    if (threadClass == NULL) stop("Unable to find class java/lang/Thread");
    jstring threadName = env->NewStringUTF("Serviceability Agent Command Thread");
    if (threadName == NULL) stop("Unable to allocate debug thread name");
    jmethodID ctor = env->GetMethodID(threadClass, "<init>", "(Ljava/lang/String;)V");
    if (ctor == NULL) stop("Unable to find appropriate constructor for java/lang/Thread");
    // Allocate thread object
    jthread thr = (jthread) env->NewObject(threadClass, ctor, threadName);
    if (thr == NULL) stop("Unable to allocate debug thread's java/lang/Thread instance");
    // Remember which thread this is
    debugThreadObj = env->NewGlobalRef(thr);
    if (debugThreadObj == NULL) stop("Unable to allocate global ref for debug thread object");
    // Start thread
    jvmdiError err;
    if ((err = jvmdi->RunDebugThread(thr, &saCommandThread, NULL, JVMDI_THREAD_NORM_PRIORITY))
        != JVMDI_ERROR_NONE) {
      char buf[256];
      sprintf(buf, "Error %d while starting debug thread", err);
      stop(buf);
    }
    // OK, initialization is done
    return;
  }

  if (!saAttached) {
    return;
  }

  switch (event->kind) {
  case JVMDI_EVENT_EXCEPTION: {
    fprintf(stderr, "SA: Exception thrown -- ignoring\n");
    saExceptionThread        = event->u.exception.thread;
    saExceptionClass         = event->u.exception.clazz;
    saExceptionMethod        = event->u.exception.method;
    saExceptionLocation      = event->u.exception.location;
    saExceptionException     = event->u.exception.exception;
    saExceptionCatchClass    = event->u.exception.catch_clazz;
    saExceptionCatchClass    = event->u.exception.catch_clazz;
    saExceptionCatchMethod   = event->u.exception.catch_method;
    saExceptionCatchLocation = event->u.exception.catch_location;
    //    saEventPending = 1;
    break;
  }

  case JVMDI_EVENT_BREAKPOINT: {
    saBreakpointThread       = event->u.breakpoint.thread;
    saBreakpointClass        = event->u.breakpoint.clazz;
    saBreakpointMethod       = event->u.breakpoint.method;
    saBreakpointLocation     = event->u.breakpoint.location;
    saEventPending = 1;
    break;
  }

  default:
    break;
  }

  while (saAttached && saEventPending) {
    SLEEP();
  }
}

extern "C" {
JNIEXPORT jint JNICALL
JVM_OnLoad(JavaVM *vm, char *options, void *reserved)
{
  jvm = vm;
  if (jvm->GetEnv((void**) &jvmdi, JVMDI_VERSION_1) != JNI_OK) {
    return -1;
  }
  if (jvmdi->SetEventHook(&saEventHook) != JVMDI_ERROR_NONE) {
    return -1;
  }
  return 0;
}
};
