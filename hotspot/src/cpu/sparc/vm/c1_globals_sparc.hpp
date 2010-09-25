/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

// Sets the default values for platform dependent flags used by the client compiler.
// (see c1_globals.hpp)

#ifndef TIERED
define_pd_global(bool, BackgroundCompilation,        true );
define_pd_global(bool, CICompileOSR,                 true );
define_pd_global(bool, InlineIntrinsics,             true );
define_pd_global(bool, PreferInterpreterNativeStubs, false);
define_pd_global(bool, ProfileTraps,                 false);
define_pd_global(bool, UseOnStackReplacement,        true );
define_pd_global(bool, TieredCompilation,            false);
define_pd_global(intx, CompileThreshold,             1000 ); // Design center runs on 1.3.1
define_pd_global(intx, BackEdgeThreshold,            100000);

define_pd_global(intx, OnStackReplacePercentage,     1400 );
define_pd_global(bool, UseTLAB,                      true );
define_pd_global(bool, ProfileInterpreter,           false);
define_pd_global(intx, FreqInlineSize,               325  );
define_pd_global(bool, ResizeTLAB,                   true );
define_pd_global(intx, ReservedCodeCacheSize,        32*M );
define_pd_global(intx, CodeCacheExpansionSize,       32*K );
define_pd_global(uintx,CodeCacheMinBlockLength,      1);
define_pd_global(uintx,PermSize,                     12*M );
define_pd_global(uintx,MaxPermSize,                  64*M );
define_pd_global(bool, NeverActAsServerClassMachine, true );
define_pd_global(intx, NewSizeThreadIncrease,        16*K );
define_pd_global(uint64_t,MaxRAM,                    1ULL*G);
define_pd_global(intx, InitialCodeCacheSize,         160*K);
#endif // !TIERED

define_pd_global(bool, UseTypeProfile,               false);
define_pd_global(bool, RoundFPResults,               false);

define_pd_global(bool, LIRFillDelaySlots,            true );
define_pd_global(bool, OptimizeSinglePrecision,      false);
define_pd_global(bool, CSEArrayLength,               true );
define_pd_global(bool, TwoOperandLIRForm,            false);

define_pd_global(intx, SafepointPollOffset,          0    );
