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

#ifndef CPU_X86_VM_C2_GLOBALS_X86_HPP
#define CPU_X86_VM_C2_GLOBALS_X86_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Sets the default values for platform dependent flags used by the server compiler.
// (see c2_globals.hpp).  Alpha-sorted.
define_pd_global(bool, BackgroundCompilation,        true);
define_pd_global(bool, UseTLAB,                      true);
define_pd_global(bool, ResizeTLAB,                   true);
define_pd_global(bool, CICompileOSR,                 true);
define_pd_global(bool, InlineIntrinsics,             true);
define_pd_global(bool, PreferInterpreterNativeStubs, false);
define_pd_global(bool, ProfileTraps,                 true);
define_pd_global(bool, UseOnStackReplacement,        true);
#ifdef CC_INTERP
define_pd_global(bool, ProfileInterpreter,           false);
#else
define_pd_global(bool, ProfileInterpreter,           true);
#endif // CC_INTERP
define_pd_global(bool, TieredCompilation,            trueInTiered);
define_pd_global(intx, CompileThreshold,             10000);
define_pd_global(intx, BackEdgeThreshold,            100000);

define_pd_global(intx, OnStackReplacePercentage,     140);
define_pd_global(intx, ConditionalMoveLimit,         3);
define_pd_global(intx, FLOATPRESSURE,                6);
define_pd_global(intx, FreqInlineSize,               325);
define_pd_global(intx, MinJumpTableSize,             10);
#ifdef AMD64
define_pd_global(intx, INTPRESSURE,                  13);
define_pd_global(intx, InteriorEntryAlignment,       16);
define_pd_global(intx, NewSizeThreadIncrease, ScaleForWordSize(4*K));
define_pd_global(intx, LoopUnrollLimit,              60);
// InitialCodeCacheSize derived from specjbb2000 run.
define_pd_global(intx, InitialCodeCacheSize,         2496*K); // Integral multiple of CodeCacheExpansionSize
define_pd_global(intx, CodeCacheExpansionSize,       64*K);

// Ergonomics related flags
define_pd_global(uint64_t,MaxRAM,                    128ULL*G);
#else
define_pd_global(intx, INTPRESSURE,                  6);
define_pd_global(intx, InteriorEntryAlignment,       4);
define_pd_global(intx, NewSizeThreadIncrease,        4*K);
define_pd_global(intx, LoopUnrollLimit,              50);     // Design center runs on 1.3.1
// InitialCodeCacheSize derived from specjbb2000 run.
define_pd_global(intx, InitialCodeCacheSize,         2304*K); // Integral multiple of CodeCacheExpansionSize
define_pd_global(intx, CodeCacheExpansionSize,       32*K);

// Ergonomics related flags
define_pd_global(uint64_t,MaxRAM,                    4ULL*G);
#endif // AMD64
define_pd_global(intx, RegisterCostAreaRatio,        16000);

// Peephole and CISC spilling both break the graph, and so makes the
// scheduler sick.
define_pd_global(bool, OptoPeephole,                 true);
define_pd_global(bool, UseCISCSpill,                 true);
define_pd_global(bool, OptoScheduling,               false);
define_pd_global(bool, OptoBundling,                 false);

define_pd_global(intx, ReservedCodeCacheSize,        48*M);
define_pd_global(uintx, CodeCacheMinBlockLength,     4);
define_pd_global(uintx, CodeCacheMinimumUseSpace,    400*K);

// Heap related flags
define_pd_global(uintx,MetaspaceSize,    ScaleForWordSize(16*M));

// Ergonomics related flags
define_pd_global(bool, NeverActAsServerClassMachine, false);

#endif // CPU_X86_VM_C2_GLOBALS_X86_HPP
