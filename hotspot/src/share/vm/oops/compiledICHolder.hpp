/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_COMPILEDICHOLDEROOP_HPP
#define SHARE_VM_OOPS_COMPILEDICHOLDEROOP_HPP

#include "oops/oop.hpp"
#include "utilities/macros.hpp"

// A CompiledICHolder* is a helper object for the inline cache implementation.
// It holds an intermediate value (method+klass pair) used when converting from
// compiled to an interpreted call.
//
// These are always allocated in the C heap and are freed during a
// safepoint by the ICBuffer logic.  It's unsafe to free them earlier
// since they might be in use.
//


class CompiledICHolder : public CHeapObj<mtCompiler> {
  friend class VMStructs;
 private:
  static volatile int _live_count; // allocated
  static volatile int _live_not_claimed_count; // allocated but not yet in use so not
                                               // reachable by iterating over nmethods

  Method* _holder_method;
  Klass*    _holder_klass;    // to avoid name conflict with oopDesc::_klass
  CompiledICHolder* _next;

 public:
  // Constructor
  CompiledICHolder(Method* method, Klass* klass);
  ~CompiledICHolder() NOT_DEBUG_RETURN;

  static int live_count() { return _live_count; }
  static int live_not_claimed_count() { return _live_not_claimed_count; }

  // accessors
  Method* holder_method() const     { return _holder_method; }
  Klass*    holder_klass()  const     { return _holder_klass; }

  void set_holder_method(Method* m) { _holder_method = m; }
  void set_holder_klass(Klass* k)   { _holder_klass = k; }

  // interpreter support (offsets in bytes)
  static int holder_method_offset()   { return offset_of(CompiledICHolder, _holder_method); }
  static int holder_klass_offset()    { return offset_of(CompiledICHolder, _holder_klass); }

  CompiledICHolder* next()     { return _next; }
  void set_next(CompiledICHolder* n) { _next = n; }

  // Verify
  void verify_on(outputStream* st);

  // Printing
  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;

  const char* internal_name() const { return "{compiledICHolder}"; }

  void claim() NOT_DEBUG_RETURN;
};

#endif // SHARE_VM_OOPS_COMPILEDICHOLDEROOP_HPP
