/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CI_CIFLAGS_HPP
#define SHARE_CI_CIFLAGS_HPP

#include "ci/ciClassList.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/ostream.hpp"

// ciFlags
//
// This class represents klass or method flags.
class ciFlags {
private:
  friend class ciInstanceKlass;
  friend class ciField;
  friend class ciMethod;

  AccessFlags _flags;
  bool _stable;
  bool _initialized_final_update;

  ciFlags() :_flags(0), _stable(false), _initialized_final_update(false) { }
  ciFlags(AccessFlags flags, bool is_stable = false, bool is_initialized_final_update = false) :
    _flags(flags), _stable(is_stable), _initialized_final_update(is_initialized_final_update) { }

public:
  // Java access flags
  bool is_public               () const { return _flags.is_public();       }
  bool is_private              () const { return _flags.is_private();      }
  bool is_protected            () const { return _flags.is_protected();    }
  bool is_static               () const { return _flags.is_static();       }
  bool is_final                () const { return _flags.is_final();        }
  bool is_synchronized         () const { return _flags.is_synchronized(); }
  bool is_super                () const { return _flags.is_super();        }
  bool is_volatile             () const { return _flags.is_volatile();     }
  bool is_transient            () const { return _flags.is_transient();    }
  bool is_native               () const { return _flags.is_native();       }
  bool is_interface            () const { return _flags.is_interface();    }
  bool is_abstract             () const { return _flags.is_abstract();     }
  bool is_stable               () const { return _stable; }
  // In case the current object represents a field, return true if
  // the field is modified outside of instance initializer methods
  // (or class/initializer methods if the field is static) and false
  // otherwise.
  bool has_initialized_final_update() const { return _initialized_final_update; };

  // Conversion
  jint   as_int()                      { return _flags.as_unsigned_short(); }

  void print_klass_flags(outputStream* st = tty);
  void print_member_flags(outputStream* st = tty);
  void print(outputStream* st = tty);
};

#endif // SHARE_CI_CIFLAGS_HPP
