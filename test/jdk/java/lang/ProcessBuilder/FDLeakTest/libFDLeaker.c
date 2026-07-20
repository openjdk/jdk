/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include "jvmti.h"

static jint limit_num_fds();

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  // Lower the number of possible open files to make the test go faster
  jint ret = limit_num_fds();
  if (ret != 0) {
    fprintf(stderr, "Failed to limit number of fds: %s", strerror(errno));
    return ret;
  }

  const char* filename = "./testfile_FDLeaker.txt";
  FILE* f = fopen(filename, "w");
  if (f == NULL) {
    fprintf(stderr, "Failed to open file: %s", strerror(errno));
    return JNI_ERR;
  }

  printf("Opened and leaked %s (%d)", filename, fileno(f));
  return JNI_OK;
}

static jint limit_num_fds() {
  struct rlimit rl;

  // Fetch the current limit
  int ret = getrlimit(RLIMIT_NOFILE, &rl);
  if (ret != 0) {
    return JNI_ERR;
  }

  // Use a lower value unless it is already low
  rlim_t limit = 100;
  if (limit < rl.rlim_cur) {
    rl.rlim_cur = limit;
  }

  // Lower the value
  int ret2 = setrlimit(RLIMIT_NOFILE, &rl);
  if (ret2 != 0) {
    return JNI_ERR;
  }

  return JNI_OK;
}
