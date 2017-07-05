/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ARGUMENTS_EXT_HPP
#define SHARE_VM_RUNTIME_ARGUMENTS_EXT_HPP

#include "memory/allocation.hpp"
#include "runtime/arguments.hpp"

class ArgumentsExt: AllStatic {
public:
  static inline void set_gc_specific_flags();
  // The argument processing extension. Returns true if there is
  // no additional parsing needed in Arguments::parse() for the option.
  // Otherwise returns false.
  static inline bool process_options(const JavaVMOption *option) { return false; }
  static inline void report_unsupported_options() { }
};

void ArgumentsExt::set_gc_specific_flags() {
  Arguments::set_gc_specific_flags();
}

#endif // SHARE_VM_RUNTIME_ARGUMENTS_EXT_HPP
