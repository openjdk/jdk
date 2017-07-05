/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_METADATA_HPP
#define SHARE_VM_OOPS_METADATA_HPP

#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

// This is the base class for an internal Class related metadata
class Metadata : public MetaspaceObj {
  // Debugging hook to check that the metadata has not been deleted.
  NOT_PRODUCT(int _valid;)
 public:
  NOT_PRODUCT(Metadata()     { _valid = 0; })
  NOT_PRODUCT(bool is_valid() const volatile { return _valid == 0; })

  int identity_hash()                { return (int)(uintptr_t)this; }

  // Rehashing support for tables containing pointers to this
  unsigned int new_hash(jint seed)   { ShouldNotReachHere();  return 0; }

  virtual bool is_klass()              const volatile { return false; }
  virtual bool is_method()             const volatile { return false; }
  virtual bool is_methodData()         const volatile { return false; }
  virtual bool is_constantPool()       const volatile { return false; }

  virtual const char* internal_name()  const = 0;

  void print()       const { print_on(tty); }
  void print_value() const { print_value_on(tty); }

  void print_maybe_null() const { print_on_maybe_null(tty); }
  void print_on_maybe_null(outputStream* st) const {
    if (this == NULL)
      st->print("NULL");
    else
      print_on(tty);
  }
  void print_value_on_maybe_null(outputStream* st) const {
    if (this == NULL)
      st->print("NULL");
    else
      print_value_on(tty);
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

#endif // SHARE_VM_OOPS_METADATA_HPP
