/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef OS_CPU_LINUX_PPC_VM_GLOBALS_LINUX_PPC_HPP
#define OS_CPU_LINUX_PPC_VM_GLOBALS_LINUX_PPC_HPP

// Sets the default values for platform dependent flags used by the runtime system.
// (see globals.hpp)

define_pd_global(bool, DontYieldALot,            false);
define_pd_global(intx, ThreadStackSize,          2048); // 0 => use system default
define_pd_global(intx, VMThreadStackSize,        2048);

// if we set CompilerThreadStackSize to a value different than 0, it will
// be used in os::create_thread(). Otherwise, due the strange logic in os::create_thread(),
// the stack size for compiler threads will default to VMThreadStackSize, although it
// is defined to 4M in os::Linux::default_stack_size()!
define_pd_global(intx, CompilerThreadStackSize,  4096);

// Allow extra space in DEBUG builds for asserts.
define_pd_global(uintx,JVMInvokeMethodSlack,     8192);

define_pd_global(intx, StackYellowPages,         6);
define_pd_global(intx, StackRedPages,            1);
define_pd_global(intx, StackShadowPages,         6 DEBUG_ONLY(+2));

// Only used on 64 bit platforms
define_pd_global(uintx,HeapBaseMinAddress,       2*G);
// Only used on 64 bit Windows platforms
define_pd_global(bool, UseVectoredExceptions,    false);

#endif // OS_CPU_LINUX_PPC_VM_GLOBALS_LINUX_PPC_HPP
