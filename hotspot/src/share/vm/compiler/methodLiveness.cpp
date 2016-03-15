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
#include "ci/ciMethod.hpp"
#include "ci/ciMethodBlocks.hpp"
#include "ci/ciStreams.hpp"
#include "compiler/methodLiveness.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/timerTrace.hpp"
#include "utilities/bitMap.inline.hpp"

// The MethodLiveness class performs a simple liveness analysis on a method
// in order to decide which locals are live (that is, will be used again) at
// a particular bytecode index (bci).
//
// The algorithm goes:
//
// 1. Break the method into a set of basic blocks.  For each basic block we
//    also keep track of its set of predecessors through normal control flow
//    and predecessors through exceptional control flow.
//
// 2. For each basic block, compute two sets, gen (the set of values used before
//    they are defined) and kill (the set of values defined before they are used)
//    in the basic block.  A basic block "needs" the locals in its gen set to
//    perform its computation.  A basic block "provides" values for the locals in
//    its kill set, allowing a need from a successor to be ignored.
//
// 3. Liveness information (the set of locals which are needed) is pushed backwards through
//    the program, from blocks to their predecessors.  We compute and store liveness
//    information for the normal/exceptional exit paths for each basic block.  When
//    this process reaches a fixed point, we are done.
//
// 4. When we are asked about the liveness at a particular bci with a basic block, we
//    compute gen/kill sets which represent execution from that bci to the exit of
//    its blocks.  We then compose this range gen/kill information with the normal
//    and exceptional exit information for the block to produce liveness information
//    at that bci.
//
// The algorithm is approximate in many respects.  Notably:
//
// 1. We do not do the analysis necessary to match jsr's with the appropriate ret.
//    Instead we make the conservative assumption that any ret can return to any
//    jsr return site.
// 2. Instead of computing the effects of exceptions at every instruction, we
//    summarize the effects of all exceptional continuations from the block as
//    a single set (_exception_exit), losing some information but simplifying the
//    analysis.


//--------------------------------------------------------------------------
// The BitCounter class is used for counting the number of bits set in
// some BitMap.  It is only used when collecting liveness statistics.

#ifndef PRODUCT

class BitCounter: public BitMapClosure {
 private:
  int _count;
 public:
  BitCounter() : _count(0) {}

  // Callback when bit in map is set
  virtual bool do_bit(size_t offset) {
    _count++;
    return true;
  }

  int count() {
    return _count;
  }
};


//--------------------------------------------------------------------------


// Counts
long MethodLiveness::_total_bytes = 0;
int  MethodLiveness::_total_methods = 0;

long MethodLiveness::_total_blocks = 0;
int  MethodLiveness::_max_method_blocks = 0;

long MethodLiveness::_total_edges = 0;
int  MethodLiveness::_max_block_edges = 0;

long MethodLiveness::_total_exc_edges = 0;
int  MethodLiveness::_max_block_exc_edges = 0;

long MethodLiveness::_total_method_locals = 0;
int  MethodLiveness::_max_method_locals = 0;

long MethodLiveness::_total_locals_queried = 0;
long MethodLiveness::_total_live_locals_queried = 0;

long MethodLiveness::_total_visits = 0;

#endif

// Timers
elapsedTimer MethodLiveness::_time_build_graph;
elapsedTimer MethodLiveness::_time_gen_kill;
elapsedTimer MethodLiveness::_time_flow;
elapsedTimer MethodLiveness::_time_query;
elapsedTimer MethodLiveness::_time_total;

MethodLiveness::MethodLiveness(Arena* arena, ciMethod* method)
#ifdef COMPILER1
  : _bci_block_start((uintptr_t*)arena->Amalloc((method->code_size() >> LogBitsPerByte) + 1), method->code_size())
#endif
{
  _arena = arena;
  _method = method;
  _bit_map_size_bits = method->max_locals();
  _bit_map_size_words = (_bit_map_size_bits / sizeof(unsigned int)) + 1;

#ifdef COMPILER1
  _bci_block_start.clear();
#endif
}

void MethodLiveness::compute_liveness() {
#ifndef PRODUCT
  if (TraceLivenessGen) {
    tty->print_cr("################################################################");
    tty->print("# Computing liveness information for ");
    method()->print_short_name();
  }

  if (TimeLivenessAnalysis) _time_total.start();
#endif

  {
    TraceTime buildGraph(NULL, &_time_build_graph, TimeLivenessAnalysis);
    init_basic_blocks();
  }
  {
    TraceTime genKill(NULL, &_time_gen_kill, TimeLivenessAnalysis);
    init_gen_kill();
  }
  {
    TraceTime flow(NULL, &_time_flow, TimeLivenessAnalysis);
    propagate_liveness();
  }

#ifndef PRODUCT
  if (TimeLivenessAnalysis) _time_total.stop();

  if (TimeLivenessAnalysis) {
    // Collect statistics
    _total_bytes += method()->code_size();
    _total_methods++;

    int num_blocks = _block_count;
    _total_blocks += num_blocks;
    _max_method_blocks = MAX2(num_blocks,_max_method_blocks);

    for (int i=0; i<num_blocks; i++) {
      BasicBlock *block = _block_list[i];

      int numEdges = block->_normal_predecessors->length();
      int numExcEdges = block->_exception_predecessors->length();

      _total_edges += numEdges;
      _total_exc_edges += numExcEdges;
      _max_block_edges = MAX2(numEdges,_max_block_edges);
      _max_block_exc_edges = MAX2(numExcEdges,_max_block_exc_edges);
    }

    int numLocals = _bit_map_size_bits;
    _total_method_locals += numLocals;
    _max_method_locals = MAX2(numLocals,_max_method_locals);
  }
#endif
}


void MethodLiveness::init_basic_blocks() {
  bool bailout = false;

  int method_len = method()->code_size();
  ciMethodBlocks *mblocks = method()->get_method_blocks();

  // Create an array to store the bci->BasicBlock mapping.
  _block_map = new (arena()) GrowableArray<BasicBlock*>(arena(), method_len, method_len, NULL);

  _block_count = mblocks->num_blocks();
  _block_list = (BasicBlock **) arena()->Amalloc(sizeof(BasicBlock *) * _block_count);

  // Used for patching up jsr/ret control flow.
  GrowableArray<BasicBlock*>* jsr_exit_list = new GrowableArray<BasicBlock*>(5);
  GrowableArray<BasicBlock*>* ret_list = new GrowableArray<BasicBlock*>(5);

  // generate our block list from ciMethodBlocks
  for (int blk = 0; blk < _block_count; blk++) {
    ciBlock *cib = mblocks->block(blk);
     int start_bci = cib->start_bci();
    _block_list[blk] = new (arena()) BasicBlock(this, start_bci, cib->limit_bci());
    _block_map->at_put(start_bci, _block_list[blk]);
#ifdef COMPILER1
    // mark all bcis where a new basic block starts
    _bci_block_start.set_bit(start_bci);
#endif // COMPILER1
  }
  // fill in the predecessors of blocks
  ciBytecodeStream bytes(method());

  for (int blk = 0; blk < _block_count; blk++) {
    BasicBlock *current_block = _block_list[blk];
    int bci =  mblocks->block(blk)->control_bci();

    if (bci == ciBlock::fall_through_bci) {
      int limit = current_block->limit_bci();
      if (limit < method_len) {
        BasicBlock *next = _block_map->at(limit);
        assert( next != NULL, "must be a block immediately following this one.");
        next->add_normal_predecessor(current_block);
      }
      continue;
    }
    bytes.reset_to_bci(bci);
    Bytecodes::Code code = bytes.next();
    BasicBlock *dest;

    // Now we need to interpret the instruction's effect
    // on control flow.
    assert (current_block != NULL, "we must have a current block");
    switch (code) {
      case Bytecodes::_ifeq:
      case Bytecodes::_ifne:
      case Bytecodes::_iflt:
      case Bytecodes::_ifge:
      case Bytecodes::_ifgt:
      case Bytecodes::_ifle:
      case Bytecodes::_if_icmpeq:
      case Bytecodes::_if_icmpne:
      case Bytecodes::_if_icmplt:
      case Bytecodes::_if_icmpge:
      case Bytecodes::_if_icmpgt:
      case Bytecodes::_if_icmple:
      case Bytecodes::_if_acmpeq:
      case Bytecodes::_if_acmpne:
      case Bytecodes::_ifnull:
      case Bytecodes::_ifnonnull:
        // Two way branch.  Set predecessors at each destination.
        dest = _block_map->at(bytes.next_bci());
        assert(dest != NULL, "must be a block immediately following this one.");
        dest->add_normal_predecessor(current_block);

        dest = _block_map->at(bytes.get_dest());
        assert(dest != NULL, "branch desination must start a block.");
        dest->add_normal_predecessor(current_block);
        break;
      case Bytecodes::_goto:
        dest = _block_map->at(bytes.get_dest());
        assert(dest != NULL, "branch desination must start a block.");
        dest->add_normal_predecessor(current_block);
        break;
      case Bytecodes::_goto_w:
        dest = _block_map->at(bytes.get_far_dest());
        assert(dest != NULL, "branch desination must start a block.");
        dest->add_normal_predecessor(current_block);
        break;
      case Bytecodes::_tableswitch:
        {
          Bytecode_tableswitch tableswitch(&bytes);

          int len = tableswitch.length();

          dest = _block_map->at(bci + tableswitch.default_offset());
          assert(dest != NULL, "branch desination must start a block.");
          dest->add_normal_predecessor(current_block);
          while (--len >= 0) {
            dest = _block_map->at(bci + tableswitch.dest_offset_at(len));
            assert(dest != NULL, "branch desination must start a block.");
            dest->add_normal_predecessor(current_block);
          }
          break;
        }

      case Bytecodes::_lookupswitch:
        {
          Bytecode_lookupswitch lookupswitch(&bytes);

          int npairs = lookupswitch.number_of_pairs();

          dest = _block_map->at(bci + lookupswitch.default_offset());
          assert(dest != NULL, "branch desination must start a block.");
          dest->add_normal_predecessor(current_block);
          while(--npairs >= 0) {
            LookupswitchPair pair = lookupswitch.pair_at(npairs);
            dest = _block_map->at( bci + pair.offset());
            assert(dest != NULL, "branch desination must start a block.");
            dest->add_normal_predecessor(current_block);
          }
          break;
        }

      case Bytecodes::_jsr:
        {
          assert(bytes.is_wide()==false, "sanity check");
          dest = _block_map->at(bytes.get_dest());
          assert(dest != NULL, "branch desination must start a block.");
          dest->add_normal_predecessor(current_block);
          BasicBlock *jsrExit = _block_map->at(current_block->limit_bci());
          assert(jsrExit != NULL, "jsr return bci must start a block.");
          jsr_exit_list->append(jsrExit);
          break;
        }
      case Bytecodes::_jsr_w:
        {
          dest = _block_map->at(bytes.get_far_dest());
          assert(dest != NULL, "branch desination must start a block.");
          dest->add_normal_predecessor(current_block);
          BasicBlock *jsrExit = _block_map->at(current_block->limit_bci());
          assert(jsrExit != NULL, "jsr return bci must start a block.");
          jsr_exit_list->append(jsrExit);
          break;
        }

      case Bytecodes::_wide:
        assert(false, "wide opcodes should not be seen here");
        break;
      case Bytecodes::_athrow:
      case Bytecodes::_ireturn:
      case Bytecodes::_lreturn:
      case Bytecodes::_freturn:
      case Bytecodes::_dreturn:
      case Bytecodes::_areturn:
      case Bytecodes::_return:
        // These opcodes are  not the normal predecessors of any other opcodes.
        break;
      case Bytecodes::_ret:
        // We will patch up jsr/rets in a subsequent pass.
        ret_list->append(current_block);
        break;
      case Bytecodes::_breakpoint:
        // Bail out of there are breakpoints in here.
        bailout = true;
        break;
      default:
        // Do nothing.
        break;
    }
  }
  // Patch up the jsr/ret's.  We conservatively assume that any ret
  // can return to any jsr site.
  int ret_list_len = ret_list->length();
  int jsr_exit_list_len = jsr_exit_list->length();
  if (ret_list_len > 0 && jsr_exit_list_len > 0) {
    for (int i = jsr_exit_list_len - 1; i >= 0; i--) {
      BasicBlock *jsrExit = jsr_exit_list->at(i);
      for (int i = ret_list_len - 1; i >= 0; i--) {
        jsrExit->add_normal_predecessor(ret_list->at(i));
      }
    }
  }

  // Compute exception edges.
  for (int b=_block_count-1; b >= 0; b--) {
    BasicBlock *block = _block_list[b];
    int block_start = block->start_bci();
    int block_limit = block->limit_bci();
    ciExceptionHandlerStream handlers(method());
    for (; !handlers.is_done(); handlers.next()) {
      ciExceptionHandler* handler = handlers.handler();
      int start       = handler->start();
      int limit       = handler->limit();
      int handler_bci = handler->handler_bci();

      int intersect_start = MAX2(block_start, start);
      int intersect_limit = MIN2(block_limit, limit);
      if (intersect_start < intersect_limit) {
        // The catch range has a nonempty intersection with this
        // basic block.  That means this basic block can be an
        // exceptional predecessor.
        _block_map->at(handler_bci)->add_exception_predecessor(block);

        if (handler->is_catch_all()) {
          // This is a catch-all block.
          if (intersect_start == block_start && intersect_limit == block_limit) {
            // The basic block is entirely contained in this catch-all block.
            // Skip the rest of the exception handlers -- they can never be
            // reached in execution.
            break;
          }
        }
      }
    }
  }
}

void MethodLiveness::init_gen_kill() {
  for (int i=_block_count-1; i >= 0; i--) {
    _block_list[i]->compute_gen_kill(method());
  }
}

void MethodLiveness::propagate_liveness() {
  int num_blocks = _block_count;
  BasicBlock *block;

  // We start our work list off with all blocks in it.
  // Alternately, we could start off the work list with the list of all
  // blocks which could exit the method directly, along with one block
  // from any infinite loop.  If this matters, it can be changed.  It
  // may not be clear from looking at the code, but the order of the
  // workList will be the opposite of the creation order of the basic
  // blocks, which should be decent for quick convergence (with the
  // possible exception of exception handlers, which are all created
  // early).
  _work_list = NULL;
  for (int i = 0; i < num_blocks; i++) {
    block = _block_list[i];
    block->set_next(_work_list);
    block->set_on_work_list(true);
    _work_list = block;
  }


  while ((block = work_list_get()) != NULL) {
    block->propagate(this);
    NOT_PRODUCT(_total_visits++;)
  }
}

void MethodLiveness::work_list_add(BasicBlock *block) {
  if (!block->on_work_list()) {
    block->set_next(_work_list);
    block->set_on_work_list(true);
    _work_list = block;
  }
}

MethodLiveness::BasicBlock *MethodLiveness::work_list_get() {
  BasicBlock *block = _work_list;
  if (block != NULL) {
    block->set_on_work_list(false);
    _work_list = block->next();
  }
  return block;
}


MethodLivenessResult MethodLiveness::get_liveness_at(int entry_bci) {
  int bci = entry_bci;
  bool is_entry = false;
  if (entry_bci == InvocationEntryBci) {
    is_entry = true;
    bci = 0;
  }

  MethodLivenessResult answer((BitMap::bm_word_t*)NULL,0);

  if (_block_count > 0) {
    if (TimeLivenessAnalysis) _time_total.start();
    if (TimeLivenessAnalysis) _time_query.start();

    assert( 0 <= bci && bci < method()->code_size(), "bci out of range" );
    BasicBlock *block = _block_map->at(bci);
    // We may not be at the block start, so search backwards to find the block
    // containing bci.
    int t = bci;
    while (block == NULL && t > 0) {
     block = _block_map->at(--t);
    }
    assert( block != NULL, "invalid bytecode index; must be instruction index" );
    assert(bci >= block->start_bci() && bci < block->limit_bci(), "block must contain bci.");

    answer = block->get_liveness_at(method(), bci);

    if (is_entry && method()->is_synchronized() && !method()->is_static()) {
      // Synchronized methods use the receiver once on entry.
      answer.at_put(0, true);
    }

#ifndef PRODUCT
    if (TraceLivenessQuery) {
      tty->print("Liveness query of ");
      method()->print_short_name();
      tty->print(" @ %d : result is ", bci);
      answer.print_on(tty);
    }

    if (TimeLivenessAnalysis) _time_query.stop();
    if (TimeLivenessAnalysis) _time_total.stop();
#endif
  }

#ifndef PRODUCT
  if (TimeLivenessAnalysis) {
    // Collect statistics.
    _total_locals_queried += _bit_map_size_bits;
    BitCounter counter;
    answer.iterate(&counter);
    _total_live_locals_queried += counter.count();
  }
#endif

  return answer;
}


#ifndef PRODUCT

void MethodLiveness::print_times() {
  tty->print_cr ("Accumulated liveness analysis times/statistics:");
  tty->print_cr ("-----------------------------------------------");
  tty->print_cr ("  Total         : %3.3f sec.", _time_total.seconds());
  tty->print_cr ("    Build graph : %3.3f sec. (%2.2f%%)", _time_build_graph.seconds(),
                 _time_build_graph.seconds() * 100 / _time_total.seconds());
  tty->print_cr ("    Gen / Kill  : %3.3f sec. (%2.2f%%)", _time_gen_kill.seconds(),
                 _time_gen_kill.seconds() * 100 / _time_total.seconds());
  tty->print_cr ("    Dataflow    : %3.3f sec. (%2.2f%%)", _time_flow.seconds(),
                 _time_flow.seconds() * 100 / _time_total.seconds());
  tty->print_cr ("    Query       : %3.3f sec. (%2.2f%%)", _time_query.seconds(),
                 _time_query.seconds() * 100 / _time_total.seconds());
  tty->print_cr ("  #bytes   : %8ld (%3.0f bytes per sec)",
                 _total_bytes,
                 _total_bytes / _time_total.seconds());
  tty->print_cr ("  #methods : %8d (%3.0f methods per sec)",
                 _total_methods,
                 _total_methods / _time_total.seconds());
  tty->print_cr ("    avg locals : %3.3f    max locals : %3d",
                 (float)_total_method_locals / _total_methods,
                 _max_method_locals);
  tty->print_cr ("    avg blocks : %3.3f    max blocks : %3d",
                 (float)_total_blocks / _total_methods,
                 _max_method_blocks);
  tty->print_cr ("    avg bytes  : %3.3f",
                 (float)_total_bytes / _total_methods);
  tty->print_cr ("  #blocks  : %8ld",
                 _total_blocks);
  tty->print_cr ("    avg normal predecessors    : %3.3f  max normal predecessors    : %3d",
                 (float)_total_edges / _total_blocks,
                 _max_block_edges);
  tty->print_cr ("    avg exception predecessors : %3.3f  max exception predecessors : %3d",
                 (float)_total_exc_edges / _total_blocks,
                 _max_block_exc_edges);
  tty->print_cr ("    avg visits                 : %3.3f",
                 (float)_total_visits / _total_blocks);
  tty->print_cr ("  #locals queried : %8ld    #live : %8ld   %%live : %2.2f%%",
                 _total_locals_queried,
                 _total_live_locals_queried,
                 100.0 * _total_live_locals_queried / _total_locals_queried);
}

#endif


MethodLiveness::BasicBlock::BasicBlock(MethodLiveness *analyzer, int start, int limit) :
         _gen((uintptr_t*)analyzer->arena()->Amalloc(BytesPerWord * analyzer->bit_map_size_words()),
                         analyzer->bit_map_size_bits()),
         _kill((uintptr_t*)analyzer->arena()->Amalloc(BytesPerWord * analyzer->bit_map_size_words()),
                         analyzer->bit_map_size_bits()),
         _entry((uintptr_t*)analyzer->arena()->Amalloc(BytesPerWord * analyzer->bit_map_size_words()),
                         analyzer->bit_map_size_bits()),
         _normal_exit((uintptr_t*)analyzer->arena()->Amalloc(BytesPerWord * analyzer->bit_map_size_words()),
                         analyzer->bit_map_size_bits()),
         _exception_exit((uintptr_t*)analyzer->arena()->Amalloc(BytesPerWord * analyzer->bit_map_size_words()),
                         analyzer->bit_map_size_bits()),
         _last_bci(-1) {
  _analyzer = analyzer;
  _start_bci = start;
  _limit_bci = limit;
  _normal_predecessors =
    new (analyzer->arena()) GrowableArray<MethodLiveness::BasicBlock*>(analyzer->arena(), 5, 0, NULL);
  _exception_predecessors =
    new (analyzer->arena()) GrowableArray<MethodLiveness::BasicBlock*>(analyzer->arena(), 5, 0, NULL);
  _normal_exit.clear();
  _exception_exit.clear();
  _entry.clear();

  // this initialization is not strictly necessary.
  // _gen and _kill are cleared at the beginning of compute_gen_kill_range()
  _gen.clear();
  _kill.clear();
}



MethodLiveness::BasicBlock *MethodLiveness::BasicBlock::split(int split_bci) {
  int start = _start_bci;
  int limit = _limit_bci;

  if (TraceLivenessGen) {
    tty->print_cr(" ** Splitting block (%d,%d) at %d", start, limit, split_bci);
  }

  GrowableArray<BasicBlock*>* save_predecessors = _normal_predecessors;

  assert (start < split_bci && split_bci < limit, "improper split");

  // Make a new block to cover the first half of the range.
  BasicBlock *first_half = new (_analyzer->arena()) BasicBlock(_analyzer, start, split_bci);

  // Assign correct values to the second half (this)
  _normal_predecessors = first_half->_normal_predecessors;
  _start_bci = split_bci;
  add_normal_predecessor(first_half);

  // Assign correct predecessors to the new first half
  first_half->_normal_predecessors = save_predecessors;

  return first_half;
}

void MethodLiveness::BasicBlock::compute_gen_kill(ciMethod* method) {
  ciBytecodeStream bytes(method);
  bytes.reset_to_bci(start_bci());
  bytes.set_max_bci(limit_bci());
  compute_gen_kill_range(&bytes);

}

void MethodLiveness::BasicBlock::compute_gen_kill_range(ciBytecodeStream *bytes) {
  _gen.clear();
  _kill.clear();

  while (bytes->next() != ciBytecodeStream::EOBC()) {
    compute_gen_kill_single(bytes);
  }
}

void MethodLiveness::BasicBlock::compute_gen_kill_single(ciBytecodeStream *instruction) {
  int localNum;

  // We prohibit _gen and _kill from having locals in common.  If we
  // know that one is definitely going to be applied before the other,
  // we could save some computation time by relaxing this prohibition.

  switch (instruction->cur_bc()) {
    case Bytecodes::_nop:
    case Bytecodes::_goto:
    case Bytecodes::_goto_w:
    case Bytecodes::_aconst_null:
    case Bytecodes::_new:
    case Bytecodes::_iconst_m1:
    case Bytecodes::_iconst_0:
    case Bytecodes::_iconst_1:
    case Bytecodes::_iconst_2:
    case Bytecodes::_iconst_3:
    case Bytecodes::_iconst_4:
    case Bytecodes::_iconst_5:
    case Bytecodes::_fconst_0:
    case Bytecodes::_fconst_1:
    case Bytecodes::_fconst_2:
    case Bytecodes::_bipush:
    case Bytecodes::_sipush:
    case Bytecodes::_lconst_0:
    case Bytecodes::_lconst_1:
    case Bytecodes::_dconst_0:
    case Bytecodes::_dconst_1:
    case Bytecodes::_ldc2_w:
    case Bytecodes::_ldc:
    case Bytecodes::_ldc_w:
    case Bytecodes::_iaload:
    case Bytecodes::_faload:
    case Bytecodes::_baload:
    case Bytecodes::_caload:
    case Bytecodes::_saload:
    case Bytecodes::_laload:
    case Bytecodes::_daload:
    case Bytecodes::_aaload:
    case Bytecodes::_iastore:
    case Bytecodes::_fastore:
    case Bytecodes::_bastore:
    case Bytecodes::_castore:
    case Bytecodes::_sastore:
    case Bytecodes::_lastore:
    case Bytecodes::_dastore:
    case Bytecodes::_aastore:
    case Bytecodes::_pop:
    case Bytecodes::_pop2:
    case Bytecodes::_dup:
    case Bytecodes::_dup_x1:
    case Bytecodes::_dup_x2:
    case Bytecodes::_dup2:
    case Bytecodes::_dup2_x1:
    case Bytecodes::_dup2_x2:
    case Bytecodes::_swap:
    case Bytecodes::_iadd:
    case Bytecodes::_fadd:
    case Bytecodes::_isub:
    case Bytecodes::_fsub:
    case Bytecodes::_imul:
    case Bytecodes::_fmul:
    case Bytecodes::_idiv:
    case Bytecodes::_fdiv:
    case Bytecodes::_irem:
    case Bytecodes::_frem:
    case Bytecodes::_ishl:
    case Bytecodes::_ishr:
    case Bytecodes::_iushr:
    case Bytecodes::_iand:
    case Bytecodes::_ior:
    case Bytecodes::_ixor:
    case Bytecodes::_l2f:
    case Bytecodes::_l2i:
    case Bytecodes::_d2f:
    case Bytecodes::_d2i:
    case Bytecodes::_fcmpl:
    case Bytecodes::_fcmpg:
    case Bytecodes::_ladd:
    case Bytecodes::_dadd:
    case Bytecodes::_lsub:
    case Bytecodes::_dsub:
    case Bytecodes::_lmul:
    case Bytecodes::_dmul:
    case Bytecodes::_ldiv:
    case Bytecodes::_ddiv:
    case Bytecodes::_lrem:
    case Bytecodes::_drem:
    case Bytecodes::_land:
    case Bytecodes::_lor:
    case Bytecodes::_lxor:
    case Bytecodes::_ineg:
    case Bytecodes::_fneg:
    case Bytecodes::_i2f:
    case Bytecodes::_f2i:
    case Bytecodes::_i2c:
    case Bytecodes::_i2s:
    case Bytecodes::_i2b:
    case Bytecodes::_lneg:
    case Bytecodes::_dneg:
    case Bytecodes::_l2d:
    case Bytecodes::_d2l:
    case Bytecodes::_lshl:
    case Bytecodes::_lshr:
    case Bytecodes::_lushr:
    case Bytecodes::_i2l:
    case Bytecodes::_i2d:
    case Bytecodes::_f2l:
    case Bytecodes::_f2d:
    case Bytecodes::_lcmp:
    case Bytecodes::_dcmpl:
    case Bytecodes::_dcmpg:
    case Bytecodes::_ifeq:
    case Bytecodes::_ifne:
    case Bytecodes::_iflt:
    case Bytecodes::_ifge:
    case Bytecodes::_ifgt:
    case Bytecodes::_ifle:
    case Bytecodes::_tableswitch:
    case Bytecodes::_ireturn:
    case Bytecodes::_freturn:
    case Bytecodes::_if_icmpeq:
    case Bytecodes::_if_icmpne:
    case Bytecodes::_if_icmplt:
    case Bytecodes::_if_icmpge:
    case Bytecodes::_if_icmpgt:
    case Bytecodes::_if_icmple:
    case Bytecodes::_lreturn:
    case Bytecodes::_dreturn:
    case Bytecodes::_if_acmpeq:
    case Bytecodes::_if_acmpne:
    case Bytecodes::_jsr:
    case Bytecodes::_jsr_w:
    case Bytecodes::_getstatic:
    case Bytecodes::_putstatic:
    case Bytecodes::_getfield:
    case Bytecodes::_putfield:
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokedynamic:
    case Bytecodes::_newarray:
    case Bytecodes::_anewarray:
    case Bytecodes::_checkcast:
    case Bytecodes::_arraylength:
    case Bytecodes::_instanceof:
    case Bytecodes::_athrow:
    case Bytecodes::_areturn:
    case Bytecodes::_monitorenter:
    case Bytecodes::_monitorexit:
    case Bytecodes::_ifnull:
    case Bytecodes::_ifnonnull:
    case Bytecodes::_multianewarray:
    case Bytecodes::_lookupswitch:
      // These bytecodes have no effect on the method's locals.
      break;

    case Bytecodes::_return:
      if (instruction->method()->intrinsic_id() == vmIntrinsics::_Object_init) {
        // return from Object.init implicitly registers a finalizer
        // for the receiver if needed, so keep it alive.
        load_one(0);
      }
      break;


    case Bytecodes::_lload:
    case Bytecodes::_dload:
      load_two(instruction->get_index());
      break;

    case Bytecodes::_lload_0:
    case Bytecodes::_dload_0:
      load_two(0);
      break;

    case Bytecodes::_lload_1:
    case Bytecodes::_dload_1:
      load_two(1);
      break;

    case Bytecodes::_lload_2:
    case Bytecodes::_dload_2:
      load_two(2);
      break;

    case Bytecodes::_lload_3:
    case Bytecodes::_dload_3:
      load_two(3);
      break;

    case Bytecodes::_iload:
    case Bytecodes::_iinc:
    case Bytecodes::_fload:
    case Bytecodes::_aload:
    case Bytecodes::_ret:
      load_one(instruction->get_index());
      break;

    case Bytecodes::_iload_0:
    case Bytecodes::_fload_0:
    case Bytecodes::_aload_0:
      load_one(0);
      break;

    case Bytecodes::_iload_1:
    case Bytecodes::_fload_1:
    case Bytecodes::_aload_1:
      load_one(1);
      break;

    case Bytecodes::_iload_2:
    case Bytecodes::_fload_2:
    case Bytecodes::_aload_2:
      load_one(2);
      break;

    case Bytecodes::_iload_3:
    case Bytecodes::_fload_3:
    case Bytecodes::_aload_3:
      load_one(3);
      break;

    case Bytecodes::_lstore:
    case Bytecodes::_dstore:
      store_two(localNum = instruction->get_index());
      break;

    case Bytecodes::_lstore_0:
    case Bytecodes::_dstore_0:
      store_two(0);
      break;

    case Bytecodes::_lstore_1:
    case Bytecodes::_dstore_1:
      store_two(1);
      break;

    case Bytecodes::_lstore_2:
    case Bytecodes::_dstore_2:
      store_two(2);
      break;

    case Bytecodes::_lstore_3:
    case Bytecodes::_dstore_3:
      store_two(3);
      break;

    case Bytecodes::_istore:
    case Bytecodes::_fstore:
    case Bytecodes::_astore:
      store_one(instruction->get_index());
      break;

    case Bytecodes::_istore_0:
    case Bytecodes::_fstore_0:
    case Bytecodes::_astore_0:
      store_one(0);
      break;

    case Bytecodes::_istore_1:
    case Bytecodes::_fstore_1:
    case Bytecodes::_astore_1:
      store_one(1);
      break;

    case Bytecodes::_istore_2:
    case Bytecodes::_fstore_2:
    case Bytecodes::_astore_2:
      store_one(2);
      break;

    case Bytecodes::_istore_3:
    case Bytecodes::_fstore_3:
    case Bytecodes::_astore_3:
      store_one(3);
      break;

    case Bytecodes::_wide:
      fatal("Iterator should skip this bytecode");
      break;

    default:
      tty->print("unexpected opcode: %d\n", instruction->cur_bc());
      ShouldNotReachHere();
      break;
  }
}

void MethodLiveness::BasicBlock::load_two(int local) {
  load_one(local);
  load_one(local+1);
}

void MethodLiveness::BasicBlock::load_one(int local) {
  if (!_kill.at(local)) {
    _gen.at_put(local, true);
  }
}

void MethodLiveness::BasicBlock::store_two(int local) {
  store_one(local);
  store_one(local+1);
}

void MethodLiveness::BasicBlock::store_one(int local) {
  if (!_gen.at(local)) {
    _kill.at_put(local, true);
  }
}

void MethodLiveness::BasicBlock::propagate(MethodLiveness *ml) {
  // These set operations could be combined for efficiency if the
  // performance of this analysis becomes an issue.
  _entry.set_union(_normal_exit);
  _entry.set_difference(_kill);
  _entry.set_union(_gen);

  // Note that we merge information from our exceptional successors
  // just once, rather than at individual bytecodes.
  _entry.set_union(_exception_exit);

  if (TraceLivenessGen) {
    tty->print_cr(" ** Visiting block at %d **", start_bci());
    print_on(tty);
  }

  int i;
  for (i=_normal_predecessors->length()-1; i>=0; i--) {
    BasicBlock *block = _normal_predecessors->at(i);
    if (block->merge_normal(_entry)) {
      ml->work_list_add(block);
    }
  }
  for (i=_exception_predecessors->length()-1; i>=0; i--) {
    BasicBlock *block = _exception_predecessors->at(i);
    if (block->merge_exception(_entry)) {
      ml->work_list_add(block);
    }
  }
}

bool MethodLiveness::BasicBlock::merge_normal(BitMap other) {
  return _normal_exit.set_union_with_result(other);
}

bool MethodLiveness::BasicBlock::merge_exception(BitMap other) {
  return _exception_exit.set_union_with_result(other);
}

MethodLivenessResult MethodLiveness::BasicBlock::get_liveness_at(ciMethod* method, int bci) {
  MethodLivenessResult answer(NEW_RESOURCE_ARRAY(BitMap::bm_word_t, _analyzer->bit_map_size_words()),
                _analyzer->bit_map_size_bits());
  answer.set_is_valid();

#ifndef ASSERT
  if (bci == start_bci()) {
    answer.set_from(_entry);
    return answer;
  }
#endif

#ifdef ASSERT
  ResourceMark rm;
  BitMap g(_gen.size()); g.set_from(_gen);
  BitMap k(_kill.size()); k.set_from(_kill);
#endif
  if (_last_bci != bci || trueInDebug) {
    ciBytecodeStream bytes(method);
    bytes.reset_to_bci(bci);
    bytes.set_max_bci(limit_bci());
    compute_gen_kill_range(&bytes);
    assert(_last_bci != bci ||
           (g.is_same(_gen) && k.is_same(_kill)), "cached computation is incorrect");
    _last_bci = bci;
  }

  answer.clear();
  answer.set_union(_normal_exit);
  answer.set_difference(_kill);
  answer.set_union(_gen);
  answer.set_union(_exception_exit);

#ifdef ASSERT
  if (bci == start_bci()) {
    assert(answer.is_same(_entry), "optimized answer must be accurate");
  }
#endif

  return answer;
}

#ifndef PRODUCT

void MethodLiveness::BasicBlock::print_on(outputStream *os) const {
  os->print_cr("===================================================================");
  os->print_cr("    Block start: %4d, limit: %4d", _start_bci, _limit_bci);
  os->print   ("    Normal predecessors (%2d)      @", _normal_predecessors->length());
  int i;
  for (i=0; i < _normal_predecessors->length(); i++) {
    os->print(" %4d", _normal_predecessors->at(i)->start_bci());
  }
  os->cr();
  os->print   ("    Exceptional predecessors (%2d) @", _exception_predecessors->length());
  for (i=0; i < _exception_predecessors->length(); i++) {
    os->print(" %4d", _exception_predecessors->at(i)->start_bci());
  }
  os->cr();
  os->print ("    Normal Exit   : ");
  _normal_exit.print_on(os);
  os->print ("    Gen           : ");
  _gen.print_on(os);
  os->print ("    Kill          : ");
  _kill.print_on(os);
  os->print ("    Exception Exit: ");
  _exception_exit.print_on(os);
  os->print ("    Entry         : ");
  _entry.print_on(os);
}

#endif // PRODUCT
