/*
 * Copyright (c) 2002, 2005, Oracle and/or its affiliates. All rights reserved.
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

private:
  volatile int _flags;

public:

   enum pd_Constants {
     flushed = 1                                 // winodows have flushed
   };

  int flags(void)                                { return _flags; }
  void set_flags(int flags)                      { _flags = flags; }

  static ByteSize flags_offset()                 { return byte_offset_of(JavaFrameAnchor, _flags); }

  // Each arch must define clear, copy
  // These are used by objects that only care about:
  //  1 - initializing a new state (thread creation, javaCalls)
  //  2 - saving a current state (javaCalls)
  //  3 - restoring an old state (javaCalls)

  void clear(void) {
    // clearing _last_Java_sp must be first
    _last_Java_sp = NULL;
    // fence?
    _flags = 0;
    _last_Java_pc = NULL;
  }

  void copy(JavaFrameAnchor* src) {
    // In order to make sure the transition state is valid for "this"
    // We must clear _last_Java_sp before copying the rest of the new data
    //
    // Hack Alert: Temporary bugfix for 4717480/4721647
    // To act like previous version (pd_cache_state) don't NULL _last_Java_sp
    // unless the value is changing
    //
    if (_last_Java_sp != src->_last_Java_sp)
      _last_Java_sp = NULL;

    _flags = src->_flags;
    _last_Java_pc = src->_last_Java_pc;
    // Must be last so profiler will always see valid frame if has_last_frame() is true
    _last_Java_sp = src->_last_Java_sp;
  }

  // Is stack walkable
  inline bool walkable( void) {
        return _flags & flushed;
  }

  void make_walkable(JavaThread* thread);

  void set_last_Java_sp(intptr_t* sp)            { _last_Java_sp = sp; }

  address last_Java_pc(void)                     { return _last_Java_pc; }

  // These are only used by friends
private:

  intptr_t* last_Java_sp() const {
    // _last_Java_sp will always be a an unbiased stack pointer
    // if is is biased then some setter screwed up. This is
    // deadly.
#ifdef _LP64
    assert(((intptr_t)_last_Java_sp & 0xF) == 0, "Biased last_Java_sp");
#endif
    return _last_Java_sp;
  }

  void capture_last_Java_pc(intptr_t* sp);

  void set_window_flushed( void) {
    _flags |= flushed;
    OrderAccess::fence();
  }
