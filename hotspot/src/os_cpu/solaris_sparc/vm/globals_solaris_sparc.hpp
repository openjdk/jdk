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

#ifndef OS_CPU_SOLARIS_SPARC_VM_GLOBALS_SOLARIS_SPARC_HPP
#define OS_CPU_SOLARIS_SPARC_VM_GLOBALS_SOLARIS_SPARC_HPP

//
// Sets the default values for platform dependent flags used by the runtime system.
// (see globals.hpp)
//

define_pd_global(uintx, JVMInvokeMethodSlack,    12288);
define_pd_global(intx, CompilerThreadStackSize,  0);

// Used on 64 bit platforms for UseCompressedOops base address
#ifdef _LP64
// use 6G as default base address because by default the OS maps the application
// to 4G on Solaris-Sparc. This leaves at least 2G for the native heap.
define_pd_global(uintx, HeapBaseMinAddress,      CONST64(6)*G);
#else
define_pd_global(uintx, HeapBaseMinAddress,      2*G);
#endif



#endif // OS_CPU_SOLARIS_SPARC_VM_GLOBALS_SOLARIS_SPARC_HPP
