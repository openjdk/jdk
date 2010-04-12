/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2007, 2008, 2009 Red Hat, Inc.
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

// A frame represents a physical stack frame on the Zero stack.

 public:
  enum {
    pc_return_offset = 0
  };

  // Constructor
 public:
  frame(intptr_t* sp, intptr_t* fp);

  // The sp of a Zero frame is the address of the highest word in
  // that frame.  We keep track of the lowest address too, so the
  // boundaries of the frame are available for debug printing.
 private:
  intptr_t* _fp;

 public:
  intptr_t* fp() const {
    return _fp;
  }

#ifdef CC_INTERP
  inline interpreterState get_interpreterState() const;
#endif // CC_INTERP

 public:
  const ZeroFrame *zeroframe() const {
    return (ZeroFrame *) sp();
  }

  const EntryFrame *zero_entryframe() const {
    return zeroframe()->as_entry_frame();
  }
  const InterpreterFrame *zero_interpreterframe() const {
    return zeroframe()->as_interpreter_frame();
  }
  const SharkFrame *zero_sharkframe() const {
    return zeroframe()->as_shark_frame();
  }

 public:
  frame sender_for_nonentry_frame(RegisterMap* map) const;

 public:
  void zero_print_on_error(int           index,
                           outputStream* st,
                           char*         buf,
                           int           buflen) const;
