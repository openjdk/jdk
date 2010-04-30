/*
 * Copyright 2003-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2007, 2008 Red Hat, Inc.
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

 public:
  static void invoke_method(methodOop method, address entry_point, TRAPS) {
    ((ZeroEntry *) entry_point)->invoke(method, THREAD);
  }
  static void invoke_osr(methodOop method,
                         address   entry_point,
                         address   osr_buf,
                         TRAPS) {
    ((ZeroEntry *) entry_point)->invoke_osr(method, osr_buf, THREAD);
  }

 public:
  static int expr_index_at(int i) {
    return stackElementWords() * i;
  }

  static int expr_offset_in_bytes(int i) {
    return stackElementSize() * i;
  }

  static int local_index_at(int i) {
    assert(i <= 0, "local direction already negated");
    return stackElementWords() * i;
  }
