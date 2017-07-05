/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include <stdlib.h>
#ifdef __APPLE__
#  include <dlfcn.h>
#endif

#include "prims/jni.h"
#include "unittest.hpp"

extern "C" {

static int init_jvm(int argc, char **argv, bool is_executing_death_test) {
  // don't care about the program name
  argc--;
  argv++;

  int extra_jvm_args = is_executing_death_test ? 4 : 2;
  int num_jvm_options = argc + extra_jvm_args;

  JavaVMOption* options = new JavaVMOption[num_jvm_options];
  options[0].optionString = (char*) "-Dsun.java.launcher.is_altjvm=true";
  options[1].optionString = (char*) "-XX:+ExecutingUnitTests";

  if (is_executing_death_test) {
    // don't create core files or hs_err files when executing death tests
    options[2].optionString = (char*) "-XX:+SuppressFatalErrorMessage";
    options[3].optionString = (char*) "-XX:-CreateCoredumpOnCrash";
  }

  for (int i = 0; i < argc; i++) {
    options[extra_jvm_args + i].optionString = argv[i];
  }

  JavaVMInitArgs args;
  args.version = JNI_VERSION_1_8;
  args.nOptions = num_jvm_options;
  args.options = options;

  JavaVM* jvm;
  JNIEnv* env;

  return JNI_CreateJavaVM(&jvm, (void**)&env, &args);
}

class JVMInitializerListener : public ::testing::EmptyTestEventListener {
 private:
  int _argc;
  char** _argv;
  bool _is_initialized;

  void initialize_jvm() {
  }

 public:
  JVMInitializerListener(int argc, char** argv) :
    _argc(argc), _argv(argv), _is_initialized(false) {
  }

  virtual void OnTestStart(const ::testing::TestInfo& test_info) {
    const char* name = test_info.name();
    if (strstr(name, "_test_vm") != NULL && !_is_initialized) {
      ASSERT_EQ(init_jvm(_argc, _argv, false), 0) << "Could not initialize the JVM";
      _is_initialized = true;
    }
  }
};

static bool is_prefix(const char* prefix, const char* str) {
  return strncmp(str, prefix, strlen(prefix)) == 0;
}

static char* get_java_home_arg(int argc, char** argv) {
  for (int i = 0; i < argc; i++) {
    if (strncmp(argv[i], "-jdk", strlen(argv[i])) == 0) {
      return argv[i+1];
    }
    if (is_prefix("--jdk=", argv[i])) {
      return argv[i] + strlen("--jdk=");
    }
    if (is_prefix("-jdk:", argv[i])) {
      return argv[i] + strlen("-jdk:");
    }
  }
  return NULL;
}

static int num_args_to_skip(char* arg) {
  if (strcmp(arg, "-jdk") == 0) {
    return 2; // skip the argument after -jdk as well
  }
  if (is_prefix("--jdk=", arg)) {
    return 1;
  }
  if (is_prefix("-jdk:", arg)) {
    return 1;
  }
  return 0;
}

static char** remove_test_runner_arguments(int* argcp, char **argv) {
  int argc = *argcp;
  char** new_argv = (char**) malloc(sizeof(char*) * argc);
  int new_argc = 0;

  int i = 0;
  while (i < argc) {
    int args_to_skip = num_args_to_skip(argv[i]);
    if (args_to_skip == 0) {
      new_argv[new_argc] = argv[i];
      i++;
      new_argc++;
    } else {
      i += num_args_to_skip(argv[i]);
    }
  }

  *argcp = new_argc;
  return new_argv;
}

JNIEXPORT void JNICALL runUnitTests(int argc, char** argv) {
  // Must look at googletest options before initializing googletest, since
  // InitGoogleTest removes googletest options from argv.
  bool is_executing_death_test = true;
  for (int i = 0; i < argc; i++) {
    const char* death_test_flag = "--gtest_internal_run_death_test";
    if (is_prefix(death_test_flag, argv[i])) {
      is_executing_death_test = true;
    }
  }

  ::testing::InitGoogleTest(&argc, argv);
  ::testing::GTEST_FLAG(death_test_style) = "threadsafe";
//  ::testing::GTEST_FLAG(death_test_output_prefix) = "Other VM";

  char* java_home = get_java_home_arg(argc, argv);
  if (java_home == NULL) {
    fprintf(stderr, "ERROR: You must specify a JDK to use for running the unit tests.\n");
    exit(1);
  }
#ifndef _WIN32
  int overwrite = 1; // overwrite an eventual existing value for JAVA_HOME
  setenv("JAVA_HOME", java_home, overwrite);

// workaround for JDK-7131356
#ifdef __APPLE__
  size_t len = strlen(java_home) + strlen("/lib/jli/libjli.dylib") + 1;
  char* path = new char[len];
  snprintf(path, len, "%s/lib/jli/libjli.dylib", java_home);
  dlopen(path, RTLD_NOW | RTLD_GLOBAL);
#endif // __APPLE__

#else  // _WIN32
  char* java_home_var = "_ALT_JAVA_HOME_DIR";
  size_t len = strlen(java_home) + strlen(java_home_var) + 2;
  char * envString = new char[len];
  sprintf_s(envString, len, "%s=%s", java_home_var, java_home);
  _putenv(envString);
#endif // _WIN32
  argv = remove_test_runner_arguments(&argc, argv);

  if (is_executing_death_test) {
    if (init_jvm(argc, argv, true) != 0) {
      abort();
    }
  } else {
    ::testing::TestEventListeners& listeners = ::testing::UnitTest::GetInstance()->listeners();
    listeners.Append(new JVMInitializerListener(argc, argv));
  }

  int result = RUN_ALL_TESTS();
  if (result != 0) {
    fprintf(stderr, "ERROR: RUN_ALL_TESTS() failed. Error %d\n", result);
    exit(2);
  }
}

} // extern "C"
