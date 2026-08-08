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

#include <dlfcn.h>
#include <errno.h>
#include <inttypes.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

#ifndef MAP_FIXED_NOREPLACE
#define MAP_FIXED_NOREPLACE 0x100000
#endif

#define RESERVE_ALIGNMENT (2 * 1024 * 1024)

#define ASSERT_JNI_OK(value)                                                   \
do {                                                                           \
  jint res = (value);                                                          \
  if (res != JNI_OK) {                                                         \
    fprintf(stderr, "Test Error: " #value " failed: %d\n", res);               \
    exit(1);                                                                   \
  }                                                                            \
} while (0)
#define ASSERT_NOT_NULL(value)                                                 \
do {                                                                           \
  if ((value) == NULL) {                                                       \
    fprintf(stderr, "Test Error: " #value " is NULL\n");                       \
    exit(1);                                                                   \
  }                                                                            \
} while (0)
#define ASSERT_TRUE(value)                                                     \
do {                                                                           \
  if (!(value)) {                                                              \
    fprintf(stderr, "Test Error: " #value " not TRUE\n");                      \
    exit(1);                                                                   \
  }                                                                            \
} while (0)
#define ASSERT_ALIGNED(value, alignment)                                       \
do {                                                                           \
  if ((value) % (alignment) != 0) {                                            \
    fprintf(stderr, "Test Error: " #value "[0x%zx] "                           \
                    "not aligned to " #alignment "[0x%zx]\n",                  \
                    (size_t)(value), (size_t)(alignment));                     \
    exit(1);                                                                   \
  }                                                                            \
} while (0)
#define ASSERT_POWEROF2(value)                                                 \
do {                                                                           \
  if (((value) & ((value) - 1)) != 0) {                                        \
    fprintf(stderr, "Test Error: " #value "[0x%zx] is not a power of two\n",   \
                    (size_t)(value));                                          \
    exit(1);                                                                   \
  }                                                                            \
} while (0)

#define OPTION(option) { (char*)option, NULL }
#define ARRAY_SIZE(a) sizeof(a)/sizeof(a[0])

#define FALSE 0
#define TRUE 1

JNIEnv* create_vm(JavaVM **jvm, const char* xmx) {
  JNIEnv* env;
  JavaVMInitArgs args;
  JavaVMOption options[] = {
    OPTION("-XX:+UseZGC"),
    OPTION("-Xlog:gc"),
    OPTION("-Xlog:gc+init=trace"),
    OPTION("-Xms32m"),
    OPTION(xmx),
  };
  args.version = JNI_VERSION_1_8;
  args.nOptions = ARRAY_SIZE(options);
  args.options = options;
  args.ignoreUnrecognized = 0;

  printf("Creating VM\n");
  fflush(stdout);
  ASSERT_JNI_OK(JNI_CreateJavaVM(jvm, (void**)&env, &args));

  return env;
}

// Simulates java --version
void run_jvm(const char* xmx) {
  // Create the vm
  JavaVM* jvm;
  jclass T_class;
  jmethodID test_method;
  JNIEnv* env = create_vm(&jvm, xmx);
  ASSERT_NOT_NULL(env);

  // Simulate java --version via upcall to java.lang.VersionProps.print(false);

  printf("Loader lookup\n");
  fflush(stdout);
  // Find the boot class loader
  typedef jclass (JNICALL FindClassFromBootLoader_t(JNIEnv *env, const char *name));
  FindClassFromBootLoader_t* find_class_from_boot_loader = (FindClassFromBootLoader_t*)dlsym(RTLD_DEFAULT, "JVM_FindClassFromBootLoader");
  ASSERT_NOT_NULL(find_class_from_boot_loader);

  printf("Class lookup\n");
  fflush(stdout);
  // Lookup the java.lang.VersionProps class
  jclass ver = find_class_from_boot_loader(env, "java/lang/VersionProps");
  ASSERT_NOT_NULL(ver);

  printf("Method lookup\n");
  fflush(stdout);
  // Lookup the java.lang.VersionProps.print(boolean) method
  jmethodID print = (*env)->GetStaticMethodID(env, ver, "print", "(Z)V");
  ASSERT_NOT_NULL(print);

  printf("Method call\n");
  fflush(stdout);
  // Call java.lang.VersionProps.print(false);
  (*env)->CallStaticVoidMethod(env, ver, print, JNI_FALSE);

  printf("Destroy VM\n");
  fflush(stdout);
  // Destroy the VM
  ASSERT_JNI_OK((*jvm)->DestroyJavaVM(jvm));
}

uintptr_t str_to_uintptr_t(const char* str) {
  char* _;
  errno = 0;
  uintptr_t ret = strtoumax(str, &_, 10);
  if (errno) {
    perror("Failed to parse uintptr_t");
    exit(1);
  }
  return ret;
}

int try_reservation(void* addr, size_t len) {
  fflush(stdout);
  errno = 0;

  // We reserve with MAP_FIXED_NOREPLACE in case we run on a kernel where the
  // address hint is not even attempted if it is next to a pre-existsing mapping.
  const int flags = MAP_ANONYMOUS | MAP_PRIVATE | MAP_NORESERVE | MAP_FIXED_NOREPLACE;
  void* const res = mmap(addr, len, PROT_NONE, flags, -1, 0);

  if (res == MAP_FAILED) {
    return FALSE;
  }

  if (res != addr) {
    // We did not get our fixed address, MAP_FIXED_NOREPLACE was ignored
    printf("MAP_FIXED_NOREPLACE unsupported\n");
    exit(0);
  }

   return TRUE;
}

size_t align_down(size_t value, size_t alignment) {
  ASSERT_POWEROF2(alignment);

  const size_t alignment_mask = alignment - 1;
  const size_t aligned_value = value & ~alignment_mask;

  ASSERT_ALIGNED(aligned_value, alignment);
  return aligned_value;
}

size_t align_up(size_t value, size_t alignment) {
  return align_down(value + alignment - 1, alignment);
}

void reserve_address_space_range(uintptr_t start, uintptr_t end) {
  ASSERT_TRUE(start < end);

  const size_t min_len = RESERVE_ALIGNMENT;

  ASSERT_ALIGNED(start, RESERVE_ALIGNMENT);
  ASSERT_ALIGNED(end, RESERVE_ALIGNMENT);

  void* const addr = (void*)start;
  const size_t len = end - start;

  if (try_reservation(addr, len)) {
    // Success
    printf("Reserved range [0x%zx - 0x%zx]\n", start, end);
  } else if (errno == EEXIST || errno == EINVAL) {
    // We check for alignment and size, assume EINVAL is either a strange
    // os page size or a too extreme address, treat it as if part of the range
    // is unmappable
    if (len > min_len) {
      // Divide and conquer
      const size_t half_len = align_up(len / 2, RESERVE_ALIGNMENT);
      const uintptr_t middle = start + half_len;
      if (middle != end) {
        reserve_address_space_range(start, middle);
        reserve_address_space_range(middle, end);
      }
    }
  } else if (errno == ENOMEM) {
    printf("ENOMEM restriction encountered\n");
    exit(0);
  } else {
    perror("Unexpected try_reservation error");
    exit(1);
  }
}

void reserve_address_space(int argc, const char** argv) {
  // We need to be careful to not reserve to close to the thread stack, as the
  // JVM will page fault in the stack space. If we have reserved that space as
  // prot none the kernel will not expand the stack but rather send a SIGSEGV.
  const size_t stack_headroom = 2 * RESERVE_ALIGNMENT;
  uintptr_t stack_top;
  stack_top = align_up((uintptr_t)&stack_top, RESERVE_ALIGNMENT);
  const uintptr_t stack_bottom = stack_top - stack_headroom;

  for (int i = 2; i < argc; i += 2) {
    printf("Got range [%s - %s]\n", argv[i-1], argv[i]);

    const uintptr_t start = str_to_uintptr_t(argv[i-1]);
    const uintptr_t end = str_to_uintptr_t(argv[i]);

    ASSERT_TRUE(start < end);

    if (start >= stack_top || end <= stack_bottom) {
      // No interference with thread stack
      reserve_address_space_range(start, end);
      continue;
    }

    printf("Interference with stack [0x%zx - 0x%zx]\n", stack_bottom, stack_top);

    if (start < stack_bottom) {
      // Reservation range below the stack
      reserve_address_space_range(start, stack_bottom);
    }

    if (end > stack_top) {
      // Reservation range above the stack
      reserve_address_space_range(stack_top, end);
    }
  }
}

int main(int argc, const char** argv) {
  printf("Started\n");

  // Parse potential -Xmx option
  const char* xmx = "-Xmx128m";
  if (argc > 1 && strncmp(argv[1], "-Xmx", 4) == 0) {
    xmx = argv[1];
    argc--;
    argv++;
  }
  printf("Size flag: %s\n", xmx);

  // Pre-reserve address space
  printf("Reserving\n");
  reserve_address_space(argc, argv);

  // Invoke a new JVM
  printf("Running\n");
  fflush(stdout);
  run_jvm(xmx);

  return 0;
}
