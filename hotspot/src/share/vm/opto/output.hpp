/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_OUTPUT_HPP
#define SHARE_VM_OPTO_OUTPUT_HPP

#include "opto/ad.hpp"
#include "opto/block.hpp"
#include "opto/node.hpp"

class Arena;
class Bundle;
class Block;
class Block_Array;
class Node;
class Node_Array;
class Node_List;
class PhaseCFG;
class PhaseChaitin;
class Pipeline_Use_Element;
class Pipeline_Use;

#ifndef PRODUCT
#define DEBUG_ARG(x) , x
#else
#define DEBUG_ARG(x)
#endif

// Define the initial sizes for allocation of the resizable code buffer
enum {
  initial_code_capacity  =  16 * 1024,
  initial_stub_capacity  =   4 * 1024,
  initial_const_capacity =   4 * 1024,
  initial_locs_capacity  =   3 * 1024
};

//------------------------------Scheduling----------------------------------
// This class contains all the information necessary to implement instruction
// scheduling and bundling.
class Scheduling {

private:
  // Arena to use
  Arena *_arena;

  // Control-Flow Graph info
  PhaseCFG *_cfg;

  // Register Allocation info
  PhaseRegAlloc *_regalloc;

  // Number of nodes in the method
  uint _node_bundling_limit;

  // List of scheduled nodes. Generated in reverse order
  Node_List _scheduled;

  // List of nodes currently available for choosing for scheduling
  Node_List _available;

  // For each instruction beginning a bundle, the number of following
  // nodes to be bundled with it.
  Bundle *_node_bundling_base;

  // Mapping from register to Node
  Node_List _reg_node;

  // Free list for pinch nodes.
  Node_List _pinch_free_list;

  // Latency from the beginning of the containing basic block (base 1)
  // for each node.
  unsigned short *_node_latency;

  // Number of uses of this node within the containing basic block.
  short *_uses;

  // Schedulable portion of current block.  Skips Region/Phi/CreateEx up
  // front, branch+proj at end.  Also skips Catch/CProj (same as
  // branch-at-end), plus just-prior exception-throwing call.
  uint _bb_start, _bb_end;

  // Latency from the end of the basic block as scheduled
  unsigned short *_current_latency;

  // Remember the next node
  Node *_next_node;

  // Use this for an unconditional branch delay slot
  Node *_unconditional_delay_slot;

  // Pointer to a Nop
  MachNopNode *_nop;

  // Length of the current bundle, in instructions
  uint _bundle_instr_count;

  // Current Cycle number, for computing latencies and bundling
  uint _bundle_cycle_number;

  // Bundle information
  Pipeline_Use_Element _bundle_use_elements[resource_count];
  Pipeline_Use         _bundle_use;

  // Dump the available list
  void dump_available() const;

public:
  Scheduling(Arena *arena, Compile &compile);

  // Destructor
  NOT_PRODUCT( ~Scheduling(); )

  // Step ahead "i" cycles
  void step(uint i);

  // Step ahead 1 cycle, and clear the bundle state (for example,
  // at a branch target)
  void step_and_clear();

  Bundle* node_bundling(const Node *n) {
    assert(valid_bundle_info(n), "oob");
    return (&_node_bundling_base[n->_idx]);
  }

  bool valid_bundle_info(const Node *n) const {
    return (_node_bundling_limit > n->_idx);
  }

  bool starts_bundle(const Node *n) const {
    return (_node_bundling_limit > n->_idx && _node_bundling_base[n->_idx].starts_bundle());
  }

  // Do the scheduling
  void DoScheduling();

  // Compute the local latencies walking forward over the list of
  // nodes for a basic block
  void ComputeLocalLatenciesForward(const Block *bb);

  // Compute the register antidependencies within a basic block
  void ComputeRegisterAntidependencies(Block *bb);
  void verify_do_def( Node *n, OptoReg::Name def, const char *msg );
  void verify_good_schedule( Block *b, const char *msg );
  void anti_do_def( Block *b, Node *def, OptoReg::Name def_reg, int is_def );
  void anti_do_use( Block *b, Node *use, OptoReg::Name use_reg );

  // Add a node to the current bundle
  void AddNodeToBundle(Node *n, const Block *bb);

  // Add a node to the list of available nodes
  void AddNodeToAvailableList(Node *n);

  // Compute the local use count for the nodes in a block, and compute
  // the list of instructions with no uses in the block as available
  void ComputeUseCount(const Block *bb);

  // Choose an instruction from the available list to add to the bundle
  Node * ChooseNodeToBundle();

  // See if this Node fits into the currently accumulating bundle
  bool NodeFitsInBundle(Node *n);

  // Decrement the use count for a node
 void DecrementUseCounts(Node *n, const Block *bb);

  // Garbage collect pinch nodes for reuse by other blocks.
  void garbage_collect_pinch_nodes();
  // Clean up a pinch node for reuse (helper for above).
  void cleanup_pinch( Node *pinch );

  // Information for statistics gathering
#ifndef PRODUCT
private:
  // Gather information on size of nops relative to total
  uint _branches, _unconditional_delays;

  static uint _total_nop_size, _total_method_size;
  static uint _total_branches, _total_unconditional_delays;
  static uint _total_instructions_per_bundle[Pipeline::_max_instrs_per_cycle+1];

public:
  static void print_statistics();

  static void increment_instructions_per_bundle(uint i) {
    _total_instructions_per_bundle[i]++;
  }

  static void increment_nop_size(uint s) {
    _total_nop_size += s;
  }

  static void increment_method_size(uint s) {
    _total_method_size += s;
  }
#endif

};

#endif // SHARE_VM_OPTO_OUTPUT_HPP
