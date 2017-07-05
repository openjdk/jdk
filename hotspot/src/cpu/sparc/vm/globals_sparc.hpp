/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// Sets the default values for platform dependent flags used by the runtime system.
// (see globals.hpp)

// For sparc we do not do call backs when a thread is in the interpreter, because the
// interpreter dispatch needs at least two instructions - first to load the dispatch address
// in a register, and second to jmp. The swapping of the dispatch table may occur _after_
// the load of the dispatch address and hence the jmp would still go to the location
// according to the prior table. So, we let the thread continue and let it block by itself.
define_pd_global(bool, DontYieldALot,               true);  // yield no more than 100 times per second
define_pd_global(bool, ConvertSleepToYield,         false); // do not convert sleep(0) to yield. Helps GUI
define_pd_global(bool, ShareVtableStubs,            false); // improves performance markedly for mtrt and compress
define_pd_global(bool, CountInterpCalls,            false); // not implemented in the interpreter
define_pd_global(bool, NeedsDeoptSuspend,           true); // register window machines need this

define_pd_global(bool, ImplicitNullChecks,          true);  // Generate code for implicit null checks
define_pd_global(bool, UncommonNullCast,            true);  // Uncommon-trap NULLs past to check cast

define_pd_global(intx, CodeEntryAlignment,    32);
define_pd_global(intx, InlineFrequencyCount,  50);  // we can use more inlining on the SPARC
define_pd_global(intx, InlineSmallCode,       1500);
#ifdef _LP64
// Stack slots are 2X larger in LP64 than in the 32 bit VM.
define_pd_global(intx, ThreadStackSize,       1024);
define_pd_global(intx, VMThreadStackSize,     1024);
#else
define_pd_global(intx, ThreadStackSize,       512);
define_pd_global(intx, VMThreadStackSize,     512);
#endif

define_pd_global(intx, StackYellowPages, 2);
define_pd_global(intx, StackRedPages, 1);
define_pd_global(intx, StackShadowPages, 3 DEBUG_ONLY(+1));

define_pd_global(intx, PreInflateSpin,       40);  // Determined by running design center

define_pd_global(bool, RewriteBytecodes,     true);
define_pd_global(bool, RewriteFrequentPairs, true);
