/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_DISABLESTACKTRACINGMARK_HPP
#define SHARE_RUNTIME_DISABLESTACKTRACINGMARK_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class JavaThread;

// Use this class to mark a section of code where stack tracing from the
// current thread is not safe and should be avoided.
class ClearFrameAnchorMark : public StackObj {
  DEBUG_ONLY(static THREAD_LOCAL bool _is_active;)
  JavaThread* _jt;
  intptr_t* _sp;

  static intptr_t* begin(JavaThread* jt);
  static void end(JavaThread* jt, intptr_t* sp);

public:
  ClearFrameAnchorMark(JavaThread* jt);
  ~ClearFrameAnchorMark();

  DEBUG_ONLY(static bool is_active() { return _is_active; })
};


#endif // SHARE_RUNTIME_DISABLESTACKTRACINGMARK_HPP
