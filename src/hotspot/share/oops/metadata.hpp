/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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

#ifndef SHARE_OOPS_METADATA_HPP
#define SHARE_OOPS_METADATA_HPP

#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

// This is the base class for an internal Class related metadata
class Metadata : public MetaspaceObj {

#ifndef PRODUCT
  uint32_t _token;

protected:
  Metadata() : _token(common_prefix) {}
  void set_metadata_token(uint32_t v) { _token = v; }

public:
  static constexpr uint32_t common_prefix        = 0x3E7A'0000;
  static constexpr uint32_t common_prefix_mask   = 0xFFFF'0000;
  static constexpr uint32_t instance_klass_token = 0x3E7A'0101;
  static constexpr uint32_t array_klass_token    = 0x3E7A'0102;

  unsigned get_metadata_token() const { return _token; }
  bool is_valid() const { return (get_metadata_token() & common_prefix_mask) == common_prefix; }

  // Return token via SafeFetch. Returns true if token could be read, false if not.
  bool get_metadata_token_safely(unsigned* out) const;
#endif // !PRODUCT

public:
  int identity_hash()                { return (int)(uintptr_t)this; }

  virtual bool is_metadata()           const { return true; }
  virtual bool is_klass()              const { return false; }
  virtual bool is_method()             const { return false; }
  virtual bool is_methodData()         const { return false; }
  virtual bool is_constantPool()       const { return false; }
  virtual bool is_methodCounters()     const { return false; }
  virtual int  size()                  const = 0;
  virtual MetaspaceObj::Type type()    const = 0;
  virtual const char* internal_name()  const = 0;
  virtual void metaspace_pointers_do(MetaspaceClosure* iter) = 0;

  void print()       const;
  void print_value() const;

  static void print_value_on_maybe_null(outputStream* st, const Metadata* m) {
    if (nullptr == m)
      st->print("null");
    else
      m->print_value_on(st);
  }

  virtual void print_on(outputStream* st) const;       // First level print
  virtual void print_value_on(outputStream* st) const = 0; // Second level print

  char* print_value_string() const;

  // Used to keep metadata alive during class redefinition
  // Can't assert because is called for delete functions (as an assert)
  virtual bool on_stack() const { return false; }
  virtual void set_on_stack(const bool value);

  // Set on_stack bit, so that the metadata is not cleared
  // during class redefinition.  This is a virtual call because only methods
  // and constant pools need to be set, but someday instanceKlasses might also.
  static void mark_on_stack(Metadata* m) { m->set_on_stack(true); }
};

template <typename M>
static void print_on_maybe_null(outputStream* st, const char* str, const M* m) {
  if (nullptr != m) {
    st->print_raw(str);
    m->print_value_on(st);
    st->cr();
  }
}

#undef BUILD32

#endif // SHARE_OOPS_METADATA_HPP
