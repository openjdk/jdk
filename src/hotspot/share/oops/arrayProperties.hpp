/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_ARRAYPROPERTIES_HPP
#define SHARE_OOPS_ARRAYPROPERTIES_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

class ArrayProperties {
public:
  // This type is mirrored in the compiler so we need to be careful changing it
  typedef uint32_t Type;

private:
  Type _flags;

  enum class Property : Type {
    NullRestricted = 1 << 0,
    NonAtomic      = 1 << 1,
    Invalid        = 1 << 2, // This needs to be last for asserts
  };

  ArrayProperties with_property(Property prop, bool enabled = true) const {
    return enabled
        ? ArrayProperties(_flags | static_cast<Type>(prop))
        : ArrayProperties(_flags &~ static_cast<Type>(prop));
  }

  bool check_flag(Property prop) const { return (_flags & static_cast<Type>(prop)) != 0; }

public:
  explicit ArrayProperties(Type flags = 0) : _flags(flags) {
    assert((flags & ~((Type(Property::Invalid) << 1) - 1)) == 0, "invalid flags set");
  }

  static ArrayProperties Default() { return ArrayProperties(); }
  static ArrayProperties Invalid() { return ArrayProperties().with_property(Property::Invalid, true); }

  ArrayProperties with_null_restricted(bool b = true) const { return with_property(Property::NullRestricted, b); }
  ArrayProperties with_non_atomic(bool b = true) const { return with_property(Property::NonAtomic, b); }

  bool is_null_restricted() const { return check_flag(Property::NullRestricted); }
  bool is_non_atomic() const { return check_flag(Property::NonAtomic); }
  bool is_invalid() const { return check_flag(Property::Invalid); }
  bool is_valid() const { return !check_flag(Property::Invalid); }

  Type value() const { return _flags; }

  const char* as_string() {
    // Caller must have set a ResourceMark
    stringStream ss;
    if (is_invalid()) {
      return "INVALID";
    } else {
      ss.print("%s", is_null_restricted() ? "NULL_RESTRICTED " : "NULLABLE ");
      ss.print("%s", is_non_atomic() ? "NON_ATOMIC " : "ATOMIC ");
    }
    return ss.as_string();
  }
};

inline bool operator==(ArrayProperties a, ArrayProperties b) {
  return a.value() == b.value();
}

inline bool operator!=(ArrayProperties a, ArrayProperties b) {
  return !(a == b);
}

#endif // SHARE_OOPS_ARRAYPROPERTIES_HPP
