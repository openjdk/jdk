/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

// Allow commas in macro arguments.
#define COMMA ,

// Apply pre-processor token pasting to the expansions of x and y.
// The token pasting operator (##) prevents its arguments from being
// expanded.  This macro allows expansion of its arguments before the
// concatenation is performed.  Note: One auxilliary level ought to be
// sufficient, but two are used because of bugs in some preprocesors.
#define PASTE_TOKENS(x, y) PASTE_TOKENS_AUX(x, y)
#define PASTE_TOKENS_AUX(x, y) PASTE_TOKENS_AUX2(x, y)
#define PASTE_TOKENS_AUX2(x, y) x ## y

// -DINCLUDE_<something>=0 | 1 can be specified on the command line to include
// or exclude functionality.

#ifndef INCLUDE_JVMTI
#define INCLUDE_JVMTI 1
#endif  // INCLUDE_JVMTI

#if INCLUDE_JVMTI
#define JVMTI_ONLY(x) x
#define NOT_JVMTI(x)
#define NOT_JVMTI_RETURN
#define NOT_JVMTI_RETURN_(code) /* next token must be ; */
#else
#define JVMTI_ONLY(x)
#define NOT_JVMTI(x) x
#define NOT_JVMTI_RETURN { return; }
#define NOT_JVMTI_RETURN_(code) { return code; }
#endif // INCLUDE_JVMTI

#ifndef INCLUDE_FPROF
#define INCLUDE_FPROF 1
#endif

#if INCLUDE_FPROF
#define NOT_FPROF_RETURN        /* next token must be ; */
#define NOT_FPROF_RETURN_(code) /* next token must be ; */
#else
#define NOT_FPROF_RETURN                {}
#define NOT_FPROF_RETURN_(code) { return code; }
#endif // INCLUDE_FPROF

#ifndef INCLUDE_VM_STRUCTS
#define INCLUDE_VM_STRUCTS 1
#endif

#if INCLUDE_VM_STRUCTS
#define NOT_VM_STRUCTS_RETURN        /* next token must be ; */
#define NOT_VM_STRUCTS_RETURN_(code) /* next token must be ; */
#else
#define NOT_VM_STRUCTS_RETURN           {}
#define NOT_VM_STRUCTS_RETURN_(code) { return code; }
#endif // INCLUDE_VM_STRUCTS

#ifndef INCLUDE_JNI_CHECK
#define INCLUDE_JNI_CHECK 1
#endif

#if INCLUDE_JNI_CHECK
#define NOT_JNI_CHECK_RETURN        /* next token must be ; */
#define NOT_JNI_CHECK_RETURN_(code) /* next token must be ; */
#else
#define NOT_JNI_CHECK_RETURN            {}
#define NOT_JNI_CHECK_RETURN_(code) { return code; }
#endif // INCLUDE_JNI_CHECK

#ifndef INCLUDE_SERVICES
#define INCLUDE_SERVICES 1
#endif

#if INCLUDE_SERVICES
#define NOT_SERVICES_RETURN        /* next token must be ; */
#define NOT_SERVICES_RETURN_(code) /* next token must be ; */
#else
#define NOT_SERVICES_RETURN             {}
#define NOT_SERVICES_RETURN_(code) { return code; }
#endif // INCLUDE_SERVICES

#ifndef INCLUDE_CDS
#define INCLUDE_CDS 1
#endif

#if INCLUDE_CDS
#define CDS_ONLY(x) x
#define NOT_CDS(x)
#define NOT_CDS_RETURN        /* next token must be ; */
#define NOT_CDS_RETURN_(code) /* next token must be ; */
#else
#define CDS_ONLY(x)
#define NOT_CDS(x) x
#define NOT_CDS_RETURN          {}
#define NOT_CDS_RETURN_(code) { return code; }
#endif // INCLUDE_CDS

#ifndef INCLUDE_MANAGEMENT
#define INCLUDE_MANAGEMENT 1
#endif // INCLUDE_MANAGEMENT

#if INCLUDE_MANAGEMENT
#define NOT_MANAGEMENT_RETURN        /* next token must be ; */
#define NOT_MANAGEMENT_RETURN_(code) /* next token must be ; */
#else
#define NOT_MANAGEMENT_RETURN        {}
#define NOT_MANAGEMENT_RETURN_(code) { return code; }
#endif // INCLUDE_MANAGEMENT

/*
 * When INCLUDE_ALL_GCS is false the only garbage collectors
 * included in the JVM are defaultNewGeneration and markCompact.
 *
 * When INCLUDE_ALL_GCS is true all garbage collectors are
 * included in the JVM.
 */
#ifndef INCLUDE_ALL_GCS
#define INCLUDE_ALL_GCS 1
#endif // INCLUDE_ALL_GCS

#if INCLUDE_ALL_GCS
#define NOT_ALL_GCS_RETURN        /* next token must be ; */
#define NOT_ALL_GCS_RETURN_(code) /* next token must be ; */
#else
#define NOT_ALL_GCS_RETURN        {}
#define NOT_ALL_GCS_RETURN_(code) { return code; }
#endif // INCLUDE_ALL_GCS

#ifndef INCLUDE_NMT
#define INCLUDE_NMT 1
#endif // INCLUDE_NMT

#if INCLUDE_NMT
#define NOT_NMT_RETURN        /* next token must be ; */
#define NOT_NMT_RETURN_(code) /* next token must be ; */
#else
#define NOT_NMT_RETURN        {}
#define NOT_NMT_RETURN_(code) { return code; }
#endif // INCLUDE_NMT

#ifndef INCLUDE_TRACE
#define INCLUDE_TRACE 1
#endif // INCLUDE_TRACE

#ifndef INCLUDE_JVMCI
#define INCLUDE_JVMCI 1
#endif

#ifdef INCLUDE_AOT
# if INCLUDE_AOT && !(INCLUDE_JVMCI)
#   error "Must have JVMCI for AOT"
# endif
#else
# define INCLUDE_AOT 0
#endif

#if INCLUDE_JVMCI
#define JVMCI_ONLY(code) code
#define NOT_JVMCI(code)
#define NOT_JVMCI_RETURN /* next token must be ; */
#else
#define JVMCI_ONLY(code)
#define NOT_JVMCI(code) code
#define NOT_JVMCI_RETURN {}
#endif // INCLUDE_JVMCI

#if INCLUDE_AOT
#define AOT_ONLY(code) code
#define NOT_AOT(code)
#define NOT_AOT_RETURN /* next token must be ; */
#else
#define AOT_ONLY(code)
#define NOT_AOT(code) code
#define NOT_AOT_RETURN {}
#endif // INCLUDE_AOT

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

// COMPILER2 or JVMCI
#if defined(COMPILER2) || INCLUDE_JVMCI
#define COMPILER2_OR_JVMCI 1
#define COMPILER2_OR_JVMCI_PRESENT(code) code
#define NOT_COMPILER2_OR_JVMCI(code)
#else
#define COMPILER2_OR_JVMCI 0
#define COMPILER2_OR_JVMCI_PRESENT(code)
#define NOT_COMPILER2_OR_JVMCI(code) code
#endif

#ifdef TIERED
#define TIERED_ONLY(code) code
#define NOT_TIERED(code)
#else // TIERED
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

#ifdef AIX
#define AIX_ONLY(code) code
#define NOT_AIX(code)
#else
#define AIX_ONLY(code)
#define NOT_AIX(code) code
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

#if defined(__FreeBSD__) || defined(__NetBSD__) || defined(__OpenBSD__) || defined(__APPLE__)
#define BSD
#define BSD_ONLY(code) code
#define NOT_BSD(code)
#else
#define BSD_ONLY(code)
#define NOT_BSD(code) code
#endif

#ifdef _WIN64
#define WIN64_ONLY(code) code
#define NOT_WIN64(code)
#else
#define WIN64_ONLY(code)
#define NOT_WIN64(code) code
#endif

#if defined(ZERO)
#define ZERO_ONLY(code) code
#define NOT_ZERO(code)
#else
#define ZERO_ONLY(code)
#define NOT_ZERO(code) code
#endif

#if defined(SHARK)
#define SHARK_ONLY(code) code
#define NOT_SHARK(code)
#else
#define SHARK_ONLY(code)
#define NOT_SHARK(code) code
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

// This is a REALLY BIG HACK, but on AIX <sys/systemcfg.h> unconditionally defines IA64.
// At least on AIX 7.1 this is a real problem because 'systemcfg.h' is indirectly included
// by 'pthread.h' and other common system headers.

#if defined(IA64) && !defined(AIX)
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

#ifdef S390
#define S390_ONLY(code) code
#define NOT_S390(code)
#else
#define S390_ONLY(code)
#define NOT_S390(code) code
#endif

#ifdef SPARC
#define SPARC_ONLY(code) code
#define NOT_SPARC(code)
#else
#define SPARC_ONLY(code)
#define NOT_SPARC(code) code
#endif

#if defined(PPC32) || defined(PPC64)
#ifndef PPC
#define PPC
#endif
#define PPC_ONLY(code) code
#define NOT_PPC(code)
#else
#undef PPC
#define PPC_ONLY(code)
#define NOT_PPC(code) code
#endif

#ifdef PPC32
#define PPC32_ONLY(code) code
#define NOT_PPC32(code)
#else
#define PPC32_ONLY(code)
#define NOT_PPC32(code) code
#endif

#ifdef PPC64
#define PPC64_ONLY(code) code
#define NOT_PPC64(code)
#else
#define PPC64_ONLY(code)
#define NOT_PPC64(code) code
#endif

#ifdef E500V2
#define E500V2_ONLY(code) code
#define NOT_E500V2(code)
#else
#define E500V2_ONLY(code)
#define NOT_E500V2(code) code
#endif

// Note: There are three ARM ports. They set the following in the makefiles:
// 1. Closed 32-bit port:   -DARM -DARM32           -DTARGET_ARCH_arm
// 2. Closed 64-bit port:   -DARM -DAARCH64 -D_LP64 -DTARGET_ARCH_arm
// 3. Open   64-bit port:         -DAARCH64 -D_LP64 -DTARGET_ARCH_aaarch64
#ifdef ARM
#define ARM_ONLY(code) code
#define NOT_ARM(code)
#else
#define ARM_ONLY(code)
#define NOT_ARM(code) code
#endif

#ifdef ARM32
#define ARM32_ONLY(code) code
#define NOT_ARM32(code)
#else
#define ARM32_ONLY(code)
#define NOT_ARM32(code) code
#endif

#ifdef AARCH64
#define AARCH64_ONLY(code) code
#define NOT_AARCH64(code)
#else
#define AARCH64_ONLY(code)
#define NOT_AARCH64(code) code
#endif

#define define_pd_global(type, name, value) const type pd_##name = value;

// Helper macros for constructing file names for includes.
#define CPU_HEADER_STEM(basename) PASTE_TOKENS(basename, INCLUDE_SUFFIX_CPU)
#define OS_HEADER_STEM(basename) PASTE_TOKENS(basename, INCLUDE_SUFFIX_OS)
#define OS_CPU_HEADER_STEM(basename) PASTE_TOKENS(basename, PASTE_TOKENS(INCLUDE_SUFFIX_OS, INCLUDE_SUFFIX_CPU))

// Include platform dependent files.
//
// This macro constructs from basename and INCLUDE_SUFFIX_OS /
// INCLUDE_SUFFIX_CPU, which are set on the command line, the name of
// platform dependent files to be included.
// Example: INCLUDE_SUFFIX_OS=_linux / INCLUDE_SUFFIX_CPU=_sparc
//   CPU_HEADER_INLINE(macroAssembler) --> macroAssembler_sparc.inline.hpp
//   OS_CPU_HEADER(vmStructs)          --> vmStructs_linux_sparc.hpp
//
// basename<cpu>.hpp / basename<cpu>.inline.hpp
#define CPU_HEADER_H(basename)         XSTR(CPU_HEADER_STEM(basename).h)
#define CPU_HEADER(basename)           XSTR(CPU_HEADER_STEM(basename).hpp)
#define CPU_HEADER_INLINE(basename)    XSTR(CPU_HEADER_STEM(basename).inline.hpp)
// basename<os>.hpp / basename<os>.inline.hpp
#define OS_HEADER_H(basename)          XSTR(OS_HEADER_STEM(basename).h)
#define OS_HEADER(basename)            XSTR(OS_HEADER_STEM(basename).hpp)
#define OS_HEADER_INLINE(basename)     XSTR(OS_HEADER_STEM(basename).inline.hpp)
// basename<os><cpu>.hpp / basename<os><cpu>.inline.hpp
#define OS_CPU_HEADER(basename)        XSTR(OS_CPU_HEADER_STEM(basename).hpp)
#define OS_CPU_HEADER_INLINE(basename) XSTR(OS_CPU_HEADER_STEM(basename).inline.hpp)

// To use Atomic::inc(jshort* dest) and Atomic::dec(jshort* dest), the address must be specially
// aligned, such that (*dest) occupies the upper 16 bits of an aligned 32-bit word. The best way to
// achieve is to place your short value next to another short value, which doesn't need atomic ops.
//
// Example
//  ATOMIC_SHORT_PAIR(
//    volatile short _refcount,  // needs atomic operation
//    unsigned short _length     // number of UTF8 characters in the symbol (does not need atomic op)
//  );

#ifdef VM_LITTLE_ENDIAN
  #define ATOMIC_SHORT_PAIR(atomic_decl, non_atomic_decl)  \
    non_atomic_decl;                                       \
    atomic_decl
#else
  #define ATOMIC_SHORT_PAIR(atomic_decl, non_atomic_decl)  \
    atomic_decl;                                           \
    non_atomic_decl
#endif

#endif // SHARE_VM_UTILITIES_MACROS_HPP
