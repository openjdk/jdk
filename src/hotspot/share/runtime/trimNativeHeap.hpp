/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023 Red Hat Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_TRIMNATIVEHEAP_HPP
#define SHARE_RUNTIME_TRIMNATIVEHEAP_HPP

#include "memory/allStatic.hpp"
#include "runtime/globals.hpp"

class outputStream;

class NativeHeapTrimmer : public AllStatic {

  // Pause periodic trim (if enabled).
  static void suspend_periodic_trim(const char* reason);

  // Unpause periodic trim (if enabled).
  static void resume_periodic_trim(const char* reason);

public:

  static void initialize();
  static void cleanup();

  static inline bool enabled() { return TrimNativeHeapInterval > 0; }

  static void print_state(outputStream* st);

  // Pause periodic trimming while in scope; when leaving scope,
  // resume periodic trimming.
  struct SuspendMark {
    const char* const _reason;
    SuspendMark(const char* reason = "unknown") : _reason(reason) {
      if (NativeHeapTrimmer::enabled()) {
        suspend_periodic_trim(_reason);
      }
    }
    ~SuspendMark()  {
      if (NativeHeapTrimmer::enabled()) {
        resume_periodic_trim(_reason);
      }
    }
  };
};

#endif // SHARE_RUNTIME_TRIMNATIVEHEAP_HPP
