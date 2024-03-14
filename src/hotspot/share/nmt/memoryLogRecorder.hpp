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

#ifdef ASSERT

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

public:
  static bool active(void) { return (NMTRecordMemoryAllocations>0); }
  static void finish(void) { log(); }
  static void log(MEMFLAGS flags = mtNone, size_t requested = 0, address ptr = nullptr, address old = nullptr,
                  const NativeCallStack *stack = nullptr);
  static void rememberThreadName(const char* name);
  static void printActualSizesFor(const char* list);
};

#endif // ASSERT

#endif // SHARE_NMT_MEMORYLOGRECORDER_HPP
