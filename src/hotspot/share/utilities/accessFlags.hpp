/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_ACCESSFLAGS_HPP
#define SHARE_UTILITIES_ACCESSFLAGS_HPP

#include "jvm_constants.h"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// AccessFlags is an abstraction over Java access flags.

class outputStream;

enum {
  // See jvm.h for shared JVM_ACC_XXX access flags

  // flags actually put in .class file
  JVM_ACC_WRITTEN_FLAGS           = 0x00007FFF,

  // Do not add new ACC flags here.
};


class AccessFlags {
  friend class VMStructs;
 private:
  jint _flags;  // TODO: move 4 access flags above to Klass and change to u2

 public:
  AccessFlags() : _flags(0) {}
  explicit AccessFlags(jint flags) : _flags(flags) {}

  // Java access flags
  bool is_public      () const         { return (_flags & JVM_ACC_PUBLIC      ) != 0; }
  bool is_private     () const         { return (_flags & JVM_ACC_PRIVATE     ) != 0; }
  bool is_protected   () const         { return (_flags & JVM_ACC_PROTECTED   ) != 0; }
  bool is_static      () const         { return (_flags & JVM_ACC_STATIC      ) != 0; }
  bool is_final       () const         { return (_flags & JVM_ACC_FINAL       ) != 0; }
  bool is_synchronized() const         { return (_flags & JVM_ACC_SYNCHRONIZED) != 0; }
  bool is_super       () const         { return (_flags & JVM_ACC_SUPER       ) != 0; }
  bool is_volatile    () const         { return (_flags & JVM_ACC_VOLATILE    ) != 0; }
  bool is_transient   () const         { return (_flags & JVM_ACC_TRANSIENT   ) != 0; }
  bool is_native      () const         { return (_flags & JVM_ACC_NATIVE      ) != 0; }
  bool is_interface   () const         { return (_flags & JVM_ACC_INTERFACE   ) != 0; }
  bool is_abstract    () const         { return (_flags & JVM_ACC_ABSTRACT    ) != 0; }

  // Attribute flags
  bool is_synthetic   () const         { return (_flags & JVM_ACC_SYNTHETIC   ) != 0; }

  // get .class file flags
  jint get_flags               () const { return (_flags & JVM_ACC_WRITTEN_FLAGS); }

  // Initialization
  void set_field_flags(jint flags)      {
    assert((flags & JVM_RECOGNIZED_FIELD_MODIFIERS) == flags, "only recognized flags");
    _flags = (flags & JVM_RECOGNIZED_FIELD_MODIFIERS);
  }
  void set_flags(jint flags)            { _flags = (flags & JVM_ACC_WRITTEN_FLAGS); }

 private:
  friend class Klass;
  friend class ClassFileParser;
  // the functions below should only be called on the _access_flags inst var directly,
  // otherwise they are just changing a copy of the flags

  // attribute flags
  void set_is_synthetic()              { _flags |= JVM_ACC_SYNTHETIC; }

 public:
  // Conversion
  jshort as_short() const              { return (jshort)_flags; }
  jint   as_int() const                { return _flags; }

  inline friend AccessFlags accessFlags_from(jint flags);

  // Printing/debugging
#if INCLUDE_JVMTI
  void print_on(outputStream* st) const;
#else
  void print_on(outputStream* st) const PRODUCT_RETURN;
#endif
};

inline AccessFlags accessFlags_from(jint flags) {
  AccessFlags af;
  af._flags = flags;
  return af;
}

#endif // SHARE_UTILITIES_ACCESSFLAGS_HPP
