/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_JAVAFRAMEANCHOR_AARCH64_HPP
#define CPU_AARCH64_JAVAFRAMEANCHOR_AARCH64_HPP

private:
  // FP value associated with _last_Java_sp:
  intptr_t* volatile        _last_Java_fp;           // pointer is volatile not what it points to

  static ByteSize last_Java_fp_offset() {
    return byte_offset_of(JavaFrameAnchor, _last_Java_fp);
  }

public:
  void clear(void) {
    // clearing _last_Java_sp must be first
    reset_last_Java_sp();
    _last_Java_fp = NULL;
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
    if (last_Java_fp() != src->_last_Java_sp) {
      reset_last_Java_sp();
    }
    _last_Java_fp = src->_last_Java_fp;
    _last_Java_pc = src->_last_Java_pc;
    set_last_Java_sp(src->_last_Java_sp);
  }

  bool walkable(void) {
    return last_Java_sp() != NULL && _last_Java_pc != NULL;
  }

  void make_walkable(JavaThread* thread);
  void capture_last_Java_pc(void);

  // last_Java_sp is acting, among other things, as the acquire/release target:
  // when last_Java_sp is not NULL, has_last_frame() is true, and the rest of
  // the frame has to be valid. This means the reads of last_Java_sp should be
  // first and acquiring, and last_Java_sp stores should be last and releasing.
  // Additionally, resets of the frame should be as prompt as possible, therefore
  // we got to "flush" it with trailing fences.

  intptr_t* last_Java_sp(void) const {
    intptr_t* sp = _last_Java_sp;
    OrderAccess::acquire();
    return sp;
  }

  void set_last_Java_sp(intptr_t* sp) {
    if (sp != NULL) {
      OrderAccess::release();
      _last_Java_sp = sp;
    } else {
      reset_last_Java_sp();
    }
  }

  void reset_last_Java_sp() {
    OrderAccess::release();
    _last_Java_sp = NULL;
    OrderAccess::fence();
  }

  intptr_t* last_Java_fp(void) { return _last_Java_fp; }
  address last_Java_pc(void)   { return _last_Java_pc; }

#endif // CPU_AARCH64_JAVAFRAMEANCHOR_AARCH64_HPP
