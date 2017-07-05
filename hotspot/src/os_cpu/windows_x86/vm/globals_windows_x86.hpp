/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

//
// Sets the default values for platform dependent flags used by the runtime system.
// (see globals.hpp)
//
define_pd_global(bool, DontYieldALot,            false);

// Default stack size on Windows is determined by the executable (java.exe
// has a default value of 320K/1MB [32bit/64bit]). Depending on Windows version, changing
// ThreadStackSize to non-zero may have significant impact on memory usage.
// See comments in os_windows.cpp.
define_pd_global(intx, ThreadStackSize,          0); // 0 => use system default
define_pd_global(intx, VMThreadStackSize,        0); // 0 => use system default

define_pd_global(intx, SurvivorRatio,            8);

#ifdef ASSERT
define_pd_global(intx, CompilerThreadStackSize,  1024);
#else
define_pd_global(intx, CompilerThreadStackSize,  0);
#endif

define_pd_global(uintx, JVMInvokeMethodSlack,    8192);

// Only used on 64 bit platforms
define_pd_global(uintx, HeapBaseMinAddress,      2*G);
// Only used on 64 bit Windows platforms
define_pd_global(bool, UseVectoredExceptions,    false);
