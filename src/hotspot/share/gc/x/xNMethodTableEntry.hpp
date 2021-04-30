/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_X_XNMETHODTABLEENTRY_HPP
#define SHARE_GC_X_XNMETHODTABLEENTRY_HPP

#include "gc/x/xBitField.hpp"
#include "memory/allocation.hpp"

class nmethod;

//
// NMethod table entry layout
// --------------------------
//
//   6
//   3                                                                   2 1 0
//  +---------------------------------------------------------------------+-+-+
//  |11111111 11111111 11111111 11111111 11111111 11111111 11111111 111111|1|1|
//  +---------------------------------------------------------------------+-+-+
//  |                                                                     | |
//  |                                      1-1 Unregistered Flag (1-bits) * |
//  |                                                                       |
//  |                                          0-0 Registered Flag (1-bits) *
//  |
//  * 63-2 NMethod Address (62-bits)
//

class XNMethodTableEntry : public CHeapObj<mtGC> {
private:
  typedef XBitField<uint64_t, bool,     0,  1>    field_registered;
  typedef XBitField<uint64_t, bool,     1,  1>    field_unregistered;
  typedef XBitField<uint64_t, nmethod*, 2, 62, 2> field_method;

  uint64_t _entry;

public:
  explicit XNMethodTableEntry(bool unregistered = false) :
      _entry(field_registered::encode(false) |
             field_unregistered::encode(unregistered) |
             field_method::encode(NULL)) {}

  explicit XNMethodTableEntry(nmethod* method) :
      _entry(field_registered::encode(true) |
             field_unregistered::encode(false) |
             field_method::encode(method)) {}

  bool registered() const {
    return field_registered::decode(_entry);
  }

  bool unregistered() const {
    return field_unregistered::decode(_entry);
  }

  nmethod* method() const {
    return field_method::decode(_entry);
  }
};

#endif // SHARE_GC_X_XNMETHODTABLEENTRY_HPP
