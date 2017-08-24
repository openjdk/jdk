/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/assembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "code/debugInfo.hpp"
#include "code/debugInfoRec.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/oopMap.hpp"
#include "memory/allocation.inline.hpp"
#include "opto/ad.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/locknode.hpp"
#include "opto/machnode.hpp"
#include "opto/optoreg.hpp"
#include "opto/output.hpp"
#include "opto/regalloc.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "opto/type.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/xmlstream.hpp"

#ifndef PRODUCT
#define DEBUG_ARG(x) , x
#else
#define DEBUG_ARG(x)
#endif

// Convert Nodes to instruction bits and pass off to the VM
void Compile::Output() {
  // RootNode goes
  assert( _cfg->get_root_block()->number_of_nodes() == 0, "" );

  // The number of new nodes (mostly MachNop) is proportional to
  // the number of java calls and inner loops which are aligned.
  if ( C->check_node_count((NodeLimitFudgeFactor + C->java_calls()*3 +
                            C->inner_loops()*(OptoLoopAlignment-1)),
                           "out of nodes before code generation" ) ) {
    return;
  }
  // Make sure I can find the Start Node
  Block *entry = _cfg->get_block(1);
  Block *broot = _cfg->get_root_block();

  const StartNode *start = entry->head()->as_Start();

  // Replace StartNode with prolog
  MachPrologNode *prolog = new MachPrologNode();
  entry->map_node(prolog, 0);
  _cfg->map_node_to_block(prolog, entry);
  _cfg->unmap_node_from_block(start); // start is no longer in any block

  // Virtual methods need an unverified entry point

  if( is_osr_compilation() ) {
    if( PoisonOSREntry ) {
      // TODO: Should use a ShouldNotReachHereNode...
      _cfg->insert( broot, 0, new MachBreakpointNode() );
    }
  } else {
    if( _method && !_method->flags().is_static() ) {
      // Insert unvalidated entry point
      _cfg->insert( broot, 0, new MachUEPNode() );
    }

  }

  // Break before main entry point
  if ((_method && C->directive()->BreakAtExecuteOption) ||
      (OptoBreakpoint && is_method_compilation())       ||
      (OptoBreakpointOSR && is_osr_compilation())       ||
      (OptoBreakpointC2R && !_method)                   ) {
    // checking for _method means that OptoBreakpoint does not apply to
    // runtime stubs or frame converters
    _cfg->insert( entry, 1, new MachBreakpointNode() );
  }

  // Insert epilogs before every return
  for (uint i = 0; i < _cfg->number_of_blocks(); i++) {
    Block* block = _cfg->get_block(i);
    if (!block->is_connector() && block->non_connector_successor(0) == _cfg->get_root_block()) { // Found a program exit point?
      Node* m = block->end();
      if (m->is_Mach() && m->as_Mach()->ideal_Opcode() != Op_Halt) {
        MachEpilogNode* epilog = new MachEpilogNode(m->as_Mach()->ideal_Opcode() == Op_Return);
        block->add_inst(epilog);
        _cfg->map_node_to_block(epilog, block);
      }
    }
  }

  uint* blk_starts = NEW_RESOURCE_ARRAY(uint, _cfg->number_of_blocks() + 1);
  blk_starts[0] = 0;

  // Initialize code buffer and process short branches.
  CodeBuffer* cb = init_buffer(blk_starts);

  if (cb == NULL || failing()) {
    return;
  }

  ScheduleAndBundle();

#ifndef PRODUCT
  if (trace_opto_output()) {
    tty->print("\n---- After ScheduleAndBundle ----\n");
    for (uint i = 0; i < _cfg->number_of_blocks(); i++) {
      tty->print("\nBB#%03d:\n", i);
      Block* block = _cfg->get_block(i);
      for (uint j = 0; j < block->number_of_nodes(); j++) {
        Node* n = block->get_node(j);
        OptoReg::Name reg = _regalloc->get_reg_first(n);
        tty->print(" %-6s ", reg >= 0 && reg < REG_COUNT ? Matcher::regName[reg] : "");
        n->dump();
      }
    }
  }
#endif

  if (failing()) {
    return;
  }

  BuildOopMaps();

  if (failing())  {
    return;
  }

  fill_buffer(cb, blk_starts);
}

bool Compile::need_stack_bang(int frame_size_in_bytes) const {
  // Determine if we need to generate a stack overflow check.
  // Do it if the method is not a stub function and
  // has java calls or has frame size > vm_page_size/8.
  // The debug VM checks that deoptimization doesn't trigger an
  // unexpected stack overflow (compiled method stack banging should
  // guarantee it doesn't happen) so we always need the stack bang in
  // a debug VM.
  return (UseStackBanging && stub_function() == NULL &&
          (has_java_calls() || frame_size_in_bytes > os::vm_page_size()>>3
           DEBUG_ONLY(|| true)));
}

bool Compile::need_register_stack_bang() const {
  // Determine if we need to generate a register stack overflow check.
  // This is only used on architectures which have split register
  // and memory stacks (ie. IA64).
  // Bang if the method is not a stub function and has java calls
  return (stub_function() == NULL && has_java_calls());
}


// Compute the size of first NumberOfLoopInstrToAlign instructions at the top
// of a loop. When aligning a loop we need to provide enough instructions
// in cpu's fetch buffer to feed decoders. The loop alignment could be
// avoided if we have enough instructions in fetch buffer at the head of a loop.
// By default, the size is set to 999999 by Block's constructor so that
// a loop will be aligned if the size is not reset here.
//
// Note: Mach instructions could contain several HW instructions
// so the size is estimated only.
//
void Compile::compute_loop_first_inst_sizes() {
  // The next condition is used to gate the loop alignment optimization.
  // Don't aligned a loop if there are enough instructions at the head of a loop
  // or alignment padding is larger then MaxLoopPad. By default, MaxLoopPad
  // is equal to OptoLoopAlignment-1 except on new Intel cpus, where it is
  // equal to 11 bytes which is the largest address NOP instruction.
  if (MaxLoopPad < OptoLoopAlignment - 1) {
    uint last_block = _cfg->number_of_blocks() - 1;
    for (uint i = 1; i <= last_block; i++) {
      Block* block = _cfg->get_block(i);
      // Check the first loop's block which requires an alignment.
      if (block->loop_alignment() > (uint)relocInfo::addr_unit()) {
        uint sum_size = 0;
        uint inst_cnt = NumberOfLoopInstrToAlign;
        inst_cnt = block->compute_first_inst_size(sum_size, inst_cnt, _regalloc);

        // Check subsequent fallthrough blocks if the loop's first
        // block(s) does not have enough instructions.
        Block *nb = block;
        while(inst_cnt > 0 &&
              i < last_block &&
              !_cfg->get_block(i + 1)->has_loop_alignment() &&
              !nb->has_successor(block)) {
          i++;
          nb = _cfg->get_block(i);
          inst_cnt  = nb->compute_first_inst_size(sum_size, inst_cnt, _regalloc);
        } // while( inst_cnt > 0 && i < last_block  )

        block->set_first_inst_size(sum_size);
      } // f( b->head()->is_Loop() )
    } // for( i <= last_block )
  } // if( MaxLoopPad < OptoLoopAlignment-1 )
}

// The architecture description provides short branch variants for some long
// branch instructions. Replace eligible long branches with short branches.
void Compile::shorten_branches(uint* blk_starts, int& code_size, int& reloc_size, int& stub_size) {
  // Compute size of each block, method size, and relocation information size
  uint nblocks  = _cfg->number_of_blocks();

  uint*      jmp_offset = NEW_RESOURCE_ARRAY(uint,nblocks);
  uint*      jmp_size   = NEW_RESOURCE_ARRAY(uint,nblocks);
  int*       jmp_nidx   = NEW_RESOURCE_ARRAY(int ,nblocks);

  // Collect worst case block paddings
  int* block_worst_case_pad = NEW_RESOURCE_ARRAY(int, nblocks);
  memset(block_worst_case_pad, 0, nblocks * sizeof(int));

  DEBUG_ONLY( uint *jmp_target = NEW_RESOURCE_ARRAY(uint,nblocks); )
  DEBUG_ONLY( uint *jmp_rule = NEW_RESOURCE_ARRAY(uint,nblocks); )

  bool has_short_branch_candidate = false;

  // Initialize the sizes to 0
  code_size  = 0;          // Size in bytes of generated code
  stub_size  = 0;          // Size in bytes of all stub entries
  // Size in bytes of all relocation entries, including those in local stubs.
  // Start with 2-bytes of reloc info for the unvalidated entry point
  reloc_size = 1;          // Number of relocation entries

  // Make three passes.  The first computes pessimistic blk_starts,
  // relative jmp_offset and reloc_size information.  The second performs
  // short branch substitution using the pessimistic sizing.  The
  // third inserts nops where needed.

  // Step one, perform a pessimistic sizing pass.
  uint last_call_adr = max_juint;
  uint last_avoid_back_to_back_adr = max_juint;
  uint nop_size = (new MachNopNode())->size(_regalloc);
  for (uint i = 0; i < nblocks; i++) { // For all blocks
    Block* block = _cfg->get_block(i);

    // During short branch replacement, we store the relative (to blk_starts)
    // offset of jump in jmp_offset, rather than the absolute offset of jump.
    // This is so that we do not need to recompute sizes of all nodes when
    // we compute correct blk_starts in our next sizing pass.
    jmp_offset[i] = 0;
    jmp_size[i]   = 0;
    jmp_nidx[i]   = -1;
    DEBUG_ONLY( jmp_target[i] = 0; )
    DEBUG_ONLY( jmp_rule[i]   = 0; )

    // Sum all instruction sizes to compute block size
    uint last_inst = block->number_of_nodes();
    uint blk_size = 0;
    for (uint j = 0; j < last_inst; j++) {
      Node* nj = block->get_node(j);
      // Handle machine instruction nodes
      if (nj->is_Mach()) {
        MachNode *mach = nj->as_Mach();
        blk_size += (mach->alignment_required() - 1) * relocInfo::addr_unit(); // assume worst case padding
        reloc_size += mach->reloc();
        if (mach->is_MachCall()) {
          // add size information for trampoline stub
          // class CallStubImpl is platform-specific and defined in the *.ad files.
          stub_size  += CallStubImpl::size_call_trampoline();
          reloc_size += CallStubImpl::reloc_call_trampoline();

          MachCallNode *mcall = mach->as_MachCall();
          // This destination address is NOT PC-relative

          mcall->method_set((intptr_t)mcall->entry_point());

          if (mcall->is_MachCallJava() && mcall->as_MachCallJava()->_method) {
            stub_size  += CompiledStaticCall::to_interp_stub_size();
            reloc_size += CompiledStaticCall::reloc_to_interp_stub();
#if INCLUDE_AOT
            stub_size  += CompiledStaticCall::to_aot_stub_size();
            reloc_size += CompiledStaticCall::reloc_to_aot_stub();
#endif
          }
        } else if (mach->is_MachSafePoint()) {
          // If call/safepoint are adjacent, account for possible
          // nop to disambiguate the two safepoints.
          // ScheduleAndBundle() can rearrange nodes in a block,
          // check for all offsets inside this block.
          if (last_call_adr >= blk_starts[i]) {
            blk_size += nop_size;
          }
        }
        if (mach->avoid_back_to_back(MachNode::AVOID_BEFORE)) {
          // Nop is inserted between "avoid back to back" instructions.
          // ScheduleAndBundle() can rearrange nodes in a block,
          // check for all offsets inside this block.
          if (last_avoid_back_to_back_adr >= blk_starts[i]) {
            blk_size += nop_size;
          }
        }
        if (mach->may_be_short_branch()) {
          if (!nj->is_MachBranch()) {
#ifndef PRODUCT
            nj->dump(3);
#endif
            Unimplemented();
          }
          assert(jmp_nidx[i] == -1, "block should have only one branch");
          jmp_offset[i] = blk_size;
          jmp_size[i]   = nj->size(_regalloc);
          jmp_nidx[i]   = j;
          has_short_branch_candidate = true;
        }
      }
      blk_size += nj->size(_regalloc);
      // Remember end of call offset
      if (nj->is_MachCall() && !nj->is_MachCallLeaf()) {
        last_call_adr = blk_starts[i]+blk_size;
      }
      // Remember end of avoid_back_to_back offset
      if (nj->is_Mach() && nj->as_Mach()->avoid_back_to_back(MachNode::AVOID_AFTER)) {
        last_avoid_back_to_back_adr = blk_starts[i]+blk_size;
      }
    }

    // When the next block starts a loop, we may insert pad NOP
    // instructions.  Since we cannot know our future alignment,
    // assume the worst.
    if (i < nblocks - 1) {
      Block* nb = _cfg->get_block(i + 1);
      int max_loop_pad = nb->code_alignment()-relocInfo::addr_unit();
      if (max_loop_pad > 0) {
        assert(is_power_of_2(max_loop_pad+relocInfo::addr_unit()), "");
        // Adjust last_call_adr and/or last_avoid_back_to_back_adr.
        // If either is the last instruction in this block, bump by
        // max_loop_pad in lock-step with blk_size, so sizing
        // calculations in subsequent blocks still can conservatively
        // detect that it may the last instruction in this block.
        if (last_call_adr == blk_starts[i]+blk_size) {
          last_call_adr += max_loop_pad;
        }
        if (last_avoid_back_to_back_adr == blk_starts[i]+blk_size) {
          last_avoid_back_to_back_adr += max_loop_pad;
        }
        blk_size += max_loop_pad;
        block_worst_case_pad[i + 1] = max_loop_pad;
      }
    }

    // Save block size; update total method size
    blk_starts[i+1] = blk_starts[i]+blk_size;
  }

  // Step two, replace eligible long jumps.
  bool progress = true;
  uint last_may_be_short_branch_adr = max_juint;
  while (has_short_branch_candidate && progress) {
    progress = false;
    has_short_branch_candidate = false;
    int adjust_block_start = 0;
    for (uint i = 0; i < nblocks; i++) {
      Block* block = _cfg->get_block(i);
      int idx = jmp_nidx[i];
      MachNode* mach = (idx == -1) ? NULL: block->get_node(idx)->as_Mach();
      if (mach != NULL && mach->may_be_short_branch()) {
#ifdef ASSERT
        assert(jmp_size[i] > 0 && mach->is_MachBranch(), "sanity");
        int j;
        // Find the branch; ignore trailing NOPs.
        for (j = block->number_of_nodes()-1; j>=0; j--) {
          Node* n = block->get_node(j);
          if (!n->is_Mach() || n->as_Mach()->ideal_Opcode() != Op_Con)
            break;
        }
        assert(j >= 0 && j == idx && block->get_node(j) == (Node*)mach, "sanity");
#endif
        int br_size = jmp_size[i];
        int br_offs = blk_starts[i] + jmp_offset[i];

        // This requires the TRUE branch target be in succs[0]
        uint bnum = block->non_connector_successor(0)->_pre_order;
        int offset = blk_starts[bnum] - br_offs;
        if (bnum > i) { // adjust following block's offset
          offset -= adjust_block_start;
        }

        // This block can be a loop header, account for the padding
        // in the previous block.
        int block_padding = block_worst_case_pad[i];
        assert(i == 0 || block_padding == 0 || br_offs >= block_padding, "Should have at least a padding on top");
        // In the following code a nop could be inserted before
        // the branch which will increase the backward distance.
        bool needs_padding = ((uint)(br_offs - block_padding) == last_may_be_short_branch_adr);
        assert(!needs_padding || jmp_offset[i] == 0, "padding only branches at the beginning of block");

        if (needs_padding && offset <= 0)
          offset -= nop_size;

        if (_matcher->is_short_branch_offset(mach->rule(), br_size, offset)) {
          // We've got a winner.  Replace this branch.
          MachNode* replacement = mach->as_MachBranch()->short_branch_version();

          // Update the jmp_size.
          int new_size = replacement->size(_regalloc);
          int diff     = br_size - new_size;
          assert(diff >= (int)nop_size, "short_branch size should be smaller");
          // Conservatively take into account padding between
          // avoid_back_to_back branches. Previous branch could be
          // converted into avoid_back_to_back branch during next
          // rounds.
          if (needs_padding && replacement->avoid_back_to_back(MachNode::AVOID_BEFORE)) {
            jmp_offset[i] += nop_size;
            diff -= nop_size;
          }
          adjust_block_start += diff;
          block->map_node(replacement, idx);
          mach->subsume_by(replacement, C);
          mach = replacement;
          progress = true;

          jmp_size[i] = new_size;
          DEBUG_ONLY( jmp_target[i] = bnum; );
          DEBUG_ONLY( jmp_rule[i] = mach->rule(); );
        } else {
          // The jump distance is not short, try again during next iteration.
          has_short_branch_candidate = true;
        }
      } // (mach->may_be_short_branch())
      if (mach != NULL && (mach->may_be_short_branch() ||
                           mach->avoid_back_to_back(MachNode::AVOID_AFTER))) {
        last_may_be_short_branch_adr = blk_starts[i] + jmp_offset[i] + jmp_size[i];
      }
      blk_starts[i+1] -= adjust_block_start;
    }
  }

#ifdef ASSERT
  for (uint i = 0; i < nblocks; i++) { // For all blocks
    if (jmp_target[i] != 0) {
      int br_size = jmp_size[i];
      int offset = blk_starts[jmp_target[i]]-(blk_starts[i] + jmp_offset[i]);
      if (!_matcher->is_short_branch_offset(jmp_rule[i], br_size, offset)) {
        tty->print_cr("target (%d) - jmp_offset(%d) = offset (%d), jump_size(%d), jmp_block B%d, target_block B%d", blk_starts[jmp_target[i]], blk_starts[i] + jmp_offset[i], offset, br_size, i, jmp_target[i]);
      }
      assert(_matcher->is_short_branch_offset(jmp_rule[i], br_size, offset), "Displacement too large for short jmp");
    }
  }
#endif

  // Step 3, compute the offsets of all blocks, will be done in fill_buffer()
  // after ScheduleAndBundle().

  // ------------------
  // Compute size for code buffer
  code_size = blk_starts[nblocks];

  // Relocation records
  reloc_size += 1;              // Relo entry for exception handler

  // Adjust reloc_size to number of record of relocation info
  // Min is 2 bytes, max is probably 6 or 8, with a tax up to 25% for
  // a relocation index.
  // The CodeBuffer will expand the locs array if this estimate is too low.
  reloc_size *= 10 / sizeof(relocInfo);
}

//------------------------------FillLocArray-----------------------------------
// Create a bit of debug info and append it to the array.  The mapping is from
// Java local or expression stack to constant, register or stack-slot.  For
// doubles, insert 2 mappings and return 1 (to tell the caller that the next
// entry has been taken care of and caller should skip it).
static LocationValue *new_loc_value( PhaseRegAlloc *ra, OptoReg::Name regnum, Location::Type l_type ) {
  // This should never have accepted Bad before
  assert(OptoReg::is_valid(regnum), "location must be valid");
  return (OptoReg::is_reg(regnum))
    ? new LocationValue(Location::new_reg_loc(l_type, OptoReg::as_VMReg(regnum)) )
    : new LocationValue(Location::new_stk_loc(l_type,  ra->reg2offset(regnum)));
}


ObjectValue*
Compile::sv_for_node_id(GrowableArray<ScopeValue*> *objs, int id) {
  for (int i = 0; i < objs->length(); i++) {
    assert(objs->at(i)->is_object(), "corrupt object cache");
    ObjectValue* sv = (ObjectValue*) objs->at(i);
    if (sv->id() == id) {
      return sv;
    }
  }
  // Otherwise..
  return NULL;
}

void Compile::set_sv_for_object_node(GrowableArray<ScopeValue*> *objs,
                                     ObjectValue* sv ) {
  assert(sv_for_node_id(objs, sv->id()) == NULL, "Precondition");
  objs->append(sv);
}


void Compile::FillLocArray( int idx, MachSafePointNode* sfpt, Node *local,
                            GrowableArray<ScopeValue*> *array,
                            GrowableArray<ScopeValue*> *objs ) {
  assert( local, "use _top instead of null" );
  if (array->length() != idx) {
    assert(array->length() == idx + 1, "Unexpected array count");
    // Old functionality:
    //   return
    // New functionality:
    //   Assert if the local is not top. In product mode let the new node
    //   override the old entry.
    assert(local == top(), "LocArray collision");
    if (local == top()) {
      return;
    }
    array->pop();
  }
  const Type *t = local->bottom_type();

  // Is it a safepoint scalar object node?
  if (local->is_SafePointScalarObject()) {
    SafePointScalarObjectNode* spobj = local->as_SafePointScalarObject();

    ObjectValue* sv = Compile::sv_for_node_id(objs, spobj->_idx);
    if (sv == NULL) {
      ciKlass* cik = t->is_oopptr()->klass();
      assert(cik->is_instance_klass() ||
             cik->is_array_klass(), "Not supported allocation.");
      sv = new ObjectValue(spobj->_idx,
                           new ConstantOopWriteValue(cik->java_mirror()->constant_encoding()));
      Compile::set_sv_for_object_node(objs, sv);

      uint first_ind = spobj->first_index(sfpt->jvms());
      for (uint i = 0; i < spobj->n_fields(); i++) {
        Node* fld_node = sfpt->in(first_ind+i);
        (void)FillLocArray(sv->field_values()->length(), sfpt, fld_node, sv->field_values(), objs);
      }
    }
    array->append(sv);
    return;
  }

  // Grab the register number for the local
  OptoReg::Name regnum = _regalloc->get_reg_first(local);
  if( OptoReg::is_valid(regnum) ) {// Got a register/stack?
    // Record the double as two float registers.
    // The register mask for such a value always specifies two adjacent
    // float registers, with the lower register number even.
    // Normally, the allocation of high and low words to these registers
    // is irrelevant, because nearly all operations on register pairs
    // (e.g., StoreD) treat them as a single unit.
    // Here, we assume in addition that the words in these two registers
    // stored "naturally" (by operations like StoreD and double stores
    // within the interpreter) such that the lower-numbered register
    // is written to the lower memory address.  This may seem like
    // a machine dependency, but it is not--it is a requirement on
    // the author of the <arch>.ad file to ensure that, for every
    // even/odd double-register pair to which a double may be allocated,
    // the word in the even single-register is stored to the first
    // memory word.  (Note that register numbers are completely
    // arbitrary, and are not tied to any machine-level encodings.)
#ifdef _LP64
    if( t->base() == Type::DoubleBot || t->base() == Type::DoubleCon ) {
      array->append(new ConstantIntValue(0));
      array->append(new_loc_value( _regalloc, regnum, Location::dbl ));
    } else if ( t->base() == Type::Long ) {
      array->append(new ConstantIntValue(0));
      array->append(new_loc_value( _regalloc, regnum, Location::lng ));
    } else if ( t->base() == Type::RawPtr ) {
      // jsr/ret return address which must be restored into a the full
      // width 64-bit stack slot.
      array->append(new_loc_value( _regalloc, regnum, Location::lng ));
    }
#else //_LP64
#ifdef SPARC
    if (t->base() == Type::Long && OptoReg::is_reg(regnum)) {
      // For SPARC we have to swap high and low words for
      // long values stored in a single-register (g0-g7).
      array->append(new_loc_value( _regalloc,              regnum   , Location::normal ));
      array->append(new_loc_value( _regalloc, OptoReg::add(regnum,1), Location::normal ));
    } else
#endif //SPARC
    if( t->base() == Type::DoubleBot || t->base() == Type::DoubleCon || t->base() == Type::Long ) {
      // Repack the double/long as two jints.
      // The convention the interpreter uses is that the second local
      // holds the first raw word of the native double representation.
      // This is actually reasonable, since locals and stack arrays
      // grow downwards in all implementations.
      // (If, on some machine, the interpreter's Java locals or stack
      // were to grow upwards, the embedded doubles would be word-swapped.)
      array->append(new_loc_value( _regalloc, OptoReg::add(regnum,1), Location::normal ));
      array->append(new_loc_value( _regalloc,              regnum   , Location::normal ));
    }
#endif //_LP64
    else if( (t->base() == Type::FloatBot || t->base() == Type::FloatCon) &&
               OptoReg::is_reg(regnum) ) {
      array->append(new_loc_value( _regalloc, regnum, Matcher::float_in_double()
                                   ? Location::float_in_dbl : Location::normal ));
    } else if( t->base() == Type::Int && OptoReg::is_reg(regnum) ) {
      array->append(new_loc_value( _regalloc, regnum, Matcher::int_in_long
                                   ? Location::int_in_long : Location::normal ));
    } else if( t->base() == Type::NarrowOop ) {
      array->append(new_loc_value( _regalloc, regnum, Location::narrowoop ));
    } else {
      array->append(new_loc_value( _regalloc, regnum, _regalloc->is_oop(local) ? Location::oop : Location::normal ));
    }
    return;
  }

  // No register.  It must be constant data.
  switch (t->base()) {
  case Type::Half:              // Second half of a double
    ShouldNotReachHere();       // Caller should skip 2nd halves
    break;
  case Type::AnyPtr:
    array->append(new ConstantOopWriteValue(NULL));
    break;
  case Type::AryPtr:
  case Type::InstPtr:          // fall through
    array->append(new ConstantOopWriteValue(t->isa_oopptr()->const_oop()->constant_encoding()));
    break;
  case Type::NarrowOop:
    if (t == TypeNarrowOop::NULL_PTR) {
      array->append(new ConstantOopWriteValue(NULL));
    } else {
      array->append(new ConstantOopWriteValue(t->make_ptr()->isa_oopptr()->const_oop()->constant_encoding()));
    }
    break;
  case Type::Int:
    array->append(new ConstantIntValue(t->is_int()->get_con()));
    break;
  case Type::RawPtr:
    // A return address (T_ADDRESS).
    assert((intptr_t)t->is_ptr()->get_con() < (intptr_t)0x10000, "must be a valid BCI");
#ifdef _LP64
    // Must be restored to the full-width 64-bit stack slot.
    array->append(new ConstantLongValue(t->is_ptr()->get_con()));
#else
    array->append(new ConstantIntValue(t->is_ptr()->get_con()));
#endif
    break;
  case Type::FloatCon: {
    float f = t->is_float_constant()->getf();
    array->append(new ConstantIntValue(jint_cast(f)));
    break;
  }
  case Type::DoubleCon: {
    jdouble d = t->is_double_constant()->getd();
#ifdef _LP64
    array->append(new ConstantIntValue(0));
    array->append(new ConstantDoubleValue(d));
#else
    // Repack the double as two jints.
    // The convention the interpreter uses is that the second local
    // holds the first raw word of the native double representation.
    // This is actually reasonable, since locals and stack arrays
    // grow downwards in all implementations.
    // (If, on some machine, the interpreter's Java locals or stack
    // were to grow upwards, the embedded doubles would be word-swapped.)
    jlong_accessor acc;
    acc.long_value = jlong_cast(d);
    array->append(new ConstantIntValue(acc.words[1]));
    array->append(new ConstantIntValue(acc.words[0]));
#endif
    break;
  }
  case Type::Long: {
    jlong d = t->is_long()->get_con();
#ifdef _LP64
    array->append(new ConstantIntValue(0));
    array->append(new ConstantLongValue(d));
#else
    // Repack the long as two jints.
    // The convention the interpreter uses is that the second local
    // holds the first raw word of the native double representation.
    // This is actually reasonable, since locals and stack arrays
    // grow downwards in all implementations.
    // (If, on some machine, the interpreter's Java locals or stack
    // were to grow upwards, the embedded doubles would be word-swapped.)
    jlong_accessor acc;
    acc.long_value = d;
    array->append(new ConstantIntValue(acc.words[1]));
    array->append(new ConstantIntValue(acc.words[0]));
#endif
    break;
  }
  case Type::Top:               // Add an illegal value here
    array->append(new LocationValue(Location()));
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

// Determine if this node starts a bundle
bool Compile::starts_bundle(const Node *n) const {
  return (_node_bundling_limit > n->_idx &&
          _node_bundling_base[n->_idx].starts_bundle());
}

//--------------------------Process_OopMap_Node--------------------------------
void Compile::Process_OopMap_Node(MachNode *mach, int current_offset) {

  // Handle special safepoint nodes for synchronization
  MachSafePointNode *sfn   = mach->as_MachSafePoint();
  MachCallNode      *mcall;

  int safepoint_pc_offset = current_offset;
  bool is_method_handle_invoke = false;
  bool return_oop = false;

  // Add the safepoint in the DebugInfoRecorder
  if( !mach->is_MachCall() ) {
    mcall = NULL;
    debug_info()->add_safepoint(safepoint_pc_offset, sfn->_oop_map);
  } else {
    mcall = mach->as_MachCall();

    // Is the call a MethodHandle call?
    if (mcall->is_MachCallJava()) {
      if (mcall->as_MachCallJava()->_method_handle_invoke) {
        assert(has_method_handle_invokes(), "must have been set during call generation");
        is_method_handle_invoke = true;
      }
    }

    // Check if a call returns an object.
    if (mcall->returns_pointer()) {
      return_oop = true;
    }
    safepoint_pc_offset += mcall->ret_addr_offset();
    debug_info()->add_safepoint(safepoint_pc_offset, mcall->_oop_map);
  }

  // Loop over the JVMState list to add scope information
  // Do not skip safepoints with a NULL method, they need monitor info
  JVMState* youngest_jvms = sfn->jvms();
  int max_depth = youngest_jvms->depth();

  // Allocate the object pool for scalar-replaced objects -- the map from
  // small-integer keys (which can be recorded in the local and ostack
  // arrays) to descriptions of the object state.
  GrowableArray<ScopeValue*> *objs = new GrowableArray<ScopeValue*>();

  // Visit scopes from oldest to youngest.
  for (int depth = 1; depth <= max_depth; depth++) {
    JVMState* jvms = youngest_jvms->of_depth(depth);
    int idx;
    ciMethod* method = jvms->has_method() ? jvms->method() : NULL;
    // Safepoints that do not have method() set only provide oop-map and monitor info
    // to support GC; these do not support deoptimization.
    int num_locs = (method == NULL) ? 0 : jvms->loc_size();
    int num_exps = (method == NULL) ? 0 : jvms->stk_size();
    int num_mon  = jvms->nof_monitors();
    assert(method == NULL || jvms->bci() < 0 || num_locs == method->max_locals(),
           "JVMS local count must match that of the method");

    // Add Local and Expression Stack Information

    // Insert locals into the locarray
    GrowableArray<ScopeValue*> *locarray = new GrowableArray<ScopeValue*>(num_locs);
    for( idx = 0; idx < num_locs; idx++ ) {
      FillLocArray( idx, sfn, sfn->local(jvms, idx), locarray, objs );
    }

    // Insert expression stack entries into the exparray
    GrowableArray<ScopeValue*> *exparray = new GrowableArray<ScopeValue*>(num_exps);
    for( idx = 0; idx < num_exps; idx++ ) {
      FillLocArray( idx,  sfn, sfn->stack(jvms, idx), exparray, objs );
    }

    // Add in mappings of the monitors
    assert( !method ||
            !method->is_synchronized() ||
            method->is_native() ||
            num_mon > 0 ||
            !GenerateSynchronizationCode,
            "monitors must always exist for synchronized methods");

    // Build the growable array of ScopeValues for exp stack
    GrowableArray<MonitorValue*> *monarray = new GrowableArray<MonitorValue*>(num_mon);

    // Loop over monitors and insert into array
    for (idx = 0; idx < num_mon; idx++) {
      // Grab the node that defines this monitor
      Node* box_node = sfn->monitor_box(jvms, idx);
      Node* obj_node = sfn->monitor_obj(jvms, idx);

      // Create ScopeValue for object
      ScopeValue *scval = NULL;

      if (obj_node->is_SafePointScalarObject()) {
        SafePointScalarObjectNode* spobj = obj_node->as_SafePointScalarObject();
        scval = Compile::sv_for_node_id(objs, spobj->_idx);
        if (scval == NULL) {
          const Type *t = spobj->bottom_type();
          ciKlass* cik = t->is_oopptr()->klass();
          assert(cik->is_instance_klass() ||
                 cik->is_array_klass(), "Not supported allocation.");
          ObjectValue* sv = new ObjectValue(spobj->_idx,
                                            new ConstantOopWriteValue(cik->java_mirror()->constant_encoding()));
          Compile::set_sv_for_object_node(objs, sv);

          uint first_ind = spobj->first_index(youngest_jvms);
          for (uint i = 0; i < spobj->n_fields(); i++) {
            Node* fld_node = sfn->in(first_ind+i);
            (void)FillLocArray(sv->field_values()->length(), sfn, fld_node, sv->field_values(), objs);
          }
          scval = sv;
        }
      } else if (!obj_node->is_Con()) {
        OptoReg::Name obj_reg = _regalloc->get_reg_first(obj_node);
        if( obj_node->bottom_type()->base() == Type::NarrowOop ) {
          scval = new_loc_value( _regalloc, obj_reg, Location::narrowoop );
        } else {
          scval = new_loc_value( _regalloc, obj_reg, Location::oop );
        }
      } else {
        const TypePtr *tp = obj_node->get_ptr_type();
        scval = new ConstantOopWriteValue(tp->is_oopptr()->const_oop()->constant_encoding());
      }

      OptoReg::Name box_reg = BoxLockNode::reg(box_node);
      Location basic_lock = Location::new_stk_loc(Location::normal,_regalloc->reg2offset(box_reg));
      bool eliminated = (box_node->is_BoxLock() && box_node->as_BoxLock()->is_eliminated());
      monarray->append(new MonitorValue(scval, basic_lock, eliminated));
    }

    // We dump the object pool first, since deoptimization reads it in first.
    debug_info()->dump_object_pool(objs);

    // Build first class objects to pass to scope
    DebugToken *locvals = debug_info()->create_scope_values(locarray);
    DebugToken *expvals = debug_info()->create_scope_values(exparray);
    DebugToken *monvals = debug_info()->create_monitor_values(monarray);

    // Make method available for all Safepoints
    ciMethod* scope_method = method ? method : _method;
    // Describe the scope here
    assert(jvms->bci() >= InvocationEntryBci && jvms->bci() <= 0x10000, "must be a valid or entry BCI");
    assert(!jvms->should_reexecute() || depth == max_depth, "reexecute allowed only for the youngest");
    // Now we can describe the scope.
    methodHandle null_mh;
    bool rethrow_exception = false;
    debug_info()->describe_scope(safepoint_pc_offset, null_mh, scope_method, jvms->bci(), jvms->should_reexecute(), rethrow_exception, is_method_handle_invoke, return_oop, locvals, expvals, monvals);
  } // End jvms loop

  // Mark the end of the scope set.
  debug_info()->end_safepoint(safepoint_pc_offset);
}



// A simplified version of Process_OopMap_Node, to handle non-safepoints.
class NonSafepointEmitter {
  Compile*  C;
  JVMState* _pending_jvms;
  int       _pending_offset;

  void emit_non_safepoint();

 public:
  NonSafepointEmitter(Compile* compile) {
    this->C = compile;
    _pending_jvms = NULL;
    _pending_offset = 0;
  }

  void observe_instruction(Node* n, int pc_offset) {
    if (!C->debug_info()->recording_non_safepoints())  return;

    Node_Notes* nn = C->node_notes_at(n->_idx);
    if (nn == NULL || nn->jvms() == NULL)  return;
    if (_pending_jvms != NULL &&
        _pending_jvms->same_calls_as(nn->jvms())) {
      // Repeated JVMS?  Stretch it up here.
      _pending_offset = pc_offset;
    } else {
      if (_pending_jvms != NULL &&
          _pending_offset < pc_offset) {
        emit_non_safepoint();
      }
      _pending_jvms = NULL;
      if (pc_offset > C->debug_info()->last_pc_offset()) {
        // This is the only way _pending_jvms can become non-NULL:
        _pending_jvms = nn->jvms();
        _pending_offset = pc_offset;
      }
    }
  }

  // Stay out of the way of real safepoints:
  void observe_safepoint(JVMState* jvms, int pc_offset) {
    if (_pending_jvms != NULL &&
        !_pending_jvms->same_calls_as(jvms) &&
        _pending_offset < pc_offset) {
      emit_non_safepoint();
    }
    _pending_jvms = NULL;
  }

  void flush_at_end() {
    if (_pending_jvms != NULL) {
      emit_non_safepoint();
    }
    _pending_jvms = NULL;
  }
};

void NonSafepointEmitter::emit_non_safepoint() {
  JVMState* youngest_jvms = _pending_jvms;
  int       pc_offset     = _pending_offset;

  // Clear it now:
  _pending_jvms = NULL;

  DebugInformationRecorder* debug_info = C->debug_info();
  assert(debug_info->recording_non_safepoints(), "sanity");

  debug_info->add_non_safepoint(pc_offset);
  int max_depth = youngest_jvms->depth();

  // Visit scopes from oldest to youngest.
  for (int depth = 1; depth <= max_depth; depth++) {
    JVMState* jvms = youngest_jvms->of_depth(depth);
    ciMethod* method = jvms->has_method() ? jvms->method() : NULL;
    assert(!jvms->should_reexecute() || depth==max_depth, "reexecute allowed only for the youngest");
    methodHandle null_mh;
    debug_info->describe_scope(pc_offset, null_mh, method, jvms->bci(), jvms->should_reexecute());
  }

  // Mark the end of the scope set.
  debug_info->end_non_safepoint(pc_offset);
}

//------------------------------init_buffer------------------------------------
CodeBuffer* Compile::init_buffer(uint* blk_starts) {

  // Set the initially allocated size
  int  code_req   = initial_code_capacity;
  int  locs_req   = initial_locs_capacity;
  int  stub_req   = initial_stub_capacity;
  int  const_req  = initial_const_capacity;

  int  pad_req    = NativeCall::instruction_size;
  // The extra spacing after the code is necessary on some platforms.
  // Sometimes we need to patch in a jump after the last instruction,
  // if the nmethod has been deoptimized.  (See 4932387, 4894843.)

  // Compute the byte offset where we can store the deopt pc.
  if (fixed_slots() != 0) {
    _orig_pc_slot_offset_in_bytes = _regalloc->reg2offset(OptoReg::stack2reg(_orig_pc_slot));
  }

  // Compute prolog code size
  _method_size = 0;
  _frame_slots = OptoReg::reg2stack(_matcher->_old_SP)+_regalloc->_framesize;
#if defined(IA64) && !defined(AIX)
  if (save_argument_registers()) {
    // 4815101: this is a stub with implicit and unknown precision fp args.
    // The usual spill mechanism can only generate stfd's in this case, which
    // doesn't work if the fp reg to spill contains a single-precision denorm.
    // Instead, we hack around the normal spill mechanism using stfspill's and
    // ldffill's in the MachProlog and MachEpilog emit methods.  We allocate
    // space here for the fp arg regs (f8-f15) we're going to thusly spill.
    //
    // If we ever implement 16-byte 'registers' == stack slots, we can
    // get rid of this hack and have SpillCopy generate stfspill/ldffill
    // instead of stfd/stfs/ldfd/ldfs.
    _frame_slots += 8*(16/BytesPerInt);
  }
#endif
  assert(_frame_slots >= 0 && _frame_slots < 1000000, "sanity check");

  if (has_mach_constant_base_node()) {
    uint add_size = 0;
    // Fill the constant table.
    // Note:  This must happen before shorten_branches.
    for (uint i = 0; i < _cfg->number_of_blocks(); i++) {
      Block* b = _cfg->get_block(i);

      for (uint j = 0; j < b->number_of_nodes(); j++) {
        Node* n = b->get_node(j);

        // If the node is a MachConstantNode evaluate the constant
        // value section.
        if (n->is_MachConstant()) {
          MachConstantNode* machcon = n->as_MachConstant();
          machcon->eval_constant(C);
        } else if (n->is_Mach()) {
          // On Power there are more nodes that issue constants.
          add_size += (n->as_Mach()->ins_num_consts() * 8);
        }
      }
    }

    // Calculate the offsets of the constants and the size of the
    // constant table (including the padding to the next section).
    constant_table().calculate_offsets_and_size();
    const_req = constant_table().size() + add_size;
  }

  // Initialize the space for the BufferBlob used to find and verify
  // instruction size in MachNode::emit_size()
  init_scratch_buffer_blob(const_req);
  if (failing())  return NULL; // Out of memory

  // Pre-compute the length of blocks and replace
  // long branches with short if machine supports it.
  shorten_branches(blk_starts, code_req, locs_req, stub_req);

  // nmethod and CodeBuffer count stubs & constants as part of method's code.
  // class HandlerImpl is platform-specific and defined in the *.ad files.
  int exception_handler_req = HandlerImpl::size_exception_handler() + MAX_stubs_size; // add marginal slop for handler
  int deopt_handler_req     = HandlerImpl::size_deopt_handler()     + MAX_stubs_size; // add marginal slop for handler
  stub_req += MAX_stubs_size;   // ensure per-stub margin
  code_req += MAX_inst_size;    // ensure per-instruction margin

  if (StressCodeBuffers)
    code_req = const_req = stub_req = exception_handler_req = deopt_handler_req = 0x10;  // force expansion

  int total_req =
    const_req +
    code_req +
    pad_req +
    stub_req +
    exception_handler_req +
    deopt_handler_req;               // deopt handler

  if (has_method_handle_invokes())
    total_req += deopt_handler_req;  // deopt MH handler

  CodeBuffer* cb = code_buffer();
  cb->initialize(total_req, locs_req);

  // Have we run out of code space?
  if ((cb->blob() == NULL) || (!CompileBroker::should_compile_new_jobs())) {
    C->record_failure("CodeCache is full");
    return NULL;
  }
  // Configure the code buffer.
  cb->initialize_consts_size(const_req);
  cb->initialize_stubs_size(stub_req);
  cb->initialize_oop_recorder(env()->oop_recorder());

  // fill in the nop array for bundling computations
  MachNode *_nop_list[Bundle::_nop_count];
  Bundle::initialize_nops(_nop_list);

  return cb;
}

//------------------------------fill_buffer------------------------------------
void Compile::fill_buffer(CodeBuffer* cb, uint* blk_starts) {
  // blk_starts[] contains offsets calculated during short branches processing,
  // offsets should not be increased during following steps.

  // Compute the size of first NumberOfLoopInstrToAlign instructions at head
  // of a loop. It is used to determine the padding for loop alignment.
  compute_loop_first_inst_sizes();

  // Create oopmap set.
  _oop_map_set = new OopMapSet();

  // !!!!! This preserves old handling of oopmaps for now
  debug_info()->set_oopmaps(_oop_map_set);

  uint nblocks  = _cfg->number_of_blocks();
  // Count and start of implicit null check instructions
  uint inct_cnt = 0;
  uint *inct_starts = NEW_RESOURCE_ARRAY(uint, nblocks+1);

  // Count and start of calls
  uint *call_returns = NEW_RESOURCE_ARRAY(uint, nblocks+1);

  uint  return_offset = 0;
  int nop_size = (new MachNopNode())->size(_regalloc);

  int previous_offset = 0;
  int current_offset  = 0;
  int last_call_offset = -1;
  int last_avoid_back_to_back_offset = -1;
#ifdef ASSERT
  uint* jmp_target = NEW_RESOURCE_ARRAY(uint,nblocks);
  uint* jmp_offset = NEW_RESOURCE_ARRAY(uint,nblocks);
  uint* jmp_size   = NEW_RESOURCE_ARRAY(uint,nblocks);
  uint* jmp_rule   = NEW_RESOURCE_ARRAY(uint,nblocks);
#endif

  // Create an array of unused labels, one for each basic block, if printing is enabled
#ifndef PRODUCT
  int *node_offsets      = NULL;
  uint node_offset_limit = unique();

  if (print_assembly())
    node_offsets         = NEW_RESOURCE_ARRAY(int, node_offset_limit);
#endif

  NonSafepointEmitter non_safepoints(this);  // emit non-safepoints lazily

  // Emit the constant table.
  if (has_mach_constant_base_node()) {
    constant_table().emit(*cb);
  }

  // Create an array of labels, one for each basic block
  Label *blk_labels = NEW_RESOURCE_ARRAY(Label, nblocks+1);
  for (uint i=0; i <= nblocks; i++) {
    blk_labels[i].init();
  }

  // ------------------
  // Now fill in the code buffer
  Node *delay_slot = NULL;

  for (uint i = 0; i < nblocks; i++) {
    Block* block = _cfg->get_block(i);
    Node* head = block->head();

    // If this block needs to start aligned (i.e, can be reached other
    // than by falling-thru from the previous block), then force the
    // start of a new bundle.
    if (Pipeline::requires_bundling() && starts_bundle(head)) {
      cb->flush_bundle(true);
    }

#ifdef ASSERT
    if (!block->is_connector()) {
      stringStream st;
      block->dump_head(_cfg, &st);
      MacroAssembler(cb).block_comment(st.as_string());
    }
    jmp_target[i] = 0;
    jmp_offset[i] = 0;
    jmp_size[i]   = 0;
    jmp_rule[i]   = 0;
#endif
    int blk_offset = current_offset;

    // Define the label at the beginning of the basic block
    MacroAssembler(cb).bind(blk_labels[block->_pre_order]);

    uint last_inst = block->number_of_nodes();

    // Emit block normally, except for last instruction.
    // Emit means "dump code bits into code buffer".
    for (uint j = 0; j<last_inst; j++) {

      // Get the node
      Node* n = block->get_node(j);

      // See if delay slots are supported
      if (valid_bundle_info(n) &&
          node_bundling(n)->used_in_unconditional_delay()) {
        assert(delay_slot == NULL, "no use of delay slot node");
        assert(n->size(_regalloc) == Pipeline::instr_unit_size(), "delay slot instruction wrong size");

        delay_slot = n;
        continue;
      }

      // If this starts a new instruction group, then flush the current one
      // (but allow split bundles)
      if (Pipeline::requires_bundling() && starts_bundle(n))
        cb->flush_bundle(false);

      // Special handling for SafePoint/Call Nodes
      bool is_mcall = false;
      if (n->is_Mach()) {
        MachNode *mach = n->as_Mach();
        is_mcall = n->is_MachCall();
        bool is_sfn = n->is_MachSafePoint();

        // If this requires all previous instructions be flushed, then do so
        if (is_sfn || is_mcall || mach->alignment_required() != 1) {
          cb->flush_bundle(true);
          current_offset = cb->insts_size();
        }

        // A padding may be needed again since a previous instruction
        // could be moved to delay slot.

        // align the instruction if necessary
        int padding = mach->compute_padding(current_offset);
        // Make sure safepoint node for polling is distinct from a call's
        // return by adding a nop if needed.
        if (is_sfn && !is_mcall && padding == 0 && current_offset == last_call_offset) {
          padding = nop_size;
        }
        if (padding == 0 && mach->avoid_back_to_back(MachNode::AVOID_BEFORE) &&
            current_offset == last_avoid_back_to_back_offset) {
          // Avoid back to back some instructions.
          padding = nop_size;
        }

        if (padding > 0) {
          assert((padding % nop_size) == 0, "padding is not a multiple of NOP size");
          int nops_cnt = padding / nop_size;
          MachNode *nop = new MachNopNode(nops_cnt);
          block->insert_node(nop, j++);
          last_inst++;
          _cfg->map_node_to_block(nop, block);
          // Ensure enough space.
          cb->insts()->maybe_expand_to_ensure_remaining(MAX_inst_size);
          if ((cb->blob() == NULL) || (!CompileBroker::should_compile_new_jobs())) {
            C->record_failure("CodeCache is full");
            return;
          }
          nop->emit(*cb, _regalloc);
          cb->flush_bundle(true);
          current_offset = cb->insts_size();
        }

        // Remember the start of the last call in a basic block
        if (is_mcall) {
          MachCallNode *mcall = mach->as_MachCall();

          // This destination address is NOT PC-relative
          mcall->method_set((intptr_t)mcall->entry_point());

          // Save the return address
          call_returns[block->_pre_order] = current_offset + mcall->ret_addr_offset();

          if (mcall->is_MachCallLeaf()) {
            is_mcall = false;
            is_sfn = false;
          }
        }

        // sfn will be valid whenever mcall is valid now because of inheritance
        if (is_sfn || is_mcall) {

          // Handle special safepoint nodes for synchronization
          if (!is_mcall) {
            MachSafePointNode *sfn = mach->as_MachSafePoint();
            // !!!!! Stubs only need an oopmap right now, so bail out
            if (sfn->jvms()->method() == NULL) {
              // Write the oopmap directly to the code blob??!!
              continue;
            }
          } // End synchronization

          non_safepoints.observe_safepoint(mach->as_MachSafePoint()->jvms(),
                                           current_offset);
          Process_OopMap_Node(mach, current_offset);
        } // End if safepoint

        // If this is a null check, then add the start of the previous instruction to the list
        else if( mach->is_MachNullCheck() ) {
          inct_starts[inct_cnt++] = previous_offset;
        }

        // If this is a branch, then fill in the label with the target BB's label
        else if (mach->is_MachBranch()) {
          // This requires the TRUE branch target be in succs[0]
          uint block_num = block->non_connector_successor(0)->_pre_order;

          // Try to replace long branch if delay slot is not used,
          // it is mostly for back branches since forward branch's
          // distance is not updated yet.
          bool delay_slot_is_used = valid_bundle_info(n) &&
                                    node_bundling(n)->use_unconditional_delay();
          if (!delay_slot_is_used && mach->may_be_short_branch()) {
           assert(delay_slot == NULL, "not expecting delay slot node");
           int br_size = n->size(_regalloc);
            int offset = blk_starts[block_num] - current_offset;
            if (block_num >= i) {
              // Current and following block's offset are not
              // finalized yet, adjust distance by the difference
              // between calculated and final offsets of current block.
              offset -= (blk_starts[i] - blk_offset);
            }
            // In the following code a nop could be inserted before
            // the branch which will increase the backward distance.
            bool needs_padding = (current_offset == last_avoid_back_to_back_offset);
            if (needs_padding && offset <= 0)
              offset -= nop_size;

            if (_matcher->is_short_branch_offset(mach->rule(), br_size, offset)) {
              // We've got a winner.  Replace this branch.
              MachNode* replacement = mach->as_MachBranch()->short_branch_version();

              // Update the jmp_size.
              int new_size = replacement->size(_regalloc);
              assert((br_size - new_size) >= (int)nop_size, "short_branch size should be smaller");
              // Insert padding between avoid_back_to_back branches.
              if (needs_padding && replacement->avoid_back_to_back(MachNode::AVOID_BEFORE)) {
                MachNode *nop = new MachNopNode();
                block->insert_node(nop, j++);
                _cfg->map_node_to_block(nop, block);
                last_inst++;
                nop->emit(*cb, _regalloc);
                cb->flush_bundle(true);
                current_offset = cb->insts_size();
              }
#ifdef ASSERT
              jmp_target[i] = block_num;
              jmp_offset[i] = current_offset - blk_offset;
              jmp_size[i]   = new_size;
              jmp_rule[i]   = mach->rule();
#endif
              block->map_node(replacement, j);
              mach->subsume_by(replacement, C);
              n    = replacement;
              mach = replacement;
            }
          }
          mach->as_MachBranch()->label_set( &blk_labels[block_num], block_num );
        } else if (mach->ideal_Opcode() == Op_Jump) {
          for (uint h = 0; h < block->_num_succs; h++) {
            Block* succs_block = block->_succs[h];
            for (uint j = 1; j < succs_block->num_preds(); j++) {
              Node* jpn = succs_block->pred(j);
              if (jpn->is_JumpProj() && jpn->in(0) == mach) {
                uint block_num = succs_block->non_connector()->_pre_order;
                Label *blkLabel = &blk_labels[block_num];
                mach->add_case_label(jpn->as_JumpProj()->proj_no(), blkLabel);
              }
            }
          }
        }
#ifdef ASSERT
        // Check that oop-store precedes the card-mark
        else if (mach->ideal_Opcode() == Op_StoreCM) {
          uint storeCM_idx = j;
          int count = 0;
          for (uint prec = mach->req(); prec < mach->len(); prec++) {
            Node *oop_store = mach->in(prec);  // Precedence edge
            if (oop_store == NULL) continue;
            count++;
            uint i4;
            for (i4 = 0; i4 < last_inst; ++i4) {
              if (block->get_node(i4) == oop_store) {
                break;
              }
            }
            // Note: This test can provide a false failure if other precedence
            // edges have been added to the storeCMNode.
            assert(i4 == last_inst || i4 < storeCM_idx, "CM card-mark executes before oop-store");
          }
          assert(count > 0, "storeCM expects at least one precedence edge");
        }
#endif
        else if (!n->is_Proj()) {
          // Remember the beginning of the previous instruction, in case
          // it's followed by a flag-kill and a null-check.  Happens on
          // Intel all the time, with add-to-memory kind of opcodes.
          previous_offset = current_offset;
        }

        // Not an else-if!
        // If this is a trap based cmp then add its offset to the list.
        if (mach->is_TrapBasedCheckNode()) {
          inct_starts[inct_cnt++] = current_offset;
        }
      }

      // Verify that there is sufficient space remaining
      cb->insts()->maybe_expand_to_ensure_remaining(MAX_inst_size);
      if ((cb->blob() == NULL) || (!CompileBroker::should_compile_new_jobs())) {
        C->record_failure("CodeCache is full");
        return;
      }

      // Save the offset for the listing
#ifndef PRODUCT
      if (node_offsets && n->_idx < node_offset_limit)
        node_offsets[n->_idx] = cb->insts_size();
#endif

      // "Normal" instruction case
      DEBUG_ONLY( uint instr_offset = cb->insts_size(); )
      n->emit(*cb, _regalloc);
      current_offset  = cb->insts_size();

      // Above we only verified that there is enough space in the instruction section.
      // However, the instruction may emit stubs that cause code buffer expansion.
      // Bail out here if expansion failed due to a lack of code cache space.
      if (failing()) {
        return;
      }

#ifdef ASSERT
      if (n->size(_regalloc) < (current_offset-instr_offset)) {
        n->dump();
        assert(false, "wrong size of mach node");
      }
#endif
      non_safepoints.observe_instruction(n, current_offset);

      // mcall is last "call" that can be a safepoint
      // record it so we can see if a poll will directly follow it
      // in which case we'll need a pad to make the PcDesc sites unique
      // see  5010568. This can be slightly inaccurate but conservative
      // in the case that return address is not actually at current_offset.
      // This is a small price to pay.

      if (is_mcall) {
        last_call_offset = current_offset;
      }

      if (n->is_Mach() && n->as_Mach()->avoid_back_to_back(MachNode::AVOID_AFTER)) {
        // Avoid back to back some instructions.
        last_avoid_back_to_back_offset = current_offset;
      }

      // See if this instruction has a delay slot
      if (valid_bundle_info(n) && node_bundling(n)->use_unconditional_delay()) {
        assert(delay_slot != NULL, "expecting delay slot node");

        // Back up 1 instruction
        cb->set_insts_end(cb->insts_end() - Pipeline::instr_unit_size());

        // Save the offset for the listing
#ifndef PRODUCT
        if (node_offsets && delay_slot->_idx < node_offset_limit)
          node_offsets[delay_slot->_idx] = cb->insts_size();
#endif

        // Support a SafePoint in the delay slot
        if (delay_slot->is_MachSafePoint()) {
          MachNode *mach = delay_slot->as_Mach();
          // !!!!! Stubs only need an oopmap right now, so bail out
          if (!mach->is_MachCall() && mach->as_MachSafePoint()->jvms()->method() == NULL) {
            // Write the oopmap directly to the code blob??!!
            delay_slot = NULL;
            continue;
          }

          int adjusted_offset = current_offset - Pipeline::instr_unit_size();
          non_safepoints.observe_safepoint(mach->as_MachSafePoint()->jvms(),
                                           adjusted_offset);
          // Generate an OopMap entry
          Process_OopMap_Node(mach, adjusted_offset);
        }

        // Insert the delay slot instruction
        delay_slot->emit(*cb, _regalloc);

        // Don't reuse it
        delay_slot = NULL;
      }

    } // End for all instructions in block

    // If the next block is the top of a loop, pad this block out to align
    // the loop top a little. Helps prevent pipe stalls at loop back branches.
    if (i < nblocks-1) {
      Block *nb = _cfg->get_block(i + 1);
      int padding = nb->alignment_padding(current_offset);
      if( padding > 0 ) {
        MachNode *nop = new MachNopNode(padding / nop_size);
        block->insert_node(nop, block->number_of_nodes());
        _cfg->map_node_to_block(nop, block);
        nop->emit(*cb, _regalloc);
        current_offset = cb->insts_size();
      }
    }
    // Verify that the distance for generated before forward
    // short branches is still valid.
    guarantee((int)(blk_starts[i+1] - blk_starts[i]) >= (current_offset - blk_offset), "shouldn't increase block size");

    // Save new block start offset
    blk_starts[i] = blk_offset;
  } // End of for all blocks
  blk_starts[nblocks] = current_offset;

  non_safepoints.flush_at_end();

  // Offset too large?
  if (failing())  return;

  // Define a pseudo-label at the end of the code
  MacroAssembler(cb).bind( blk_labels[nblocks] );

  // Compute the size of the first block
  _first_block_size = blk_labels[1].loc_pos() - blk_labels[0].loc_pos();

#ifdef ASSERT
  for (uint i = 0; i < nblocks; i++) { // For all blocks
    if (jmp_target[i] != 0) {
      int br_size = jmp_size[i];
      int offset = blk_starts[jmp_target[i]]-(blk_starts[i] + jmp_offset[i]);
      if (!_matcher->is_short_branch_offset(jmp_rule[i], br_size, offset)) {
        tty->print_cr("target (%d) - jmp_offset(%d) = offset (%d), jump_size(%d), jmp_block B%d, target_block B%d", blk_starts[jmp_target[i]], blk_starts[i] + jmp_offset[i], offset, br_size, i, jmp_target[i]);
        assert(false, "Displacement too large for short jmp");
      }
    }
  }
#endif

#ifndef PRODUCT
  // Information on the size of the method, without the extraneous code
  Scheduling::increment_method_size(cb->insts_size());
#endif

  // ------------------
  // Fill in exception table entries.
  FillExceptionTables(inct_cnt, call_returns, inct_starts, blk_labels);

  // Only java methods have exception handlers and deopt handlers
  // class HandlerImpl is platform-specific and defined in the *.ad files.
  if (_method) {
    // Emit the exception handler code.
    _code_offsets.set_value(CodeOffsets::Exceptions, HandlerImpl::emit_exception_handler(*cb));
    if (failing()) {
      return; // CodeBuffer::expand failed
    }
    // Emit the deopt handler code.
    _code_offsets.set_value(CodeOffsets::Deopt, HandlerImpl::emit_deopt_handler(*cb));

    // Emit the MethodHandle deopt handler code (if required).
    if (has_method_handle_invokes() && !failing()) {
      // We can use the same code as for the normal deopt handler, we
      // just need a different entry point address.
      _code_offsets.set_value(CodeOffsets::DeoptMH, HandlerImpl::emit_deopt_handler(*cb));
    }
  }

  // One last check for failed CodeBuffer::expand:
  if ((cb->blob() == NULL) || (!CompileBroker::should_compile_new_jobs())) {
    C->record_failure("CodeCache is full");
    return;
  }

#ifndef PRODUCT
  // Dump the assembly code, including basic-block numbers
  if (print_assembly()) {
    ttyLocker ttyl;  // keep the following output all in one block
    if (!VMThread::should_terminate()) {  // test this under the tty lock
      // This output goes directly to the tty, not the compiler log.
      // To enable tools to match it up with the compilation activity,
      // be sure to tag this tty output with the compile ID.
      if (xtty != NULL) {
        xtty->head("opto_assembly compile_id='%d'%s", compile_id(),
                   is_osr_compilation()    ? " compile_kind='osr'" :
                   "");
      }
      if (method() != NULL) {
        method()->print_metadata();
      }
      dump_asm(node_offsets, node_offset_limit);
      if (xtty != NULL) {
        // print_metadata and dump_asm above may safepoint which makes us loose the ttylock.
        // Retake lock too make sure the end tag is coherent, and that xmlStream->pop_tag is done
        // thread safe
        ttyLocker ttyl2;
        xtty->tail("opto_assembly");
      }
    }
  }
#endif

}

void Compile::FillExceptionTables(uint cnt, uint *call_returns, uint *inct_starts, Label *blk_labels) {
  _inc_table.set_size(cnt);

  uint inct_cnt = 0;
  for (uint i = 0; i < _cfg->number_of_blocks(); i++) {
    Block* block = _cfg->get_block(i);
    Node *n = NULL;
    int j;

    // Find the branch; ignore trailing NOPs.
    for (j = block->number_of_nodes() - 1; j >= 0; j--) {
      n = block->get_node(j);
      if (!n->is_Mach() || n->as_Mach()->ideal_Opcode() != Op_Con) {
        break;
      }
    }

    // If we didn't find anything, continue
    if (j < 0) {
      continue;
    }

    // Compute ExceptionHandlerTable subtable entry and add it
    // (skip empty blocks)
    if (n->is_Catch()) {

      // Get the offset of the return from the call
      uint call_return = call_returns[block->_pre_order];
#ifdef ASSERT
      assert( call_return > 0, "no call seen for this basic block" );
      while (block->get_node(--j)->is_MachProj()) ;
      assert(block->get_node(j)->is_MachCall(), "CatchProj must follow call");
#endif
      // last instruction is a CatchNode, find it's CatchProjNodes
      int nof_succs = block->_num_succs;
      // allocate space
      GrowableArray<intptr_t> handler_bcis(nof_succs);
      GrowableArray<intptr_t> handler_pcos(nof_succs);
      // iterate through all successors
      for (int j = 0; j < nof_succs; j++) {
        Block* s = block->_succs[j];
        bool found_p = false;
        for (uint k = 1; k < s->num_preds(); k++) {
          Node* pk = s->pred(k);
          if (pk->is_CatchProj() && pk->in(0) == n) {
            const CatchProjNode* p = pk->as_CatchProj();
            found_p = true;
            // add the corresponding handler bci & pco information
            if (p->_con != CatchProjNode::fall_through_index) {
              // p leads to an exception handler (and is not fall through)
              assert(s == _cfg->get_block(s->_pre_order), "bad numbering");
              // no duplicates, please
              if (!handler_bcis.contains(p->handler_bci())) {
                uint block_num = s->non_connector()->_pre_order;
                handler_bcis.append(p->handler_bci());
                handler_pcos.append(blk_labels[block_num].loc_pos());
              }
            }
          }
        }
        assert(found_p, "no matching predecessor found");
        // Note:  Due to empty block removal, one block may have
        // several CatchProj inputs, from the same Catch.
      }

      // Set the offset of the return from the call
      _handler_table.add_subtable(call_return, &handler_bcis, NULL, &handler_pcos);
      continue;
    }

    // Handle implicit null exception table updates
    if (n->is_MachNullCheck()) {
      uint block_num = block->non_connector_successor(0)->_pre_order;
      _inc_table.append(inct_starts[inct_cnt++], blk_labels[block_num].loc_pos());
      continue;
    }
    // Handle implicit exception table updates: trap instructions.
    if (n->is_Mach() && n->as_Mach()->is_TrapBasedCheckNode()) {
      uint block_num = block->non_connector_successor(0)->_pre_order;
      _inc_table.append(inct_starts[inct_cnt++], blk_labels[block_num].loc_pos());
      continue;
    }
  } // End of for all blocks fill in exception table entries
}

// Static Variables
#ifndef PRODUCT
uint Scheduling::_total_nop_size = 0;
uint Scheduling::_total_method_size = 0;
uint Scheduling::_total_branches = 0;
uint Scheduling::_total_unconditional_delays = 0;
uint Scheduling::_total_instructions_per_bundle[Pipeline::_max_instrs_per_cycle+1];
#endif

// Initializer for class Scheduling

Scheduling::Scheduling(Arena *arena, Compile &compile)
  : _arena(arena),
    _cfg(compile.cfg()),
    _regalloc(compile.regalloc()),
    _reg_node(arena),
    _bundle_instr_count(0),
    _bundle_cycle_number(0),
    _scheduled(arena),
    _available(arena),
    _next_node(NULL),
    _bundle_use(0, 0, resource_count, &_bundle_use_elements[0]),
    _pinch_free_list(arena)
#ifndef PRODUCT
  , _branches(0)
  , _unconditional_delays(0)
#endif
{
  // Create a MachNopNode
  _nop = new MachNopNode();

  // Now that the nops are in the array, save the count
  // (but allow entries for the nops)
  _node_bundling_limit = compile.unique();
  uint node_max = _regalloc->node_regs_max_index();

  compile.set_node_bundling_limit(_node_bundling_limit);

  // This one is persistent within the Compile class
  _node_bundling_base = NEW_ARENA_ARRAY(compile.comp_arena(), Bundle, node_max);

  // Allocate space for fixed-size arrays
  _node_latency    = NEW_ARENA_ARRAY(arena, unsigned short, node_max);
  _uses            = NEW_ARENA_ARRAY(arena, short,          node_max);
  _current_latency = NEW_ARENA_ARRAY(arena, unsigned short, node_max);

  // Clear the arrays
  memset(_node_bundling_base, 0, node_max * sizeof(Bundle));
  memset(_node_latency,       0, node_max * sizeof(unsigned short));
  memset(_uses,               0, node_max * sizeof(short));
  memset(_current_latency,    0, node_max * sizeof(unsigned short));

  // Clear the bundling information
  memcpy(_bundle_use_elements, Pipeline_Use::elaborated_elements, sizeof(Pipeline_Use::elaborated_elements));

  // Get the last node
  Block* block = _cfg->get_block(_cfg->number_of_blocks() - 1);

  _next_node = block->get_node(block->number_of_nodes() - 1);
}

#ifndef PRODUCT
// Scheduling destructor
Scheduling::~Scheduling() {
  _total_branches             += _branches;
  _total_unconditional_delays += _unconditional_delays;
}
#endif

// Step ahead "i" cycles
void Scheduling::step(uint i) {

  Bundle *bundle = node_bundling(_next_node);
  bundle->set_starts_bundle();

  // Update the bundle record, but leave the flags information alone
  if (_bundle_instr_count > 0) {
    bundle->set_instr_count(_bundle_instr_count);
    bundle->set_resources_used(_bundle_use.resourcesUsed());
  }

  // Update the state information
  _bundle_instr_count = 0;
  _bundle_cycle_number += i;
  _bundle_use.step(i);
}

void Scheduling::step_and_clear() {
  Bundle *bundle = node_bundling(_next_node);
  bundle->set_starts_bundle();

  // Update the bundle record
  if (_bundle_instr_count > 0) {
    bundle->set_instr_count(_bundle_instr_count);
    bundle->set_resources_used(_bundle_use.resourcesUsed());

    _bundle_cycle_number += 1;
  }

  // Clear the bundling information
  _bundle_instr_count = 0;
  _bundle_use.reset();

  memcpy(_bundle_use_elements,
    Pipeline_Use::elaborated_elements,
    sizeof(Pipeline_Use::elaborated_elements));
}

// Perform instruction scheduling and bundling over the sequence of
// instructions in backwards order.
void Compile::ScheduleAndBundle() {

  // Don't optimize this if it isn't a method
  if (!_method)
    return;

  // Don't optimize this if scheduling is disabled
  if (!do_scheduling())
    return;

  // Scheduling code works only with pairs (16 bytes) maximum.
  if (max_vector_size() > 16)
    return;

  TracePhase tp("isched", &timers[_t_instrSched]);

  // Create a data structure for all the scheduling information
  Scheduling scheduling(Thread::current()->resource_area(), *this);

  // Walk backwards over each basic block, computing the needed alignment
  // Walk over all the basic blocks
  scheduling.DoScheduling();
}

// Compute the latency of all the instructions.  This is fairly simple,
// because we already have a legal ordering.  Walk over the instructions
// from first to last, and compute the latency of the instruction based
// on the latency of the preceding instruction(s).
void Scheduling::ComputeLocalLatenciesForward(const Block *bb) {
#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    tty->print("# -> ComputeLocalLatenciesForward\n");
#endif

  // Walk over all the schedulable instructions
  for( uint j=_bb_start; j < _bb_end; j++ ) {

    // This is a kludge, forcing all latency calculations to start at 1.
    // Used to allow latency 0 to force an instruction to the beginning
    // of the bb
    uint latency = 1;
    Node *use = bb->get_node(j);
    uint nlen = use->len();

    // Walk over all the inputs
    for ( uint k=0; k < nlen; k++ ) {
      Node *def = use->in(k);
      if (!def)
        continue;

      uint l = _node_latency[def->_idx] + use->latency(k);
      if (latency < l)
        latency = l;
    }

    _node_latency[use->_idx] = latency;

#ifndef PRODUCT
    if (_cfg->C->trace_opto_output()) {
      tty->print("# latency %4d: ", latency);
      use->dump();
    }
#endif
  }

#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    tty->print("# <- ComputeLocalLatenciesForward\n");
#endif

} // end ComputeLocalLatenciesForward

// See if this node fits into the present instruction bundle
bool Scheduling::NodeFitsInBundle(Node *n) {
  uint n_idx = n->_idx;

  // If this is the unconditional delay instruction, then it fits
  if (n == _unconditional_delay_slot) {
#ifndef PRODUCT
    if (_cfg->C->trace_opto_output())
      tty->print("#     NodeFitsInBundle [%4d]: TRUE; is in unconditional delay slot\n", n->_idx);
#endif
    return (true);
  }

  // If the node cannot be scheduled this cycle, skip it
  if (_current_latency[n_idx] > _bundle_cycle_number) {
#ifndef PRODUCT
    if (_cfg->C->trace_opto_output())
      tty->print("#     NodeFitsInBundle [%4d]: FALSE; latency %4d > %d\n",
        n->_idx, _current_latency[n_idx], _bundle_cycle_number);
#endif
    return (false);
  }

  const Pipeline *node_pipeline = n->pipeline();

  uint instruction_count = node_pipeline->instructionCount();
  if (node_pipeline->mayHaveNoCode() && n->size(_regalloc) == 0)
    instruction_count = 0;
  else if (node_pipeline->hasBranchDelay() && !_unconditional_delay_slot)
    instruction_count++;

  if (_bundle_instr_count + instruction_count > Pipeline::_max_instrs_per_cycle) {
#ifndef PRODUCT
    if (_cfg->C->trace_opto_output())
      tty->print("#     NodeFitsInBundle [%4d]: FALSE; too many instructions: %d > %d\n",
        n->_idx, _bundle_instr_count + instruction_count, Pipeline::_max_instrs_per_cycle);
#endif
    return (false);
  }

  // Don't allow non-machine nodes to be handled this way
  if (!n->is_Mach() && instruction_count == 0)
    return (false);

  // See if there is any overlap
  uint delay = _bundle_use.full_latency(0, node_pipeline->resourceUse());

  if (delay > 0) {
#ifndef PRODUCT
    if (_cfg->C->trace_opto_output())
      tty->print("#     NodeFitsInBundle [%4d]: FALSE; functional units overlap\n", n_idx);
#endif
    return false;
  }

#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    tty->print("#     NodeFitsInBundle [%4d]:  TRUE\n", n_idx);
#endif

  return true;
}

Node * Scheduling::ChooseNodeToBundle() {
  uint siz = _available.size();

  if (siz == 0) {

#ifndef PRODUCT
    if (_cfg->C->trace_opto_output())
      tty->print("#   ChooseNodeToBundle: NULL\n");
#endif
    return (NULL);
  }

  // Fast path, if only 1 instruction in the bundle
  if (siz == 1) {
#ifndef PRODUCT
    if (_cfg->C->trace_opto_output()) {
      tty->print("#   ChooseNodeToBundle (only 1): ");
      _available[0]->dump();
    }
#endif
    return (_available[0]);
  }

  // Don't bother, if the bundle is already full
  if (_bundle_instr_count < Pipeline::_max_instrs_per_cycle) {
    for ( uint i = 0; i < siz; i++ ) {
      Node *n = _available[i];

      // Skip projections, we'll handle them another way
      if (n->is_Proj())
        continue;

      // This presupposed that instructions are inserted into the
      // available list in a legality order; i.e. instructions that
      // must be inserted first are at the head of the list
      if (NodeFitsInBundle(n)) {
#ifndef PRODUCT
        if (_cfg->C->trace_opto_output()) {
          tty->print("#   ChooseNodeToBundle: ");
          n->dump();
        }
#endif
        return (n);
      }
    }
  }

  // Nothing fits in this bundle, choose the highest priority
#ifndef PRODUCT
  if (_cfg->C->trace_opto_output()) {
    tty->print("#   ChooseNodeToBundle: ");
    _available[0]->dump();
  }
#endif

  return _available[0];
}

void Scheduling::AddNodeToAvailableList(Node *n) {
  assert( !n->is_Proj(), "projections never directly made available" );
#ifndef PRODUCT
  if (_cfg->C->trace_opto_output()) {
    tty->print("#   AddNodeToAvailableList: ");
    n->dump();
  }
#endif

  int latency = _current_latency[n->_idx];

  // Insert in latency order (insertion sort)
  uint i;
  for ( i=0; i < _available.size(); i++ )
    if (_current_latency[_available[i]->_idx] > latency)
      break;

  // Special Check for compares following branches
  if( n->is_Mach() && _scheduled.size() > 0 ) {
    int op = n->as_Mach()->ideal_Opcode();
    Node *last = _scheduled[0];
    if( last->is_MachIf() && last->in(1) == n &&
        ( op == Op_CmpI ||
          op == Op_CmpU ||
          op == Op_CmpUL ||
          op == Op_CmpP ||
          op == Op_CmpF ||
          op == Op_CmpD ||
          op == Op_CmpL ) ) {

      // Recalculate position, moving to front of same latency
      for ( i=0 ; i < _available.size(); i++ )
        if (_current_latency[_available[i]->_idx] >= latency)
          break;
    }
  }

  // Insert the node in the available list
  _available.insert(i, n);

#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    dump_available();
#endif
}

void Scheduling::DecrementUseCounts(Node *n, const Block *bb) {
  for ( uint i=0; i < n->len(); i++ ) {
    Node *def = n->in(i);
    if (!def) continue;
    if( def->is_Proj() )        // If this is a machine projection, then
      def = def->in(0);         // propagate usage thru to the base instruction

    if(_cfg->get_block_for_node(def) != bb) { // Ignore if not block-local
      continue;
    }

    // Compute the latency
    uint l = _bundle_cycle_number + n->latency(i);
    if (_current_latency[def->_idx] < l)
      _current_latency[def->_idx] = l;

    // If this does not have uses then schedule it
    if ((--_uses[def->_idx]) == 0)
      AddNodeToAvailableList(def);
  }
}

void Scheduling::AddNodeToBundle(Node *n, const Block *bb) {
#ifndef PRODUCT
  if (_cfg->C->trace_opto_output()) {
    tty->print("#   AddNodeToBundle: ");
    n->dump();
  }
#endif

  // Remove this from the available list
  uint i;
  for (i = 0; i < _available.size(); i++)
    if (_available[i] == n)
      break;
  assert(i < _available.size(), "entry in _available list not found");
  _available.remove(i);

  // See if this fits in the current bundle
  const Pipeline *node_pipeline = n->pipeline();
  const Pipeline_Use& node_usage = node_pipeline->resourceUse();

  // Check for instructions to be placed in the delay slot. We
  // do this before we actually schedule the current instruction,
  // because the delay slot follows the current instruction.
  if (Pipeline::_branch_has_delay_slot &&
      node_pipeline->hasBranchDelay() &&
      !_unconditional_delay_slot) {

    uint siz = _available.size();

    // Conditional branches can support an instruction that
    // is unconditionally executed and not dependent by the
    // branch, OR a conditionally executed instruction if
    // the branch is taken.  In practice, this means that
    // the first instruction at the branch target is
    // copied to the delay slot, and the branch goes to
    // the instruction after that at the branch target
    if ( n->is_MachBranch() ) {

      assert( !n->is_MachNullCheck(), "should not look for delay slot for Null Check" );
      assert( !n->is_Catch(),         "should not look for delay slot for Catch" );

#ifndef PRODUCT
      _branches++;
#endif

      // At least 1 instruction is on the available list
      // that is not dependent on the branch
      for (uint i = 0; i < siz; i++) {
        Node *d = _available[i];
        const Pipeline *avail_pipeline = d->pipeline();

        // Don't allow safepoints in the branch shadow, that will
        // cause a number of difficulties
        if ( avail_pipeline->instructionCount() == 1 &&
            !avail_pipeline->hasMultipleBundles() &&
            !avail_pipeline->hasBranchDelay() &&
            Pipeline::instr_has_unit_size() &&
            d->size(_regalloc) == Pipeline::instr_unit_size() &&
            NodeFitsInBundle(d) &&
            !node_bundling(d)->used_in_delay()) {

          if (d->is_Mach() && !d->is_MachSafePoint()) {
            // A node that fits in the delay slot was found, so we need to
            // set the appropriate bits in the bundle pipeline information so
            // that it correctly indicates resource usage.  Later, when we
            // attempt to add this instruction to the bundle, we will skip
            // setting the resource usage.
            _unconditional_delay_slot = d;
            node_bundling(n)->set_use_unconditional_delay();
            node_bundling(d)->set_used_in_unconditional_delay();
            _bundle_use.add_usage(avail_pipeline->resourceUse());
            _current_latency[d->_idx] = _bundle_cycle_number;
            _next_node = d;
            ++_bundle_instr_count;
#ifndef PRODUCT
            _unconditional_delays++;
#endif
            break;
          }
        }
      }
    }

    // No delay slot, add a nop to the usage
    if (!_unconditional_delay_slot) {
      // See if adding an instruction in the delay slot will overflow
      // the bundle.
      if (!NodeFitsInBundle(_nop)) {
#ifndef PRODUCT
        if (_cfg->C->trace_opto_output())
          tty->print("#  *** STEP(1 instruction for delay slot) ***\n");
#endif
        step(1);
      }

      _bundle_use.add_usage(_nop->pipeline()->resourceUse());
      _next_node = _nop;
      ++_bundle_instr_count;
    }

    // See if the instruction in the delay slot requires a
    // step of the bundles
    if (!NodeFitsInBundle(n)) {
#ifndef PRODUCT
        if (_cfg->C->trace_opto_output())
          tty->print("#  *** STEP(branch won't fit) ***\n");
#endif
        // Update the state information
        _bundle_instr_count = 0;
        _bundle_cycle_number += 1;
        _bundle_use.step(1);
    }
  }

  // Get the number of instructions
  uint instruction_count = node_pipeline->instructionCount();
  if (node_pipeline->mayHaveNoCode() && n->size(_regalloc) == 0)
    instruction_count = 0;

  // Compute the latency information
  uint delay = 0;

  if (instruction_count > 0 || !node_pipeline->mayHaveNoCode()) {
    int relative_latency = _current_latency[n->_idx] - _bundle_cycle_number;
    if (relative_latency < 0)
      relative_latency = 0;

    delay = _bundle_use.full_latency(relative_latency, node_usage);

    // Does not fit in this bundle, start a new one
    if (delay > 0) {
      step(delay);

#ifndef PRODUCT
      if (_cfg->C->trace_opto_output())
        tty->print("#  *** STEP(%d) ***\n", delay);
#endif
    }
  }

  // If this was placed in the delay slot, ignore it
  if (n != _unconditional_delay_slot) {

    if (delay == 0) {
      if (node_pipeline->hasMultipleBundles()) {
#ifndef PRODUCT
        if (_cfg->C->trace_opto_output())
          tty->print("#  *** STEP(multiple instructions) ***\n");
#endif
        step(1);
      }

      else if (instruction_count + _bundle_instr_count > Pipeline::_max_instrs_per_cycle) {
#ifndef PRODUCT
        if (_cfg->C->trace_opto_output())
          tty->print("#  *** STEP(%d >= %d instructions) ***\n",
            instruction_count + _bundle_instr_count,
            Pipeline::_max_instrs_per_cycle);
#endif
        step(1);
      }
    }

    if (node_pipeline->hasBranchDelay() && !_unconditional_delay_slot)
      _bundle_instr_count++;

    // Set the node's latency
    _current_latency[n->_idx] = _bundle_cycle_number;

    // Now merge the functional unit information
    if (instruction_count > 0 || !node_pipeline->mayHaveNoCode())
      _bundle_use.add_usage(node_usage);

    // Increment the number of instructions in this bundle
    _bundle_instr_count += instruction_count;

    // Remember this node for later
    if (n->is_Mach())
      _next_node = n;
  }

  // It's possible to have a BoxLock in the graph and in the _bbs mapping but
  // not in the bb->_nodes array.  This happens for debug-info-only BoxLocks.
  // 'Schedule' them (basically ignore in the schedule) but do not insert them
  // into the block.  All other scheduled nodes get put in the schedule here.
  int op = n->Opcode();
  if( (op == Op_Node && n->req() == 0) || // anti-dependence node OR
      (op != Op_Node &&         // Not an unused antidepedence node and
       // not an unallocated boxlock
       (OptoReg::is_valid(_regalloc->get_reg_first(n)) || op != Op_BoxLock)) ) {

    // Push any trailing projections
    if( bb->get_node(bb->number_of_nodes()-1) != n ) {
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node *foi = n->fast_out(i);
        if( foi->is_Proj() )
          _scheduled.push(foi);
      }
    }

    // Put the instruction in the schedule list
    _scheduled.push(n);
  }

#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    dump_available();
#endif

  // Walk all the definitions, decrementing use counts, and
  // if a definition has a 0 use count, place it in the available list.
  DecrementUseCounts(n,bb);
}

// This method sets the use count within a basic block.  We will ignore all
// uses outside the current basic block.  As we are doing a backwards walk,
// any node we reach that has a use count of 0 may be scheduled.  This also
// avoids the problem of cyclic references from phi nodes, as long as phi
// nodes are at the front of the basic block.  This method also initializes
// the available list to the set of instructions that have no uses within this
// basic block.
void Scheduling::ComputeUseCount(const Block *bb) {
#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    tty->print("# -> ComputeUseCount\n");
#endif

  // Clear the list of available and scheduled instructions, just in case
  _available.clear();
  _scheduled.clear();

  // No delay slot specified
  _unconditional_delay_slot = NULL;

#ifdef ASSERT
  for( uint i=0; i < bb->number_of_nodes(); i++ )
    assert( _uses[bb->get_node(i)->_idx] == 0, "_use array not clean" );
#endif

  // Force the _uses count to never go to zero for unscheduable pieces
  // of the block
  for( uint k = 0; k < _bb_start; k++ )
    _uses[bb->get_node(k)->_idx] = 1;
  for( uint l = _bb_end; l < bb->number_of_nodes(); l++ )
    _uses[bb->get_node(l)->_idx] = 1;

  // Iterate backwards over the instructions in the block.  Don't count the
  // branch projections at end or the block header instructions.
  for( uint j = _bb_end-1; j >= _bb_start; j-- ) {
    Node *n = bb->get_node(j);
    if( n->is_Proj() ) continue; // Projections handled another way

    // Account for all uses
    for ( uint k = 0; k < n->len(); k++ ) {
      Node *inp = n->in(k);
      if (!inp) continue;
      assert(inp != n, "no cycles allowed" );
      if (_cfg->get_block_for_node(inp) == bb) { // Block-local use?
        if (inp->is_Proj()) { // Skip through Proj's
          inp = inp->in(0);
        }
        ++_uses[inp->_idx];     // Count 1 block-local use
      }
    }

    // If this instruction has a 0 use count, then it is available
    if (!_uses[n->_idx]) {
      _current_latency[n->_idx] = _bundle_cycle_number;
      AddNodeToAvailableList(n);
    }

#ifndef PRODUCT
    if (_cfg->C->trace_opto_output()) {
      tty->print("#   uses: %3d: ", _uses[n->_idx]);
      n->dump();
    }
#endif
  }

#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    tty->print("# <- ComputeUseCount\n");
#endif
}

// This routine performs scheduling on each basic block in reverse order,
// using instruction latencies and taking into account function unit
// availability.
void Scheduling::DoScheduling() {
#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    tty->print("# -> DoScheduling\n");
#endif

  Block *succ_bb = NULL;
  Block *bb;

  // Walk over all the basic blocks in reverse order
  for (int i = _cfg->number_of_blocks() - 1; i >= 0; succ_bb = bb, i--) {
    bb = _cfg->get_block(i);

#ifndef PRODUCT
    if (_cfg->C->trace_opto_output()) {
      tty->print("#  Schedule BB#%03d (initial)\n", i);
      for (uint j = 0; j < bb->number_of_nodes(); j++) {
        bb->get_node(j)->dump();
      }
    }
#endif

    // On the head node, skip processing
    if (bb == _cfg->get_root_block()) {
      continue;
    }

    // Skip empty, connector blocks
    if (bb->is_connector())
      continue;

    // If the following block is not the sole successor of
    // this one, then reset the pipeline information
    if (bb->_num_succs != 1 || bb->non_connector_successor(0) != succ_bb) {
#ifndef PRODUCT
      if (_cfg->C->trace_opto_output()) {
        tty->print("*** bundle start of next BB, node %d, for %d instructions\n",
                   _next_node->_idx, _bundle_instr_count);
      }
#endif
      step_and_clear();
    }

    // Leave untouched the starting instruction, any Phis, a CreateEx node
    // or Top.  bb->get_node(_bb_start) is the first schedulable instruction.
    _bb_end = bb->number_of_nodes()-1;
    for( _bb_start=1; _bb_start <= _bb_end; _bb_start++ ) {
      Node *n = bb->get_node(_bb_start);
      // Things not matched, like Phinodes and ProjNodes don't get scheduled.
      // Also, MachIdealNodes do not get scheduled
      if( !n->is_Mach() ) continue;     // Skip non-machine nodes
      MachNode *mach = n->as_Mach();
      int iop = mach->ideal_Opcode();
      if( iop == Op_CreateEx ) continue; // CreateEx is pinned
      if( iop == Op_Con ) continue;      // Do not schedule Top
      if( iop == Op_Node &&     // Do not schedule PhiNodes, ProjNodes
          mach->pipeline() == MachNode::pipeline_class() &&
          !n->is_SpillCopy() && !n->is_MachMerge() )  // Breakpoints, Prolog, etc
        continue;
      break;                    // Funny loop structure to be sure...
    }
    // Compute last "interesting" instruction in block - last instruction we
    // might schedule.  _bb_end points just after last schedulable inst.  We
    // normally schedule conditional branches (despite them being forced last
    // in the block), because they have delay slots we can fill.  Calls all
    // have their delay slots filled in the template expansions, so we don't
    // bother scheduling them.
    Node *last = bb->get_node(_bb_end);
    // Ignore trailing NOPs.
    while (_bb_end > 0 && last->is_Mach() &&
           last->as_Mach()->ideal_Opcode() == Op_Con) {
      last = bb->get_node(--_bb_end);
    }
    assert(!last->is_Mach() || last->as_Mach()->ideal_Opcode() != Op_Con, "");
    if( last->is_Catch() ||
       // Exclude unreachable path case when Halt node is in a separate block.
       (_bb_end > 1 && last->is_Mach() && last->as_Mach()->ideal_Opcode() == Op_Halt) ) {
      // There must be a prior call.  Skip it.
      while( !bb->get_node(--_bb_end)->is_MachCall() ) {
        assert( bb->get_node(_bb_end)->is_MachProj(), "skipping projections after expected call" );
      }
    } else if( last->is_MachNullCheck() ) {
      // Backup so the last null-checked memory instruction is
      // outside the schedulable range. Skip over the nullcheck,
      // projection, and the memory nodes.
      Node *mem = last->in(1);
      do {
        _bb_end--;
      } while (mem != bb->get_node(_bb_end));
    } else {
      // Set _bb_end to point after last schedulable inst.
      _bb_end++;
    }

    assert( _bb_start <= _bb_end, "inverted block ends" );

    // Compute the register antidependencies for the basic block
    ComputeRegisterAntidependencies(bb);
    if (_cfg->C->failing())  return;  // too many D-U pinch points

    // Compute intra-bb latencies for the nodes
    ComputeLocalLatenciesForward(bb);

    // Compute the usage within the block, and set the list of all nodes
    // in the block that have no uses within the block.
    ComputeUseCount(bb);

    // Schedule the remaining instructions in the block
    while ( _available.size() > 0 ) {
      Node *n = ChooseNodeToBundle();
      guarantee(n != NULL, "no nodes available");
      AddNodeToBundle(n,bb);
    }

    assert( _scheduled.size() == _bb_end - _bb_start, "wrong number of instructions" );
#ifdef ASSERT
    for( uint l = _bb_start; l < _bb_end; l++ ) {
      Node *n = bb->get_node(l);
      uint m;
      for( m = 0; m < _bb_end-_bb_start; m++ )
        if( _scheduled[m] == n )
          break;
      assert( m < _bb_end-_bb_start, "instruction missing in schedule" );
    }
#endif

    // Now copy the instructions (in reverse order) back to the block
    for ( uint k = _bb_start; k < _bb_end; k++ )
      bb->map_node(_scheduled[_bb_end-k-1], k);

#ifndef PRODUCT
    if (_cfg->C->trace_opto_output()) {
      tty->print("#  Schedule BB#%03d (final)\n", i);
      uint current = 0;
      for (uint j = 0; j < bb->number_of_nodes(); j++) {
        Node *n = bb->get_node(j);
        if( valid_bundle_info(n) ) {
          Bundle *bundle = node_bundling(n);
          if (bundle->instr_count() > 0 || bundle->flags() > 0) {
            tty->print("*** Bundle: ");
            bundle->dump();
          }
          n->dump();
        }
      }
    }
#endif
#ifdef ASSERT
  verify_good_schedule(bb,"after block local scheduling");
#endif
  }

#ifndef PRODUCT
  if (_cfg->C->trace_opto_output())
    tty->print("# <- DoScheduling\n");
#endif

  // Record final node-bundling array location
  _regalloc->C->set_node_bundling_base(_node_bundling_base);

} // end DoScheduling

// Verify that no live-range used in the block is killed in the block by a
// wrong DEF.  This doesn't verify live-ranges that span blocks.

// Check for edge existence.  Used to avoid adding redundant precedence edges.
static bool edge_from_to( Node *from, Node *to ) {
  for( uint i=0; i<from->len(); i++ )
    if( from->in(i) == to )
      return true;
  return false;
}

#ifdef ASSERT
void Scheduling::verify_do_def( Node *n, OptoReg::Name def, const char *msg ) {
  // Check for bad kills
  if( OptoReg::is_valid(def) ) { // Ignore stores & control flow
    Node *prior_use = _reg_node[def];
    if( prior_use && !edge_from_to(prior_use,n) ) {
      tty->print("%s = ",OptoReg::as_VMReg(def)->name());
      n->dump();
      tty->print_cr("...");
      prior_use->dump();
      assert(edge_from_to(prior_use,n), "%s", msg);
    }
    _reg_node.map(def,NULL); // Kill live USEs
  }
}

void Scheduling::verify_good_schedule( Block *b, const char *msg ) {

  // Zap to something reasonable for the verify code
  _reg_node.clear();

  // Walk over the block backwards.  Check to make sure each DEF doesn't
  // kill a live value (other than the one it's supposed to).  Add each
  // USE to the live set.
  for( uint i = b->number_of_nodes()-1; i >= _bb_start; i-- ) {
    Node *n = b->get_node(i);
    int n_op = n->Opcode();
    if( n_op == Op_MachProj && n->ideal_reg() == MachProjNode::fat_proj ) {
      // Fat-proj kills a slew of registers
      RegMask rm = n->out_RegMask();// Make local copy
      while( rm.is_NotEmpty() ) {
        OptoReg::Name kill = rm.find_first_elem();
        rm.Remove(kill);
        verify_do_def( n, kill, msg );
      }
    } else if( n_op != Op_Node ) { // Avoid brand new antidependence nodes
      // Get DEF'd registers the normal way
      verify_do_def( n, _regalloc->get_reg_first(n), msg );
      verify_do_def( n, _regalloc->get_reg_second(n), msg );
    }

    // Now make all USEs live
    for( uint i=1; i<n->req(); i++ ) {
      Node *def = n->in(i);
      assert(def != 0, "input edge required");
      OptoReg::Name reg_lo = _regalloc->get_reg_first(def);
      OptoReg::Name reg_hi = _regalloc->get_reg_second(def);
      if( OptoReg::is_valid(reg_lo) ) {
        assert(!_reg_node[reg_lo] || edge_from_to(_reg_node[reg_lo],def), "%s", msg);
        _reg_node.map(reg_lo,n);
      }
      if( OptoReg::is_valid(reg_hi) ) {
        assert(!_reg_node[reg_hi] || edge_from_to(_reg_node[reg_hi],def), "%s", msg);
        _reg_node.map(reg_hi,n);
      }
    }

  }

  // Zap to something reasonable for the Antidependence code
  _reg_node.clear();
}
#endif

// Conditionally add precedence edges.  Avoid putting edges on Projs.
static void add_prec_edge_from_to( Node *from, Node *to ) {
  if( from->is_Proj() ) {       // Put precedence edge on Proj's input
    assert( from->req() == 1 && (from->len() == 1 || from->in(1)==0), "no precedence edges on projections" );
    from = from->in(0);
  }
  if( from != to &&             // No cycles (for things like LD L0,[L0+4] )
      !edge_from_to( from, to ) ) // Avoid duplicate edge
    from->add_prec(to);
}

void Scheduling::anti_do_def( Block *b, Node *def, OptoReg::Name def_reg, int is_def ) {
  if( !OptoReg::is_valid(def_reg) ) // Ignore stores & control flow
    return;

  Node *pinch = _reg_node[def_reg]; // Get pinch point
  if ((pinch == NULL) || _cfg->get_block_for_node(pinch) != b || // No pinch-point yet?
      is_def ) {    // Check for a true def (not a kill)
    _reg_node.map(def_reg,def); // Record def/kill as the optimistic pinch-point
    return;
  }

  Node *kill = def;             // Rename 'def' to more descriptive 'kill'
  debug_only( def = (Node*)0xdeadbeef; )

  // After some number of kills there _may_ be a later def
  Node *later_def = NULL;

  // Finding a kill requires a real pinch-point.
  // Check for not already having a pinch-point.
  // Pinch points are Op_Node's.
  if( pinch->Opcode() != Op_Node ) { // Or later-def/kill as pinch-point?
    later_def = pinch;            // Must be def/kill as optimistic pinch-point
    if ( _pinch_free_list.size() > 0) {
      pinch = _pinch_free_list.pop();
    } else {
      pinch = new Node(1); // Pinch point to-be
    }
    if (pinch->_idx >= _regalloc->node_regs_max_index()) {
      _cfg->C->record_method_not_compilable("too many D-U pinch points");
      return;
    }
    _cfg->map_node_to_block(pinch, b);      // Pretend it's valid in this block (lazy init)
    _reg_node.map(def_reg,pinch); // Record pinch-point
    //_regalloc->set_bad(pinch->_idx); // Already initialized this way.
    if( later_def->outcnt() == 0 || later_def->ideal_reg() == MachProjNode::fat_proj ) { // Distinguish def from kill
      pinch->init_req(0, _cfg->C->top());     // set not NULL for the next call
      add_prec_edge_from_to(later_def,pinch); // Add edge from kill to pinch
      later_def = NULL;           // and no later def
    }
    pinch->set_req(0,later_def);  // Hook later def so we can find it
  } else {                        // Else have valid pinch point
    if( pinch->in(0) )            // If there is a later-def
      later_def = pinch->in(0);   // Get it
  }

  // Add output-dependence edge from later def to kill
  if( later_def )               // If there is some original def
    add_prec_edge_from_to(later_def,kill); // Add edge from def to kill

  // See if current kill is also a use, and so is forced to be the pinch-point.
  if( pinch->Opcode() == Op_Node ) {
    Node *uses = kill->is_Proj() ? kill->in(0) : kill;
    for( uint i=1; i<uses->req(); i++ ) {
      if( _regalloc->get_reg_first(uses->in(i)) == def_reg ||
          _regalloc->get_reg_second(uses->in(i)) == def_reg ) {
        // Yes, found a use/kill pinch-point
        pinch->set_req(0,NULL);  //
        pinch->replace_by(kill); // Move anti-dep edges up
        pinch = kill;
        _reg_node.map(def_reg,pinch);
        return;
      }
    }
  }

  // Add edge from kill to pinch-point
  add_prec_edge_from_to(kill,pinch);
}

void Scheduling::anti_do_use( Block *b, Node *use, OptoReg::Name use_reg ) {
  if( !OptoReg::is_valid(use_reg) ) // Ignore stores & control flow
    return;
  Node *pinch = _reg_node[use_reg]; // Get pinch point
  // Check for no later def_reg/kill in block
  if ((pinch != NULL) && _cfg->get_block_for_node(pinch) == b &&
      // Use has to be block-local as well
      _cfg->get_block_for_node(use) == b) {
    if( pinch->Opcode() == Op_Node && // Real pinch-point (not optimistic?)
        pinch->req() == 1 ) {   // pinch not yet in block?
      pinch->del_req(0);        // yank pointer to later-def, also set flag
      // Insert the pinch-point in the block just after the last use
      b->insert_node(pinch, b->find_node(use) + 1);
      _bb_end++;                // Increase size scheduled region in block
    }

    add_prec_edge_from_to(pinch,use);
  }
}

// We insert antidependences between the reads and following write of
// allocated registers to prevent illegal code motion. Hopefully, the
// number of added references should be fairly small, especially as we
// are only adding references within the current basic block.
void Scheduling::ComputeRegisterAntidependencies(Block *b) {

#ifdef ASSERT
  verify_good_schedule(b,"before block local scheduling");
#endif

  // A valid schedule, for each register independently, is an endless cycle
  // of: a def, then some uses (connected to the def by true dependencies),
  // then some kills (defs with no uses), finally the cycle repeats with a new
  // def.  The uses are allowed to float relative to each other, as are the
  // kills.  No use is allowed to slide past a kill (or def).  This requires
  // antidependencies between all uses of a single def and all kills that
  // follow, up to the next def.  More edges are redundant, because later defs
  // & kills are already serialized with true or antidependencies.  To keep
  // the edge count down, we add a 'pinch point' node if there's more than
  // one use or more than one kill/def.

  // We add dependencies in one bottom-up pass.

  // For each instruction we handle it's DEFs/KILLs, then it's USEs.

  // For each DEF/KILL, we check to see if there's a prior DEF/KILL for this
  // register.  If not, we record the DEF/KILL in _reg_node, the
  // register-to-def mapping.  If there is a prior DEF/KILL, we insert a
  // "pinch point", a new Node that's in the graph but not in the block.
  // We put edges from the prior and current DEF/KILLs to the pinch point.
  // We put the pinch point in _reg_node.  If there's already a pinch point
  // we merely add an edge from the current DEF/KILL to the pinch point.

  // After doing the DEF/KILLs, we handle USEs.  For each used register, we
  // put an edge from the pinch point to the USE.

  // To be expedient, the _reg_node array is pre-allocated for the whole
  // compilation.  _reg_node is lazily initialized; it either contains a NULL,
  // or a valid def/kill/pinch-point, or a leftover node from some prior
  // block.  Leftover node from some prior block is treated like a NULL (no
  // prior def, so no anti-dependence needed).  Valid def is distinguished by
  // it being in the current block.
  bool fat_proj_seen = false;
  uint last_safept = _bb_end-1;
  Node* end_node         = (_bb_end-1 >= _bb_start) ? b->get_node(last_safept) : NULL;
  Node* last_safept_node = end_node;
  for( uint i = _bb_end-1; i >= _bb_start; i-- ) {
    Node *n = b->get_node(i);
    int is_def = n->outcnt();   // def if some uses prior to adding precedence edges
    if( n->is_MachProj() && n->ideal_reg() == MachProjNode::fat_proj ) {
      // Fat-proj kills a slew of registers
      // This can add edges to 'n' and obscure whether or not it was a def,
      // hence the is_def flag.
      fat_proj_seen = true;
      RegMask rm = n->out_RegMask();// Make local copy
      while( rm.is_NotEmpty() ) {
        OptoReg::Name kill = rm.find_first_elem();
        rm.Remove(kill);
        anti_do_def( b, n, kill, is_def );
      }
    } else {
      // Get DEF'd registers the normal way
      anti_do_def( b, n, _regalloc->get_reg_first(n), is_def );
      anti_do_def( b, n, _regalloc->get_reg_second(n), is_def );
    }

    // Kill projections on a branch should appear to occur on the
    // branch, not afterwards, so grab the masks from the projections
    // and process them.
    if (n->is_MachBranch() || n->is_Mach() && n->as_Mach()->ideal_Opcode() == Op_Jump) {
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node* use = n->fast_out(i);
        if (use->is_Proj()) {
          RegMask rm = use->out_RegMask();// Make local copy
          while( rm.is_NotEmpty() ) {
            OptoReg::Name kill = rm.find_first_elem();
            rm.Remove(kill);
            anti_do_def( b, n, kill, false );
          }
        }
      }
    }

    // Check each register used by this instruction for a following DEF/KILL
    // that must occur afterward and requires an anti-dependence edge.
    for( uint j=0; j<n->req(); j++ ) {
      Node *def = n->in(j);
      if( def ) {
        assert( !def->is_MachProj() || def->ideal_reg() != MachProjNode::fat_proj, "" );
        anti_do_use( b, n, _regalloc->get_reg_first(def) );
        anti_do_use( b, n, _regalloc->get_reg_second(def) );
      }
    }
    // Do not allow defs of new derived values to float above GC
    // points unless the base is definitely available at the GC point.

    Node *m = b->get_node(i);

    // Add precedence edge from following safepoint to use of derived pointer
    if( last_safept_node != end_node &&
        m != last_safept_node) {
      for (uint k = 1; k < m->req(); k++) {
        const Type *t = m->in(k)->bottom_type();
        if( t->isa_oop_ptr() &&
            t->is_ptr()->offset() != 0 ) {
          last_safept_node->add_prec( m );
          break;
        }
      }
    }

    if( n->jvms() ) {           // Precedence edge from derived to safept
      // Check if last_safept_node was moved by pinch-point insertion in anti_do_use()
      if( b->get_node(last_safept) != last_safept_node ) {
        last_safept = b->find_node(last_safept_node);
      }
      for( uint j=last_safept; j > i; j-- ) {
        Node *mach = b->get_node(j);
        if( mach->is_Mach() && mach->as_Mach()->ideal_Opcode() == Op_AddP )
          mach->add_prec( n );
      }
      last_safept = i;
      last_safept_node = m;
    }
  }

  if (fat_proj_seen) {
    // Garbage collect pinch nodes that were not consumed.
    // They are usually created by a fat kill MachProj for a call.
    garbage_collect_pinch_nodes();
  }
}

// Garbage collect pinch nodes for reuse by other blocks.
//
// The block scheduler's insertion of anti-dependence
// edges creates many pinch nodes when the block contains
// 2 or more Calls.  A pinch node is used to prevent a
// combinatorial explosion of edges.  If a set of kills for a
// register is anti-dependent on a set of uses (or defs), rather
// than adding an edge in the graph between each pair of kill
// and use (or def), a pinch is inserted between them:
//
//            use1   use2  use3
//                \   |   /
//                 \  |  /
//                  pinch
//                 /  |  \
//                /   |   \
//            kill1 kill2 kill3
//
// One pinch node is created per register killed when
// the second call is encountered during a backwards pass
// over the block.  Most of these pinch nodes are never
// wired into the graph because the register is never
// used or def'ed in the block.
//
void Scheduling::garbage_collect_pinch_nodes() {
#ifndef PRODUCT
    if (_cfg->C->trace_opto_output()) tty->print("Reclaimed pinch nodes:");
#endif
    int trace_cnt = 0;
    for (uint k = 0; k < _reg_node.Size(); k++) {
      Node* pinch = _reg_node[k];
      if ((pinch != NULL) && pinch->Opcode() == Op_Node &&
          // no predecence input edges
          (pinch->req() == pinch->len() || pinch->in(pinch->req()) == NULL) ) {
        cleanup_pinch(pinch);
        _pinch_free_list.push(pinch);
        _reg_node.map(k, NULL);
#ifndef PRODUCT
        if (_cfg->C->trace_opto_output()) {
          trace_cnt++;
          if (trace_cnt > 40) {
            tty->print("\n");
            trace_cnt = 0;
          }
          tty->print(" %d", pinch->_idx);
        }
#endif
      }
    }
#ifndef PRODUCT
    if (_cfg->C->trace_opto_output()) tty->print("\n");
#endif
}

// Clean up a pinch node for reuse.
void Scheduling::cleanup_pinch( Node *pinch ) {
  assert (pinch && pinch->Opcode() == Op_Node && pinch->req() == 1, "just checking");

  for (DUIterator_Last imin, i = pinch->last_outs(imin); i >= imin; ) {
    Node* use = pinch->last_out(i);
    uint uses_found = 0;
    for (uint j = use->req(); j < use->len(); j++) {
      if (use->in(j) == pinch) {
        use->rm_prec(j);
        uses_found++;
      }
    }
    assert(uses_found > 0, "must be a precedence edge");
    i -= uses_found;    // we deleted 1 or more copies of this edge
  }
  // May have a later_def entry
  pinch->set_req(0, NULL);
}

#ifndef PRODUCT

void Scheduling::dump_available() const {
  tty->print("#Availist  ");
  for (uint i = 0; i < _available.size(); i++)
    tty->print(" N%d/l%d", _available[i]->_idx,_current_latency[_available[i]->_idx]);
  tty->cr();
}

// Print Scheduling Statistics
void Scheduling::print_statistics() {
  // Print the size added by nops for bundling
  tty->print("Nops added %d bytes to total of %d bytes",
    _total_nop_size, _total_method_size);
  if (_total_method_size > 0)
    tty->print(", for %.2f%%",
      ((double)_total_nop_size) / ((double) _total_method_size) * 100.0);
  tty->print("\n");

  // Print the number of branch shadows filled
  if (Pipeline::_branch_has_delay_slot) {
    tty->print("Of %d branches, %d had unconditional delay slots filled",
      _total_branches, _total_unconditional_delays);
    if (_total_branches > 0)
      tty->print(", for %.2f%%",
        ((double)_total_unconditional_delays) / ((double)_total_branches) * 100.0);
    tty->print("\n");
  }

  uint total_instructions = 0, total_bundles = 0;

  for (uint i = 1; i <= Pipeline::_max_instrs_per_cycle; i++) {
    uint bundle_count   = _total_instructions_per_bundle[i];
    total_instructions += bundle_count * i;
    total_bundles      += bundle_count;
  }

  if (total_bundles > 0)
    tty->print("Average ILP (excluding nops) is %.2f\n",
      ((double)total_instructions) / ((double)total_bundles));
}
#endif
