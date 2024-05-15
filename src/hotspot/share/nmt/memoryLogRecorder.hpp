/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MEMORYLOGRECORDER_HPP
#define SHARE_NMT_MEMORYLOGRECORDER_HPP

#include "memory/allocation.hpp"
#include "nmt/nmtCommon.hpp"
#include "runtime/globals.hpp"

class NMT_MemoryLogRecorder : public StackObj {

private:
  struct Entry {
    jlong time;
    intx thread;
    address ptr;
    address old;
    address stack[NMT_TrackingStackDepth];
    size_t requested;
    size_t actual;
    jlong flags;
  };
  static void log(MEMFLAGS flags, size_t requested, address ptr, address old, const NativeCallStack *stack);
public:
  static void initialize(intx count);
  static void finish(void);
  static void log_free(MEMFLAGS flags, void *ptr);
  static void log_malloc(MEMFLAGS flags, size_t requested, void* ptr, const NativeCallStack *stack);
  static void log_realloc(MEMFLAGS flags, size_t requested, void* ptr, void* old, const NativeCallStack *stack);
  static void replay(const char* path, const int pid);
  static void rememberThreadName(const char* name);
  static void printActualSizesFor(const char* list);
};

#endif // SHARE_NMT_MEMORYLOGRECORDER_HPP
