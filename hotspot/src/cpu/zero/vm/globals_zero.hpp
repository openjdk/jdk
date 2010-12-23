/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2009, 2010 Red Hat, Inc.
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

#ifndef CPU_ZERO_VM_GLOBALS_ZERO_HPP
#define CPU_ZERO_VM_GLOBALS_ZERO_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Set the default values for platform dependent flags used by the
// runtime system.  See globals.hpp for details of what they do.

define_pd_global(bool,  ConvertSleepToYield,  true);
define_pd_global(bool,  ShareVtableStubs,     true);
define_pd_global(bool,  CountInterpCalls,     true);
define_pd_global(bool,  NeedsDeoptSuspend,    false);

define_pd_global(bool,  ImplicitNullChecks,   true);
define_pd_global(bool,  UncommonNullCast,     true);

define_pd_global(intx,  CodeEntryAlignment,   32);
define_pd_global(intx,  OptoLoopAlignment,    16);
define_pd_global(intx,  InlineFrequencyCount, 100);
define_pd_global(intx,  PreInflateSpin,       10);

define_pd_global(intx,  StackYellowPages,     2);
define_pd_global(intx,  StackRedPages,        1);
define_pd_global(intx,  StackShadowPages,     5 LP64_ONLY(+1) DEBUG_ONLY(+3));

define_pd_global(bool,  RewriteBytecodes,     true);
define_pd_global(bool,  RewriteFrequentPairs, true);

define_pd_global(bool,  UseMembar,            false);

#endif // CPU_ZERO_VM_GLOBALS_ZERO_HPP
