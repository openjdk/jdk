/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciReplay.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compilationPolicy.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerEvent.hpp"
#include "compiler/compileLog.hpp"
#include "interpreter/linkResolver.hpp"
#include "jfr/jfrEvents.hpp"
#include "oops/objArrayKlass.hpp"
#include "opto/callGenerator.hpp"
#include "opto/parse.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/events.hpp"

//=============================================================================
//------------------------------InlineTree-------------------------------------
InlineTree::InlineTree(Compile* c,
                       const InlineTree *caller_tree, ciMethod* callee,
                       JVMState* caller_jvms, int caller_bci,
                       int max_inline_level) :
  C(c),
  _caller_jvms(nullptr),
  _method(callee),
  _late_inline(false),
  _caller_tree((InlineTree*) caller_tree),
  _count_inline_bcs(method()->code_size_for_inlining()),
  _max_inline_level(max_inline_level),
  _subtrees(c->comp_arena(), 2, 0, nullptr),
  _msg(nullptr)
{
#ifndef PRODUCT
  _count_inlines = 0;
  _forced_inline = false;
#endif
  if (caller_jvms != nullptr) {
    // Keep a private copy of the caller_jvms:
    _caller_jvms = new (C) JVMState(caller_jvms->method(), caller_tree->caller_jvms());
    _caller_jvms->set_bci(caller_jvms->bci());
    assert(!caller_jvms->should_reexecute(), "there should be no reexecute bytecode with inlining");
    assert(_caller_jvms->same_calls_as(caller_jvms), "consistent JVMS");
  }
  assert((caller_tree == nullptr ? 0 : caller_tree->stack_depth() + 1) == stack_depth(), "correct (redundant) depth parameter");
  assert(caller_bci == this->caller_bci(), "correct (redundant) bci parameter");
  // Update hierarchical counts, count_inline_bcs() and count_inlines()
  InlineTree *caller = (InlineTree *)caller_tree;
  for( ; caller != nullptr; caller = ((InlineTree *)(caller->caller_tree())) ) {
    caller->_count_inline_bcs += count_inline_bcs();
    NOT_PRODUCT(caller->_count_inlines++;)
  }
}

/**
 *  Return true when EA is ON and a java constructor is called or
 *  a super constructor is called from an inlined java constructor.
 *  Also return true for boxing methods.
 *  Also return true for methods returning Iterator (including Iterable::iterator())
 *  that is essential for forall-loops performance.
 */
static bool is_init_with_ea(ciMethod* callee_method,
                            ciMethod* caller_method, Compile* C) {
  if (!C->do_escape_analysis() || !EliminateAllocations) {
    return false; // EA is off
  }
  if (callee_method->is_initializer()) {
    return true; // constructor
  }
  if (caller_method->is_initializer() &&
      caller_method != C->method() &&
      caller_method->holder()->is_subclass_of(callee_method->holder())) {
    return true; // super constructor is called from inlined constructor
  }
  if (C->eliminate_boxing() && callee_method->is_boxing_method()) {
    return true;
  }
  ciType *retType = callee_method->signature()->return_type();
  ciKlass *iter = C->env()->Iterator_klass();
  if(retType->is_loaded() && iter->is_loaded() && retType->is_subtype_of(iter)) {
    return true;
  }
  return false;
}

/**
 *  Force inlining unboxing accessor.
 */
static bool is_unboxing_method(ciMethod* callee_method, Compile* C) {
  return C->eliminate_boxing() && callee_method->is_unboxing_method();
}

// positive filter: should callee be inlined?
bool InlineTree::should_inline(ciMethod* callee_method, ciMethod* caller_method,
                               int caller_bci, bool& should_delay, ciCallProfile& profile) {
  // Allows targeted inlining
  if (C->directive()->should_inline(callee_method)) {
    set_msg("force inline by CompileCommand");
    _forced_inline = true;
    return true;
  }

  if (callee_method->force_inline()) {
    set_msg("force inline by annotation");
    _forced_inline = true;
    return true;
  }

  int inline_depth = inline_level() + 1;
  if (ciReplay::should_inline(C->replay_inline_data(), callee_method, caller_bci, inline_depth, should_delay)) {
    if (should_delay) {
      set_msg("force (incremental) inline by ciReplay");
    } else {
      set_msg("force inline by ciReplay");
    }
    _forced_inline = true;
    return true;
  }

  int size = callee_method->code_size_for_inlining();

  // Check for too many throws (and not too huge)
  if(callee_method->interpreter_throwout_count() > InlineThrowCount &&
     size < InlineThrowMaxSize ) {
    if (C->print_inlining() && Verbose) {
      CompileTask::print_inline_indent(inline_level());
      tty->print_cr("Inlined method with many throws (throws=%d):", callee_method->interpreter_throwout_count());
    }
    set_msg("many throws");
    return true;
  }

  int default_max_inline_size = C->max_inline_size();
  int inline_small_code_size  = InlineSmallCode / 4;
  int max_inline_size         = default_max_inline_size;

  int call_site_count  = caller_method->scale_count(profile.count());
  int invoke_count     = caller_method->interpreter_invocation_count();

  assert(invoke_count != 0, "require invocation count greater than zero");
  double freq = (double)call_site_count / (double)invoke_count;

  // bump the max size if the call is frequent
  if ((freq >= InlineFrequencyRatio) ||
      is_unboxing_method(callee_method, C) ||
      is_init_with_ea(callee_method, caller_method, C)) {

    max_inline_size = C->freq_inline_size();
    if (size <= max_inline_size && TraceFrequencyInlining) {
      CompileTask::print_inline_indent(inline_level());
      tty->print_cr("Inlined frequent method (freq=%lf):", freq);
      CompileTask::print_inline_indent(inline_level());
      callee_method->print();
      tty->cr();
    }
  } else {
    // Not hot.  Check for medium-sized pre-existing nmethod at cold sites.
    if (callee_method->has_compiled_code() &&
        callee_method->inline_instructions_size() > inline_small_code_size) {
      set_msg("already compiled into a medium method");
      return false;
    }
  }
  if (size > max_inline_size) {
    if (max_inline_size > default_max_inline_size) {
      set_msg("hot method too big");
    } else {
      set_msg("too big");
    }
    return false;
  }
  return true;
}


// negative filter: should callee NOT be inlined?
bool InlineTree::should_not_inline(ciMethod* callee_method, ciMethod* caller_method,
                                   int caller_bci, bool& should_delay, ciCallProfile& profile) {
  const char* fail_msg = nullptr;

  // First check all inlining restrictions which are required for correctness
  if (callee_method->is_abstract()) {
    fail_msg = "abstract method"; // // note: we allow ik->is_abstract()
  } else if (!callee_method->holder()->is_initialized() &&
             // access allowed in the context of static initializer
             C->needs_clinit_barrier(callee_method->holder(), caller_method)) {
    fail_msg = "method holder not initialized";
  } else if (callee_method->is_native()) {
    fail_msg = "native method";
  } else if (callee_method->dont_inline()) {
    fail_msg = "don't inline by annotation";
  }

  // Don't inline a method that changes Thread.currentThread() except
  // into another method that is annotated @ChangesCurrentThread.
  if (callee_method->changes_current_thread()
      && ! C->method()->changes_current_thread()) {
    fail_msg = "method changes current thread";
  }

  // one more inlining restriction
  if (fail_msg == nullptr && callee_method->has_unloaded_classes_in_signature()) {
    fail_msg = "unloaded signature classes";
  }

  if (fail_msg != nullptr) {
    set_msg(fail_msg);
    return true;
  }

  // ignore heuristic controls on inlining
  if (C->directive()->should_inline(callee_method)) {
    set_msg("force inline by CompileCommand");
    return false;
  }

  if (C->directive()->should_not_inline(callee_method)) {
    set_msg("disallowed by CompileCommand");
    return true;
  }

  int inline_depth = inline_level() + 1;
  if (ciReplay::should_inline(C->replay_inline_data(), callee_method, caller_bci, inline_depth, should_delay)) {
    if (should_delay) {
      set_msg("force (incremental) inline by ciReplay");
    } else {
      set_msg("force inline by ciReplay");
    }
    return false;
  }

  if (ciReplay::should_not_inline(C->replay_inline_data(), callee_method, caller_bci, inline_depth)) {
    set_msg("disallowed by ciReplay");
    return true;
  }

  if (ciReplay::should_not_inline(callee_method)) {
    set_msg("disallowed by ciReplay");
    return true;
  }

  if (callee_method->force_inline()) {
    set_msg("force inline by annotation");
    return false;
  }

  // Now perform checks which are heuristic

  if (is_unboxing_method(callee_method, C)) {
    // Inline unboxing methods.
    return false;
  }

  if (callee_method->has_compiled_code() &&
      callee_method->inline_instructions_size() > InlineSmallCode) {
    set_msg("already compiled into a big method");
    return true;
  }

  // don't inline exception code unless the top method belongs to an
  // exception class
  if (caller_tree() != nullptr &&
      callee_method->holder()->is_subclass_of(C->env()->Throwable_klass())) {
    const InlineTree *top = this;
    while (top->caller_tree() != nullptr) top = top->caller_tree();
    ciInstanceKlass* k = top->method()->holder();
    if (!k->is_subclass_of(C->env()->Throwable_klass())) {
      set_msg("exception method");
      return true;
    }
  }

  // use frequency-based objections only for non-trivial methods
  if (callee_method->code_size() <= MaxTrivialSize) {
    return false;
  }

  // don't use counts with -Xcomp
  if (UseInterpreter) {
    if (!callee_method->has_compiled_code() &&
        !callee_method->was_executed_more_than(0)) {
      set_msg("never executed");
      return true;
    }

    if (is_init_with_ea(callee_method, caller_method, C)) {
      // Escape Analysis: inline all executed constructors
      return false;
    }

    if (MinInlineFrequencyRatio > 0) {
      int call_site_count  = caller_method->scale_count(profile.count());
      int invoke_count     = caller_method->interpreter_invocation_count();
      assert(invoke_count != 0, "require invocation count greater than zero");
      double freq = (double)call_site_count / (double)invoke_count;
      double min_freq = MAX2(MinInlineFrequencyRatio, 1.0 / CompilationPolicy::min_invocations());

      if (freq < min_freq) {
        set_msg("low call site frequency");
        return true;
      }
    }
  }

  return false;
}

bool InlineTree::is_not_reached(ciMethod* callee_method, ciMethod* caller_method, int caller_bci, ciCallProfile& profile) {
  if (!UseInterpreter) {
    return false; // -Xcomp
  }
  if (profile.count() > 0) {
    return false; // reachable according to profile
  }
  if (!callee_method->was_executed_more_than(0)) {
    return true; // callee was never executed
  }
  if (caller_method->is_not_reached(caller_bci)) {
    return true; // call site not resolved
  }
  if (profile.count() == -1) {
    return false; // immature profile; optimistically treat as reached
  }
  assert(profile.count() == 0, "sanity");

  // Profile info is scarce.
  // Try to guess: check if the call site belongs to a start block.
  // Call sites in a start block should be reachable if no exception is thrown earlier.
  ciMethodBlocks* caller_blocks = caller_method->get_method_blocks();
  bool is_start_block = caller_blocks->block_containing(caller_bci)->start_bci() == 0;
  if (is_start_block) {
    return false; // treat the call reached as part of start block
  }
  return true; // give up and treat the call site as not reached
}

//-----------------------------try_to_inline-----------------------------------
// return true if ok
// Relocated from "InliningClosure::try_to_inline"
bool InlineTree::try_to_inline(ciMethod* callee_method, ciMethod* caller_method,
                               int caller_bci, JVMState* jvms, ciCallProfile& profile,
                               bool& should_delay) {

  if (ClipInlining && (int)count_inline_bcs() >= DesiredMethodLimit) {
    if (!callee_method->force_inline() || !IncrementalInline) {
      set_msg("size > DesiredMethodLimit");
      return false;
    } else if (!C->inlining_incrementally()) {
      should_delay = true;
    }
  }

  _forced_inline = false; // Reset

  // 'should_delay' can be overridden during replay compilation
  if (!should_inline(callee_method, caller_method, caller_bci, should_delay, profile)) {
    return false;
  }
  // 'should_delay' can be overridden during replay compilation
  if (should_not_inline(callee_method, caller_method, caller_bci, should_delay, profile)) {
    return false;
  }

  if (InlineAccessors && callee_method->is_accessor()) {
    // accessor methods are not subject to any of the following limits.
    set_msg("accessor");
    return true;
  }

  // suppress a few checks for accessors and trivial methods
  if (callee_method->code_size() > MaxTrivialSize) {

    // don't inline into giant methods
    if (C->over_inlining_cutoff()) {
      if ((!callee_method->force_inline() && !caller_method->is_compiled_lambda_form())
          || !IncrementalInline) {
        set_msg("NodeCountInliningCutoff");
        return false;
      } else {
        should_delay = true;
      }
    }

    if (!UseInterpreter &&
        is_init_with_ea(callee_method, caller_method, C)) {
      // Escape Analysis stress testing when running Xcomp:
      // inline constructors even if they are not reached.
    } else if (forced_inline()) {
      // Inlining was forced by CompilerOracle, ciReplay or annotation
    } else if (is_not_reached(callee_method, caller_method, caller_bci, profile)) {
      // don't inline unreached call sites
       set_msg("call site not reached");
       return false;
    }
  }

  if (!C->do_inlining() && InlineAccessors) {
    set_msg("not an accessor");
    return false;
  }

  // Limit inlining depth in case inlining is forced or
  // _max_inline_level was increased to compensate for lambda forms.
  if (inline_level() > MaxForceInlineLevel) {
    set_msg("MaxForceInlineLevel");
    return false;
  }
  if (inline_level() > _max_inline_level) {
    if (!callee_method->force_inline() || !IncrementalInline) {
      set_msg("inlining too deep");
      return false;
    } else if (!C->inlining_incrementally()) {
      should_delay = true;
    }
  }

  // detect direct and indirect recursive inlining
  {
    // count the current method and the callee
    const bool is_compiled_lambda_form = callee_method->is_compiled_lambda_form();
    int inline_level = 0;
    if (!is_compiled_lambda_form) {
      if (method() == callee_method) {
        inline_level++;
      }
    }
    // count callers of current method and callee
    Node* callee_argument0 = is_compiled_lambda_form ? jvms->map()->argument(jvms, 0)->uncast() : nullptr;
    for (JVMState* j = jvms->caller(); j != nullptr && j->has_method(); j = j->caller()) {
      if (j->method() == callee_method) {
        if (is_compiled_lambda_form) {
          // Since compiled lambda forms are heavily reused we allow recursive inlining.  If it is truly
          // a recursion (using the same "receiver") we limit inlining otherwise we can easily blow the
          // compiler stack.
          Node* caller_argument0 = j->map()->argument(j, 0)->uncast();
          if (caller_argument0 == callee_argument0) {
            inline_level++;
          }
        } else {
          inline_level++;
        }
      }
    }
    if (inline_level > MaxRecursiveInlineLevel) {
      set_msg("recursive inlining is too deep");
      return false;
    }
  }

  int size = callee_method->code_size_for_inlining();

  if (ClipInlining && (int)count_inline_bcs() + size >= DesiredMethodLimit) {
    if (!callee_method->force_inline() || !IncrementalInline) {
      set_msg("size > DesiredMethodLimit");
      return false;
    } else if (!C->inlining_incrementally()) {
      should_delay = true;
    }
  }

  // ok, inline this method
  return true;
}

//------------------------------pass_initial_checks----------------------------
bool InlineTree::pass_initial_checks(ciMethod* caller_method, int caller_bci, ciMethod* callee_method) {
  // Check if a callee_method was suggested
  if (callee_method == nullptr) {
    return false;
  }
  ciInstanceKlass *callee_holder = callee_method->holder();
  // Check if klass of callee_method is loaded
  if (!callee_holder->is_loaded()) {
    return false;
  }
  if (!callee_holder->is_initialized() &&
      // access allowed in the context of static initializer
      C->needs_clinit_barrier(callee_holder, caller_method)) {
    return false;
  }
  if( !UseInterpreter ) /* running Xcomp */ {
    // Checks that constant pool's call site has been visited
    // stricter than callee_holder->is_initialized()
    ciBytecodeStream iter(caller_method);
    iter.force_bci(caller_bci);
    Bytecodes::Code call_bc = iter.cur_bc();
    // An invokedynamic instruction does not have a klass.
    if (call_bc != Bytecodes::_invokedynamic) {
      int index = iter.get_index_u2_cpcache();
      if (!caller_method->is_klass_loaded(index, call_bc, true)) {
        return false;
      }
      // Try to do constant pool resolution if running Xcomp
      if( !caller_method->check_call(index, call_bc == Bytecodes::_invokestatic) ) {
        return false;
      }
    }
  }
  return true;
}

//------------------------------check_can_parse--------------------------------
const char* InlineTree::check_can_parse(ciMethod* callee) {
  // Certain methods cannot be parsed at all:
  if ( callee->is_native())                     return "native method";
  if ( callee->is_abstract())                   return "abstract method";
  if (!callee->has_balanced_monitors())         return "not compilable (unbalanced monitors)";
  if ( callee->get_flow_analysis()->failing())  return "not compilable (flow analysis failed)";
  if (!callee->can_be_parsed())                 return "cannot be parsed";
  return nullptr;
}

//------------------------------print_inlining---------------------------------
void InlineTree::print_inlining(ciMethod* callee_method, int caller_bci,
                                ciMethod* caller_method, bool success) const {
  const char* inline_msg = msg();
  assert(inline_msg != nullptr, "just checking");
  if (C->log() != nullptr) {
    if (success) {
      C->log()->inline_success(inline_msg);
    } else {
      C->log()->inline_fail(inline_msg);
    }
  }
  CompileTask::print_inlining_ul(callee_method, inline_level(),
                                 caller_bci, inlining_result_of(success), inline_msg);
  if (C->print_inlining()) {
    C->print_inlining(callee_method, inline_level(), caller_bci, inlining_result_of(success), inline_msg);
    guarantee(callee_method != nullptr, "would crash in CompilerEvent::InlineEvent::post");
    if (Verbose) {
      const InlineTree *top = this;
      while (top->caller_tree() != nullptr) { top = top->caller_tree(); }
      //tty->print("  bcs: %d+%d  invoked: %d", top->count_inline_bcs(), callee_method->code_size(), callee_method->interpreter_invocation_count());
    }
  }
  EventCompilerInlining event;
  if (event.should_commit()) {
    CompilerEvent::InlineEvent::post(event, C->compile_id(), caller_method->get_Method(), callee_method, success, inline_msg, caller_bci);
  }
}

//------------------------------ok_to_inline-----------------------------------
bool InlineTree::ok_to_inline(ciMethod* callee_method, JVMState* jvms, ciCallProfile& profile,
                              bool& should_delay) {
#ifdef ASSERT
  assert(callee_method != nullptr, "caller checks for optimized virtual!");
  // Make sure the incoming jvms has the same information content as me.
  // This means that we can eventually make this whole class AllStatic.
  if (jvms->caller() == nullptr) {
    assert(_caller_jvms == nullptr, "redundant instance state");
  } else {
    assert(_caller_jvms->same_calls_as(jvms->caller()), "redundant instance state");
  }
  assert(_method == jvms->method(), "redundant instance state");
#endif
  int         caller_bci    = jvms->bci();
  ciMethod*   caller_method = jvms->method();

  // Do some initial checks.
  if (!pass_initial_checks(caller_method, caller_bci, callee_method)) {
    set_msg("failed initial checks");
    print_inlining(callee_method, caller_bci, caller_method, false /* !success */);
    return false;
  }

  // Do some parse checks.
  set_msg(check_can_parse(callee_method));
  if (msg() != nullptr) {
    print_inlining(callee_method, caller_bci, caller_method, false /* !success */);
    return false;
  }

  // Check if inlining policy says no.
  bool success = try_to_inline(callee_method, caller_method, caller_bci, jvms, profile,
                               should_delay); // out
  if (success) {
    // Inline!
    if (msg() == nullptr) {
      set_msg("inline (hot)");
    }
    print_inlining(callee_method, caller_bci, caller_method, true /* success */);
    InlineTree* callee_tree = build_inline_tree_for_callee(callee_method, jvms, caller_bci);
    if (should_delay) {
      // Record late inlining decision in order to dump it for compiler replay
      callee_tree->set_late_inline();
    }
    return true;
  } else {
    // Do not inline
    if (msg() == nullptr) {
      set_msg("too cold to inline");
    }
    print_inlining(callee_method, caller_bci, caller_method, false /* !success */ );
    return false;
  }
}

//------------------------------build_inline_tree_for_callee-------------------
InlineTree *InlineTree::build_inline_tree_for_callee( ciMethod* callee_method, JVMState* caller_jvms, int caller_bci) {
  // Attempt inlining.
  InlineTree* old_ilt = callee_at(caller_bci, callee_method);
  if (old_ilt != nullptr) {
    return old_ilt;
  }
  int max_inline_level_adjust = 0;
  if (caller_jvms->method() != nullptr) {
    if (caller_jvms->method()->is_compiled_lambda_form()) {
      max_inline_level_adjust += 1;  // don't count actions in MH or indy adapter frames
    } else if (callee_method->is_method_handle_intrinsic() ||
               callee_method->is_compiled_lambda_form()) {
      max_inline_level_adjust += 1;  // don't count method handle calls from java.lang.invoke implementation
    }
    if (max_inline_level_adjust != 0 && C->print_inlining() && (Verbose || WizardMode)) {
      CompileTask::print_inline_indent(inline_level());
      tty->print_cr(" \\-> discounting inline depth");
    }
    if (max_inline_level_adjust != 0 && C->log()) {
      int id1 = C->log()->identify(caller_jvms->method());
      int id2 = C->log()->identify(callee_method);
      C->log()->elem("inline_level_discount caller='%d' callee='%d'", id1, id2);
    }
  }
  // Allocate in the comp_arena to make sure the InlineTree is live when dumping a replay compilation file
  InlineTree* ilt = new (C->comp_arena()) InlineTree(C, this, callee_method, caller_jvms, caller_bci, _max_inline_level + max_inline_level_adjust);
  _subtrees.append(ilt);

  NOT_PRODUCT( _count_inlines += 1; )

  return ilt;
}


//---------------------------------------callee_at-----------------------------
InlineTree *InlineTree::callee_at(int bci, ciMethod* callee) const {
  for (int i = 0; i < _subtrees.length(); i++) {
    InlineTree* sub = _subtrees.at(i);
    if (sub->caller_bci() == bci && callee == sub->method()) {
      return sub;
    }
  }
  return nullptr;
}


//------------------------------build_inline_tree_root-------------------------
InlineTree *InlineTree::build_inline_tree_root() {
  Compile* C = Compile::current();

  // Root of inline tree
  InlineTree* ilt = new InlineTree(C, nullptr, C->method(), nullptr, -1, MaxInlineLevel);

  return ilt;
}


//-------------------------find_subtree_from_root-----------------------------
// Given a jvms, which determines a call chain from the root method,
// find the corresponding inline tree.
// Note: This method will be removed or replaced as InlineTree goes away.
InlineTree* InlineTree::find_subtree_from_root(InlineTree* root, JVMState* jvms, ciMethod* callee) {
  InlineTree* iltp = root;
  uint depth = jvms && jvms->has_method() ? jvms->depth() : 0;
  for (uint d = 1; d <= depth; d++) {
    JVMState* jvmsp  = jvms->of_depth(d);
    // Select the corresponding subtree for this bci.
    assert(jvmsp->method() == iltp->method(), "tree still in sync");
    ciMethod* d_callee = (d == depth) ? callee : jvms->of_depth(d+1)->method();
    InlineTree* sub = iltp->callee_at(jvmsp->bci(), d_callee);
    if (sub == nullptr) {
      if (d == depth) {
        sub = iltp->build_inline_tree_for_callee(d_callee, jvmsp, jvmsp->bci());
      }
      guarantee(sub != nullptr, "should be a sub-ilt here");
      return sub;
    }
    iltp = sub;
  }
  return iltp;
}

// Count number of nodes in this subtree
int InlineTree::count() const {
  int result = 1;
  for (int i = 0 ; i < _subtrees.length(); i++) {
    result += _subtrees.at(i)->count();
  }
  return result;
}

void InlineTree::dump_replay_data(outputStream* out, int depth_adjust) {
  out->print(" %d %d %d ", inline_level() + depth_adjust, caller_bci(), _late_inline);
  method()->dump_name_as_ascii(out);
  for (int i = 0 ; i < _subtrees.length(); i++) {
    _subtrees.at(i)->dump_replay_data(out, depth_adjust);
  }
}


#ifndef PRODUCT
void InlineTree::print_impl(outputStream* st, int indent) const {
  for (int i = 0; i < indent; i++) st->print(" ");
  st->print(" @ %d", caller_bci());
  method()->print_short_name(st);
  st->cr();

  for (int i = 0 ; i < _subtrees.length(); i++) {
    _subtrees.at(i)->print_impl(st, indent + 2);
  }
}

void InlineTree::print_value_on(outputStream* st) const {
  print_impl(st, 2);
}
#endif
