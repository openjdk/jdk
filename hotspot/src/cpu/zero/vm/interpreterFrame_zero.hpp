/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2008 Red Hat, Inc.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#ifdef CC_INTERP
// |  ...               |
// +--------------------+  ------------------
// | stack slot n-1     |       low addresses
// |  ...               |
// | stack slot 0       |
// | monitor 0 (maybe)  |
// |  ...               |
// | interpreter state  |
// |  ...               |
// | frame_type         |
// | next_frame         |      high addresses
// +--------------------+  ------------------
// |  ...               |

class InterpreterFrame : public ZeroFrame {
  friend class AbstractInterpreter;

 private:
  InterpreterFrame() : ZeroFrame() {
    ShouldNotCallThis();
  }

 protected:
  enum Layout {
    istate_off = jf_header_words +
      (align_size_up_(sizeof(BytecodeInterpreter),
                      wordSize) >> LogBytesPerWord) - 1,
    header_words
  };

 public:
  static InterpreterFrame *build(ZeroStack*      stack,
                                 const methodOop method,
                                 JavaThread*     thread);
  static InterpreterFrame *build(ZeroStack* stack, int size);

 public:
  interpreterState interpreter_state() const {
    return (interpreterState) addr_of_word(istate_off);
  }

 public:
  void identify_word(int   frame_index,
                     int   offset,
                     char* fieldbuf,
                     char* valuebuf,
                     int   buflen) const;
};
#endif // CC_INTERP
