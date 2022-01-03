/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_ASM_REGISTER_HPP
#define SHARE_ASM_REGISTER_HPP

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Use AbstractRegister as shortcut
class AbstractRegisterImpl;
typedef AbstractRegisterImpl* AbstractRegister;


// The super class for platform specific registers. Instead of using value objects,
// registers are implemented as pointers. Subclassing is used so all registers can
// use the debugging suport below. No virtual functions are used for efficiency.
// They are canonicalized; i.e., registers are equal if their pointers are equal,
// and vice versa. A concrete implementation may just map the register onto 'this'.

class AbstractRegisterImpl {
 protected:
  int value() const                              { return (int)(intx)this; }
};

#define AS_REGISTER(type,name)         ((type)name##_##type##EnumValue)

#define CONSTANT_REGISTER_DECLARATION(type, name, value)        \
const type name = ((type)value);                                \
enum { name##_##type##EnumValue = (value) }

#define REGISTER_DECLARATION(type, name, value)                 \
const type name = ((type)value)

#define REGISTER_DEFINITION(type, name)

#include CPU_HEADER(register)

// Debugging support

template<typename R, typename... Rx>
inline void assert_different_registers(R first_register, Rx... more_registers) {
#ifdef ASSERT
  const R regs[] = { first_register, more_registers... };
  // Verify there are no equal entries.
  for (size_t i = 0; i < ARRAY_SIZE(regs) - 1; ++i) {
    for (size_t j = i + 1; j < ARRAY_SIZE(regs); ++j) {
      assert(regs[i] != regs[j], "Multiple uses of register: %s", regs[i]->name());
    }
  }
#endif
}

#endif // SHARE_ASM_REGISTER_HPP
