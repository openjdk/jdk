/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

/* This code tests the fact that we actually remove stack guard page when calling
 * JavaThread::exit() i.e. when detaching from current thread.
 * We overflow the stack and check that we get access error because of a guard page.
 * Than we detach from vm thread and overflow stack once again. This time we shouldn't
 * get access error because stack guard page is removed
 *
 * Notice: due a complicated interaction of signal handlers, the test may crash.
 * It's OK - don't file a bug.
 */

#include <assert.h>
#include <jni.h>
#include <jvm.h>
#ifndef _BSDONLY_SOURCE
#include <alloca.h>
#endif
#include <signal.h>
#include <string.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <sys/ucontext.h>
#include <setjmp.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <errno.h>

#include <pthread.h>
#ifdef __FreeBSD__
#include <pthread_np.h>
#endif

#define CLASS_PATH_OPT "-Djava.class.path="

JavaVM* _jvm;

static jmp_buf  context;

static volatile int _last_si_code = -1;
static volatile int _failures = 0;
static volatile int _rec_count = 0; // Number of allocations to hit stack guard page
static volatile int _kp_rec_count = 0; // Kept record of rec_count, for retrying
static int _peek_value = 0; // Used for accessing memory to cause SIGSEGV

#ifdef __FreeBSD__
int gettid(void) {
  return pthread_getthreadid_np();
}
int is_main_thread(void) {
  return pthread_main_np();
}
#else
pid_t gettid() {
  return (pid_t) syscall(SYS_gettid);
}
int is_main_thread(void) {
  return gettid() == getpid();
}
#endif

static void handler(int sig, siginfo_t *si, void *unused) {
  _last_si_code = si->si_code;
  printf("Got SIGSEGV(%d) at address: 0x%lx\n",si->si_code, (long) si->si_addr);
  longjmp(context, 1);
}

static char* altstack = NULL;

void set_signal_handler() {
  if (altstack == NULL) {
    // Dynamically allocated in case SIGSTKSZ is not constant
    altstack = (char*)malloc(SIGSTKSZ);
    if (altstack == NULL) {
      fprintf(stderr, "Test ERROR. Unable to malloc altstack space\n");
      exit(7);
    }
  }

  stack_t ss = {
    .ss_size = SIGSTKSZ,
    .ss_flags = 0,
    .ss_sp = altstack
  };

  struct sigaction sa = {
    .sa_sigaction = handler,
    .sa_flags = SA_ONSTACK | SA_SIGINFO | SA_RESETHAND
  };

  _last_si_code = -1;

  sigaltstack(&ss, 0);
  sigemptyset(&sa.sa_mask);
  if (sigaction(SIGSEGV, &sa, NULL) == -1) {
    fprintf(stderr, "Test ERROR. Can't set sigaction (%d)\n", errno);
    exit(7);
  }
}

size_t get_java_stacksize () {
  pthread_attr_t attr;
  JDK1_1InitArgs jdk_args;

  memset(&jdk_args, 0, (sizeof jdk_args));

  jdk_args.version = JNI_VERSION_1_1;
  JNI_GetDefaultJavaVMInitArgs(&jdk_args);
  if (jdk_args.javaStackSize <= 0) {
    fprintf(stderr, "Test ERROR. Can't get a valid value for the default stacksize.\n");
    exit(7);
  }
  return jdk_args.javaStackSize;
}

// Call DoOverflow::`method` on JVM
void call_method_on_jvm(const char* method) {
  JNIEnv *env;
  jclass class_id;
  jmethodID method_id;
  int res;

  res = (*_jvm)->AttachCurrentThread(_jvm, (void **)&env, NULL);
  if (res != JNI_OK) {
    fprintf(stderr, "Test ERROR. Can't attach to current thread\n");
    exit(7);
  }

  class_id = (*env)->FindClass(env, "DoOverflow");
  if (class_id == NULL) {
    fprintf(stderr, "Test ERROR. Can't load class DoOverflow\n");
    exit(7);
  }

  method_id = (*env)->GetStaticMethodID(env, class_id, method, "()V");
  if (method_id == NULL) {
    fprintf(stderr, "Test ERROR. Can't find method DoOverflow.%s\n", method);
    exit(7);
  }

  (*env)->CallStaticVoidMethod(env, class_id, method_id, NULL);
}

void *run_java_overflow (void *p) {
  volatile int res;
  call_method_on_jvm("printIt");

  res = (*_jvm)->DetachCurrentThread(_jvm);
  if (res != JNI_OK) {
    fprintf(stderr, "Test ERROR. Can't call detach from current thread\n");
    exit(7);
  }
  return NULL;
}

void do_overflow(){
  volatile int *p = NULL;
  if (_kp_rec_count == 0 || _rec_count < _kp_rec_count) {
    for(;;) {
      _rec_count++;
      p = (int*)alloca(128);
      _peek_value = p[0]; // Peek
    }
  }
}

void *run_native_overflow(void *p) {
  // Test that stack guard page is correctly set for initial and non initial thread
  // and correctly removed for the initial thread
  volatile int res;
  printf("run_native_overflow %ld\n", (long) gettid());
  call_method_on_jvm("printAlive");

  // Initialize statics used in do_overflow
  _kp_rec_count = 0;
  _rec_count = 0;

  set_signal_handler();
  if (! setjmp(context)) {
    do_overflow();
  }

  if (_last_si_code == SEGV_ACCERR) {
    printf("Test PASSED. Got access violation accessing guard page at %d\n", _rec_count);
  }

  res = (*_jvm)->DetachCurrentThread(_jvm);
  if (res != JNI_OK) {
    fprintf(stderr, "Test ERROR. Can't call detach from current thread\n");
    exit(7);
  }

  if (!is_main_thread()) {
    // For non-initial thread we don't unmap the region but call os::uncommit_memory and keep PROT_NONE
    // so if host has enough swap space we will get the same SEGV with code SEGV_ACCERR(2) trying
    // to access it as if the guard page is present.
    // We have no way to check this, so bail out, marking test as succeeded
    printf("Test PASSED. Not initial thread\n");
    return NULL;
  }

  // Limit depth of recursion for second run. It can't exceed one for first run.
  _kp_rec_count = _rec_count;
  _rec_count = 0;

  set_signal_handler();
  if (! setjmp(context)) {
    do_overflow();
  }

  if (_last_si_code == SEGV_ACCERR) {
      ++ _failures;
      fprintf(stderr,"Test FAILED. Stack guard page is still there at %d\n", _rec_count);
  } else if (_last_si_code == -1) {
      printf("Test PASSED. No stack guard page is present. Maximum recursion level reached at %d\n", _rec_count);
  }
  else{
      printf("Test PASSED. No stack guard page is present. SIGSEGV(%d) at %d\n", _last_si_code, _rec_count);
  }

  return NULL;
}

void usage() {
  fprintf(stderr, "Usage: invoke test_java_overflow\n");
  fprintf(stderr, "       invoke test_java_overflow_initial\n");
  fprintf(stderr, "       invoke test_native_overflow\n");
  fprintf(stderr, "       invoke test_native_overflow_initial\n");
}

void init_thread_or_die(pthread_t *thr, pthread_attr_t *thread_attr) {
  size_t stack_size = get_java_stacksize();
  if (pthread_attr_init(thread_attr) != 0 ||
      pthread_attr_setstacksize(thread_attr, stack_size) != 0) {
    printf("Failed to set stacksize. Exiting test.\n");
    exit(0);
  }
}


int main (int argc, const char** argv) {
  JavaVMInitArgs vm_args;
  JavaVMOption options[3];
  JNIEnv* env;
  int optlen;
  char *javaclasspath = NULL;
  char javaclasspathopt[4096];

  printf("Test started with pid: %ld\n", (long) getpid());

  /* set the java class path so the DoOverflow class can be found */
  javaclasspath = getenv("CLASSPATH");

  if (javaclasspath == NULL) {
    fprintf(stderr, "Test ERROR. CLASSPATH is not set\n");
    exit(7);
  }
  optlen = strlen(CLASS_PATH_OPT) + strlen(javaclasspath) + 1;
  if (optlen > 4096) {
    fprintf(stderr, "Test ERROR. CLASSPATH is too long\n");
    exit(7);
  }
  snprintf(javaclasspathopt, sizeof(javaclasspathopt), "%s%s",
      CLASS_PATH_OPT, javaclasspath);

  options[0].optionString = "-Xint";
  options[1].optionString = "-Xss1M";
  options[2].optionString = javaclasspathopt;

  vm_args.version = JNI_VERSION_1_2;
  vm_args.ignoreUnrecognized = JNI_TRUE;
  vm_args.options = options;
  vm_args.nOptions = 3;

  if (JNI_CreateJavaVM (&_jvm, (void **)&env, &vm_args) < 0 ) {
    fprintf(stderr, "Test ERROR. Can't create JavaVM\n");
    exit(7);
  }

  pthread_t thr;
  pthread_attr_t thread_attr;

  if (argc < 2) {
    fprintf(stderr, "No test selected");
    usage();
    exit(7);
  }

  if (strcmp(argv[1], "test_java_overflow_initial") == 0) {
    printf("\nTesting JAVA_OVERFLOW\n");
    printf("Testing stack guard page behaviour for initial thread\n");

    run_java_overflow(NULL);
    // This test crash on error
    exit(0);
  }

  if (strcmp(argv[1], "test_java_overflow") == 0) {
    init_thread_or_die(&thr, &thread_attr);
    printf("\nTesting JAVA_OVERFLOW\n");
    printf("Testing stack guard page behaviour for other thread\n");

    pthread_create(&thr, &thread_attr, run_java_overflow, NULL);
    pthread_join(thr, NULL);

    // This test crash on error
    exit(0);
  }

  if (strcmp(argv[1], "test_native_overflow_initial") == 0) {
    printf("\nTesting NATIVE_OVERFLOW\n");
    printf("Testing stack guard page behaviour for initial thread\n");

    run_native_overflow(NULL);

    exit((_failures > 0) ? 1 : 0);
  }

  if (strcmp(argv[1], "test_native_overflow") == 0) {
    init_thread_or_die(&thr, &thread_attr);
    printf("\nTesting NATIVE_OVERFLOW\n");
    printf("Testing stack guard page behaviour for other thread\n");

    pthread_create(&thr, &thread_attr, run_native_overflow, NULL);
    pthread_join(thr, NULL);

    exit((_failures > 0) ? 1 : 0);
  }

  fprintf(stderr, "Test ERROR. Unknown parameter %s\n", ((argc > 1) ? argv[1] : "none"));
  usage();
  exit(7);
}
