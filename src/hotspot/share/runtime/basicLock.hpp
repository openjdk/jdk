/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_BASICLOCK_HPP
#define SHARE_RUNTIME_BASICLOCK_HPP

#include "oops/markWord.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.hpp"

// A BasicObjectLock represents a locked object.
// It is currently embedded in an interpreter frame.

// Because some machines have alignment restrictions on the control stack,
// the actual space allocated by the interpreter may include padding words
// after the end of the BasicObjectLock.

class BasicObjectLock {
  friend class VMStructs;
 private:
  oop       _obj;                                     // object holds the lock;
#if defined(AARCH64) || defined(RISCV64)
  // Stack needs to be 16-byte-aligned. Inserting a dummy field here is
  // the simplest way to achieve that.
  intptr_t _dummy;
#endif

 public:
  // Manipulation
  oop      obj() const                                { return _obj;  }
  void set_obj(oop obj)                               { _obj = obj; }

  // Note: Use frame::interpreter_frame_monitor_size() for the size of BasicObjectLocks
  //       in interpreter activation frames since it includes machine-specific padding.
  static int size()                                   { return sizeof(BasicObjectLock)/wordSize; }

  // GC support
  void oops_do(OopClosure* f) { f->do_oop(&_obj); }

  static int obj_offset_in_bytes()                    { return offset_of(BasicObjectLock, _obj);  }
};


#endif // SHARE_RUNTIME_BASICLOCK_HPP
