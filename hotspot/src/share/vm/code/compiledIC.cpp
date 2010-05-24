/*
 * Copyright 1997-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_compiledIC.cpp.incl"


// Every time a compiled IC is changed or its type is being accessed,
// either the CompiledIC_lock must be set or we must be at a safe point.

//-----------------------------------------------------------------------------
// Low-level access to an inline cache. Private, since they might not be
// MT-safe to use.

void CompiledIC::set_cached_oop(oop cache) {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  assert (!is_optimized(), "an optimized virtual call does not have a cached oop");
  assert (cache == NULL || cache != badOop, "invalid oop");

  if (TraceCompiledIC) {
    tty->print("  ");
    print_compiled_ic();
    tty->print_cr(" changing oop to " INTPTR_FORMAT, (address)cache);
  }

  if (cache == NULL)  cache = (oop)Universe::non_oop_word();

  *_oop_addr = cache;
  // fix up the relocations
  RelocIterator iter = _oops;
  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type) {
      oop_Relocation* r = iter.oop_reloc();
      if (r->oop_addr() == _oop_addr)
        r->fix_oop_relocation();
    }
  }
  return;
}


oop CompiledIC::cached_oop() const {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  assert (!is_optimized(), "an optimized virtual call does not have a cached oop");

  if (!is_in_transition_state()) {
    oop data = *_oop_addr;
    // If we let the oop value here be initialized to zero...
    assert(data != NULL || Universe::non_oop_word() == NULL,
           "no raw nulls in CompiledIC oops, because of patching races");
    return (data == (oop)Universe::non_oop_word()) ? (oop)NULL : data;
  } else {
    return InlineCacheBuffer::cached_oop_for((CompiledIC *)this);
  }
}


void CompiledIC::set_ic_destination(address entry_point) {
  assert(entry_point != NULL, "must set legal entry point");
  assert(CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  if (TraceCompiledIC) {
    tty->print("  ");
    print_compiled_ic();
    tty->print_cr(" changing destination to " INTPTR_FORMAT, entry_point);
  }
  MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);
#ifdef ASSERT
  CodeBlob* cb = CodeCache::find_blob_unsafe(_ic_call);
  assert(cb != NULL && cb->is_nmethod(), "must be nmethod");
#endif
  _ic_call->set_destination_mt_safe(entry_point);
}


address CompiledIC::ic_destination() const {
 assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
 if (!is_in_transition_state()) {
   return _ic_call->destination();
 } else {
   return InlineCacheBuffer::ic_destination_for((CompiledIC *)this);
 }
}


bool CompiledIC::is_in_transition_state() const {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  return InlineCacheBuffer::contains(_ic_call->destination());
}


// Returns native address of 'call' instruction in inline-cache. Used by
// the InlineCacheBuffer when it needs to find the stub.
address CompiledIC::stub_address() const {
  assert(is_in_transition_state(), "should only be called when we are in a transition state");
  return _ic_call->destination();
}


//-----------------------------------------------------------------------------
// High-level access to an inline cache. Guaranteed to be MT-safe.


void CompiledIC::set_to_megamorphic(CallInfo* call_info, Bytecodes::Code bytecode, TRAPS) {
  methodHandle method = call_info->selected_method();
  bool is_invoke_interface = (bytecode == Bytecodes::_invokeinterface && !call_info->has_vtable_index());
  assert(CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  assert(method->is_oop(), "cannot be NULL and must be oop");
  assert(!is_optimized(), "cannot set an optimized virtual call to megamorphic");
  assert(is_call_to_compiled() || is_call_to_interpreted(), "going directly to megamorphic?");

  address entry;
  if (is_invoke_interface) {
    int index = klassItable::compute_itable_index(call_info->resolved_method()());
    entry = VtableStubs::create_stub(false, index, method());
    assert(entry != NULL, "entry not computed");
    klassOop k = call_info->resolved_method()->method_holder();
    assert(Klass::cast(k)->is_interface(), "sanity check");
    InlineCacheBuffer::create_transition_stub(this, k, entry);
  } else {
    // Can be different than method->vtable_index(), due to package-private etc.
    int vtable_index = call_info->vtable_index();
    entry = VtableStubs::create_stub(true, vtable_index, method());
    InlineCacheBuffer::create_transition_stub(this, method(), entry);
  }

  if (TraceICs) {
    ResourceMark rm;
    tty->print_cr ("IC@" INTPTR_FORMAT ": to megamorphic %s entry: " INTPTR_FORMAT,
                   instruction_address(), method->print_value_string(), entry);
  }

  Events::log("compiledIC " INTPTR_FORMAT " --> megamorphic " INTPTR_FORMAT, this, (address)method());
  // We can't check this anymore. With lazy deopt we could have already
  // cleaned this IC entry before we even return. This is possible if
  // we ran out of space in the inline cache buffer trying to do the
  // set_next and we safepointed to free up space. This is a benign
  // race because the IC entry was complete when we safepointed so
  // cleaning it immediately is harmless.
  // assert(is_megamorphic(), "sanity check");
}


// true if destination is megamorphic stub
bool CompiledIC::is_megamorphic() const {
  assert(CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  assert(!is_optimized(), "an optimized call cannot be megamorphic");

  // Cannot rely on cached_oop. It is either an interface or a method.
  return VtableStubs::is_entry_point(ic_destination());
}

bool CompiledIC::is_call_to_compiled() const {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");

  // Use unsafe, since an inline cache might point to a zombie method. However, the zombie
  // method is guaranteed to still exist, since we only remove methods after all inline caches
  // has been cleaned up
  CodeBlob* cb = CodeCache::find_blob_unsafe(ic_destination());
  bool is_monomorphic = (cb != NULL && cb->is_nmethod());
  // Check that the cached_oop is a klass for non-optimized monomorphic calls
  // This assertion is invalid for compiler1: a call that does not look optimized (no static stub) can be used
  // for calling directly to vep without using the inline cache (i.e., cached_oop == NULL)
#ifdef ASSERT
#ifdef TIERED
  CodeBlob* caller = CodeCache::find_blob_unsafe(instruction_address());
  bool is_c1_method = caller->is_compiled_by_c1();
#else
#ifdef COMPILER1
  bool is_c1_method = true;
#else
  bool is_c1_method = false;
#endif // COMPILER1
#endif // TIERED
  assert( is_c1_method ||
         !is_monomorphic ||
         is_optimized() ||
         (cached_oop() != NULL && cached_oop()->is_klass()), "sanity check");
#endif // ASSERT
  return is_monomorphic;
}


bool CompiledIC::is_call_to_interpreted() const {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  // Call to interpreter if destination is either calling to a stub (if it
  // is optimized), or calling to an I2C blob
  bool is_call_to_interpreted = false;
  if (!is_optimized()) {
    // must use unsafe because the destination can be a zombie (and we're cleaning)
    // and the print_compiled_ic code wants to know if site (in the non-zombie)
    // is to the interpreter.
    CodeBlob* cb = CodeCache::find_blob_unsafe(ic_destination());
    is_call_to_interpreted = (cb != NULL && cb->is_adapter_blob());
    assert(!is_call_to_interpreted ||  (cached_oop() != NULL && cached_oop()->is_compiledICHolder()), "sanity check");
  } else {
    // Check if we are calling into our own codeblob (i.e., to a stub)
    CodeBlob* cb = CodeCache::find_blob(_ic_call->instruction_address());
    address dest = ic_destination();
#ifdef ASSERT
    {
      CodeBlob* db = CodeCache::find_blob_unsafe(dest);
      assert(!db->is_adapter_blob(), "must use stub!");
    }
#endif /* ASSERT */
    is_call_to_interpreted = cb->contains(dest);
  }
  return is_call_to_interpreted;
}


void CompiledIC::set_to_clean() {
  assert(SafepointSynchronize::is_at_safepoint() || CompiledIC_lock->is_locked() , "MT-unsafe call");
  if (TraceInlineCacheClearing || TraceICs) {
    tty->print_cr("IC@" INTPTR_FORMAT ": set to clean", instruction_address());
    print();
  }

  address entry;
  if (is_optimized()) {
    entry = SharedRuntime::get_resolve_opt_virtual_call_stub();
  } else {
    entry = SharedRuntime::get_resolve_virtual_call_stub();
  }

  // A zombie transition will always be safe, since the oop has already been set to NULL, so
  // we only need to patch the destination
  bool safe_transition = is_optimized() || SafepointSynchronize::is_at_safepoint();

  if (safe_transition) {
    if (!is_optimized()) set_cached_oop(NULL);
    // Kill any leftover stub we might have too
    if (is_in_transition_state()) {
      ICStub* old_stub = ICStub_from_destination_address(stub_address());
      old_stub->clear();
    }
    set_ic_destination(entry);
  } else {
    // Unsafe transition - create stub.
    InlineCacheBuffer::create_transition_stub(this, NULL, entry);
  }
  // We can't check this anymore. With lazy deopt we could have already
  // cleaned this IC entry before we even return. This is possible if
  // we ran out of space in the inline cache buffer trying to do the
  // set_next and we safepointed to free up space. This is a benign
  // race because the IC entry was complete when we safepointed so
  // cleaning it immediately is harmless.
  // assert(is_clean(), "sanity check");
}


bool CompiledIC::is_clean() const {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  bool is_clean = false;
  address dest = ic_destination();
  is_clean = dest == SharedRuntime::get_resolve_opt_virtual_call_stub() ||
             dest == SharedRuntime::get_resolve_virtual_call_stub();
  assert(!is_clean || is_optimized() || cached_oop() == NULL, "sanity check");
  return is_clean;
}


void CompiledIC::set_to_monomorphic(const CompiledICInfo& info) {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "");
  // Updating a cache to the wrong entry can cause bugs that are very hard
  // to track down - if cache entry gets invalid - we just clean it. In
  // this way it is always the same code path that is responsible for
  // updating and resolving an inline cache
  //
  // The above is no longer true. SharedRuntime::fixup_callers_callsite will change optimized
  // callsites. In addition ic_miss code will update a site to monomorphic if it determines
  // that an monomorphic call to the interpreter can now be monomorphic to compiled code.
  //
  // In both of these cases the only thing being modifed is the jump/call target and these
  // transitions are mt_safe

  Thread *thread = Thread::current();
  if (info._to_interpreter) {
    // Call to interpreter
    if (info.is_optimized() && is_optimized()) {
       assert(is_clean(), "unsafe IC path");
       MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);
      // the call analysis (callee structure) specifies that the call is optimized
      // (either because of CHA or the static target is final)
      // At code generation time, this call has been emitted as static call
      // Call via stub
      assert(info.cached_oop().not_null() && info.cached_oop()->is_method(), "sanity check");
      CompiledStaticCall* csc = compiledStaticCall_at(instruction_address());
      methodHandle method (thread, (methodOop)info.cached_oop()());
      csc->set_to_interpreted(method, info.entry());
      if (TraceICs) {
         ResourceMark rm(thread);
         tty->print_cr ("IC@" INTPTR_FORMAT ": monomorphic to interpreter: %s",
           instruction_address(),
           method->print_value_string());
      }
    } else {
      // Call via method-klass-holder
      assert(info.cached_oop().not_null(), "must be set");
      InlineCacheBuffer::create_transition_stub(this, info.cached_oop()(), info.entry());

      if (TraceICs) {
         ResourceMark rm(thread);
         tty->print_cr ("IC@" INTPTR_FORMAT ": monomorphic to interpreter via mkh", instruction_address());
      }
    }
  } else {
    // Call to compiled code
    bool static_bound = info.is_optimized() || (info.cached_oop().is_null());
#ifdef ASSERT
    CodeBlob* cb = CodeCache::find_blob_unsafe(info.entry());
    assert (cb->is_nmethod(), "must be compiled!");
#endif /* ASSERT */

    // This is MT safe if we come from a clean-cache and go through a
    // non-verified entry point
    bool safe = SafepointSynchronize::is_at_safepoint() ||
                (!is_in_transition_state() && (info.is_optimized() || static_bound || is_clean()));

    if (!safe) {
      InlineCacheBuffer::create_transition_stub(this, info.cached_oop()(), info.entry());
    } else {
      set_ic_destination(info.entry());
      if (!is_optimized()) set_cached_oop(info.cached_oop()());
    }

    if (TraceICs) {
      ResourceMark rm(thread);
      assert(info.cached_oop() == NULL || info.cached_oop()()->is_klass(), "must be");
      tty->print_cr ("IC@" INTPTR_FORMAT ": monomorphic to compiled (rcvr klass) %s: %s",
        instruction_address(),
        ((klassOop)info.cached_oop()())->print_value_string(),
        (safe) ? "" : "via stub");
    }
  }
  // We can't check this anymore. With lazy deopt we could have already
  // cleaned this IC entry before we even return. This is possible if
  // we ran out of space in the inline cache buffer trying to do the
  // set_next and we safepointed to free up space. This is a benign
  // race because the IC entry was complete when we safepointed so
  // cleaning it immediately is harmless.
  // assert(is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}


// is_optimized: Compiler has generated an optimized call (i.e., no inline
// cache) static_bound: The call can be static bound (i.e, no need to use
// inline cache)
void CompiledIC::compute_monomorphic_entry(methodHandle method,
                                           KlassHandle receiver_klass,
                                           bool is_optimized,
                                           bool static_bound,
                                           CompiledICInfo& info,
                                           TRAPS) {
  info._is_optimized = is_optimized;

  nmethod* method_code = method->code();
  address entry = NULL;
  if (method_code != NULL) {
    // Call to compiled code
    if (static_bound || is_optimized) {
      entry      = method_code->verified_entry_point();
    } else {
      entry      = method_code->entry_point();
    }
  }
  if (entry != NULL) {
    // Call to compiled code
    info._entry      = entry;
    if (static_bound || is_optimized) {
      info._cached_oop = Handle(THREAD, (oop)NULL);
    } else {
      info._cached_oop = receiver_klass;
    }
    info._to_interpreter = false;
  } else {
    // Note: the following problem exists with Compiler1:
    //   - at compile time we may or may not know if the destination is final
    //   - if we know that the destination is final, we will emit an optimized
    //     virtual call (no inline cache), and need a methodOop to make a call
    //     to the interpreter
    //   - if we do not know if the destination is final, we emit a standard
    //     virtual call, and use CompiledICHolder to call interpreted code
    //     (no static call stub has been generated)
    //     However in that case we will now notice it is static_bound
    //     and convert the call into what looks to be an optimized
    //     virtual call. This causes problems in verifying the IC because
    //     it look vanilla but is optimized. Code in is_call_to_interpreted
    //     is aware of this and weakens its asserts.

    info._to_interpreter = true;
    // static_bound should imply is_optimized -- otherwise we have a
    // performance bug (statically-bindable method is called via
    // dynamically-dispatched call note: the reverse implication isn't
    // necessarily true -- the call may have been optimized based on compiler
    // analysis (static_bound is only based on "final" etc.)
#ifdef COMPILER2
#ifdef TIERED
#if defined(ASSERT)
    // can't check the assert because we don't have the CompiledIC with which to
    // find the address if the call instruction.
    //
    // CodeBlob* cb = find_blob_unsafe(instruction_address());
    // assert(cb->is_compiled_by_c1() || !static_bound || is_optimized, "static_bound should imply is_optimized");
#endif // ASSERT
#else
    assert(!static_bound || is_optimized, "static_bound should imply is_optimized");
#endif // TIERED
#endif // COMPILER2
    if (is_optimized) {
      // Use stub entry
      info._entry      = method()->get_c2i_entry();
      info._cached_oop = method;
    } else {
      // Use mkh entry
      oop holder = oopFactory::new_compiledICHolder(method, receiver_klass, CHECK);
      info._cached_oop = Handle(THREAD, holder);
      info._entry      = method()->get_c2i_unverified_entry();
    }
  }
}


inline static RelocIterator parse_ic(nmethod* nm, address ic_call, oop* &_oop_addr, bool *is_optimized) {
   address  first_oop = NULL;
   // Mergers please note: Sun SC5.x CC insists on an lvalue for a reference parameter.
   nmethod* tmp_nm = nm;
   return virtual_call_Relocation::parse_ic(tmp_nm, ic_call, first_oop, _oop_addr, is_optimized);
}

CompiledIC::CompiledIC(NativeCall* ic_call)
  : _ic_call(ic_call),
    _oops(parse_ic(NULL, ic_call->instruction_address(), _oop_addr, &_is_optimized))
{
}


CompiledIC::CompiledIC(Relocation* ic_reloc)
  : _ic_call(nativeCall_at(ic_reloc->addr())),
    _oops(parse_ic(ic_reloc->code(), ic_reloc->addr(), _oop_addr, &_is_optimized))
{
  assert(ic_reloc->type() == relocInfo::virtual_call_type ||
         ic_reloc->type() == relocInfo::opt_virtual_call_type, "wrong reloc. info");
}


// ----------------------------------------------------------------------------

void CompiledStaticCall::set_to_clean() {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "mt unsafe call");
  // Reset call site
  MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);
#ifdef ASSERT
  CodeBlob* cb = CodeCache::find_blob_unsafe(this);
  assert(cb != NULL && cb->is_nmethod(), "must be nmethod");
#endif
  set_destination_mt_safe(SharedRuntime::get_resolve_static_call_stub());

  // Do not reset stub here:  It is too expensive to call find_stub.
  // Instead, rely on caller (nmethod::clear_inline_caches) to clear
  // both the call and its stub.
}


bool CompiledStaticCall::is_clean() const {
  return destination() == SharedRuntime::get_resolve_static_call_stub();
}

bool CompiledStaticCall::is_call_to_compiled() const {
  return CodeCache::contains(destination());
}


bool CompiledStaticCall::is_call_to_interpreted() const {
  // It is a call to interpreted, if it calls to a stub. Hence, the destination
  // must be in the stub part of the nmethod that contains the call
  nmethod* nm = CodeCache::find_nmethod(instruction_address());
  return nm->stub_contains(destination());
}


void CompiledStaticCall::set_to_interpreted(methodHandle callee, address entry) {
  address stub=find_stub();
  assert(stub!=NULL, "stub not found");

  if (TraceICs) {
    ResourceMark rm;
    tty->print_cr("CompiledStaticCall@" INTPTR_FORMAT ": set_to_interpreted %s",
                  instruction_address(),
                  callee->name_and_sig_as_C_string());
  }

  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);   // creation also verifies the object
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());

  assert(method_holder->data()    == 0           || method_holder->data()    == (intptr_t)callee(), "a) MT-unsafe modification of inline cache");
  assert(jump->jump_destination() == (address)-1 || jump->jump_destination() == entry, "b) MT-unsafe modification of inline cache");

  // Update stub
  method_holder->set_data((intptr_t)callee());
  jump->set_jump_destination(entry);

  // Update jump to call
  set_destination_mt_safe(stub);
}


void CompiledStaticCall::set(const StaticCallInfo& info) {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "mt unsafe call");
  MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);
  // Updating a cache to the wrong entry can cause bugs that are very hard
  // to track down - if cache entry gets invalid - we just clean it. In
  // this way it is always the same code path that is responsible for
  // updating and resolving an inline cache
  assert(is_clean(), "do not update a call entry - use clean");

  if (info._to_interpreter) {
    // Call to interpreted code
    set_to_interpreted(info.callee(), info.entry());
  } else {
    if (TraceICs) {
      ResourceMark rm;
      tty->print_cr("CompiledStaticCall@" INTPTR_FORMAT ": set_to_compiled " INTPTR_FORMAT,
                    instruction_address(),
                    info.entry());
    }
    // Call to compiled code
    assert (CodeCache::contains(info.entry()), "wrong entry point");
    set_destination_mt_safe(info.entry());
  }
}


// Compute settings for a CompiledStaticCall. Since we might have to set
// the stub when calling to the interpreter, we need to return arguments.
void CompiledStaticCall::compute_entry(methodHandle m, StaticCallInfo& info) {
  nmethod* m_code = m->code();
  info._callee = m;
  if (m_code != NULL) {
    info._to_interpreter = false;
    info._entry  = m_code->verified_entry_point();
  } else {
    // Callee is interpreted code.  In any case entering the interpreter
    // puts a converter-frame on the stack to save arguments.
    info._to_interpreter = true;
    info._entry      = m()->get_c2i_entry();
  }
}


void CompiledStaticCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "mt unsafe call");
  // Reset stub
  address stub = static_stub->addr();
  assert(stub!=NULL, "stub not found");
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);   // creation also verifies the object
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());
  method_holder->set_data(0);
  jump->set_jump_destination((address)-1);
}


address CompiledStaticCall::find_stub() {
  // Find reloc. information containing this call-site
  RelocIterator iter((nmethod*)NULL, instruction_address());
  while (iter.next()) {
    if (iter.addr() == instruction_address()) {
      switch(iter.type()) {
        case relocInfo::static_call_type:
          return iter.static_call_reloc()->static_stub();
        // We check here for opt_virtual_call_type, since we reuse the code
        // from the CompiledIC implementation
        case relocInfo::opt_virtual_call_type:
          return iter.opt_virtual_call_reloc()->static_stub();
        case relocInfo::poll_type:
        case relocInfo::poll_return_type: // A safepoint can't overlap a call.
        default:
          ShouldNotReachHere();
      }
    }
  }
  return NULL;
}


//-----------------------------------------------------------------------------
// Non-product mode code
#ifndef PRODUCT

void CompiledIC::verify() {
  // make sure code pattern is actually a call imm32 instruction
  _ic_call->verify();
  if (os::is_MP()) {
    _ic_call->verify_alignment();
  }
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted()
          || is_optimized() || is_megamorphic(), "sanity check");
}


void CompiledIC::print() {
  print_compiled_ic();
  tty->cr();
}


void CompiledIC::print_compiled_ic() {
  tty->print("Inline cache at " INTPTR_FORMAT ", calling %s " INTPTR_FORMAT,
             instruction_address(), is_call_to_interpreted() ? "interpreted " : "", ic_destination());
}


void CompiledStaticCall::print() {
  tty->print("static call at " INTPTR_FORMAT " -> ", instruction_address());
  if (is_clean()) {
    tty->print("clean");
  } else if (is_call_to_compiled()) {
    tty->print("compiled");
  } else if (is_call_to_interpreted()) {
    tty->print("interpreted");
  }
  tty->cr();
}

void CompiledStaticCall::verify() {
  // Verify call
  NativeCall::verify();
  if (os::is_MP()) {
    verify_alignment();
  }

  // Verify stub
  address stub = find_stub();
  assert(stub != NULL, "no stub found for static call");
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);   // creation also verifies the object
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());

  // Verify state
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}

#endif
