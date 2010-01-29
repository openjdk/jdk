/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_doCall.cpp.incl"

#ifndef PRODUCT
void trace_type_profile(ciMethod *method, int depth, int bci, ciMethod *prof_method, ciKlass *prof_klass, int site_count, int receiver_count) {
  if (TraceTypeProfile || PrintInlining || PrintOptoInlining) {
    tty->print("   ");
    for( int i = 0; i < depth; i++ ) tty->print("  ");
    if (!PrintOpto) {
      method->print_short_name();
      tty->print(" ->");
    }
    tty->print(" @ %d  ", bci);
    prof_method->print_short_name();
    tty->print("  >>TypeProfile (%d/%d counts) = ", receiver_count, site_count);
    prof_klass->name()->print_symbol();
    tty->print_cr(" (%d bytes)", prof_method->code_size());
  }
}
#endif

CallGenerator* Compile::call_generator(ciMethod* call_method, int vtable_index, bool call_is_virtual,
                                       JVMState* jvms, bool allow_inline,
                                       float prof_factor) {
  CallGenerator* cg;

  // Dtrace currently doesn't work unless all calls are vanilla
  if (env()->dtrace_method_probes()) {
    allow_inline = false;
  }

  // Note: When we get profiling during stage-1 compiles, we want to pull
  // from more specific profile data which pertains to this inlining.
  // Right now, ignore the information in jvms->caller(), and do method[bci].
  ciCallProfile profile = jvms->method()->call_profile_at_bci(jvms->bci());

  // See how many times this site has been invoked.
  int site_count = profile.count();
  int receiver_count = -1;
  if (call_is_virtual && UseTypeProfile && profile.has_receiver(0)) {
    // Receivers in the profile structure are ordered by call counts
    // so that the most called (major) receiver is profile.receiver(0).
    receiver_count = profile.receiver_count(0);
  }

  CompileLog* log = this->log();
  if (log != NULL) {
    int rid = (receiver_count >= 0)? log->identify(profile.receiver(0)): -1;
    int r2id = (profile.morphism() == 2)? log->identify(profile.receiver(1)):-1;
    log->begin_elem("call method='%d' count='%d' prof_factor='%g'",
                    log->identify(call_method), site_count, prof_factor);
    if (call_is_virtual)  log->print(" virtual='1'");
    if (allow_inline)     log->print(" inline='1'");
    if (receiver_count >= 0) {
      log->print(" receiver='%d' receiver_count='%d'", rid, receiver_count);
      if (profile.has_receiver(1)) {
        log->print(" receiver2='%d' receiver2_count='%d'", r2id, profile.receiver_count(1));
      }
    }
    log->end_elem();
  }

  // Special case the handling of certain common, profitable library
  // methods.  If these methods are replaced with specialized code,
  // then we return it as the inlined version of the call.
  // We do this before the strict f.p. check below because the
  // intrinsics handle strict f.p. correctly.
  if (allow_inline) {
    cg = find_intrinsic(call_method, call_is_virtual);
    if (cg != NULL)  return cg;
  }

  // Do not inline strict fp into non-strict code, or the reverse
  bool caller_method_is_strict = jvms->method()->is_strict();
  if( caller_method_is_strict ^ call_method->is_strict() ) {
    allow_inline = false;
  }

  // Attempt to inline...
  if (allow_inline) {
    // The profile data is only partly attributable to this caller,
    // scale back the call site information.
    float past_uses = jvms->method()->scale_count(site_count, prof_factor);
    // This is the number of times we expect the call code to be used.
    float expected_uses = past_uses;

    // Try inlining a bytecoded method:
    if (!call_is_virtual) {
      InlineTree* ilt;
      if (UseOldInlining) {
        ilt = InlineTree::find_subtree_from_root(this->ilt(), jvms->caller(), jvms->method());
      } else {
        // Make a disembodied, stateless ILT.
        // TO DO:  When UseOldInlining is removed, copy the ILT code elsewhere.
        float site_invoke_ratio = prof_factor;
        // Note:  ilt is for the root of this parse, not the present call site.
        ilt = new InlineTree(this, jvms->method(), jvms->caller(), site_invoke_ratio, 0);
      }
      WarmCallInfo scratch_ci;
      if (!UseOldInlining)
        scratch_ci.init(jvms, call_method, profile, prof_factor);
      WarmCallInfo* ci = ilt->ok_to_inline(call_method, jvms, profile, &scratch_ci);
      assert(ci != &scratch_ci, "do not let this pointer escape");
      bool allow_inline   = (ci != NULL && !ci->is_cold());
      bool require_inline = (allow_inline && ci->is_hot());

      if (allow_inline) {
        CallGenerator* cg = CallGenerator::for_inline(call_method, expected_uses);
        if (require_inline && cg != NULL && should_delay_inlining(call_method, jvms)) {
          // Delay the inlining of this method to give us the
          // opportunity to perform some high level optimizations
          // first.
          return CallGenerator::for_late_inline(call_method, cg);
        }
        if (cg == NULL) {
          // Fall through.
        } else if (require_inline || !InlineWarmCalls) {
          return cg;
        } else {
          CallGenerator* cold_cg = call_generator(call_method, vtable_index, call_is_virtual, jvms, false, prof_factor);
          return CallGenerator::for_warm_call(ci, cold_cg, cg);
        }
      }
    }

    // Try using the type profile.
    if (call_is_virtual && site_count > 0 && receiver_count > 0) {
      // The major receiver's count >= TypeProfileMajorReceiverPercent of site_count.
      bool have_major_receiver = (100.*profile.receiver_prob(0) >= (float)TypeProfileMajorReceiverPercent);
      ciMethod* receiver_method = NULL;
      if (have_major_receiver || profile.morphism() == 1 ||
          (profile.morphism() == 2 && UseBimorphicInlining)) {
        // receiver_method = profile.method();
        // Profiles do not suggest methods now.  Look it up in the major receiver.
        receiver_method = call_method->resolve_invoke(jvms->method()->holder(),
                                                      profile.receiver(0));
      }
      if (receiver_method != NULL) {
        // The single majority receiver sufficiently outweighs the minority.
        CallGenerator* hit_cg = this->call_generator(receiver_method,
              vtable_index, !call_is_virtual, jvms, allow_inline, prof_factor);
        if (hit_cg != NULL) {
          // Look up second receiver.
          CallGenerator* next_hit_cg = NULL;
          ciMethod* next_receiver_method = NULL;
          if (profile.morphism() == 2 && UseBimorphicInlining) {
            next_receiver_method = call_method->resolve_invoke(jvms->method()->holder(),
                                                               profile.receiver(1));
            if (next_receiver_method != NULL) {
              next_hit_cg = this->call_generator(next_receiver_method,
                                  vtable_index, !call_is_virtual, jvms,
                                  allow_inline, prof_factor);
              if (next_hit_cg != NULL && !next_hit_cg->is_inline() &&
                  have_major_receiver && UseOnlyInlinedBimorphic) {
                  // Skip if we can't inline second receiver's method
                  next_hit_cg = NULL;
              }
            }
          }
          CallGenerator* miss_cg;
          if (( profile.morphism() == 1 ||
               (profile.morphism() == 2 && next_hit_cg != NULL) ) &&

              !too_many_traps(Deoptimization::Reason_class_check)

              // Check only total number of traps per method to allow
              // the transition from monomorphic to bimorphic case between
              // compilations without falling into virtual call.
              // A monomorphic case may have the class_check trap flag is set
              // due to the time gap between the uncommon trap processing
              // when flags are set in MDO and the call site bytecode execution
              // in Interpreter when MDO counters are updated.
              // There was also class_check trap in monomorphic case due to
              // the bug 6225440.

             ) {
            // Generate uncommon trap for class check failure path
            // in case of monomorphic or bimorphic virtual call site.
            miss_cg = CallGenerator::for_uncommon_trap(call_method,
                        Deoptimization::Reason_class_check,
                        Deoptimization::Action_maybe_recompile);
          } else {
            // Generate virtual call for class check failure path
            // in case of polymorphic virtual call site.
            miss_cg = CallGenerator::for_virtual_call(call_method, vtable_index);
          }
          if (miss_cg != NULL) {
            if (next_hit_cg != NULL) {
              NOT_PRODUCT(trace_type_profile(jvms->method(), jvms->depth(), jvms->bci(), next_receiver_method, profile.receiver(1), site_count, profile.receiver_count(1)));
              // We don't need to record dependency on a receiver here and below.
              // Whenever we inline, the dependency is added by Parse::Parse().
              miss_cg = CallGenerator::for_predicted_call(profile.receiver(1), miss_cg, next_hit_cg, PROB_MAX);
            }
            if (miss_cg != NULL) {
              NOT_PRODUCT(trace_type_profile(jvms->method(), jvms->depth(), jvms->bci(), receiver_method, profile.receiver(0), site_count, receiver_count));
              cg = CallGenerator::for_predicted_call(profile.receiver(0), miss_cg, hit_cg, profile.receiver_prob(0));
              if (cg != NULL)  return cg;
            }
          }
        }
      }
    }
  }

  // Do MethodHandle calls.
  if (call_method->is_method_handle_invoke()) {
    if (jvms->method()->java_code_at_bci(jvms->bci()) != Bytecodes::_invokedynamic) {
      GraphKit kit(jvms);
      Node* n = kit.argument(0);

      if (n->Opcode() == Op_ConP) {
        const TypeOopPtr* oop_ptr = n->bottom_type()->is_oopptr();
        ciObject* const_oop = oop_ptr->const_oop();
        ciMethodHandle* method_handle = const_oop->as_method_handle();

        // Set the actually called method to have access to the class
        // and signature in the MethodHandleCompiler.
        method_handle->set_callee(call_method);

        // Get an adapter for the MethodHandle.
        ciMethod* target_method = method_handle->get_method_handle_adapter();

        CallGenerator* hit_cg = this->call_generator(target_method, vtable_index, false, jvms, true, prof_factor);
        if (hit_cg != NULL && hit_cg->is_inline())
          return hit_cg;
      }

      return CallGenerator::for_direct_call(call_method);
    }
    else {
      // Get the MethodHandle from the CallSite.
      ciMethod* caller_method = jvms->method();
      ciBytecodeStream str(caller_method);
      str.force_bci(jvms->bci());  // Set the stream to the invokedynamic bci.
      ciCallSite*     call_site     = str.get_call_site();
      ciMethodHandle* method_handle = call_site->get_target();

      // Set the actually called method to have access to the class
      // and signature in the MethodHandleCompiler.
      method_handle->set_callee(call_method);

      // Get an adapter for the MethodHandle.
      ciMethod* target_method = method_handle->get_invokedynamic_adapter();

      CallGenerator* hit_cg = this->call_generator(target_method, vtable_index, false, jvms, true, prof_factor);
      if (hit_cg != NULL && hit_cg->is_inline()) {
        CallGenerator* miss_cg = CallGenerator::for_dynamic_call(call_method);
        return CallGenerator::for_predicted_dynamic_call(method_handle, miss_cg, hit_cg, prof_factor);
      }

      // If something failed, generate a normal dynamic call.
      return CallGenerator::for_dynamic_call(call_method);
    }
  }

  // There was no special inlining tactic, or it bailed out.
  // Use a more generic tactic, like a simple call.
  if (call_is_virtual) {
    return CallGenerator::for_virtual_call(call_method, vtable_index);
  } else {
    // Class Hierarchy Analysis or Type Profile reveals a unique target,
    // or it is a static or special call.
    return CallGenerator::for_direct_call(call_method, should_delay_inlining(call_method, jvms));
  }
}

// Return true for methods that shouldn't be inlined early so that
// they are easier to analyze and optimize as intrinsics.
bool Compile::should_delay_inlining(ciMethod* call_method, JVMState* jvms) {
  if (has_stringbuilder()) {

    if ((call_method->holder() == C->env()->StringBuilder_klass() ||
         call_method->holder() == C->env()->StringBuffer_klass()) &&
        (jvms->method()->holder() == C->env()->StringBuilder_klass() ||
         jvms->method()->holder() == C->env()->StringBuffer_klass())) {
      // Delay SB calls only when called from non-SB code
      return false;
    }

    switch (call_method->intrinsic_id()) {
      case vmIntrinsics::_StringBuilder_void:
      case vmIntrinsics::_StringBuilder_int:
      case vmIntrinsics::_StringBuilder_String:
      case vmIntrinsics::_StringBuilder_append_char:
      case vmIntrinsics::_StringBuilder_append_int:
      case vmIntrinsics::_StringBuilder_append_String:
      case vmIntrinsics::_StringBuilder_toString:
      case vmIntrinsics::_StringBuffer_void:
      case vmIntrinsics::_StringBuffer_int:
      case vmIntrinsics::_StringBuffer_String:
      case vmIntrinsics::_StringBuffer_append_char:
      case vmIntrinsics::_StringBuffer_append_int:
      case vmIntrinsics::_StringBuffer_append_String:
      case vmIntrinsics::_StringBuffer_toString:
      case vmIntrinsics::_Integer_toString:
        return true;

      case vmIntrinsics::_String_String:
        {
          Node* receiver = jvms->map()->in(jvms->argoff() + 1);
          if (receiver->is_Proj() && receiver->in(0)->is_CallStaticJava()) {
            CallStaticJavaNode* csj = receiver->in(0)->as_CallStaticJava();
            ciMethod* m = csj->method();
            if (m != NULL &&
                (m->intrinsic_id() == vmIntrinsics::_StringBuffer_toString ||
                 m->intrinsic_id() == vmIntrinsics::_StringBuilder_toString))
              // Delay String.<init>(new SB())
              return true;
          }
          return false;
        }

      default:
        return false;
    }
  }
  return false;
}


// uncommon-trap call-sites where callee is unloaded, uninitialized or will not link
bool Parse::can_not_compile_call_site(ciMethod *dest_method, ciInstanceKlass* klass) {
  // Additional inputs to consider...
  // bc      = bc()
  // caller  = method()
  // iter().get_method_holder_index()
  assert( dest_method->is_loaded(), "ciTypeFlow should not let us get here" );
  // Interface classes can be loaded & linked and never get around to
  // being initialized.  Uncommon-trap for not-initialized static or
  // v-calls.  Let interface calls happen.
  ciInstanceKlass* holder_klass = dest_method->holder();
  if (!holder_klass->is_initialized() &&
      !holder_klass->is_interface()) {
    uncommon_trap(Deoptimization::Reason_uninitialized,
                  Deoptimization::Action_reinterpret,
                  holder_klass);
    return true;
  }

  assert(dest_method->will_link(method()->holder(), klass, bc()), "dest_method: typeflow responsibility");
  return false;
}


//------------------------------do_call----------------------------------------
// Handle your basic call.  Inline if we can & want to, else just setup call.
void Parse::do_call() {
  // It's likely we are going to add debug info soon.
  // Also, if we inline a guy who eventually needs debug info for this JVMS,
  // our contribution to it is cleaned up right here.
  kill_dead_locals();

  // Set frequently used booleans
  bool is_virtual = bc() == Bytecodes::_invokevirtual;
  bool is_virtual_or_interface = is_virtual || bc() == Bytecodes::_invokeinterface;
  bool has_receiver = is_virtual_or_interface || bc() == Bytecodes::_invokespecial;
  bool is_invokedynamic = bc() == Bytecodes::_invokedynamic;

  // Find target being called
  bool             will_link;
  ciMethod*        dest_method   = iter().get_method(will_link);
  ciInstanceKlass* holder_klass  = dest_method->holder();
  ciKlass* holder = iter().get_declared_method_holder();
  ciInstanceKlass* klass = ciEnv::get_instance_klass_for_declared_method_holder(holder);

  int nargs = dest_method->arg_size();
  if (is_invokedynamic)  nargs -= 1;

  // uncommon-trap when callee is unloaded, uninitialized or will not link
  // bailout when too many arguments for register representation
  if (!will_link || can_not_compile_call_site(dest_method, klass)) {
#ifndef PRODUCT
    if (PrintOpto && (Verbose || WizardMode)) {
      method()->print_name(); tty->print_cr(" can not compile call at bci %d to:", bci());
      dest_method->print_name(); tty->cr();
    }
#endif
    return;
  }
  assert(holder_klass->is_loaded(), "");
  assert((dest_method->is_static() || is_invokedynamic) == !has_receiver , "must match bc");
  // Note: this takes into account invokeinterface of methods declared in java/lang/Object,
  // which should be invokevirtuals but according to the VM spec may be invokeinterfaces
  assert(holder_klass->is_interface() || holder_klass->super() == NULL || (bc() != Bytecodes::_invokeinterface), "must match bc");
  // Note:  In the absence of miranda methods, an abstract class K can perform
  // an invokevirtual directly on an interface method I.m if K implements I.

  // ---------------------
  // Does Class Hierarchy Analysis reveal only a single target of a v-call?
  // Then we may inline or make a static call, but become dependent on there being only 1 target.
  // Does the call-site type profile reveal only one receiver?
  // Then we may introduce a run-time check and inline on the path where it succeeds.
  // The other path may uncommon_trap, check for another receiver, or do a v-call.

  // Choose call strategy.
  bool call_is_virtual = is_virtual_or_interface;
  int vtable_index = methodOopDesc::invalid_vtable_index;
  ciMethod* call_method = dest_method;

  // Try to get the most accurate receiver type
  if (is_virtual_or_interface) {
    Node*             receiver_node = stack(sp() - nargs);
    const TypeOopPtr* receiver_type = _gvn.type(receiver_node)->isa_oopptr();
    ciMethod* optimized_virtual_method = optimize_inlining(method(), bci(), klass, dest_method, receiver_type);

    // Have the call been sufficiently improved such that it is no longer a virtual?
    if (optimized_virtual_method != NULL) {
      call_method     = optimized_virtual_method;
      call_is_virtual = false;
    } else if (!UseInlineCaches && is_virtual && call_method->is_loaded()) {
      // We can make a vtable call at this site
      vtable_index = call_method->resolve_vtable_index(method()->holder(), klass);
    }
  }

  // Note:  It's OK to try to inline a virtual call.
  // The call generator will not attempt to inline a polymorphic call
  // unless it knows how to optimize the receiver dispatch.
  bool try_inline = (C->do_inlining() || InlineAccessors);

  // ---------------------
  inc_sp(- nargs);              // Temporarily pop args for JVM state of call
  JVMState* jvms = sync_jvms();

  // ---------------------
  // Decide call tactic.
  // This call checks with CHA, the interpreter profile, intrinsics table, etc.
  // It decides whether inlining is desirable or not.
  CallGenerator* cg = C->call_generator(call_method, vtable_index, call_is_virtual, jvms, try_inline, prof_factor());

  // ---------------------
  // Round double arguments before call
  round_double_arguments(dest_method);

#ifndef PRODUCT
  // bump global counters for calls
  count_compiled_calls(false/*at_method_entry*/, cg->is_inline());

  // Record first part of parsing work for this call
  parse_histogram()->record_change();
#endif // not PRODUCT

  assert(jvms == this->jvms(), "still operating on the right JVMS");
  assert(jvms_in_sync(),       "jvms must carry full info into CG");

  // save across call, for a subsequent cast_not_null.
  Node* receiver = has_receiver ? argument(0) : NULL;

  // Bump method data counters (We profile *before* the call is made
  // because exceptions don't return to the call site.)
  profile_call(receiver);

  JVMState* new_jvms;
  if ((new_jvms = cg->generate(jvms)) == NULL) {
    // When inlining attempt fails (e.g., too many arguments),
    // it may contaminate the current compile state, making it
    // impossible to pull back and try again.  Once we call
    // cg->generate(), we are committed.  If it fails, the whole
    // compilation task is compromised.
    if (failing())  return;
#ifndef PRODUCT
    if (PrintOpto || PrintOptoInlining || PrintInlining) {
      // Only one fall-back, so if an intrinsic fails, ignore any bytecodes.
      if (cg->is_intrinsic() && call_method->code_size() > 0) {
        tty->print("Bailed out of intrinsic, will not inline: ");
        call_method->print_name(); tty->cr();
      }
    }
#endif
    // This can happen if a library intrinsic is available, but refuses
    // the call site, perhaps because it did not match a pattern the
    // intrinsic was expecting to optimize.  The fallback position is
    // to call out-of-line.
    try_inline = false;  // Inline tactic bailed out.
    cg = C->call_generator(call_method, vtable_index, call_is_virtual, jvms, try_inline, prof_factor());
    if ((new_jvms = cg->generate(jvms)) == NULL) {
      guarantee(failing(), "call failed to generate:  calls should work");
      return;
    }
  }

  if (cg->is_inline()) {
    // Accumulate has_loops estimate
    C->set_has_loops(C->has_loops() || call_method->has_loops());
    C->env()->notice_inlined_method(call_method);
  }

  // Reset parser state from [new_]jvms, which now carries results of the call.
  // Return value (if any) is already pushed on the stack by the cg.
  add_exception_states_from(new_jvms);
  if (new_jvms->map()->control() == top()) {
    stop_and_kill_map();
  } else {
    assert(new_jvms->same_calls_as(jvms), "method/bci left unchanged");
    set_jvms(new_jvms);
  }

  if (!stopped()) {
    // This was some sort of virtual call, which did a null check for us.
    // Now we can assert receiver-not-null, on the normal return path.
    if (receiver != NULL && cg->is_virtual()) {
      Node* cast = cast_not_null(receiver);
      // %%% assert(receiver == cast, "should already have cast the receiver");
    }

    // Round double result after a call from strict to non-strict code
    round_double_result(dest_method);

    // If the return type of the method is not loaded, assert that the
    // value we got is a null.  Otherwise, we need to recompile.
    if (!dest_method->return_type()->is_loaded()) {
#ifndef PRODUCT
      if (PrintOpto && (Verbose || WizardMode)) {
        method()->print_name(); tty->print_cr(" asserting nullness of result at bci: %d", bci());
        dest_method->print_name(); tty->cr();
      }
#endif
      if (C->log() != NULL) {
        C->log()->elem("assert_null reason='return' klass='%d'",
                       C->log()->identify(dest_method->return_type()));
      }
      // If there is going to be a trap, put it at the next bytecode:
      set_bci(iter().next_bci());
      do_null_assert(peek(), T_OBJECT);
      set_bci(iter().cur_bci()); // put it back
    }
  }

  // Restart record of parsing work after possible inlining of call
#ifndef PRODUCT
  parse_histogram()->set_initial_state(bc());
#endif
}

//---------------------------catch_call_exceptions-----------------------------
// Put a Catch and CatchProj nodes behind a just-created call.
// Send their caught exceptions to the proper handler.
// This may be used after a call to the rethrow VM stub,
// when it is needed to process unloaded exception classes.
void Parse::catch_call_exceptions(ciExceptionHandlerStream& handlers) {
  // Exceptions are delivered through this channel:
  Node* i_o = this->i_o();

  // Add a CatchNode.
  GrowableArray<int>* bcis = new (C->node_arena()) GrowableArray<int>(C->node_arena(), 8, 0, -1);
  GrowableArray<const Type*>* extypes = new (C->node_arena()) GrowableArray<const Type*>(C->node_arena(), 8, 0, NULL);
  GrowableArray<int>* saw_unloaded = new (C->node_arena()) GrowableArray<int>(C->node_arena(), 8, 0, 0);

  for (; !handlers.is_done(); handlers.next()) {
    ciExceptionHandler* h        = handlers.handler();
    int                 h_bci    = h->handler_bci();
    ciInstanceKlass*    h_klass  = h->is_catch_all() ? env()->Throwable_klass() : h->catch_klass();
    // Do not introduce unloaded exception types into the graph:
    if (!h_klass->is_loaded()) {
      if (saw_unloaded->contains(h_bci)) {
        /* We've already seen an unloaded exception with h_bci,
           so don't duplicate. Duplication will cause the CatchNode to be
           unnecessarily large. See 4713716. */
        continue;
      } else {
        saw_unloaded->append(h_bci);
      }
    }
    const Type*         h_extype = TypeOopPtr::make_from_klass(h_klass);
    // (We use make_from_klass because it respects UseUniqueSubclasses.)
    h_extype = h_extype->join(TypeInstPtr::NOTNULL);
    assert(!h_extype->empty(), "sanity");
    // Note:  It's OK if the BCIs repeat themselves.
    bcis->append(h_bci);
    extypes->append(h_extype);
  }

  int len = bcis->length();
  CatchNode *cn = new (C, 2) CatchNode(control(), i_o, len+1);
  Node *catch_ = _gvn.transform(cn);

  // now branch with the exception state to each of the (potential)
  // handlers
  for(int i=0; i < len; i++) {
    // Setup JVM state to enter the handler.
    PreserveJVMState pjvms(this);
    // Locals are just copied from before the call.
    // Get control from the CatchNode.
    int handler_bci = bcis->at(i);
    Node* ctrl = _gvn.transform( new (C, 1) CatchProjNode(catch_, i+1,handler_bci));
    // This handler cannot happen?
    if (ctrl == top())  continue;
    set_control(ctrl);

    // Create exception oop
    const TypeInstPtr* extype = extypes->at(i)->is_instptr();
    Node *ex_oop = _gvn.transform(new (C, 2) CreateExNode(extypes->at(i), ctrl, i_o));

    // Handle unloaded exception classes.
    if (saw_unloaded->contains(handler_bci)) {
      // An unloaded exception type is coming here.  Do an uncommon trap.
#ifndef PRODUCT
      // We do not expect the same handler bci to take both cold unloaded
      // and hot loaded exceptions.  But, watch for it.
      if (extype->is_loaded()) {
        tty->print_cr("Warning: Handler @%d takes mixed loaded/unloaded exceptions in ");
        method()->print_name(); tty->cr();
      } else if (PrintOpto && (Verbose || WizardMode)) {
        tty->print("Bailing out on unloaded exception type ");
        extype->klass()->print_name();
        tty->print(" at bci:%d in ", bci());
        method()->print_name(); tty->cr();
      }
#endif
      // Emit an uncommon trap instead of processing the block.
      set_bci(handler_bci);
      push_ex_oop(ex_oop);
      uncommon_trap(Deoptimization::Reason_unloaded,
                    Deoptimization::Action_reinterpret,
                    extype->klass(), "!loaded exception");
      set_bci(iter().cur_bci()); // put it back
      continue;
    }

    // go to the exception handler
    if (handler_bci < 0) {     // merge with corresponding rethrow node
      throw_to_exit(make_exception_state(ex_oop));
    } else {                      // Else jump to corresponding handle
      push_ex_oop(ex_oop);        // Clear stack and push just the oop.
      merge_exception(handler_bci);
    }
  }

  // The first CatchProj is for the normal return.
  // (Note:  If this is a call to rethrow_Java, this node goes dead.)
  set_control(_gvn.transform( new (C, 1) CatchProjNode(catch_, CatchProjNode::fall_through_index, CatchProjNode::no_handler_bci)));
}


//----------------------------catch_inline_exceptions--------------------------
// Handle all exceptions thrown by an inlined method or individual bytecode.
// Common case 1: we have no handler, so all exceptions merge right into
// the rethrow case.
// Case 2: we have some handlers, with loaded exception klasses that have
// no subklasses.  We do a Deutsch-Shiffman style type-check on the incoming
// exception oop and branch to the handler directly.
// Case 3: We have some handlers with subklasses or are not loaded at
// compile-time.  We have to call the runtime to resolve the exception.
// So we insert a RethrowCall and all the logic that goes with it.
void Parse::catch_inline_exceptions(SafePointNode* ex_map) {
  // Caller is responsible for saving away the map for normal control flow!
  assert(stopped(), "call set_map(NULL) first");
  assert(method()->has_exception_handlers(), "don't come here w/o work to do");

  Node* ex_node = saved_ex_oop(ex_map);
  if (ex_node == top()) {
    // No action needed.
    return;
  }
  const TypeInstPtr* ex_type = _gvn.type(ex_node)->isa_instptr();
  NOT_PRODUCT(if (ex_type==NULL) tty->print_cr("*** Exception not InstPtr"));
  if (ex_type == NULL)
    ex_type = TypeOopPtr::make_from_klass(env()->Throwable_klass())->is_instptr();

  // determine potential exception handlers
  ciExceptionHandlerStream handlers(method(), bci(),
                                    ex_type->klass()->as_instance_klass(),
                                    ex_type->klass_is_exact());

  // Start executing from the given throw state.  (Keep its stack, for now.)
  // Get the exception oop as known at compile time.
  ex_node = use_exception_state(ex_map);

  // Get the exception oop klass from its header
  Node* ex_klass_node = NULL;
  if (has_ex_handler() && !ex_type->klass_is_exact()) {
    Node* p = basic_plus_adr( ex_node, ex_node, oopDesc::klass_offset_in_bytes());
    ex_klass_node = _gvn.transform( LoadKlassNode::make(_gvn, immutable_memory(), p, TypeInstPtr::KLASS, TypeKlassPtr::OBJECT) );

    // Compute the exception klass a little more cleverly.
    // Obvious solution is to simple do a LoadKlass from the 'ex_node'.
    // However, if the ex_node is a PhiNode, I'm going to do a LoadKlass for
    // each arm of the Phi.  If I know something clever about the exceptions
    // I'm loading the class from, I can replace the LoadKlass with the
    // klass constant for the exception oop.
    if( ex_node->is_Phi() ) {
      ex_klass_node = new (C, ex_node->req()) PhiNode( ex_node->in(0), TypeKlassPtr::OBJECT );
      for( uint i = 1; i < ex_node->req(); i++ ) {
        Node* p = basic_plus_adr( ex_node->in(i), ex_node->in(i), oopDesc::klass_offset_in_bytes() );
        Node* k = _gvn.transform( LoadKlassNode::make(_gvn, immutable_memory(), p, TypeInstPtr::KLASS, TypeKlassPtr::OBJECT) );
        ex_klass_node->init_req( i, k );
      }
      _gvn.set_type(ex_klass_node, TypeKlassPtr::OBJECT);

    }
  }

  // Scan the exception table for applicable handlers.
  // If none, we can call rethrow() and be done!
  // If precise (loaded with no subklasses), insert a D.S. style
  // pointer compare to the correct handler and loop back.
  // If imprecise, switch to the Rethrow VM-call style handling.

  int remaining = handlers.count_remaining();

  // iterate through all entries sequentially
  for (;!handlers.is_done(); handlers.next()) {
    // Do nothing if turned off
    if( !DeutschShiffmanExceptions ) break;
    ciExceptionHandler* handler = handlers.handler();

    if (handler->is_rethrow()) {
      // If we fell off the end of the table without finding an imprecise
      // exception klass (and without finding a generic handler) then we
      // know this exception is not handled in this method.  We just rethrow
      // the exception into the caller.
      throw_to_exit(make_exception_state(ex_node));
      return;
    }

    // exception handler bci range covers throw_bci => investigate further
    int handler_bci = handler->handler_bci();

    if (remaining == 1) {
      push_ex_oop(ex_node);        // Push exception oop for handler
#ifndef PRODUCT
      if (PrintOpto && WizardMode) {
        tty->print_cr("  Catching every inline exception bci:%d -> handler_bci:%d", bci(), handler_bci);
      }
#endif
      merge_exception(handler_bci); // jump to handler
      return;                   // No more handling to be done here!
    }

    // %%% The following logic replicates make_from_klass_unique.
    // TO DO:  Replace by a subroutine call.  Then generalize
    // the type check, as noted in the next "%%%" comment.

    ciInstanceKlass* klass = handler->catch_klass();
    if (UseUniqueSubclasses) {
      // (We use make_from_klass because it respects UseUniqueSubclasses.)
      const TypeOopPtr* tp = TypeOopPtr::make_from_klass(klass);
      klass = tp->klass()->as_instance_klass();
    }

    // Get the handler's klass
    if (!klass->is_loaded())    // klass is not loaded?
      break;                    // Must call Rethrow!
    if (klass->is_interface())  // should not happen, but...
      break;                    // bail out
    // See if the loaded exception klass has no subtypes
    if (klass->has_subklass())
      break;                    // Cannot easily do precise test ==> Rethrow

    // %%% Now that subclass checking is very fast, we need to rewrite
    // this section and remove the option "DeutschShiffmanExceptions".
    // The exception processing chain should be a normal typecase pattern,
    // with a bailout to the interpreter only in the case of unloaded
    // classes.  (The bailout should mark the method non-entrant.)
    // This rewrite should be placed in GraphKit::, not Parse::.

    // Add a dependence; if any subclass added we need to recompile
    // %%% should use stronger assert_unique_concrete_subtype instead
    if (!klass->is_final()) {
      C->dependencies()->assert_leaf_type(klass);
    }

    // Implement precise test
    const TypeKlassPtr *tk = TypeKlassPtr::make(klass);
    Node* con = _gvn.makecon(tk);
    Node* cmp = _gvn.transform( new (C, 3) CmpPNode(ex_klass_node, con) );
    Node* bol = _gvn.transform( new (C, 2) BoolNode(cmp, BoolTest::ne) );
    { BuildCutout unless(this, bol, PROB_LIKELY(0.7f));
      const TypeInstPtr* tinst = TypeInstPtr::make_exact(TypePtr::NotNull, klass);
      Node* ex_oop = _gvn.transform(new (C, 2) CheckCastPPNode(control(), ex_node, tinst));
      push_ex_oop(ex_oop);      // Push exception oop for handler
#ifndef PRODUCT
      if (PrintOpto && WizardMode) {
        tty->print("  Catching inline exception bci:%d -> handler_bci:%d -- ", bci(), handler_bci);
        klass->print_name();
        tty->cr();
      }
#endif
      merge_exception(handler_bci);
    }

    // Come here if exception does not match handler.
    // Carry on with more handler checks.
    --remaining;
  }

  assert(!stopped(), "you should return if you finish the chain");

  if (remaining == 1) {
    // Further checks do not matter.
  }

  if (can_rerun_bytecode()) {
    // Do not push_ex_oop here!
    // Re-executing the bytecode will reproduce the throwing condition.
    bool must_throw = true;
    uncommon_trap(Deoptimization::Reason_unhandled,
                  Deoptimization::Action_none,
                  (ciKlass*)NULL, (const char*)NULL, // default args
                  must_throw);
    return;
  }

  // Oops, need to call into the VM to resolve the klasses at runtime.
  // Note:  This call must not deoptimize, since it is not a real at this bci!
  kill_dead_locals();

  make_runtime_call(RC_NO_LEAF | RC_MUST_THROW,
                    OptoRuntime::rethrow_Type(),
                    OptoRuntime::rethrow_stub(),
                    NULL, NULL,
                    ex_node);

  // Rethrow is a pure call, no side effects, only a result.
  // The result cannot be allocated, so we use I_O

  // Catch exceptions from the rethrow
  catch_call_exceptions(handlers);
}


// (Note:  Moved add_debug_info into GraphKit::add_safepoint_edges.)


#ifndef PRODUCT
void Parse::count_compiled_calls(bool at_method_entry, bool is_inline) {
  if( CountCompiledCalls ) {
    if( at_method_entry ) {
      // bump invocation counter if top method (for statistics)
      if (CountCompiledCalls && depth() == 1) {
        const TypeInstPtr* addr_type = TypeInstPtr::make(method());
        Node* adr1 = makecon(addr_type);
        Node* adr2 = basic_plus_adr(adr1, adr1, in_bytes(methodOopDesc::compiled_invocation_counter_offset()));
        increment_counter(adr2);
      }
    } else if (is_inline) {
      switch (bc()) {
      case Bytecodes::_invokevirtual:   increment_counter(SharedRuntime::nof_inlined_calls_addr()); break;
      case Bytecodes::_invokeinterface: increment_counter(SharedRuntime::nof_inlined_interface_calls_addr()); break;
      case Bytecodes::_invokestatic:
      case Bytecodes::_invokedynamic:
      case Bytecodes::_invokespecial:   increment_counter(SharedRuntime::nof_inlined_static_calls_addr()); break;
      default: fatal("unexpected call bytecode");
      }
    } else {
      switch (bc()) {
      case Bytecodes::_invokevirtual:   increment_counter(SharedRuntime::nof_normal_calls_addr()); break;
      case Bytecodes::_invokeinterface: increment_counter(SharedRuntime::nof_interface_calls_addr()); break;
      case Bytecodes::_invokestatic:
      case Bytecodes::_invokedynamic:
      case Bytecodes::_invokespecial:   increment_counter(SharedRuntime::nof_static_calls_addr()); break;
      default: fatal("unexpected call bytecode");
      }
    }
  }
}
#endif //PRODUCT


// Identify possible target method and inlining style
ciMethod* Parse::optimize_inlining(ciMethod* caller, int bci, ciInstanceKlass* klass,
                                   ciMethod *dest_method, const TypeOopPtr* receiver_type) {
  // only use for virtual or interface calls

  // If it is obviously final, do not bother to call find_monomorphic_target,
  // because the class hierarchy checks are not needed, and may fail due to
  // incompletely loaded classes.  Since we do our own class loading checks
  // in this module, we may confidently bind to any method.
  if (dest_method->can_be_statically_bound()) {
    return dest_method;
  }

  // Attempt to improve the receiver
  bool actual_receiver_is_exact = false;
  ciInstanceKlass* actual_receiver = klass;
  if (receiver_type != NULL) {
    // Array methods are all inherited from Object, and are monomorphic.
    if (receiver_type->isa_aryptr() &&
        dest_method->holder() == env()->Object_klass()) {
      return dest_method;
    }

    // All other interesting cases are instance klasses.
    if (!receiver_type->isa_instptr()) {
      return NULL;
    }

    ciInstanceKlass *ikl = receiver_type->klass()->as_instance_klass();
    if (ikl->is_loaded() && ikl->is_initialized() && !ikl->is_interface() &&
        (ikl == actual_receiver || ikl->is_subtype_of(actual_receiver))) {
      // ikl is a same or better type than the original actual_receiver,
      // e.g. static receiver from bytecodes.
      actual_receiver = ikl;
      // Is the actual_receiver exact?
      actual_receiver_is_exact = receiver_type->klass_is_exact();
    }
  }

  ciInstanceKlass*   calling_klass = caller->holder();
  ciMethod* cha_monomorphic_target = dest_method->find_monomorphic_target(calling_klass, klass, actual_receiver);
  if (cha_monomorphic_target != NULL) {
    assert(!cha_monomorphic_target->is_abstract(), "");
    // Look at the method-receiver type.  Does it add "too much information"?
    ciKlass*    mr_klass = cha_monomorphic_target->holder();
    const Type* mr_type  = TypeInstPtr::make(TypePtr::BotPTR, mr_klass);
    if (receiver_type == NULL || !receiver_type->higher_equal(mr_type)) {
      // Calling this method would include an implicit cast to its holder.
      // %%% Not yet implemented.  Would throw minor asserts at present.
      // %%% The most common wins are already gained by +UseUniqueSubclasses.
      // To fix, put the higher_equal check at the call of this routine,
      // and add a CheckCastPP to the receiver.
      if (TraceDependencies) {
        tty->print_cr("found unique CHA method, but could not cast up");
        tty->print("  method  = ");
        cha_monomorphic_target->print();
        tty->cr();
      }
      if (C->log() != NULL) {
        C->log()->elem("missed_CHA_opportunity klass='%d' method='%d'",
                       C->log()->identify(klass),
                       C->log()->identify(cha_monomorphic_target));
      }
      cha_monomorphic_target = NULL;
    }
  }
  if (cha_monomorphic_target != NULL) {
    // Hardwiring a virtual.
    // If we inlined because CHA revealed only a single target method,
    // then we are dependent on that target method not getting overridden
    // by dynamic class loading.  Be sure to test the "static" receiver
    // dest_method here, as opposed to the actual receiver, which may
    // falsely lead us to believe that the receiver is final or private.
    C->dependencies()->assert_unique_concrete_method(actual_receiver, cha_monomorphic_target);
    return cha_monomorphic_target;
  }

  // If the type is exact, we can still bind the method w/o a vcall.
  // (This case comes after CHA so we can see how much extra work it does.)
  if (actual_receiver_is_exact) {
    // In case of evolution, there is a dependence on every inlined method, since each
    // such method can be changed when its class is redefined.
    ciMethod* exact_method = dest_method->resolve_invoke(calling_klass, actual_receiver);
    if (exact_method != NULL) {
#ifndef PRODUCT
      if (PrintOpto) {
        tty->print("  Calling method via exact type @%d --- ", bci);
        exact_method->print_name();
        tty->cr();
      }
#endif
      return exact_method;
    }
  }

  return NULL;
}
