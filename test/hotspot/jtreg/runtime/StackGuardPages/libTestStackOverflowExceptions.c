/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <stdint.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#include <unistd.h>
#endif

static size_t align_up(size_t value, size_t alignment) {
  return ((value + alignment - 1) / alignment) * alignment;
}

static size_t native_page_size(void) {
#ifdef _WIN32
  SYSTEM_INFO system_info;
  GetSystemInfo(&system_info);
  return system_info.dwPageSize;

#else
  return (size_t)sysconf(_SC_PAGESIZE);
#endif
}

static uintptr_t stack_bottom(void) {
#ifdef _WIN32
  volatile unsigned char marker = 0;
  MEMORY_BASIC_INFORMATION info;
  VirtualQuery((void*)&marker, &info, sizeof(info));
  return (uintptr_t)info.AllocationBase;

#elif defined(__APPLE__)
  pthread_t self = pthread_self();
  uintptr_t base = (uintptr_t)pthread_get_stackaddr_np(self);
  return base - pthread_get_stacksize_np(self);

#else
  size_t size;
  void* address;
  pthread_attr_t attr;

  pthread_getattr_np(pthread_self(), &attr);
  pthread_attr_getstack(&attr, &address, &size);
  pthread_attr_destroy(&attr);

  return (uintptr_t)address;
#endif
}

JNIEXPORT void JNICALL
Java_TestStackOverflowExceptions_bangRedZone(JNIEnv* env, jclass cls) {
  // Any red zone access should trigger the unrecoverable handler, so we
  // intentionally access just one page in the red zone.
  volatile unsigned char* page = (volatile unsigned char*)stack_bottom();
  *page = 0;
}

JNIEXPORT void JNICALL
Java_TestStackOverflowExceptions_bangYellowZone(JNIEnv* env, jclass cls,
                                                jint red_page_count,
                                                jint yellow_page_count) {
  // We want to bang every page in the yellow zone, so we first need to know
  // where the yellow zone begins and ends.  Ordinarily, we'd just multiply the
  // red page count with 4KB and add it to the bottom of the stack, but there's
  // a small discrepancy: HotSpot tracks red and yellow zone sizes in terms of
  // 4KB pages, but the OS may choose a larger page size (e.g. macOS on Apple
  // Silicon uses 16KB page sizes).  So we bump the (logical) red/yellow zone
  // size (assuming 4KB pages) to the (physical) size (using the OS page size).

  const size_t logical_page_size = 4 * 1024;
  const size_t physical_page_size = native_page_size();

  const size_t __red_size = red_page_count * logical_page_size;
  const size_t red_size = align_up(__red_size, physical_page_size);

  const size_t __yellow_size = yellow_page_count * logical_page_size;
  const size_t yellow_size = align_up(__yellow_size, physical_page_size);

  uintptr_t address = stack_bottom() + red_size;
  volatile unsigned char* pointer = (volatile unsigned char*)address;
  volatile unsigned char* yellow_end = pointer + yellow_size;

  // We deliberately bang every page in the yellow zone to ensure that none of
  // the yellow zone accesses raise an unrecoverable exception or a crash.
  for (; pointer < yellow_end; pointer += physical_page_size) {
    *pointer = 0;
  }
}
