/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_PHASE_HPP
#define SHARE_VM_OPTO_PHASE_HPP

#include "libadt/port.hpp"
#include "runtime/timer.hpp"

class Compile;

//------------------------------Phase------------------------------------------
// Most optimizations are done in Phases.  Creating a phase does any long
// running analysis required, and caches the analysis in internal data
// structures.  Later the analysis is queried using transform() calls to
// guide transforming the program.  When the Phase is deleted, so is any
// cached analysis info.  This basic Phase class mostly contains timing and
// memory management code.
class Phase : public StackObj {
public:
  enum PhaseNumber {
    Compiler,                   // Top-level compiler phase
    Parser,                     // Parse bytecodes
    Remove_Useless,             // Remove useless nodes
    Optimistic,                 // Optimistic analysis phase
    GVN,                        // Pessimistic global value numbering phase
    Ins_Select,                 // Instruction selection phase
    CFG,                        // Build a CFG
    BlockLayout,                // Linear ordering of blocks
    Register_Allocation,        // Register allocation, duh
    LIVE,                       // Dragon-book LIVE range problem
    StringOpts,                 // StringBuilder related optimizations
    Interference_Graph,         // Building the IFG
    Coalesce,                   // Coalescing copies
    Ideal_Loop,                 // Find idealized trip-counted loops
    Macro_Expand,               // Expand macro nodes
    Peephole,                   // Apply peephole optimizations
    last_phase
  };
protected:
  enum PhaseNumber _pnum;       // Phase number (for stat gathering)

#ifndef PRODUCT
  static int _total_bytes_compiled;

  // accumulated timers
  static elapsedTimer _t_totalCompilation;
  static elapsedTimer _t_methodCompilation;
  static elapsedTimer _t_stubCompilation;
#endif

// The next timers used for LogCompilation
  static elapsedTimer _t_parser;
  static elapsedTimer _t_optimizer;
public:
  // ConnectionGraph can't be Phase since it is used after EA done.
  static elapsedTimer   _t_escapeAnalysis;
  static elapsedTimer     _t_connectionGraph;
protected:
  static elapsedTimer   _t_idealLoop;
  static elapsedTimer   _t_ccp;
  static elapsedTimer _t_matcher;
  static elapsedTimer _t_registerAllocation;
  static elapsedTimer _t_output;

#ifndef PRODUCT
  static elapsedTimer _t_graphReshaping;
  static elapsedTimer _t_scheduler;
  static elapsedTimer _t_blockOrdering;
  static elapsedTimer _t_macroEliminate;
  static elapsedTimer _t_macroExpand;
  static elapsedTimer _t_peephole;
  static elapsedTimer _t_codeGeneration;
  static elapsedTimer _t_registerMethod;
  static elapsedTimer _t_temporaryTimer1;
  static elapsedTimer _t_temporaryTimer2;
  static elapsedTimer _t_idealLoopVerify;

// Subtimers for _t_optimizer
  static elapsedTimer   _t_iterGVN;
  static elapsedTimer   _t_iterGVN2;
  static elapsedTimer   _t_incrInline;

// Subtimers for _t_registerAllocation
  static elapsedTimer   _t_ctorChaitin;
  static elapsedTimer   _t_buildIFGphysical;
  static elapsedTimer   _t_computeLive;
  static elapsedTimer   _t_regAllocSplit;
  static elapsedTimer   _t_postAllocCopyRemoval;
  static elapsedTimer   _t_fixupSpills;

// Subtimers for _t_output
  static elapsedTimer   _t_instrSched;
  static elapsedTimer   _t_buildOopMaps;
#endif
public:
  Compile * C;
  Phase( PhaseNumber pnum );
#ifndef PRODUCT
  static void print_timers();
#endif
};

#endif // SHARE_VM_OPTO_PHASE_HPP
