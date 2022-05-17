/*
 * Copyright (c) 2004, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_PRIMS_FORTE_HPP
#define SHARE_PRIMS_FORTE_HPP

#include "memory/allocation.hpp"

// Interface to Forte support.

class Forte : AllStatic {
 public:
   static void register_stub(const char* name, address start, address end)
                                                 NOT_JVMTI_RETURN;
                                                 // register internal VM stub
};

// A small RAII mark class to manage 'in_asgct' thread status
class ASGCTMark : public StackObj {
 private:
  JavaThread* _thread;
  NONCOPYABLE(ASGCTMark);

 public:
  ASGCTMark(JavaThread* thread) : _thread(thread) {
    if (_thread != nullptr) {
      assert(_thread == Thread::current_or_null_safe(), "not the current thread");
      _thread->set_in_asgct(true);
    }
  }
  ~ASGCTMark() {
    if (_thread != nullptr) {
      assert(_thread == Thread::current_or_null_safe(), "not the current thread");
      _thread->set_in_asgct(false);
    }
  }
};

#endif // SHARE_PRIMS_FORTE_HPP
