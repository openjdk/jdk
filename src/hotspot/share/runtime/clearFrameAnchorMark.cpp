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

#include "precompiled.hpp"
#include "runtime/clearFrameAnchorMark.hpp"
#include "runtime/javaThread.hpp"

DEBUG_ONLY(THREAD_LOCAL bool ClearFrameAnchorMark::_is_active = false;)

ClearFrameAnchorMark::ClearFrameAnchorMark(JavaThread* jt)
  : _jt(nullptr),
    _sp(nullptr) {
  if (jt == Thread::current()) {
    // This class only clears the frame anchor for the current thread.
    // We can't safely clear the frame anchor of another thread. Besides,
    // the point of this class is to protect against stack walks from
    // the current thread in a given scope.
    _jt = jt;
    _sp = begin(jt);
  }
}

ClearFrameAnchorMark::~ClearFrameAnchorMark() {
  if (_jt != nullptr) {
    end(_jt, _sp);
  }
}

intptr_t* ClearFrameAnchorMark::begin(JavaThread* jt) {
  assert(!_is_active, "nesting not supported");
  DEBUG_ONLY(_is_active = true;)
  intptr_t* sp = jt->frame_anchor()->last_Java_sp();
  jt->frame_anchor()->set_last_Java_sp(NULL);
  return sp;
}

void ClearFrameAnchorMark::end(JavaThread* jt, intptr_t* sp) {
  assert(_is_active, "mismatched begin and end");
  jt->frame_anchor()->set_last_Java_sp(sp);
  DEBUG_ONLY(_is_active = false;)
}
