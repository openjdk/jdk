/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009, 2010 Red Hat, Inc.
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

#ifndef CPU_ZERO_VM_SHARK_GLOBALS_ZERO_HPP
#define CPU_ZERO_VM_SHARK_GLOBALS_ZERO_HPP

// Set the default values for platform dependent flags used by the
// Shark compiler.  See globals.hpp for details of what they do.

define_pd_global(bool,     BackgroundCompilation,        true );
define_pd_global(bool,     UseTLAB,                      true );
define_pd_global(bool,     ResizeTLAB,                   true );
define_pd_global(bool,     InlineIntrinsics,             false);
define_pd_global(bool,     PreferInterpreterNativeStubs, false);
define_pd_global(bool,     ProfileTraps,                 false);
define_pd_global(bool,     UseOnStackReplacement,        true );
define_pd_global(bool,     TieredCompilation,            false);

define_pd_global(intx,     CompileThreshold,             1500);
define_pd_global(intx,     Tier2CompileThreshold,        1500);
define_pd_global(intx,     Tier3CompileThreshold,        2500);
define_pd_global(intx,     Tier4CompileThreshold,        4500);

define_pd_global(intx,     BackEdgeThreshold,            100000);
define_pd_global(intx,     Tier2BackEdgeThreshold,       100000);
define_pd_global(intx,     Tier3BackEdgeThreshold,       100000);
define_pd_global(intx,     Tier4BackEdgeThreshold,       100000);

define_pd_global(intx,     OnStackReplacePercentage,     933  );
define_pd_global(intx,     FreqInlineSize,               325  );
define_pd_global(intx,     InlineSmallCode,              1000 );
define_pd_global(uintx,    NewRatio,                     12   );
define_pd_global(intx,     NewSizeThreadIncrease,        4*K  );
define_pd_global(intx,     InitialCodeCacheSize,         160*K);
define_pd_global(intx,     ReservedCodeCacheSize,        32*M );
define_pd_global(bool,     ProfileInterpreter,           false);
define_pd_global(intx,     CodeCacheExpansionSize,       32*K );
define_pd_global(uintx,    CodeCacheMinBlockLength,      1    );
define_pd_global(uintx,    CodeCacheMinimumUseSpace,     200*K);

define_pd_global(uintx,    MetaspaceSize,                12*M );
define_pd_global(bool,     NeverActAsServerClassMachine, true );
define_pd_global(uint64_t, MaxRAM,                       1ULL*G);
define_pd_global(bool,     CICompileOSR,                 true );

#endif // CPU_ZERO_VM_SHARK_GLOBALS_ZERO_HPP
