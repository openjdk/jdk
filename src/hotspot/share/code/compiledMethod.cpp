/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compiledIC.hpp"
#include "code/compiledMethod.inline.hpp"
#include "code/scopeDesc.hpp"
#include "code/codeCache.hpp"
#include "interpreter/bytecode.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "memory/resourceArea.hpp"
#include "oops/methodData.hpp"
#include "oops/method.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"

CompiledMethod::CompiledMethod(Method* method, const char* name, CompilerType type, const CodeBlobLayout& layout, int frame_complete_offset, int frame_size, ImmutableOopMapSet* oop_maps, bool caller_must_gc_arguments)
  : CodeBlob(name, type, layout, frame_complete_offset, frame_size, oop_maps, caller_must_gc_arguments),
  _method(method), _mark_for_deoptimization_status(not_marked) {
  init_defaults();
}

CompiledMethod::CompiledMethod(Method* method, const char* name, CompilerType type, int size, int header_size, CodeBuffer* cb, int frame_complete_offset, int frame_size, OopMapSet* oop_maps, bool caller_must_gc_arguments)
  : CodeBlob(name, type, CodeBlobLayout((address) this, size, header_size, cb), cb, frame_complete_offset, frame_size, oop_maps, caller_must_gc_arguments),
  _method(method), _mark_for_deoptimization_status(not_marked) {
  init_defaults();
}

void CompiledMethod::init_defaults() {
  _has_unsafe_access          = 0;
  _has_method_handle_invokes  = 0;
  _lazy_critical_native       = 0;
  _has_wide_vectors           = 0;
  _unloading_clock            = 0;
}

bool CompiledMethod::is_method_handle_return(address return_pc) {
  if (!has_method_handle_invokes())  return false;
  PcDesc* pd = pc_desc_at(return_pc);
  if (pd == NULL)
    return false;
  return pd->is_method_handle_invoke();
}

// Returns a string version of the method state.
const char* CompiledMethod::state() const {
  int state = get_state();
  switch (state) {
  case not_installed:
    return "not installed";
  case in_use:
    return "in use";
  case not_used:
    return "not_used";
  case not_entrant:
    return "not_entrant";
  case zombie:
    return "zombie";
  case unloaded:
    return "unloaded";
  default:
    fatal("unexpected method state: %d", state);
    return NULL;
  }
}

//-----------------------------------------------------------------------------

void CompiledMethod::add_exception_cache_entry(ExceptionCache* new_entry) {
  assert(ExceptionCache_lock->owned_by_self(),"Must hold the ExceptionCache_lock");
  assert(new_entry != NULL,"Must be non null");
  assert(new_entry->next() == NULL, "Must be null");

  ExceptionCache *ec = exception_cache();
  if (ec != NULL) {
    new_entry->set_next(ec);
  }
  release_set_exception_cache(new_entry);
}

void CompiledMethod::clean_exception_cache() {
  ExceptionCache* prev = NULL;
  ExceptionCache* curr = exception_cache();

  while (curr != NULL) {
    ExceptionCache* next = curr->next();

    Klass* ex_klass = curr->exception_type();
    if (ex_klass != NULL && !ex_klass->is_loader_alive()) {
      if (prev == NULL) {
        set_exception_cache(next);
      } else {
        prev->set_next(next);
      }
      delete curr;
      // prev stays the same.
    } else {
      prev = curr;
    }

    curr = next;
  }
}

// public method for accessing the exception cache
// These are the public access methods.
address CompiledMethod::handler_for_exception_and_pc(Handle exception, address pc) {
  // We never grab a lock to read the exception cache, so we may
  // have false negatives. This is okay, as it can only happen during
  // the first few exception lookups for a given nmethod.
  ExceptionCache* ec = exception_cache();
  while (ec != NULL) {
    address ret_val;
    if ((ret_val = ec->match(exception,pc)) != NULL) {
      return ret_val;
    }
    ec = ec->next();
  }
  return NULL;
}

void CompiledMethod::add_handler_for_exception_and_pc(Handle exception, address pc, address handler) {
  // There are potential race conditions during exception cache updates, so we
  // must own the ExceptionCache_lock before doing ANY modifications. Because
  // we don't lock during reads, it is possible to have several threads attempt
  // to update the cache with the same data. We need to check for already inserted
  // copies of the current data before adding it.

  MutexLocker ml(ExceptionCache_lock);
  ExceptionCache* target_entry = exception_cache_entry_for_exception(exception);

  if (target_entry == NULL || !target_entry->add_address_and_handler(pc,handler)) {
    target_entry = new ExceptionCache(exception,pc,handler);
    add_exception_cache_entry(target_entry);
  }
}

//-------------end of code for ExceptionCache--------------

// private method for handling exception cache
// These methods are private, and used to manipulate the exception cache
// directly.
ExceptionCache* CompiledMethod::exception_cache_entry_for_exception(Handle exception) {
  ExceptionCache* ec = exception_cache();
  while (ec != NULL) {
    if (ec->match_exception_with_space(exception)) {
      return ec;
    }
    ec = ec->next();
  }
  return NULL;
}

bool CompiledMethod::is_at_poll_return(address pc) {
  RelocIterator iter(this, pc, pc+1);
  while (iter.next()) {
    if (iter.type() == relocInfo::poll_return_type)
      return true;
  }
  return false;
}


bool CompiledMethod::is_at_poll_or_poll_return(address pc) {
  RelocIterator iter(this, pc, pc+1);
  while (iter.next()) {
    relocInfo::relocType t = iter.type();
    if (t == relocInfo::poll_return_type || t == relocInfo::poll_type)
      return true;
  }
  return false;
}

void CompiledMethod::verify_oop_relocations() {
  // Ensure sure that the code matches the current oop values
  RelocIterator iter(this, NULL, NULL);
  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type) {
      oop_Relocation* reloc = iter.oop_reloc();
      if (!reloc->oop_is_immediate()) {
        reloc->verify_oop_relocation();
      }
    }
  }
}


ScopeDesc* CompiledMethod::scope_desc_at(address pc) {
  PcDesc* pd = pc_desc_at(pc);
  guarantee(pd != NULL, "scope must be present");
  return new ScopeDesc(this, pd->scope_decode_offset(),
                       pd->obj_decode_offset(), pd->should_reexecute(), pd->rethrow_exception(),
                       pd->return_oop());
}

ScopeDesc* CompiledMethod::scope_desc_near(address pc) {
  PcDesc* pd = pc_desc_near(pc);
  guarantee(pd != NULL, "scope must be present");
  return new ScopeDesc(this, pd->scope_decode_offset(),
                       pd->obj_decode_offset(), pd->should_reexecute(), pd->rethrow_exception(),
                       pd->return_oop());
}

address CompiledMethod::oops_reloc_begin() const {
  // If the method is not entrant or zombie then a JMP is plastered over the
  // first few bytes.  If an oop in the old code was there, that oop
  // should not get GC'd.  Skip the first few bytes of oops on
  // not-entrant methods.
  address low_boundary = verified_entry_point();
  if (!is_in_use() && is_nmethod()) {
    low_boundary += NativeJump::instruction_size;
    // %%% Note:  On SPARC we patch only a 4-byte trap, not a full NativeJump.
    // This means that the low_boundary is going to be a little too high.
    // This shouldn't matter, since oops of non-entrant methods are never used.
    // In fact, why are we bothering to look at oops in a non-entrant method??
  }
  return low_boundary;
}

int CompiledMethod::verify_icholder_relocations() {
  ResourceMark rm;
  int count = 0;

  RelocIterator iter(this);
  while(iter.next()) {
    if (iter.type() == relocInfo::virtual_call_type) {
      if (CompiledIC::is_icholder_call_site(iter.virtual_call_reloc(), this)) {
        CompiledIC *ic = CompiledIC_at(&iter);
        if (TraceCompiledIC) {
          tty->print("noticed icholder " INTPTR_FORMAT " ", p2i(ic->cached_icholder()));
          ic->print();
        }
        assert(ic->cached_icholder() != NULL, "must be non-NULL");
        count++;
      }
    }
  }

  return count;
}

// Method that knows how to preserve outgoing arguments at call. This method must be
// called with a frame corresponding to a Java invoke
void CompiledMethod::preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, OopClosure* f) {
  if (method() != NULL && !method()->is_native()) {
    address pc = fr.pc();
    SimpleScopeDesc ssd(this, pc);
    Bytecode_invoke call(ssd.method(), ssd.bci());
    bool has_receiver = call.has_receiver();
    bool has_appendix = call.has_appendix();
    Symbol* signature = call.signature();

    // The method attached by JIT-compilers should be used, if present.
    // Bytecode can be inaccurate in such case.
    Method* callee = attached_method_before_pc(pc);
    if (callee != NULL) {
      has_receiver = !(callee->access_flags().is_static());
      has_appendix = false;
      signature = callee->signature();
    }

    fr.oops_compiled_arguments_do(signature, has_receiver, has_appendix, reg_map, f);
  }
}

Method* CompiledMethod::attached_method(address call_instr) {
  assert(code_contains(call_instr), "not part of the nmethod");
  RelocIterator iter(this, call_instr, call_instr + 1);
  while (iter.next()) {
    if (iter.addr() == call_instr) {
      switch(iter.type()) {
        case relocInfo::static_call_type:      return iter.static_call_reloc()->method_value();
        case relocInfo::opt_virtual_call_type: return iter.opt_virtual_call_reloc()->method_value();
        case relocInfo::virtual_call_type:     return iter.virtual_call_reloc()->method_value();
        default:                               break;
      }
    }
  }
  return NULL; // not found
}

Method* CompiledMethod::attached_method_before_pc(address pc) {
  if (NativeCall::is_call_before(pc)) {
    NativeCall* ncall = nativeCall_before(pc);
    return attached_method(ncall->instruction_address());
  }
  return NULL; // not a call
}

void CompiledMethod::clear_inline_caches() {
  assert(SafepointSynchronize::is_at_safepoint(), "cleaning of IC's only allowed at safepoint");
  if (is_zombie()) {
    return;
  }

  RelocIterator iter(this);
  while (iter.next()) {
    iter.reloc()->clear_inline_cache();
  }
}

// Clear ICStubs of all compiled ICs
void CompiledMethod::clear_ic_stubs() {
  assert_locked_or_safepoint(CompiledIC_lock);
  ResourceMark rm;
  RelocIterator iter(this);
  while(iter.next()) {
    if (iter.type() == relocInfo::virtual_call_type) {
      CompiledIC* ic = CompiledIC_at(&iter);
      ic->clear_ic_stub();
    }
  }
}

#ifdef ASSERT
// Check class_loader is alive for this bit of metadata.
static void check_class(Metadata* md) {
   Klass* klass = NULL;
   if (md->is_klass()) {
     klass = ((Klass*)md);
   } else if (md->is_method()) {
     klass = ((Method*)md)->method_holder();
   } else if (md->is_methodData()) {
     klass = ((MethodData*)md)->method()->method_holder();
   } else {
     md->print();
     ShouldNotReachHere();
   }
   assert(klass->is_loader_alive(), "must be alive");
}
#endif // ASSERT


void CompiledMethod::clean_ic_if_metadata_is_dead(CompiledIC *ic) {
  if (ic->is_icholder_call()) {
    // The only exception is compiledICHolder metdata which may
    // yet be marked below. (We check this further below).
    CompiledICHolder* cichk_metdata = ic->cached_icholder();

    if (cichk_metdata->is_loader_alive()) {
      return;
    }
  } else {
    Metadata* ic_metdata = ic->cached_metadata();
    if (ic_metdata != NULL) {
      if (ic_metdata->is_klass()) {
        if (((Klass*)ic_metdata)->is_loader_alive()) {
          return;
        }
      } else if (ic_metdata->is_method()) {
        Method* method = (Method*)ic_metdata;
        assert(!method->is_old(), "old method should have been cleaned");
        if (method->method_holder()->is_loader_alive()) {
          return;
        }
      } else {
        ShouldNotReachHere();
      }
    }
  }

  ic->set_to_clean();
}

unsigned char CompiledMethod::_global_unloading_clock = 0;

void CompiledMethod::increase_unloading_clock() {
  _global_unloading_clock++;
  if (_global_unloading_clock == 0) {
    // _nmethods are allocated with _unloading_clock == 0,
    // so 0 is never used as a clock value.
    _global_unloading_clock = 1;
  }
}

void CompiledMethod::set_unloading_clock(unsigned char unloading_clock) {
  OrderAccess::release_store(&_unloading_clock, unloading_clock);
}

unsigned char CompiledMethod::unloading_clock() {
  return OrderAccess::load_acquire(&_unloading_clock);
}


// static_stub_Relocations may have dangling references to
// nmethods so trim them out here.  Otherwise it looks like
// compiled code is maintaining a link to dead metadata.
void CompiledMethod::clean_ic_stubs() {
#ifdef ASSERT
  address low_boundary = oops_reloc_begin();
  RelocIterator iter(this, low_boundary);
  while (iter.next()) {
    address static_call_addr = NULL;
    if (iter.type() == relocInfo::opt_virtual_call_type) {
      CompiledIC* cic = CompiledIC_at(&iter);
      if (!cic->is_call_to_interpreted()) {
        static_call_addr = iter.addr();
      }
    } else if (iter.type() == relocInfo::static_call_type) {
      CompiledStaticCall* csc = compiledStaticCall_at(iter.reloc());
      if (!csc->is_call_to_interpreted()) {
        static_call_addr = iter.addr();
      }
    }
    if (static_call_addr != NULL) {
      RelocIterator sciter(this, low_boundary);
      while (sciter.next()) {
        if (sciter.type() == relocInfo::static_stub_type &&
            sciter.static_stub_reloc()->static_call() == static_call_addr) {
          sciter.static_stub_reloc()->clear_inline_cache();
        }
      }
    }
  }
#endif
}

// This is called at the end of the strong tracing/marking phase of a
// GC to unload an nmethod if it contains otherwise unreachable
// oops.

void CompiledMethod::do_unloading(BoolObjectClosure* is_alive) {
  // Make sure the oop's ready to receive visitors
  assert(!is_zombie() && !is_unloaded(),
         "should not call follow on zombie or unloaded nmethod");

  address low_boundary = oops_reloc_begin();

  if (do_unloading_oops(low_boundary, is_alive)) {
    return;
  }

#if INCLUDE_JVMCI
  if (do_unloading_jvmci()) {
    return;
  }
#endif

  // Cleanup exception cache and inline caches happens
  // after all the unloaded methods are found.
}

// Clean references to unloaded nmethods at addr from this one, which is not unloaded.
template <class CompiledICorStaticCall>
static bool clean_if_nmethod_is_unloaded(CompiledICorStaticCall *ic, address addr, CompiledMethod* from,
                                         bool parallel, bool clean_all) {
  // Ok, to lookup references to zombies here
  CodeBlob *cb = CodeCache::find_blob_unsafe(addr);
  CompiledMethod* nm = (cb != NULL) ? cb->as_compiled_method_or_null() : NULL;
  if (nm != NULL) {
    if (parallel && nm->unloading_clock() != CompiledMethod::global_unloading_clock()) {
      // The nmethod has not been processed yet.
      return true;
    }

    // Clean inline caches pointing to both zombie and not_entrant methods
    if (clean_all || !nm->is_in_use() || (nm->method()->code() != nm)) {
      ic->set_to_clean(from->is_alive());
      assert(ic->is_clean(), "nmethod " PTR_FORMAT "not clean %s", p2i(from), from->method()->name_and_sig_as_C_string());
    }
  }

  return false;
}

static bool clean_if_nmethod_is_unloaded(CompiledIC *ic, CompiledMethod* from,
                                         bool parallel, bool clean_all = false) {
  return clean_if_nmethod_is_unloaded(ic, ic->ic_destination(), from, parallel, clean_all);
}

static bool clean_if_nmethod_is_unloaded(CompiledStaticCall *csc, CompiledMethod* from,
                                         bool parallel, bool clean_all = false) {
  return clean_if_nmethod_is_unloaded(csc, csc->destination(), from, parallel, clean_all);
}

bool CompiledMethod::do_unloading_parallel(BoolObjectClosure* is_alive, bool unloading_occurred) {
  ResourceMark rm;

  // Make sure the oop's ready to receive visitors
  assert(!is_zombie() && !is_unloaded(),
         "should not call follow on zombie or unloaded nmethod");

  address low_boundary = oops_reloc_begin();

  if (do_unloading_oops(low_boundary, is_alive)) {
    return false;
  }

#if INCLUDE_JVMCI
  if (do_unloading_jvmci()) {
    return false;
  }
#endif

  return unload_nmethod_caches(/*parallel*/true, unloading_occurred);
}

// Cleans caches in nmethods that point to either classes that are unloaded
// or nmethods that are unloaded.
//
// Can be called either in parallel by G1 currently or after all
// nmethods are unloaded.  Return postponed=true in the parallel case for
// inline caches found that point to nmethods that are not yet visited during
// the do_unloading walk.
bool CompiledMethod::unload_nmethod_caches(bool parallel, bool unloading_occurred) {

  // Exception cache only needs to be called if unloading occurred
  if (unloading_occurred) {
    clean_exception_cache();
  }

  bool postponed = cleanup_inline_caches_impl(parallel, unloading_occurred, /*clean_all*/false);

  // All static stubs need to be cleaned.
  clean_ic_stubs();

  // Check that the metadata embedded in the nmethod is alive
  DEBUG_ONLY(metadata_do(check_class));

  return postponed;
}

// Called to clean up after class unloading for live nmethods and from the sweeper
// for all methods.
bool CompiledMethod::cleanup_inline_caches_impl(bool parallel, bool unloading_occurred, bool clean_all) {
  assert_locked_or_safepoint(CompiledIC_lock);
  bool postponed = false;
  ResourceMark rm;

  // Find all calls in an nmethod and clear the ones that point to non-entrant,
  // zombie and unloaded nmethods.
  RelocIterator iter(this, oops_reloc_begin());
  while(iter.next()) {

    switch (iter.type()) {

    case relocInfo::virtual_call_type:
      if (unloading_occurred) {
        // If class unloading occurred we first clear ICs where the cached metadata
        // is referring to an unloaded klass or method.
        clean_ic_if_metadata_is_dead(CompiledIC_at(&iter));
      }

      postponed |= clean_if_nmethod_is_unloaded(CompiledIC_at(&iter), this, parallel, clean_all);
      break;

    case relocInfo::opt_virtual_call_type:
      postponed |= clean_if_nmethod_is_unloaded(CompiledIC_at(&iter), this, parallel, clean_all);
      break;

    case relocInfo::static_call_type:
      postponed |= clean_if_nmethod_is_unloaded(compiledStaticCall_at(iter.reloc()), this, parallel, clean_all);
      break;

    case relocInfo::oop_type:
      // handled by do_unloading_oops already
      break;

    case relocInfo::metadata_type:
      break; // nothing to do.

    default:
      break;
    }
  }

  return postponed;
}

void CompiledMethod::do_unloading_parallel_postponed() {
  ResourceMark rm;

  // Make sure the oop's ready to receive visitors
  assert(!is_zombie(),
         "should not call follow on zombie nmethod");

  RelocIterator iter(this, oops_reloc_begin());
  while(iter.next()) {

    switch (iter.type()) {

    case relocInfo::virtual_call_type:
      clean_if_nmethod_is_unloaded(CompiledIC_at(&iter), this, true);
      break;

    case relocInfo::opt_virtual_call_type:
      clean_if_nmethod_is_unloaded(CompiledIC_at(&iter), this, true);
      break;

    case relocInfo::static_call_type:
      clean_if_nmethod_is_unloaded(compiledStaticCall_at(iter.reloc()), this, true);
      break;

    default:
      break;
    }
  }
}
