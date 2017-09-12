/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

#define _POSIX_PTHREAD_SEMANTICS // to enable POSIX semantics for certain common APIs

#include <jni.h>
#include <dlfcn.h>
#include <limits.h>
#include <pthread.h>
#include <signal.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

void *handle;
char *error;
char path[PATH_MAX];

jint(JNICALL *jni_create_java_vm)(JavaVM **, JNIEnv **, void *) = NULL;

JavaVM *jvm;

// method to perform dlclose on an open dynamic library handle
void closeHandle() {
  dlclose(handle);
  if ((error = dlerror()) != NULL) {
    fputs("Error occurred while closing handle\n", stderr);
  }
}

// method to exit with a fail status
void fail() {
  if (handle) {
    closeHandle();
  }
  exit(1);
}

// method to handle occurred error and fail
void handleError(char *messageTitle, char *messageBody) {
  fprintf(stderr, "%s: %s\n", messageTitle, messageBody);
  fail();
}

// method to load the dynamic library libjvm
void loadJVM() {
  char lib[PATH_MAX];
  snprintf(lib, sizeof (lib), "%s/lib/server/libjvm.so", path);
  handle = dlopen(lib, RTLD_LAZY);
  if (!handle) {
    handleError(dlerror(), "2");
  }
  fputs("Will load JVM...\n", stdout);

  // find the address of function
  *(void **) (&jni_create_java_vm) = dlsym(handle, "JNI_CreateJavaVM");
  if ((error = dlerror()) != NULL) {
    handleError(error, "3");
  }

  fputs("JVM loaded okay.\n", stdout);
}

// method to get created jvm environment
JNIEnv* initJVM() {
  JNIEnv *env = NULL;
  JavaVMInitArgs vm_args;
  JavaVMOption options[1];
  jint res;

  options[0].optionString = "-Xrs";

  vm_args.version = JNI_VERSION_1_2;
  vm_args.nOptions = 1;
  vm_args.options = options;
  vm_args.ignoreUnrecognized = JNI_FALSE;

  fputs("Will create JVM...\n", stdout);

  res = (*jni_create_java_vm)(&jvm, &env, &vm_args);
  if (res < 0) {
    handleError("Can't create Java VM", strerror(res));
  }

  fputs("JVM created OK!\n", stdout);
  return env;
}

// method to invoke java method from java class
void callJava(JNIEnv *env) {
  jclass cls;
  jmethodID mid;
  jstring jstr;
  jobjectArray args;

  cls = (*env)->FindClass(env, "Prog");
  if (cls == 0) {
    handleError("FindClass", "Can't find Prog class");
  }

  mid = (*env)->GetStaticMethodID(env, cls, "main", "([Ljava/lang/String;)V");
  if (mid == 0) {
    handleError("GetStaticMethodID", "Can't find Prog.main");
  }

  jstr = (*env)->NewStringUTF(env, "from C!");
  if (jstr == 0) {
    handleError("NewStringUTF", "Out of memory");
  }
  args = (*env)->NewObjectArray(env, 1,
          (*env)->FindClass(env, "java/lang/String"), jstr);
  if (args == 0) {
    handleError("NewObjectArray", "Out of memory");
  }
  (*env)->CallStaticVoidMethod(env, cls, mid, args);

}

// method to load, init jvm and then invoke java method
void* loadAndCallJava(void* x) {
  JNIEnv *env;

  fputs("Some thread will create JVM.\n", stdout);
  loadJVM();
  env = initJVM();

  fputs("Some thread will call Java.\n", stdout);

  callJava(env);

  if ((*jvm)->DetachCurrentThread(jvm) != 0)
    fputs("Error: thread not detached!\n", stderr);
  fputs("Some thread exiting.\n", stdout);
  return env;
}

int main(int argc, char **argv) {
  JNIEnv *env;
  sigset_t set;
  pthread_t thr1;
  pthread_attr_t attr;
  size_t ss = 0;
  int sig;
  int rc; // return code for pthread_* methods

  // verify input
  if (argc != 2) {
    handleError("usage", "a.out jdk_path");
  }
  // copy input jdk path into a char buffer
  strncpy(path, argv[1], PATH_MAX);
  // add null termination character
  path[PATH_MAX - 1] = '\0';

  fputs("Main thread will set signal mask.\n", stdout);

  // initialize the signal set
  sigemptyset(&set);
  // add a number of signals to a signal set
  sigaddset(&set, SIGPIPE);
  sigaddset(&set, SIGTERM);
  sigaddset(&set, SIGHUP);
  sigaddset(&set, SIGINT);

  // examine and change mask of blocked signal
  if ((rc = pthread_sigmask(SIG_BLOCK, &set, NULL))) {
    // handle error if occurred
    handleError("main: pthread_sigmask() error", strerror(rc));
  }

  // initializes the thread attributes object with default attribute values
  if ((rc = pthread_attr_init(&attr))) {
    // handle error if occurred
    handleError("main: pthread_attr_init() error", strerror(rc));
  }

  ss = 1024 * 1024;
  // set the stack size attribute of the thread attributes object
  if ((rc = pthread_attr_setstacksize(&attr, ss))) {
    // handle error if occurred
    handleError("main: pthread_attr_setstacksize() error", strerror(rc));
  }
  // get the stack size attribute of the thread attributes object
  if ((rc = pthread_attr_getstacksize(&attr, &ss))) {
    // handle error if occurred
    handleError("main: pthread_attr_getstacksize() error", strerror(rc));
  }
  fprintf(stderr, "Stack size: %zu\n", ss);

  // start a new thread in the calling process,
  // loadAndCallJava logic is passed as a start_routine argument
  if ((rc = pthread_create(&thr1, NULL, loadAndCallJava, NULL))) {
    // handle error if occurred
    handleError("main: pthread_create() error", strerror(rc));
  }

  // initialize the signal set
  sigemptyset(&set);
  // add a number of signals to a signal set
  sigaddset(&set, SIGTERM);
  sigaddset(&set, SIGHUP);
  sigaddset(&set, SIGINT);

  fputs("Main thread waiting for signal.\n", stdout);

  do {
    int err;

    sig = 0;
    err = sigwait(&set, &sig);
    if (err != 0) {
      // print error message if unexpected signal occurred
      fprintf(stderr, "main: sigwait() error:  %s\n", strerror(err));
    } else {
      // print success message and exit if expected signal occurred
      // this branch generally acts when JVM executes destroy()
      fprintf(stdout, "main: sigwait() got:  %d\nSucceed!\n", sig);
      exit(0);
    }
  } while (sig != SIGTERM && sig != SIGINT); // exit the loop condition

  // join with a terminated thread
  if ((rc = pthread_join(thr1, NULL))) {
    // handle error if occurred
    handleError("main: pthread_join() error", strerror(rc));
  }

  // close an open dynamic library handle
  closeHandle();
  fputs("Main thread exiting.\n", stdout);
  return 0;
}
