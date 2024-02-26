/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeBehaviours.hpp"
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "code/vtableStubs.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compressedKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/continuationEntry.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "sanitizers/leak.hpp"


// Every time a compiled IC is changed or its type is being accessed,
// either the CompiledIC_lock must be set or we must be at a safe point.

CompiledICLocker::CompiledICLocker(CompiledMethod* method)
  : _method(method),
    _behaviour(CompiledICProtectionBehaviour::current()),
    _locked(_behaviour->lock(_method)) {
}

CompiledICLocker::~CompiledICLocker() {
  if (_locked) {
    _behaviour->unlock(_method);
  }
}

bool CompiledICLocker::is_safe(CompiledMethod* method) {
  return CompiledICProtectionBehaviour::current()->is_safe(method);
}

bool CompiledICLocker::is_safe(address code) {
  CodeBlob* cb = CodeCache::find_blob(code);
  assert(cb != nullptr && cb->is_compiled(), "must be compiled");
  CompiledMethod* cm = cb->as_compiled_method();
  return CompiledICProtectionBehaviour::current()->is_safe(cm);
}

CompiledICData::CompiledICData()
  : _speculated_method(),
    _speculated_klass(),
    _itable_defc_klass(),
    _itable_refc_klass(),
    _is_initialized() {}

// Inline cache callsite info is initialized once the first time it is resolved
void CompiledICData::initialize(CallInfo* call_info, Klass* receiver_klass) {
  _speculated_method = call_info->selected_method();
  if (UseCompressedClassPointers) {
    _speculated_klass = (uintptr_t)CompressedKlassPointers::encode_not_null(receiver_klass);
  } else {
    _speculated_klass = (uintptr_t)receiver_klass;
  }
  if (call_info->call_kind() == CallInfo::itable_call) {
    _itable_defc_klass = call_info->resolved_method()->method_holder();
    _itable_refc_klass = call_info->resolved_klass();
  }
  _is_initialized = true;
}

bool CompiledICData::is_speculated_klass_unloaded() const {
  return is_initialized() && _speculated_klass == 0;
}

void CompiledICData::clean_metadata() {
  if (!is_initialized() || is_speculated_klass_unloaded()) {
    return;
  }

  // GC cleaning doesn't need to change the state of the inline cache,
  // only nuke stale speculated metadata if it gets unloaded. If the
  // inline cache is monomorphic, the unverified entries will miss, and
  // subsequent miss handlers will upgrade the callsite to megamorphic,
  // which makes sense as it obviously is megamorphic then.
  if (!speculated_klass()->is_loader_alive()) {
    Atomic::store(&_speculated_klass, (uintptr_t)0);
    Atomic::store(&_speculated_method, (Method*)nullptr);
  }

  assert(_speculated_method == nullptr || _speculated_method->method_holder()->is_loader_alive(),
         "Speculated method is not unloaded despite class being unloaded");
}

void CompiledICData::metadata_do(MetadataClosure* cl) {
  if (!is_initialized()) {
    return;
  }

  if (!is_speculated_klass_unloaded()) {
    cl->do_metadata(_speculated_method);
    cl->do_metadata(speculated_klass());
  }
  if (_itable_refc_klass != nullptr) {
    cl->do_metadata(_itable_refc_klass);
  }
  if (_itable_defc_klass != nullptr) {
    cl->do_metadata(_itable_defc_klass);
  }
}

Klass* CompiledICData::speculated_klass() const {
  if (is_speculated_klass_unloaded()) {
    return nullptr;
  }

  if (UseCompressedClassPointers) {
    return CompressedKlassPointers::decode_not_null((narrowKlass)_speculated_klass);
  } else {
    return (Klass*)_speculated_klass;
  }
}

//-----------------------------------------------------------------------------
// High-level access to an inline cache. Guaranteed to be MT-safe.

CompiledICData* CompiledIC::data() const {
  return _data;
}

CompiledICData* data_from_reloc_iter(RelocIterator* iter) {
  assert(iter->type() == relocInfo::virtual_call_type, "wrong reloc. info");

  virtual_call_Relocation* r = iter->virtual_call_reloc();
  NativeMovConstReg* value = nativeMovConstReg_at(r->cached_value());

  return (CompiledICData*)value->data();
}

CompiledIC::CompiledIC(RelocIterator* iter)
  : _method(iter->code()),
    _data(data_from_reloc_iter(iter)),
    _call(nativeCall_at(iter->addr()))
{
  assert(_method != nullptr, "must pass compiled method");
  assert(_method->contains(iter->addr()), "must be in compiled method");
  assert(CompiledICLocker::is_safe(_method), "mt unsafe call");
}

CompiledIC* CompiledIC_before(CompiledMethod* nm, address return_addr) {
  address call_site = nativeCall_before(return_addr)->instruction_address();
  return CompiledIC_at(nm, call_site);
}

CompiledIC* CompiledIC_at(CompiledMethod* nm, address call_site) {
  RelocIterator iter(nm, call_site, call_site + 1);
  iter.next();
  return CompiledIC_at(&iter);
}

CompiledIC* CompiledIC_at(Relocation* call_reloc) {
  address call_site = call_reloc->addr();
  CompiledMethod* cm = CodeCache::find_blob(call_reloc->addr())->as_compiled_method();
  return CompiledIC_at(cm, call_site);
}

CompiledIC* CompiledIC_at(RelocIterator* reloc_iter) {
  CompiledIC* c_ic = new CompiledIC(reloc_iter);
  c_ic->verify();
  return c_ic;
}

void CompiledIC::ensure_initialized(CallInfo* call_info, Klass* receiver_klass) {
  if (!_data->is_initialized()) {
    _data->initialize(call_info, receiver_klass);
  }
}

void CompiledIC::set_to_clean() {
  log_debug(inlinecache)("IC@" INTPTR_FORMAT ": set to clean", p2i(_call->instruction_address()));
  _call->set_destination_mt_safe(SharedRuntime::get_resolve_virtual_call_stub());
}

void CompiledIC::set_to_monomorphic() {
  assert(data()->is_initialized(), "must be initialized");
  Method* method = data()->speculated_method();
  CompiledMethod* code = method->code();
  address entry;
  bool to_compiled = code != nullptr && code->is_in_use() && !code->is_unloading();

  if (to_compiled) {
    entry = code->entry_point();
  } else {
    entry = method->get_c2i_unverified_entry();
  }

  log_trace(inlinecache)("IC@" INTPTR_FORMAT ": monomorphic to %s: %s",
                         p2i(_call->instruction_address()),
                         to_compiled ? "compiled" : "interpreter",
                         method->print_value_string());

  _call->set_destination_mt_safe(entry);
}

void CompiledIC::set_to_megamorphic(CallInfo* call_info) {
  assert(data()->is_initialized(), "must be initialized");

  address entry;
  if (call_info->call_kind() == CallInfo::direct_call) {
    // C1 sometimes compiles a callsite before the target method is loaded, resulting in
    // dynamically bound callsites that should really be statically bound. However, the
    // target method might not have a vtable or itable. We just wait for better code to arrive
    return;
  } else if (call_info->call_kind() == CallInfo::itable_call) {
    int itable_index = call_info->itable_index();
    entry = VtableStubs::find_itable_stub(itable_index);
    if (entry == nullptr) {
      return;
    }
#ifdef ASSERT
    int index = call_info->resolved_method()->itable_index();
    assert(index == itable_index, "CallInfo pre-computes this");
    InstanceKlass* k = call_info->resolved_method()->method_holder();
    assert(k->verify_itable_index(itable_index), "sanity check");
#endif //ASSERT
  } else {
    assert(call_info->call_kind() == CallInfo::vtable_call, "what else?");
    // Can be different than selected_method->vtable_index(), due to package-private etc.
    int vtable_index = call_info->vtable_index();
    assert(call_info->resolved_klass()->verify_vtable_index(vtable_index), "sanity check");
    entry = VtableStubs::find_vtable_stub(vtable_index);
    if (entry == nullptr) {
      return;
    }
  }

  log_trace(inlinecache)("IC@" INTPTR_FORMAT ": to megamorphic %s entry: " INTPTR_FORMAT,
                         p2i(_call->instruction_address()), call_info->selected_method()->print_value_string(), p2i(entry));

  _call->set_destination_mt_safe(entry);
  assert(is_megamorphic(), "sanity check");
}

void CompiledIC::update(CallInfo* call_info, Klass* receiver_klass) {
  // If this is the first time we fix the inline cache, we ensure it's initialized
  ensure_initialized(call_info, receiver_klass);

  if (is_megamorphic()) {
    // Terminal state for the inline cache
    return;
  }

  if (is_speculated_klass(receiver_klass)) {
    // If the speculated class matches the receiver klass, we can speculate that will
    // continue to be the case with a monomorphic inline cache
    set_to_monomorphic();
  } else {
    // If the dynamic type speculation fails, we try to transform to a megamorphic state
    // for the inline cache using stubs to dispatch in tables
    set_to_megamorphic(call_info);
  }
}

bool CompiledIC::is_clean() const {
  return destination() == SharedRuntime::get_resolve_virtual_call_stub();
}

bool CompiledIC::is_monomorphic() const {
  return !is_clean() && !is_megamorphic();
}

bool CompiledIC::is_megamorphic() const {
  return VtableStubs::entry_point(destination()) != nullptr;;
}

bool CompiledIC::is_speculated_klass(Klass* receiver_klass) {
  return data()->speculated_klass() == receiver_klass;
}

// GC support
void CompiledIC::clean_metadata() {
  data()->clean_metadata();
}

void CompiledIC::metadata_do(MetadataClosure* cl) {
  data()->metadata_do(cl);
}

#ifndef PRODUCT
void CompiledIC::print() {
  tty->print("Inline cache at " INTPTR_FORMAT ", calling " INTPTR_FORMAT " cached_value " INTPTR_FORMAT,
             p2i(instruction_address()), p2i(destination()), p2i(data()));
  tty->cr();
}

void CompiledIC::verify() {
  _call->verify();
}
#endif

// ----------------------------------------------------------------------------

void CompiledDirectCall::set_to_clean() {
  // in_use is unused but needed to match template function in CompiledMethod
  assert(CompiledICLocker::is_safe(instruction_address()), "mt unsafe call");
  // Reset call site
  RelocIterator iter((nmethod*)nullptr, instruction_address(), instruction_address() + 1);
  while (iter.next()) {
    switch(iter.type()) {
    case relocInfo::static_call_type:
      _call->set_destination_mt_safe(SharedRuntime::get_resolve_static_call_stub());
      break;
    case relocInfo::opt_virtual_call_type:
      _call->set_destination_mt_safe(SharedRuntime::get_resolve_opt_virtual_call_stub());
      break;
    default:
      ShouldNotReachHere();
    }
  }
  assert(is_clean(), "should be clean after cleaning");

  log_debug(inlinecache)("DC@" INTPTR_FORMAT ": set to clean", p2i(_call->instruction_address()));
}

void CompiledDirectCall::set(const methodHandle& callee_method) {
  CompiledMethod* code = callee_method->code();
  CompiledMethod* caller = CodeCache::find_compiled(instruction_address());

  bool to_interp_cont_enter = caller->method()->is_continuation_enter_intrinsic() &&
                              ContinuationEntry::is_interpreted_call(instruction_address());

  bool to_compiled = !to_interp_cont_enter && code != nullptr && code->is_in_use() && !code->is_unloading();

  if (to_compiled) {
    _call->set_destination_mt_safe(code->verified_entry_point());
    assert(is_call_to_compiled(), "should be compiled after set to compiled");
  } else {
    // Patch call site to C2I adapter if code is deoptimized or unloaded.
    // We also need to patch the static call stub to set the rmethod register
    // to the callee_method so the c2i adapter knows how to build the frame
    set_to_interpreted(callee_method, callee_method->get_c2i_entry());
    assert(is_call_to_interpreted(), "should be interpreted after set to interpreted");
  }

  log_trace(inlinecache)("DC@" INTPTR_FORMAT ": set to %s: %s: " INTPTR_FORMAT,
                         p2i(_call->instruction_address()),
                         to_compiled ? "compiled" : "interpreter",
                         callee_method->print_value_string(),
                         p2i(_call->destination()));
}

bool CompiledDirectCall::is_clean() const {
  return destination() == SharedRuntime::get_resolve_static_call_stub() ||
         destination() == SharedRuntime::get_resolve_opt_virtual_call_stub();
}

bool CompiledDirectCall::is_call_to_interpreted() const {
  // It is a call to interpreted, if it calls to a stub. Hence, the destination
  // must be in the stub part of the nmethod that contains the call
  CompiledMethod* cm = CodeCache::find_compiled(instruction_address());
  return cm->stub_contains(destination());
}

bool CompiledDirectCall::is_call_to_compiled() const {
  CompiledMethod* caller = CodeCache::find_compiled(instruction_address());
  CodeBlob* dest_cb = CodeCache::find_blob(destination());
  return !caller->stub_contains(destination()) && dest_cb->is_compiled();
}

address CompiledDirectCall::find_stub_for(address instruction) {
  // Find reloc. information containing this call-site
  RelocIterator iter((nmethod*)nullptr, instruction);
  while (iter.next()) {
    if (iter.addr() == instruction) {
      switch(iter.type()) {
        case relocInfo::static_call_type:
          return iter.static_call_reloc()->static_stub();
        // We check here for opt_virtual_call_type, since we reuse the code
        // from the CompiledIC implementation
        case relocInfo::opt_virtual_call_type:
          return iter.opt_virtual_call_reloc()->static_stub();
        default:
          ShouldNotReachHere();
      }
    }
  }
  return nullptr;
}

address CompiledDirectCall::find_stub() {
  return find_stub_for(instruction_address());
}

#ifndef PRODUCT
void CompiledDirectCall::print() {
  tty->print("direct call at " INTPTR_FORMAT " to " INTPTR_FORMAT " -> ", p2i(instruction_address()), p2i(destination()));
  if (is_clean()) {
    tty->print("clean");
  } else if (is_call_to_compiled()) {
    tty->print("compiled");
  } else if (is_call_to_interpreted()) {
    tty->print("interpreted");
  }
  tty->cr();
}

void CompiledDirectCall::verify_mt_safe(const methodHandle& callee, address entry,
                                        NativeMovConstReg* method_holder,
                                        NativeJump* jump) {
  _call->verify();
  // A generated lambda form might be deleted from the Lambdaform
  // cache in MethodTypeForm.  If a jit compiled lambdaform method
  // becomes not entrant and the cache access returns null, the new
  // resolve will lead to a new generated LambdaForm.
  Method* old_method = reinterpret_cast<Method*>(method_holder->data());
  assert(old_method == nullptr || old_method == callee() ||
         callee->is_compiled_lambda_form() ||
         !old_method->method_holder()->is_loader_alive() ||
         old_method->is_old(),  // may be race patching deoptimized nmethod due to redefinition.
         "a) MT-unsafe modification of inline cache");

  address destination = jump->jump_destination();
  assert(destination == (address)-1 || destination == entry
         || old_method == nullptr || !old_method->method_holder()->is_loader_alive() // may have a race due to class unloading.
         || old_method->is_old(),  // may be race patching deoptimized nmethod due to redefinition.
         "b) MT-unsafe modification of inline cache");
}
#endif
