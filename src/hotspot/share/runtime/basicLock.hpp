/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/globalDefinitions.hpp"
#include "utilities/sizes.hpp"

class BasicLock {
  friend class VMStructs;
  friend class JVMCIVMStructs;
 private:
  // * For LM_MONITOR
  // Unused.
  // * For LM_LEGACY
  // This is either the actual displaced header from a locked object, or
  // a sentinel zero value indicating a recursive stack-lock.
  // * For LM_LIGHTWEIGHT
  // Used as a cache of the ObjectMonitor* used when locking. Must either
  // be nullptr or the ObjectMonitor* used when locking.
  volatile uintptr_t _metadata;

  uintptr_t get_metadata() const { return Atomic::load(&_metadata); }
  void set_metadata(uintptr_t value) { Atomic::store(&_metadata, value); }
  static int metadata_offset_in_bytes() { return (int)offset_of(BasicLock, _metadata); }

 public:
  BasicLock() : _metadata(0) {}

  // LM_MONITOR
  void set_bad_metadata_deopt() { set_metadata(badDispHeaderDeopt); }

  // LM_LEGACY
  inline markWord displaced_header() const;
  inline void set_displaced_header(markWord header);
  static int displaced_header_offset_in_bytes() { return metadata_offset_in_bytes(); }

  // LM_LIGHTWEIGHT
  inline ObjectMonitor* object_monitor_cache() const;
  inline void clear_object_monitor_cache();
  inline void set_object_monitor_cache(ObjectMonitor* mon);
  static int object_monitor_cache_offset_in_bytes() { return metadata_offset_in_bytes(); }

  void print_on(outputStream* st, oop owner) const;

  // move a basic lock (used during deoptimization)
  void move_to(oop obj, BasicLock* dest);
};

// A BasicObjectLock associates a specific Java object with a BasicLock.
// It is currently embedded in an interpreter frame.

// Because some machines have alignment restrictions on the control stack,
// the actual space allocated by the interpreter may include padding words
// after the end of the BasicObjectLock.  Also, in order to guarantee
// alignment of the embedded BasicLock objects on such machines, we
// put the embedded BasicLock at the beginning of the struct.

class BasicObjectLock {
  friend class VMStructs;
 private:
  BasicLock _lock;                                    // the lock, must be double word aligned
  oop       _obj;                                     // object holds the lock;

 public:
  // Manipulation
  oop      obj() const                                { return _obj;  }
  oop*     obj_adr()                                  { return &_obj; }
  void set_obj(oop obj)                               { _obj = obj; }
  BasicLock* lock()                                   { return &_lock; }

  // Note: Use frame::interpreter_frame_monitor_size() for the size of BasicObjectLocks
  //       in interpreter activation frames since it includes machine-specific padding.
  static int size()                                   { return sizeof(BasicObjectLock)/wordSize; }

  // GC support
  void oops_do(OopClosure* f) { f->do_oop(&_obj); }

  static ByteSize obj_offset()                { return byte_offset_of(BasicObjectLock, _obj);  }
  static ByteSize lock_offset()               { return byte_offset_of(BasicObjectLock, _lock); }
};


#endif // SHARE_RUNTIME_BASICLOCK_HPP
