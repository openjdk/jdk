/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_ASM_REGISTER_HPP
#define SHARE_VM_ASM_REGISTER_HPP

#include "utilities/top.hpp"

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


//
// Macros for use in defining Register instances.  We'd like to be
// able to simply define const instances of the RegisterImpl* for each
// of the registers needed on a system in a header file.  However many
// compilers don't handle this very well and end up producing a
// private definition in every file which includes the header file.
// Along with the static constructors necessary for initialization it
// can consume a significant amount of space in the result library.
//
// The following macros allow us to declare the instance in a .hpp and
// produce an enumeration value which has the same number.  Then in a
// .cpp the the register instance can be defined using the enumeration
// value.  This avoids the use of static constructors and multiple
// definitions per .cpp.  In addition #defines for the register can be
// produced so that the constant registers can be inlined.  These
// macros should not be used inside other macros, because you may get
// multiple evaluations of the macros which can give bad results.
//
// Here are some example uses and expansions.  Note that the macro
// invocation is terminated with a ;.
//
// CONSTANT_REGISTER_DECLARATION(Register, G0, 0);
//
// extern const Register G0 ;
// enum { G0_RegisterEnumValue = 0 } ;
//
// REGISTER_DECLARATION(Register, Gmethod, G5);
//
// extern const Register Gmethod ;
// enum { Gmethod_RegisterEnumValue = G5_RegisterEnumValue } ;
//
// REGISTER_DEFINITION(Register, G0);
//
// const Register G0 = ( ( Register ) G0_RegisterEnumValue ) ;
//

#define AS_REGISTER(type,name)         ((type)name##_##type##EnumValue)

#define CONSTANT_REGISTER_DECLARATION(type, name, value) \
extern const type name;                                  \
enum { name##_##type##EnumValue = (value) }

#define REGISTER_DECLARATION(type, name, value) \
extern const type name;                         \
enum { name##_##type##EnumValue = value##_##type##EnumValue }

#define REGISTER_DEFINITION(type, name) \
const type name = ((type)name##_##type##EnumValue)

#ifdef TARGET_ARCH_x86
# include "register_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "register_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "register_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "register_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "register_ppc.hpp"
#endif


// Debugging support

inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b
) {
  assert(
    a != b,
    err_msg_res("registers must be different: a=%d, b=%d",
                a, b)
  );
}


inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b,
  AbstractRegister c
) {
  assert(
    a != b && a != c
           && b != c,
    err_msg_res("registers must be different: a=%d, b=%d, c=%d",
                a, b, c)
  );
}


inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b,
  AbstractRegister c,
  AbstractRegister d
) {
  assert(
    a != b && a != c && a != d
           && b != c && b != d
                     && c != d,
    err_msg_res("registers must be different: a=%d, b=%d, c=%d, d=%d",
                a, b, c, d)
  );
}


inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b,
  AbstractRegister c,
  AbstractRegister d,
  AbstractRegister e
) {
  assert(
    a != b && a != c && a != d && a != e
           && b != c && b != d && b != e
                     && c != d && c != e
                               && d != e,
    err_msg_res("registers must be different: a=%d, b=%d, c=%d, d=%d, e=%d",
                a, b, c, d, e)
  );
}


inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b,
  AbstractRegister c,
  AbstractRegister d,
  AbstractRegister e,
  AbstractRegister f
) {
  assert(
    a != b && a != c && a != d && a != e && a != f
           && b != c && b != d && b != e && b != f
                     && c != d && c != e && c != f
                               && d != e && d != f
                                         && e != f,
    err_msg_res("registers must be different: a=%d, b=%d, c=%d, d=%d, e=%d, f=%d",
                a, b, c, d, e, f)
  );
}


inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b,
  AbstractRegister c,
  AbstractRegister d,
  AbstractRegister e,
  AbstractRegister f,
  AbstractRegister g
) {
  assert(
    a != b && a != c && a != d && a != e && a != f && a != g
           && b != c && b != d && b != e && b != f && b != g
                     && c != d && c != e && c != f && c != g
                               && d != e && d != f && d != g
                                         && e != f && e != g
                                                   && f != g,
    err_msg_res("registers must be different: a=%d, b=%d, c=%d, d=%d, e=%d, f=%d, g=%d",
                a, b, c, d, e, f, g)
  );
}


inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b,
  AbstractRegister c,
  AbstractRegister d,
  AbstractRegister e,
  AbstractRegister f,
  AbstractRegister g,
  AbstractRegister h
) {
  assert(
    a != b && a != c && a != d && a != e && a != f && a != g && a != h
           && b != c && b != d && b != e && b != f && b != g && b != h
                     && c != d && c != e && c != f && c != g && c != h
                               && d != e && d != f && d != g && d != h
                                         && e != f && e != g && e != h
                                                   && f != g && f != h
                                                             && g != h,
    err_msg_res("registers must be different: a=%d, b=%d, c=%d, d=%d, e=%d, f=%d, g=%d, h=%d",
                a, b, c, d, e, f, g, h)
  );
}


inline void assert_different_registers(
  AbstractRegister a,
  AbstractRegister b,
  AbstractRegister c,
  AbstractRegister d,
  AbstractRegister e,
  AbstractRegister f,
  AbstractRegister g,
  AbstractRegister h,
  AbstractRegister i
) {
  assert(
    a != b && a != c && a != d && a != e && a != f && a != g && a != h && a != i
           && b != c && b != d && b != e && b != f && b != g && b != h && b != i
                     && c != d && c != e && c != f && c != g && c != h && c != i
                               && d != e && d != f && d != g && d != h && d != i
                                         && e != f && e != g && e != h && e != i
                                                   && f != g && f != h && f != i
                                                             && g != h && g != i
                                                                       && h != i,
    err_msg_res("registers must be different: a=%d, b=%d, c=%d, d=%d, e=%d, f=%d, g=%d, h=%d, i=%d",
                a, b, c, d, e, f, g, h, i)
  );
}

#endif // SHARE_VM_ASM_REGISTER_HPP
