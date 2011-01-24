/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_MACROS_HPP
#define SHARE_VM_UTILITIES_MACROS_HPP

// Use this to mark code that needs to be cleaned up (for development only)
#define NEEDS_CLEANUP

// Makes a string of the argument (which is not macro-expanded)
#define STR(a)  #a

// Makes a string of the macro expansion of a
#define XSTR(a) STR(a)

// KERNEL variant
#ifdef KERNEL
#define COMPILER1
#define SERIALGC

#define JVMTI_KERNEL
#define FPROF_KERNEL
#define VM_STRUCTS_KERNEL
#define JNICHECK_KERNEL
#define SERVICES_KERNEL

#define KERNEL_RETURN        {}
#define KERNEL_RETURN_(code) { code }

#else  // KERNEL

#define KERNEL_RETURN        /* next token must be ; */
#define KERNEL_RETURN_(code) /* next token must be ; */

#endif // KERNEL

// COMPILER1 variant
#ifdef COMPILER1
#ifdef COMPILER2
  #define TIERED
#endif
#define COMPILER1_PRESENT(code) code
#else // COMPILER1
#define COMPILER1_PRESENT(code)
#endif // COMPILER1

// COMPILER2 variant
#ifdef COMPILER2
#define COMPILER2_PRESENT(code) code
#define NOT_COMPILER2(code)
#else // COMPILER2
#define COMPILER2_PRESENT(code)
#define NOT_COMPILER2(code) code
#endif // COMPILER2

#ifdef TIERED
#define TIERED_ONLY(code) code
#define NOT_TIERED(code)
#else
#define TIERED_ONLY(code)
#define NOT_TIERED(code) code
#endif // TIERED


// PRODUCT variant
#ifdef PRODUCT
#define PRODUCT_ONLY(code) code
#define NOT_PRODUCT(code)
#define NOT_PRODUCT_ARG(arg)
#define PRODUCT_RETURN  {}
#define PRODUCT_RETURN0 { return 0; }
#define PRODUCT_RETURN_(code) { code }
#else // PRODUCT
#define PRODUCT_ONLY(code)
#define NOT_PRODUCT(code) code
#define NOT_PRODUCT_ARG(arg) arg,
#define PRODUCT_RETURN  /*next token must be ;*/
#define PRODUCT_RETURN0 /*next token must be ;*/
#define PRODUCT_RETURN_(code)  /*next token must be ;*/
#endif // PRODUCT

#ifdef CHECK_UNHANDLED_OOPS
#define CHECK_UNHANDLED_OOPS_ONLY(code) code
#define NOT_CHECK_UNHANDLED_OOPS(code)
#else
#define CHECK_UNHANDLED_OOPS_ONLY(code)
#define NOT_CHECK_UNHANDLED_OOPS(code)  code
#endif // CHECK_UNHANDLED_OOPS

#ifdef CC_INTERP
#define CC_INTERP_ONLY(code) code
#define NOT_CC_INTERP(code)
#else
#define CC_INTERP_ONLY(code)
#define NOT_CC_INTERP(code) code
#endif // CC_INTERP

#ifdef ASSERT
#define DEBUG_ONLY(code) code
#define NOT_DEBUG(code)
#define NOT_DEBUG_RETURN  /*next token must be ;*/
// Historical.
#define debug_only(code) code
#else // ASSERT
#define DEBUG_ONLY(code)
#define NOT_DEBUG(code) code
#define NOT_DEBUG_RETURN {}
#define debug_only(code)
#endif // ASSERT

#ifdef  _LP64
#define LP64_ONLY(code) code
#define NOT_LP64(code)
#else  // !_LP64
#define LP64_ONLY(code)
#define NOT_LP64(code) code
#endif // _LP64

#ifdef LINUX
#define LINUX_ONLY(code) code
#define NOT_LINUX(code)
#else
#define LINUX_ONLY(code)
#define NOT_LINUX(code) code
#endif

#ifdef SOLARIS
#define SOLARIS_ONLY(code) code
#define NOT_SOLARIS(code)
#else
#define SOLARIS_ONLY(code)
#define NOT_SOLARIS(code) code
#endif

#ifdef _WINDOWS
#define WINDOWS_ONLY(code) code
#define NOT_WINDOWS(code)
#else
#define WINDOWS_ONLY(code)
#define NOT_WINDOWS(code) code
#endif

#if defined(IA32) || defined(AMD64)
#define X86
#define X86_ONLY(code) code
#define NOT_X86(code)
#else
#undef X86
#define X86_ONLY(code)
#define NOT_X86(code) code
#endif

#ifdef IA32
#define IA32_ONLY(code) code
#define NOT_IA32(code)
#else
#define IA32_ONLY(code)
#define NOT_IA32(code) code
#endif

#ifdef IA64
#define IA64_ONLY(code) code
#define NOT_IA64(code)
#else
#define IA64_ONLY(code)
#define NOT_IA64(code) code
#endif

#ifdef AMD64
#define AMD64_ONLY(code) code
#define NOT_AMD64(code)
#else
#define AMD64_ONLY(code)
#define NOT_AMD64(code) code
#endif

#ifdef SPARC
#define SPARC_ONLY(code) code
#define NOT_SPARC(code)
#else
#define SPARC_ONLY(code)
#define NOT_SPARC(code) code
#endif

#ifdef PPC
#define PPC_ONLY(code) code
#define NOT_PPC(code)
#else
#define PPC_ONLY(code)
#define NOT_PPC(code) code
#endif

#ifdef E500V2
#define E500V2_ONLY(code) code
#define NOT_E500V2(code)
#else
#define E500V2_ONLY(code)
#define NOT_E500V2(code) code
#endif


#ifdef ARM
#define ARM_ONLY(code) code
#define NOT_ARM(code)
#else
#define ARM_ONLY(code)
#define NOT_ARM(code) code
#endif

#ifdef JAVASE_EMBEDDED
#define EMBEDDED_ONLY(code) code
#define NOT_EMBEDDED(code)
#else
#define EMBEDDED_ONLY(code)
#define NOT_EMBEDDED(code) code
#endif

#define define_pd_global(type, name, value) const type pd_##name = value;

#endif // SHARE_VM_UTILITIES_MACROS_HPP
