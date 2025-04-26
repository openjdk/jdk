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
 *
 */

#ifndef SHARE_NMT_MEMLOGRECORDER_HPP
#define SHARE_NMT_MEMLOGRECORDER_HPP

#include "memory/allocation.hpp"
#include "nmt/nmtCommon.hpp"
#include "runtime/globals.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/macros.hpp"

#if defined(LINUX) || defined(__APPLE__)

#if defined(LINUX)
#define MAXTHREADNAMESIZE 256
#endif

class NMT_LogRecorder : public StackObj {
protected:
  long int _limit  = 0;
  long int _count  = 0;
  int _log_fd;
  volatile bool _done = true;

protected:
  volatile size_t _threads_names_size = 0;
  typedef struct thread_name_info {
    char name[MAXTHREADNAMESIZE];
    long int thread;
  } thread_name_info;
  thread_name_info *_threads_names = nullptr;

public:
  static void initialize();
  static void finish();
  static void replay();
  static void logThreadName(const char* name);

public:
  void init();
  void get_thread_name(char* buf);
  bool done() {
    return _done;
  }
  void logThreadName();
};

class NMT_MemoryLogRecorder : public NMT_LogRecorder {

private:
  static NMT_MemoryLogRecorder _recorder;

private:
    struct Entry {
    long int time;
    long int thread;
    address ptr;
    address old;
    address stack[NMT_TrackingStackDepth];
    long int requested;
    long int actual;
    long int mem_tag;
  };

public:
  static NMT_MemoryLogRecorder* instance() {
    return &_recorder;
  };
  static void initialize(long int count);
  static bool initialized() {
    return false;
  }
  static void print(Entry *e);
  static void finish(void);
  static void replay(const int pid);
  static void record_free(void *ptr);
  static void record_malloc(MemTag mem_tag, size_t requested, void* ptr, const NativeCallStack *stack, void* old = nullptr);
  static void printActualSizesFor(const char* list);

private:
  static void _record(MemTag mem_tag, size_t requested, address ptr, address old, const NativeCallStack *stack);
};

class NMT_VirtualMemoryLogRecorder : public NMT_LogRecorder {

private:
  static NMT_VirtualMemoryLogRecorder _recorder;

private:
  struct Entry {
    long int time;
    long int thread;
    address ptr;
    address stack[NMT_TrackingStackDepth];
    long int mem_tag;
    long int mem_tag_split;
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

public:
  static NMT_VirtualMemoryLogRecorder* instance() {
    return &_recorder;
  };
  static void initialize(long int count);
  static void finish(void);
  static void replay(const int pid);
  static void record_virtual_memory_reserve(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
  static void record_virtual_memory_release(address addr, size_t size);
  static void record_virtual_memory_uncommit(address addr, size_t size);
  static void record_virtual_memory_reserve_and_commit(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
  static void record_virtual_memory_commit(void* addr, size_t size, const NativeCallStack& stack);
  static void record_virtual_memory_split_reserved(void* addr, size_t size, size_t split, MemTag flag, MemTag mem_tag_split);
  static void record_virtual_memory_tag(void* addr, size_t size, MemTag mem_tag);

private:
  static void _record(NMT_VirtualMemoryLogRecorder::Type type, MemTag mem_tag, MemTag mem_tag_split, size_t size, size_t size_split, address ptr, const NativeCallStack *stack);
};

#else // defined(LINUX) || defined(__APPLE__)

class NMT_LogRecorder : public StackObj {
public:
  static void initialize() { // TODO
  }
  static void finish() { // TODO
  }
  static void replay() { // TODO
  }
};

class NMT_MemoryLogRecorder : public NMT_LogRecorder {
public:
  static void record_free(void *ptr) { // TODO
  }
  static void record_malloc(MemTag mem_tag, size_t requested, void* ptr, const NativeCallStack *stack, void* old = nullptr) { // TODO
  }
};

class NMT_VirtualMemoryLogRecorder : public NMT_LogRecorder {
public:
  static void record_virtual_memory_reserve(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone) { // TODO
  }
  static void record_virtual_memory_release(address addr, size_t size) { // TODO
  }
  static void record_virtual_memory_uncommit(address addr, size_t size) { // TODO
  }
  static void record_virtual_memory_reserve_and_commit(void* addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone) { // TODO
  }
  static void record_virtual_memory_commit(void* addr, size_t size, const NativeCallStack& stack) { // TODO
  }
  static void record_virtual_memory_split_reserved(void* addr, size_t size, size_t split, MemTag flag, MemTag mem_tag_split) { // TODO
  }
  static void record_virtual_memory_tag(void* addr, size_t size, MemTag mem_tag) { // TODO
  }
};

#endif // defined(LINUX) || defined(__APPLE__)

#endif // SHARE_NMT_MEMLOGRECORDER_HPP
