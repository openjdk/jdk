/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009 Red Hat, Inc.
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

#ifndef CPU_ZERO_VM_SHARKFRAME_ZERO_HPP
#define CPU_ZERO_VM_SHARKFRAME_ZERO_HPP

#include "oops/methodOop.hpp"
#include "stack_zero.hpp"

// |  ...               |
// +--------------------+  ------------------
// | stack slot n-1     |       low addresses
// |  ...               |
// | stack slot 0       |
// | monitor m-1        |
// |  ...               |
// | monitor 0          |
// | oop_tmp            |
// | method             |
// | unextended_sp      |
// | pc                 |
// | frame_type         |
// | next_frame         |      high addresses
// +--------------------+  ------------------
// |  ...               |

class SharkFrame : public ZeroFrame {
  friend class SharkStack;

 private:
  SharkFrame() : ZeroFrame() {
    ShouldNotCallThis();
  }

 protected:
  enum Layout {
    pc_off = jf_header_words,
    unextended_sp_off,
    method_off,
    oop_tmp_off,
    header_words
  };

 public:
  address pc() const {
    return (address) value_of_word(pc_off);
  }

  intptr_t* unextended_sp() const {
    return (intptr_t *) value_of_word(unextended_sp_off);
  }

  methodOop method() const {
    return (methodOop) value_of_word(method_off);
  }

 public:
  void identify_word(int   frame_index,
                     int   offset,
                     char* fieldbuf,
                     char* valuebuf,
                     int   buflen) const;
};

#endif // CPU_ZERO_VM_SHARKFRAME_ZERO_HPP
