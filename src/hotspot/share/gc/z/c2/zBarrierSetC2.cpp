/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/macroAssembler.hpp"
#include "classfile/javaClasses.hpp"
#include "gc/z/c2/zBarrierSetC2.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/block.hpp"
#include "opto/compile.hpp"
#include "opto/graphKit.hpp"
#include "opto/machnode.hpp"
#include "opto/macro.hpp"
#include "opto/memnode.hpp"
#include "opto/node.hpp"
#include "opto/output.hpp"
#include "opto/regalloc.hpp"
#include "opto/runtime.hpp"
#include "opto/type.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

template<typename K, typename V, size_t TableSize>
class ZArenaHashtable : public ResourceObj {
  class ZArenaHashtableEntry : public ResourceObj {
  public:
    ZArenaHashtableEntry* _next;
    K _key;
    V _value;
  };

  static const size_t TableMask = TableSize - 1;

  Arena* _arena;
  ZArenaHashtableEntry* _table[TableSize];

public:
  class Iterator {
    ZArenaHashtable* _table;
    ZArenaHashtableEntry* _current_entry;
    size_t _current_index;

  public:
    Iterator(ZArenaHashtable* table)
      : _table(table),
        _current_entry(table->_table[0]),
        _current_index(0) {
      if (_current_entry == nullptr) {
        next();
      }
    }

    bool has_next() { return _current_entry != nullptr; }
    K key()         { return _current_entry->_key; }
    V value()       { return _current_entry->_value; }

    void next() {
      if (_current_entry != nullptr) {
        _current_entry = _current_entry->_next;
      }
      while (_current_entry == nullptr && ++_current_index < TableSize) {
        _current_entry = _table->_table[_current_index];
      }
    }
  };

  ZArenaHashtable(Arena* arena)
    : _arena(arena),
      _table() {
    Copy::zero_to_bytes(&_table, sizeof(_table));
  }

  void add(K key, V value) {
    ZArenaHashtableEntry* entry = new (_arena) ZArenaHashtableEntry();
    entry->_key = key;
    entry->_value = value;
    entry->_next = _table[key & TableMask];
    _table[key & TableMask] = entry;
  }

  V* get(K key) const {
    for (ZArenaHashtableEntry* e = _table[key & TableMask]; e != nullptr; e = e->_next) {
      if (e->_key == key) {
        return &(e->_value);
      }
    }
    return nullptr;
  }

  Iterator iterator() {
    return Iterator(this);
  }
};

typedef ZArenaHashtable<intptr_t, bool, 4> ZOffsetTable;

class ZBarrierSetC2State : public BarrierSetC2State {
private:
  GrowableArray<ZBarrierStubC2*>* _stubs;
  int                             _trampoline_stubs_count;
  int                             _stubs_start_offset;

public:
  ZBarrierSetC2State(Arena* arena)
    : BarrierSetC2State(arena),
      _stubs(new (arena) GrowableArray<ZBarrierStubC2*>(arena, 8,  0, nullptr)),
      _trampoline_stubs_count(0),
      _stubs_start_offset(0) {}

  GrowableArray<ZBarrierStubC2*>* stubs() {
    return _stubs;
  }

  bool needs_liveness_data(const MachNode* mach) const {
    // Don't need liveness data for nodes without barriers
    return mach->barrier_data() != ZBarrierElided;
  }

  bool needs_livein_data() const {
    return true;
  }

  void inc_trampoline_stubs_count() {
    assert(_trampoline_stubs_count != INT_MAX, "Overflow");
    ++_trampoline_stubs_count;
  }

  int trampoline_stubs_count() {
    return _trampoline_stubs_count;
  }

  void set_stubs_start_offset(int offset) {
    _stubs_start_offset = offset;
  }

  int stubs_start_offset() {
    return _stubs_start_offset;
  }
};

static ZBarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<ZBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

void ZBarrierStubC2::register_stub(ZBarrierStubC2* stub) {
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(stub);
  }
}

void ZBarrierStubC2::inc_trampoline_stubs_count() {
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->inc_trampoline_stubs_count();
  }
}

int ZBarrierStubC2::trampoline_stubs_count() {
  return barrier_set_state()->trampoline_stubs_count();
}

int ZBarrierStubC2::stubs_start_offset() {
  return barrier_set_state()->stubs_start_offset();
}

ZBarrierStubC2::ZBarrierStubC2(const MachNode* node) : BarrierStubC2(node) {}

ZLoadBarrierStubC2* ZLoadBarrierStubC2::create(const MachNode* node, Address ref_addr, Register ref) {
  AARCH64_ONLY(fatal("Should use ZLoadBarrierStubC2Aarch64::create"));
  ZLoadBarrierStubC2* const stub = new (Compile::current()->comp_arena()) ZLoadBarrierStubC2(node, ref_addr, ref);
  register_stub(stub);

  return stub;
}

ZLoadBarrierStubC2::ZLoadBarrierStubC2(const MachNode* node, Address ref_addr, Register ref)
  : ZBarrierStubC2(node),
    _ref_addr(ref_addr),
    _ref(ref) {
  assert_different_registers(ref, ref_addr.base());
  assert_different_registers(ref, ref_addr.index());
  // The runtime call updates the value of ref, so we should not spill and
  // reload its outdated value.
  dont_preserve(ref);
}

Address ZLoadBarrierStubC2::ref_addr() const {
  return _ref_addr;
}

Register ZLoadBarrierStubC2::ref() const {
  return _ref;
}

address ZLoadBarrierStubC2::slow_path() const {
  const uint8_t barrier_data = _node->barrier_data();
  DecoratorSet decorators = DECORATORS_NONE;
  if (barrier_data & ZBarrierStrong) {
    decorators |= ON_STRONG_OOP_REF;
  }
  if (barrier_data & ZBarrierWeak) {
    decorators |= ON_WEAK_OOP_REF;
  }
  if (barrier_data & ZBarrierPhantom) {
    decorators |= ON_PHANTOM_OOP_REF;
  }
  if (barrier_data & ZBarrierNoKeepalive) {
    decorators |= AS_NO_KEEPALIVE;
  }
  return ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators);
}

void ZLoadBarrierStubC2::emit_code(MacroAssembler& masm) {
  ZBarrierSet::assembler()->generate_c2_load_barrier_stub(&masm, static_cast<ZLoadBarrierStubC2*>(this));
}

ZStoreBarrierStubC2* ZStoreBarrierStubC2::create(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic, bool is_nokeepalive) {
  AARCH64_ONLY(fatal("Should use ZStoreBarrierStubC2Aarch64::create"));
  ZStoreBarrierStubC2* const stub = new (Compile::current()->comp_arena()) ZStoreBarrierStubC2(node, ref_addr, new_zaddress, new_zpointer, is_native, is_atomic, is_nokeepalive);
  register_stub(stub);

  return stub;
}

ZStoreBarrierStubC2::ZStoreBarrierStubC2(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer,
                                         bool is_native, bool is_atomic, bool is_nokeepalive)
  : ZBarrierStubC2(node),
    _ref_addr(ref_addr),
    _new_zaddress(new_zaddress),
    _new_zpointer(new_zpointer),
    _is_native(is_native),
    _is_atomic(is_atomic),
    _is_nokeepalive(is_nokeepalive) {}

Address ZStoreBarrierStubC2::ref_addr() const {
  return _ref_addr;
}

Register ZStoreBarrierStubC2::new_zaddress() const {
  return _new_zaddress;
}

Register ZStoreBarrierStubC2::new_zpointer() const {
  return _new_zpointer;
}

bool ZStoreBarrierStubC2::is_native() const {
  return _is_native;
}

bool ZStoreBarrierStubC2::is_atomic() const {
  return _is_atomic;
}

bool ZStoreBarrierStubC2::is_nokeepalive() const {
  return _is_nokeepalive;
}

void ZStoreBarrierStubC2::emit_code(MacroAssembler& masm) {
  ZBarrierSet::assembler()->generate_c2_store_barrier_stub(&masm, static_cast<ZStoreBarrierStubC2*>(this));
}

uint ZBarrierSetC2::estimated_barrier_size(const Node* node) const {
  uint8_t barrier_data = MemNode::barrier_data(node);
  assert(barrier_data != 0, "should be a barrier node");
  uint uncolor_or_color_size = node->is_Load() ? 1 : 2;
  if ((barrier_data & ZBarrierElided) != 0) {
    return uncolor_or_color_size;
  }
  // A compare and branch corresponds to approximately four fast-path Ideal
  // nodes (Cmp, Bool, If, If projection). The slow path (If projection and
  // runtime call) is excluded since the corresponding code is laid out
  // separately and does not directly affect performance.
  return uncolor_or_color_size + 4;
}

void* ZBarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new (comp_arena) ZBarrierSetC2State(comp_arena);
}

void ZBarrierSetC2::late_barrier_analysis() const {
  compute_liveness_at_stubs();
  analyze_dominating_barriers();
}

void ZBarrierSetC2::emit_stubs(CodeBuffer& cb) const {
  MacroAssembler masm(&cb);
  GrowableArray<ZBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  barrier_set_state()->set_stubs_start_offset(masm.offset());

  for (int i = 0; i < stubs->length(); i++) {
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(PhaseOutput::MAX_inst_size) && cb.blob() == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }

    stubs->at(i)->emit_code(masm);
  }

  masm.flush();
}

int ZBarrierSetC2::estimate_stub_size() const {
  Compile* const C = Compile::current();
  BufferBlob* const blob = C->output()->scratch_buffer_blob();
  GrowableArray<ZBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  int size = 0;

  for (int i = 0; i < stubs->length(); i++) {
    CodeBuffer cb(blob->content_begin(), checked_cast<CodeBuffer::csize_t>((address)C->output()->scratch_locs_memory() - blob->content_begin()));
    MacroAssembler masm(&cb);
    stubs->at(i)->emit_code(masm);
    size += cb.insts_size();
  }

  return size;
}

static void set_barrier_data(C2Access& access) {
  if (!ZBarrierSet::barrier_needed(access.decorators(), access.type())) {
    return;
  }

  if (access.decorators() & C2_TIGHTLY_COUPLED_ALLOC) {
    access.set_barrier_data(ZBarrierElided);
    return;
  }

  uint8_t barrier_data = 0;

  if (access.decorators() & ON_PHANTOM_OOP_REF) {
    barrier_data |= ZBarrierPhantom;
  } else if (access.decorators() & ON_WEAK_OOP_REF) {
    barrier_data |= ZBarrierWeak;
  } else {
    barrier_data |= ZBarrierStrong;
  }

  if (access.decorators() & IN_NATIVE) {
    barrier_data |= ZBarrierNative;
  }

  if (access.decorators() & AS_NO_KEEPALIVE) {
    barrier_data |= ZBarrierNoKeepalive;
  }

  access.set_barrier_data(barrier_data);
}

Node* ZBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  set_barrier_data(access);
  return BarrierSetC2::store_at_resolved(access, val);
}

Node* ZBarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  set_barrier_data(access);
  return BarrierSetC2::load_at_resolved(access, val_type);
}

Node* ZBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                    Node* new_val, const Type* val_type) const {
  set_barrier_data(access);
  return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, val_type);
}

Node* ZBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                     Node* new_val, const Type* value_type) const {
  set_barrier_data(access);
  return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* ZBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* val_type) const {
  set_barrier_data(access);
  return BarrierSetC2::atomic_xchg_at_resolved(access, new_val, val_type);
}

bool ZBarrierSetC2::array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type,
                                                    bool is_clone, bool is_clone_instance,
                                                    ArrayCopyPhase phase) const {
  if (phase == ArrayCopyPhase::Parsing) {
    return false;
  }
  if (phase == ArrayCopyPhase::Optimization) {
    return is_clone_instance;
  }
  // else ArrayCopyPhase::Expansion
  return type == T_OBJECT || type == T_ARRAY;
}

#define XTOP LP64_ONLY(COMMA phase->top())

void ZBarrierSetC2::clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const {
  Node* const src = ac->in(ArrayCopyNode::Src);
  const TypeAryPtr* const ary_ptr = src->get_ptr_type()->isa_aryptr();

  if (ac->is_clone_array() && ary_ptr != nullptr) {
    BasicType bt = ary_ptr->elem()->array_element_basic_type();
    if (is_reference_type(bt)) {
      // Clone object array
      bt = T_OBJECT;
    } else {
      // Clone primitive array
      bt = T_LONG;
    }

    Node* const ctrl = ac->in(TypeFunc::Control);
    Node* const mem = ac->in(TypeFunc::Memory);
    Node* const src = ac->in(ArrayCopyNode::Src);
    Node* src_offset = ac->in(ArrayCopyNode::SrcPos);
    Node* const dest = ac->in(ArrayCopyNode::Dest);
    Node* dest_offset = ac->in(ArrayCopyNode::DestPos);
    Node* length = ac->in(ArrayCopyNode::Length);

    if (bt == T_OBJECT) {
      // BarrierSetC2::clone sets the offsets via BarrierSetC2::arraycopy_payload_base_offset
      // which 8-byte aligns them to allow for word size copies. Make sure the offsets point
      // to the first element in the array when cloning object arrays. Otherwise, load
      // barriers are applied to parts of the header. Also adjust the length accordingly.
      assert(src_offset == dest_offset, "should be equal");
      const jlong offset = src_offset->get_long();
      if (offset != arrayOopDesc::base_offset_in_bytes(T_OBJECT)) {
        assert(!UseCompressedClassPointers || UseCompactObjectHeaders, "should only happen without compressed class pointers");
        assert((arrayOopDesc::base_offset_in_bytes(T_OBJECT) - offset) == BytesPerLong, "unexpected offset");
        length = phase->transform_later(new SubLNode(length, phase->longcon(1))); // Size is in longs
        src_offset = phase->longcon(arrayOopDesc::base_offset_in_bytes(T_OBJECT));
        dest_offset = src_offset;
      }
    }
    Node* const payload_src = phase->basic_plus_adr(src, src_offset);
    Node* const payload_dst = phase->basic_plus_adr(dest, dest_offset);

    const char*   copyfunc_name = "arraycopy";
    const address copyfunc_addr = phase->basictype2arraycopy(bt, nullptr, nullptr, true, copyfunc_name, true);

    const TypePtr* const raw_adr_type = TypeRawPtr::BOTTOM;
    const TypeFunc* const call_type = OptoRuntime::fast_arraycopy_Type();

    Node* const call = phase->make_leaf_call(ctrl, mem, call_type, copyfunc_addr, copyfunc_name, raw_adr_type, payload_src, payload_dst, length XTOP);
    phase->transform_later(call);

    phase->igvn().replace_node(ac, call);
    return;
  }

  // Clone instance or array where 'src' is only known to be an object (ary_ptr
  // is null). This can happen in bytecode generated dynamically to implement
  // reflective array clones.
  clone_in_runtime(phase, ac, ZBarrierSetRuntime::clone_addr(), "ZBarrierSetRuntime::clone");
}

#undef XTOP

void ZBarrierSetC2::elide_dominated_barrier(MachNode* mach) const {
  mach->set_barrier_data(ZBarrierElided);
}

void ZBarrierSetC2::analyze_dominating_barriers() const {
  ResourceMark rm;
  Compile* const C = Compile::current();
  PhaseCFG* const cfg = C->cfg();

  Node_List loads;
  Node_List load_dominators;

  Node_List stores;
  Node_List store_dominators;

  Node_List atomics;
  Node_List atomic_dominators;

  // Step 1 - Find accesses and allocations, and track them in lists
  for (uint i = 0; i < cfg->number_of_blocks(); ++i) {
    const Block* const block = cfg->get_block(i);
    for (uint j = 0; j < block->number_of_nodes(); ++j) {
      Node* const node = block->get_node(j);
      if (node->is_Phi()) {
        if (is_allocation(node)) {
          load_dominators.push(node);
          store_dominators.push(node);
          // An allocation can't be considered to "dominate" an atomic operation.
          // For example a CAS requires the memory location to be store-good.
          // When you have a dominating store or atomic instruction, that is
          // indeed ensured to be the case. However, as for allocations, the
          // initialized memory location could be raw null, which isn't store-good.
        }
        continue;
      } else if (!node->is_Mach()) {
        continue;
      }

      MachNode* const mach = node->as_Mach();
      switch (mach->ideal_Opcode()) {
      case Op_LoadP:
        if ((mach->barrier_data() & ZBarrierStrong) != 0 &&
            (mach->barrier_data() & ZBarrierNoKeepalive) == 0) {
          loads.push(mach);
          load_dominators.push(mach);
        }
        break;
      case Op_StoreP:
        if (mach->barrier_data() != 0) {
          stores.push(mach);
          load_dominators.push(mach);
          store_dominators.push(mach);
          atomic_dominators.push(mach);
        }
        break;
      case Op_CompareAndExchangeP:
      case Op_CompareAndSwapP:
      case Op_GetAndSetP:
        if (mach->barrier_data() != 0) {
          atomics.push(mach);
          load_dominators.push(mach);
          store_dominators.push(mach);
          atomic_dominators.push(mach);
        }
        break;

      default:
        break;
      }
    }
  }

  // Step 2 - Find dominating accesses or allocations for each access
  elide_dominated_barriers(loads, load_dominators);
  elide_dominated_barriers(stores, store_dominators);
  elide_dominated_barriers(atomics, atomic_dominators);
}

void ZBarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const {
  eliminate_gc_barrier_data(node);
}

void ZBarrierSetC2::eliminate_gc_barrier_data(Node* node) const {
  if (node->is_LoadStore()) {
    LoadStoreNode* loadstore = node->as_LoadStore();
    loadstore->set_barrier_data(ZBarrierElided);
  } else if (node->is_Mem()) {
    MemNode* mem = node->as_Mem();
    mem->set_barrier_data(ZBarrierElided);
  }
}

#ifndef PRODUCT
void ZBarrierSetC2::dump_barrier_data(const MachNode* mach, outputStream* st) const {
  if ((mach->barrier_data() & ZBarrierStrong) != 0) {
    st->print("strong ");
  }
  if ((mach->barrier_data() & ZBarrierWeak) != 0) {
    st->print("weak ");
  }
  if ((mach->barrier_data() & ZBarrierPhantom) != 0) {
    st->print("phantom ");
  }
  if ((mach->barrier_data() & ZBarrierNoKeepalive) != 0) {
    st->print("nokeepalive ");
  }
  if ((mach->barrier_data() & ZBarrierNative) != 0) {
    st->print("native ");
  }
  if ((mach->barrier_data() & ZBarrierElided) != 0) {
    st->print("elided ");
  }
}
#endif // !PRODUCT
