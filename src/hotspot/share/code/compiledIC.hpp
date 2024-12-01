/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_COMPILEDIC_HPP
#define SHARE_CODE_COMPILEDIC_HPP

#include "code/nativeInst.hpp"
#include "interpreter/linkResolver.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "opto/c2_MacroAssembler.hpp"

//-----------------------------------------------------------------------------
// The CompiledIC represents a compiled inline cache.
//
// It's safe to transition from any state to any state. Typically an inline cache starts
// in the clean state, meaning it will resolve the call when called. Then it typically
// transitions to monomorphic, assuming the first dynamic receiver will be the only one
// observed. If that speculation fails, we transition to megamorphic.
//
class CompiledIC;
class CompiledICProtectionBehaviour;
class nmethod;

class CompiledICLocker: public StackObj {
  nmethod* _method;
  CompiledICProtectionBehaviour* _behaviour;
  bool _locked;
  NoSafepointVerifier _nsv;

public:
  CompiledICLocker(nmethod* method);
  ~CompiledICLocker();
  static bool is_safe(nmethod* method);
  static bool is_safe(address code);
};

// A CompiledICData is a helper object for the inline cache implementation.
// It comprises:
// (1) The first receiver klass and its selected method
// (2) Itable call metadata

class CompiledICData : public CHeapObj<mtCode> {
  friend class VMStructs;
  friend class JVMCIVMStructs;

  Method*   volatile _speculated_method;
  uintptr_t volatile _speculated_klass;
  Klass*             _itable_defc_klass;
  Klass*             _itable_refc_klass;
  bool               _is_initialized;

  bool is_speculated_klass_unloaded() const;

 public:
  // Constructor
  CompiledICData();

  // accessors
  Klass*    speculated_klass()  const;
  Method*   speculated_method() const { return _speculated_method; }
  Klass*    itable_defc_klass() const { return _itable_defc_klass; }
  Klass*    itable_refc_klass() const { return _itable_refc_klass; }

  static ByteSize speculated_method_offset() { return byte_offset_of(CompiledICData, _speculated_method); }
  static ByteSize speculated_klass_offset()  { return byte_offset_of(CompiledICData, _speculated_klass); }

  static ByteSize itable_defc_klass_offset() { return byte_offset_of(CompiledICData, _itable_defc_klass); }
  static ByteSize itable_refc_klass_offset() { return byte_offset_of(CompiledICData, _itable_refc_klass); }

  void initialize(CallInfo* call_info, Klass* receiver_klass);

  bool is_initialized()       const { return _is_initialized; }

  // GC Support
  void clean_metadata();
  void metadata_do(MetadataClosure* cl);
};

class CompiledIC: public ResourceObj {
private:
  nmethod* _method;
  CompiledICData* _data;
  NativeCall* _call;

  CompiledIC(RelocIterator* iter);

  // CompiledICData wrappers
  void ensure_initialized(CallInfo* call_info, Klass* receiver_klass);
  bool is_speculated_klass(Klass* receiver_klass);

  // Inline cache states
  void set_to_monomorphic();
  void set_to_megamorphic(CallInfo* call_info);

public:
  // conversion (machine PC to CompiledIC*)
  friend CompiledIC* CompiledIC_before(nmethod* nm, address return_addr);
  friend CompiledIC* CompiledIC_at(nmethod* nm, address call_site);
  friend CompiledIC* CompiledIC_at(Relocation* call_site);
  friend CompiledIC* CompiledIC_at(RelocIterator* reloc_iter);

  CompiledICData* data() const;

  // State
  bool is_clean()       const;
  bool is_monomorphic() const;
  bool is_megamorphic() const;

  address end_of_call() const { return _call->return_address(); }

  // MT-safe patching of inline caches. Note: Only safe to call is_xxx when holding the CompiledICLocker
  // so you are guaranteed that no patching takes place. The same goes for verify.
  void set_to_clean();
  void update(CallInfo* call_info, Klass* receiver_klass);

  // GC support
  void clean_metadata();
  void metadata_do(MetadataClosure* cl);

  // Location
  address instruction_address() const { return _call->instruction_address(); }
  address destination() const         { return _call->destination(); }

  // Misc
  void print()             PRODUCT_RETURN;
  void verify()            PRODUCT_RETURN;
};

CompiledIC* CompiledIC_before(nmethod* nm, address return_addr);
CompiledIC* CompiledIC_at(nmethod* nm, address call_site);
CompiledIC* CompiledIC_at(Relocation* call_site);
CompiledIC* CompiledIC_at(RelocIterator* reloc_iter);

//-----------------------------------------------------------------------------
// The CompiledDirectCall represents a call to a method in the compiled code
//
//
//           -----<----- Clean ----->-----
//          /                             \
//         /                               \
//    compilled code <------------> interpreted code
//
//  Clean:            Calls directly to runtime method for fixup
//  Compiled code:    Calls directly to compiled code
//  Interpreted code: Calls to stub that set Method* reference
//
//

class CompiledDirectCall : public ResourceObj {
private:
  friend class CompiledIC;
  friend class DirectNativeCallWrapper;

  // Also used by CompiledIC
  void set_to_interpreted(const methodHandle& callee, address entry);
  void verify_mt_safe(const methodHandle& callee, address entry,
                      NativeMovConstReg* method_holder,
                      NativeJump*        jump) PRODUCT_RETURN;
  address instruction_address() const { return _call->instruction_address(); }
  void set_destination_mt_safe(address dest) { _call->set_destination_mt_safe(dest); }

  NativeCall* _call;

  CompiledDirectCall(NativeCall* call) : _call(call) {}

 public:
  // Returns null if CodeBuffer::expand fails
  static address emit_to_interp_stub(MacroAssembler *masm, address mark = nullptr);
  static int to_interp_stub_size();
  static int to_trampoline_stub_size();
  static int reloc_to_interp_stub();

  static inline CompiledDirectCall* before(address return_addr) {
    CompiledDirectCall* st = new CompiledDirectCall(nativeCall_before(return_addr));
    st->verify();
    return st;
  }

  static inline CompiledDirectCall* at(address native_call) {
    CompiledDirectCall* st = new CompiledDirectCall(nativeCall_at(native_call));
    st->verify();
    return st;
  }

  static inline CompiledDirectCall* at(Relocation* call_site) {
    return at(call_site->addr());
  }

  // Delegation
  address destination() const { return _call->destination(); }
  address end_of_call() const { return _call->return_address(); }

  // Clean static call (will force resolving on next use)
  void set_to_clean();

  void set(const methodHandle& callee_method);

  // State
  bool is_clean() const;
  bool is_call_to_interpreted() const;
  bool is_call_to_compiled() const;

  // Stub support
  static address find_stub_for(address instruction);
  address find_stub();
  static void set_stub_to_clean(static_stub_Relocation* static_stub);

  // Misc.
  void print()  PRODUCT_RETURN;
  void verify() PRODUCT_RETURN;
};

#endif // SHARE_CODE_COMPILEDIC_HPP
