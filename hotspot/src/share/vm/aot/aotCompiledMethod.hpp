/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_VM_AOT_AOTCOMPILEDMETHOD_HPP
#define SHARE_VM_AOT_AOTCOMPILEDMETHOD_HPP

#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/compiledMethod.hpp"
#include "code/pcDesc.hpp"
#include "code/relocInfo.hpp"

class AOTCodeHeap;

class aot_metadata {
private:
  int _size;
  int _code_size;
  int _entry;
  int _verified_entry;
  int _exception_handler_offset;
  int _deopt_handler_offset;
  int _stubs_offset;
  int _frame_size;
  // location in frame (offset for sp) that deopt can store the original
  // pc during a deopt.
  int _orig_pc_offset;
  int _unsafe_access;

  int _pc_desc_begin;
  int _scopes_begin;
  int _reloc_begin;
  int _exception_table_begin;
  int _oopmap_begin;
  address at_offset(size_t offset) const { return ((address) this) + offset; }
public:
  int code_size() const { return _code_size; }
  int frame_size() const { return _frame_size / HeapWordSize; }
  PcDesc *scopes_pcs_begin() const { return (PcDesc *) at_offset(_pc_desc_begin); }
  PcDesc *scopes_pcs_end() const { return (PcDesc *) at_offset(_scopes_begin); }
  address scopes_data_begin() const { return at_offset(_scopes_begin); }
  address scopes_data_end() const { return at_offset(_reloc_begin); }
  relocInfo* relocation_begin() const { return (relocInfo*) at_offset(_reloc_begin); }
  relocInfo* relocation_end() const { return (relocInfo*) at_offset(_exception_table_begin); }
  address handler_table_begin   () const { return at_offset(_exception_table_begin); }
  address handler_table_end() const { return at_offset(_oopmap_begin); }

  address nul_chk_table_begin() const { return at_offset(_oopmap_begin); }
  address nul_chk_table_end() const { return at_offset(_oopmap_begin); }

  ImmutableOopMapSet* oopmap_set() const { return (ImmutableOopMapSet*) at_offset(_oopmap_begin); }

  address consts_begin() const { return at_offset(_size); }
  address consts_end() const { return at_offset(_size); }
  int stub_offset() const { return _stubs_offset; }
  int entry_offset() const { return _entry; }
  int verified_entry_offset() const { return _verified_entry; }
  int exception_handler_offset() const { return _exception_handler_offset; }
  int deopt_handler_offset() const { return _deopt_handler_offset; }
  int orig_pc_offset() const { return _orig_pc_offset; }

  int handler_table_size() const { return handler_table_end() - handler_table_begin(); }
  int nul_chk_table_size() const { return nul_chk_table_end() - nul_chk_table_begin(); }
  bool has_unsafe_access() const { return _unsafe_access != 0; }

};

/*
 * Use this for AOTCompiledMethods since a lot of the fields in CodeBlob gets the same
 * value when they come from AOT. code_begin == content_begin, etc... */
class AOTCompiledMethodLayout : public CodeBlobLayout {
public:
  AOTCompiledMethodLayout(address code_begin, address code_end, address relocation_begin, address relocation_end) :
    CodeBlobLayout(
        code_begin, // code_begin
        code_end, // code_end
        code_begin, // content_begin
        code_end, // content_end
        code_end, // data_end
        relocation_begin, // relocation_begin
        relocation_end
        ) {
    }
};

class AOTCompiledMethod : public CompiledMethod, public CHeapObj<mtCode> {
private:
  address       _code;
  aot_metadata* _meta;
  Metadata**    _metadata_got;
  jlong*        _state_adr; // Address of cell to indicate aot method state (in_use or not_entrant)
  AOTCodeHeap*  _heap;    // code heap which has this method
  const char*   _name;    // For stub: "AOT Stub<name>" for stub,
                          // For nmethod: "<u2_size>Ljava/lang/ThreadGroup;<u2_size>addUnstarted<u2_size>()V"
  const int _metadata_size; // size of _metadata_got
  const int _aot_id;
  const int _method_index;
  oop _oop;  // method()->method_holder()->klass_holder()

  address* orig_pc_addr(const frame* fr) { return (address*) ((address)fr->unextended_sp() + _meta->orig_pc_offset()); }
  bool make_not_entrant_helper(int new_state);

 public:
  using CHeapObj<mtCode>::operator new;
  using CHeapObj<mtCode>::operator delete;

  int method_index() const { return _method_index; }
  void set_oop(oop o) { _oop = o; }

  AOTCompiledMethod(address code, Method* method, aot_metadata* meta, address metadata_got, int metadata_size, jlong* state_adr, AOTCodeHeap* heap, const char* name, int method_index, int aot_id) :
    CompiledMethod(method, name, compiler_jvmci, // AOT code is generated by JVMCI compiler
        AOTCompiledMethodLayout(code, code + meta->code_size(), (address) meta->relocation_begin(), (address) meta->relocation_end()),
        0 /* frame_complete_offset */, meta->frame_size() /* frame_size */, meta->oopmap_set(), false /* caller_must_gc_arguments */),
    _code(code),
    _meta(meta),
    _metadata_got((Metadata**) metadata_got),
    _state_adr(state_adr),
    _heap(heap),
    _name(name),
    _metadata_size(metadata_size),
    _method_index(method_index),
    _aot_id(aot_id) {

    _is_far_code = CodeCache::is_far_target(code) ||
                   CodeCache::is_far_target(code + meta->code_size());
    _exception_cache = NULL;

    _scopes_data_begin = (address) _meta->scopes_data_begin();
    _deopt_handler_begin = (address) _code + _meta->deopt_handler_offset();
    _deopt_mh_handler_begin = (address) this;

    _pc_desc_container.reset_to(scopes_pcs_begin());

    // Mark the AOTCompiledMethod as in_use
    *_state_adr = nmethod::in_use;
    set_has_unsafe_access(_meta->has_unsafe_access());
    _oop = NULL;
  }

  virtual bool is_aot() const { return true; }
  virtual bool is_runtime_stub() const { return is_aot_runtime_stub(); }

  virtual bool is_compiled() const     { return !is_aot_runtime_stub(); }

  virtual bool is_locked_by_vm() const { return false; }

  int state() const { return *_state_adr; }

  // Non-virtual for speed
  bool _is_alive() const { return state() < zombie; }

  virtual bool is_zombie() const { return state() == zombie; }
  virtual bool is_unloaded() const { return state() == unloaded; }
  virtual bool is_not_entrant() const { return state() == not_entrant ||
                                                 state() == not_used; }
  virtual bool is_alive() const { return _is_alive(); }
  virtual bool is_in_use() const { return state() == in_use; }

  address exception_begin() { return (address) _code + _meta->exception_handler_offset(); }

  virtual const char* name() const { return _name; }

  virtual int compile_id() const { return _aot_id; }

  void print_on(outputStream* st) const;
  void print_on(outputStream* st, const char* msg) const;
  void print() const;

  virtual void print_value_on(outputStream *stream) const;
  virtual void print_block_comment(outputStream *stream, address block_begin) const { }
  virtual void verify() {}

  virtual int comp_level() const { return CompLevel_aot; }
  virtual address verified_entry_point() const { return _code + _meta->verified_entry_offset(); }
  virtual void log_identity(xmlStream* stream) const;
  virtual void log_state_change() const;
  virtual bool make_entrant();
  virtual bool make_not_entrant() { return make_not_entrant_helper(not_entrant); }
  virtual bool make_not_used() { return make_not_entrant_helper(not_used); }
  virtual address entry_point() const { return _code + _meta->entry_offset(); }
  virtual bool make_zombie() { ShouldNotReachHere(); return false; }
  virtual bool is_osr_method() const { return false; }
  virtual int osr_entry_bci() const { ShouldNotReachHere(); return -1; }
  // AOT compiled methods do not get into zombie state
  virtual bool can_convert_to_zombie() { return false; }

  virtual bool is_evol_dependent_on(Klass* dependee);
  virtual bool is_dependent_on_method(Method* dependee) { return true; }

  virtual void clear_inline_caches();

  virtual void print_pcs() {}

  virtual address scopes_data_end() const { return _meta->scopes_data_end(); }

  virtual oop oop_at(int index) const;
  virtual Metadata* metadata_at(int index) const;

  virtual PcDesc* scopes_pcs_begin() const { return _meta->scopes_pcs_begin(); }
  virtual PcDesc* scopes_pcs_end() const { return _meta->scopes_pcs_end(); }

  virtual address handler_table_begin() const { return _meta->handler_table_begin(); }
  virtual address handler_table_end() const { return _meta->handler_table_end(); }

  virtual address nul_chk_table_begin() const { return _meta->nul_chk_table_begin(); }
  virtual address nul_chk_table_end() const { return _meta->nul_chk_table_end(); }

  virtual address consts_begin() const { return _meta->consts_begin(); }
  virtual address consts_end() const { return _meta->consts_end(); }

  virtual address stub_begin() const { return code_begin() + _meta->stub_offset(); }
  virtual address stub_end() const { return code_end(); }

  virtual oop* oop_addr_at(int index) const { ShouldNotReachHere(); return NULL; }
  virtual Metadata** metadata_addr_at(int index) const { ShouldNotReachHere(); return NULL; }

  // Accessor/mutator for the original pc of a frame before a frame was deopted.
  address get_original_pc(const frame* fr) { return *orig_pc_addr(fr); }
  void    set_original_pc(const frame* fr, address pc) { *orig_pc_addr(fr) = pc; }

#ifdef HOTSWAP
  // Flushing and deoptimization in case of evolution
  void flush_evol_dependents_on(instanceKlassHandle dependee);
#endif // HOTSWAP

  virtual void metadata_do(void f(Metadata*));

  bool metadata_got_contains(Metadata **p) {
    return p >= &_metadata_got[0] && p < &_metadata_got[_metadata_size];
  }

  Metadata** metadata_begin() const { return &_metadata_got[0] ; }
  Metadata** metadata_end()   const { return &_metadata_got[_metadata_size] ; }
  const char* compile_kind() const { return "AOT"; }

  int get_state() const {
    return (int) (*_state_adr);
  }

  // inlined and non-virtual for AOTCodeHeap::oops_do
  void do_oops(OopClosure* f) {
    assert(_is_alive(), "");
    if (_oop != NULL) {
      f->do_oop(&_oop);
    }
#if 0
    metadata_oops_do(metadata_begin(), metadata_end(), f);
#endif
  }


protected:
  // AOT compiled methods are not flushed
  void flush() {};

  NativeCallWrapper* call_wrapper_at(address call) const;
  NativeCallWrapper* call_wrapper_before(address return_pc) const;
  address call_instruction_address(address pc) const;

  CompiledStaticCall* compiledStaticCall_at(Relocation* call_site) const;
  CompiledStaticCall* compiledStaticCall_at(address addr) const;
  CompiledStaticCall* compiledStaticCall_before(address addr) const;
private:
  bool is_aot_runtime_stub() const { return _method == NULL; }

protected:
  virtual bool do_unloading_oops(address low_boundary, BoolObjectClosure* is_alive, bool unloading_occurred);
  virtual bool do_unloading_jvmci(BoolObjectClosure* is_alive, bool unloading_occurred) { return false; }

};

class PltNativeCallWrapper: public NativeCallWrapper {
private:
  NativePltCall* _call;

public:
  PltNativeCallWrapper(NativePltCall* call) : _call(call) {}

  virtual address destination() const { return _call->destination(); }
  virtual address instruction_address() const { return _call->instruction_address(); }
  virtual address next_instruction_address() const { return _call->next_instruction_address(); }
  virtual address return_address() const { return _call->return_address(); }
  virtual address get_resolve_call_stub(bool is_optimized) const { return _call->plt_resolve_call(); }
  virtual void set_destination_mt_safe(address dest) { _call->set_destination_mt_safe(dest); }
  virtual void set_to_interpreted(const methodHandle& method, CompiledICInfo& info);
  virtual void verify() const { _call->verify(); }
  virtual void verify_resolve_call(address dest) const;

  virtual bool is_call_to_interpreted(address dest) const { return (dest == _call->plt_c2i_stub()); }
  // TODO: assume for now that patching of aot code (got cell) is safe.
  virtual bool is_safe_for_patching() const { return true; }

  virtual NativeInstruction* get_load_instruction(virtual_call_Relocation* r) const;

  virtual void *get_data(NativeInstruction* instruction) const {
    return (void*)((NativeLoadGot*) instruction)->data();
  }

  virtual void set_data(NativeInstruction* instruction, intptr_t data) {
    ((NativeLoadGot*) instruction)->set_data(data);
  }
};

#endif //SHARE_VM_AOT_AOTCOMPILEDMETHOD_HPP
