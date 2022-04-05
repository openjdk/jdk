/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/population_count.hpp"

// Use AbstractRegister as shortcut
class AbstractRegisterImpl;
typedef AbstractRegisterImpl* AbstractRegister;


// The super class for platform specific registers. Instead of using value objects,
// registers are implemented as pointers. Subclassing is used so all registers can
// use the debugging support below. No virtual functions are used for efficiency.
// They are canonicalized; i.e., registers are equal if their pointers are equal,
// and vice versa. A concrete implementation may just map the register onto 'this'.

class AbstractRegisterImpl {
 protected:
  int value() const                              { return (int)(intx)this; }
};


// Macros to help define all kinds of registers

#ifndef USE_POINTERS_TO_REGISTER_IMPL_ARRAY

#define AS_REGISTER(type,name)         ((type)name##_##type##EnumValue)

#define CONSTANT_REGISTER_DECLARATION(type, name, value)                \
const type name = ((type)value);                                        \
enum { name##_##type##EnumValue = (value) }

#else // USE_POINTERS_TO_REGISTER_IMPL_ARRAY

#define REGISTER_IMPL_DECLARATION(type, impl_type, reg_count)           \
inline constexpr type as_ ## type(int encoding) {                       \
  return impl_type::first() + encoding;                                 \
}                                                                       \
extern impl_type all_ ## type ## s[reg_count + 1] INTERNAL_VISIBILITY;  \
inline constexpr type impl_type::first() { return all_ ## type ## s + 1; }

#define REGISTER_IMPL_DEFINITION(type, impl_type, reg_count)            \
impl_type all_ ## type ## s[reg_count + 1];

#define CONSTANT_REGISTER_DECLARATION(type, name, value)                \
constexpr type name = as_ ## type(value);

#endif // USE_POINTERS_TO_REGISTER_IMPL_ARRAY


#define REGISTER_DECLARATION(type, name, value) \
const type name = ((type)value)


// For definitions of RegisterImpl* instances. To be redefined in an
// OS-specific way.
#ifdef __GNUC__
#define INTERNAL_VISIBILITY  __attribute__ ((visibility ("internal")))
#else
#define INTERNAL_VISIBILITY
#endif

template <class RegImpl> class RegSetIterator;
template <class RegImpl> class ReverseRegSetIterator;

// A set of registers
template <class RegImpl>
class AbstractRegSet {
  uint32_t _bitset;

  AbstractRegSet(uint32_t bitset) : _bitset(bitset) { }

public:

  AbstractRegSet() : _bitset(0) { }

  AbstractRegSet(RegImpl r1) : _bitset(1 << r1->encoding()) { }

  AbstractRegSet operator+(const AbstractRegSet aSet) const {
    AbstractRegSet result(_bitset | aSet._bitset);
    return result;
  }

  AbstractRegSet operator-(const AbstractRegSet aSet) const {
    AbstractRegSet result(_bitset & ~aSet._bitset);
    return result;
  }

  AbstractRegSet &operator+=(const AbstractRegSet aSet) {
    *this = *this + aSet;
    return *this;
  }

  AbstractRegSet &operator-=(const AbstractRegSet aSet) {
    *this = *this - aSet;
    return *this;
  }

  static AbstractRegSet of(RegImpl r1) {
    return AbstractRegSet(r1);
  }

  static AbstractRegSet of(RegImpl r1, RegImpl r2) {
    return of(r1) + r2;
  }

  static AbstractRegSet of(RegImpl r1, RegImpl r2, RegImpl r3) {
    return of(r1, r2) + r3;
  }

  static AbstractRegSet of(RegImpl r1, RegImpl r2, RegImpl r3, RegImpl r4) {
    return of(r1, r2, r3) + r4;
  }

  static AbstractRegSet range(RegImpl start, RegImpl end) {
    assert(start <= end, "must be");
    uint32_t bits = ~0;
    bits <<= start->encoding();
    bits <<= 31 - end->encoding();
    bits >>= 31 - end->encoding();

    return AbstractRegSet(bits);
  }

  uint size() const { return population_count(_bitset); }

  uint32_t bits() const { return _bitset; }

private:

  RegImpl first();
  RegImpl last();

public:

  friend class RegSetIterator<RegImpl>;
  friend class ReverseRegSetIterator<RegImpl>;

  RegSetIterator<RegImpl> begin();
  ReverseRegSetIterator<RegImpl> rbegin();
};

template <class RegImpl>
class RegSetIterator {
  AbstractRegSet<RegImpl> _regs;

public:
  RegSetIterator(AbstractRegSet<RegImpl> x): _regs(x) {}
  RegSetIterator(const RegSetIterator& mit) : _regs(mit._regs) {}

  RegSetIterator& operator++() {
    RegImpl r = _regs.first();
    if (r->is_valid())
      _regs -= r;
    return *this;
  }

  bool operator==(const RegSetIterator& rhs) const {
    return _regs.bits() == rhs._regs.bits();
  }
  bool operator!=(const RegSetIterator& rhs) const {
    return ! (rhs == *this);
  }

  RegImpl operator*() {
    return _regs.first();
  }
};

template <class RegImpl>
inline RegSetIterator<RegImpl> AbstractRegSet<RegImpl>::begin() {
  return RegSetIterator<RegImpl>(*this);
}

template <class RegImpl>
class ReverseRegSetIterator {
  AbstractRegSet<RegImpl> _regs;

public:
  ReverseRegSetIterator(AbstractRegSet<RegImpl> x): _regs(x) {}
  ReverseRegSetIterator(const ReverseRegSetIterator& mit) : _regs(mit._regs) {}

  ReverseRegSetIterator& operator++() {
    RegImpl r = _regs.last();
    if (r->is_valid())
      _regs -= r;
    return *this;
  }

  bool operator==(const ReverseRegSetIterator& rhs) const {
    return _regs.bits() == rhs._regs.bits();
  }
  bool operator!=(const ReverseRegSetIterator& rhs) const {
    return ! (rhs == *this);
  }

  RegImpl operator*() {
    return _regs.last();
  }
};

template <class RegImpl>
inline ReverseRegSetIterator<RegImpl> AbstractRegSet<RegImpl>::rbegin() {
  return ReverseRegSetIterator<RegImpl>(*this);
}

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
