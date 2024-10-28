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

#ifndef SHARE_NMT_MEMLOGRECORDER_HPP
#define SHARE_NMT_MEMLOGRECORDER_HPP

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
    jlong mem_tag;
  };

  static void log(MemTag mem_tag, size_t requested, address ptr, address old, const NativeCallStack *stack);

public:
  static void initialize(intx count);
  static void finish(void);
  static void replay(const char* path, const int pid);

  static void log_free(MemTag mem_tag, void *ptr);
  static void log_malloc(MemTag mem_tag, size_t requested, void* ptr, const NativeCallStack *stack);
  static void log_realloc(MemTag mem_tag, size_t requested, void* ptr, void* old, const NativeCallStack *stack);

  static void rememberThreadName(const char* name);
  static void printActualSizesFor(const char* list);
};

class NMT_VirtualMemoryLogRecorder : public StackObj {

private:
  struct Entry {
    jlong time;
    intx thread;
    address ptr;
    address stack[NMT_TrackingStackDepth];
    jlong mem_tag;
    jlong mem_tag_split;
    size_t size;
    size_t size_split;
    int type;
  };

public:
  enum Type {
    RESERVE,
    RELEASE,
    UNCOMMIT,
    RESERVE_AND_COMMIT,
    COMMIT,
    SPLIT_RESERVED,
    TAG
  };

  static void initialize(intx count);
  static void finish(void);
  static void replay(const char* path, const int pid);

  static void log_virtual_memory_reserve(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
  static void log_virtual_memory_release(address addr, size_t size);
  static void log_virtual_memory_uncommit(address addr, size_t size);
  static void log_virtual_memory_reserve_and_commit(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
  static void log_virtual_memory_commit(void* addr, size_t size, const NativeCallStack& stack);
  static void log_virtual_memory_split_reserved(void* addr, size_t size, size_t split, MemTag flag, MemTag mem_tag_split);
  static void log_virtual_memory_tag(void* addr, MemTag mem_tag);

private:
  static void log(NMT_VirtualMemoryLogRecorder::Type type, MemTag mem_tag, MemTag mem_tag_split, size_t size, size_t size_split, address ptr, const NativeCallStack *stack);
};

#endif // SHARE_NMT_MEMLOGRECORDER_HPP
