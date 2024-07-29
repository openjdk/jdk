/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "classfile/javaClasses.hpp"
#include "gc/z/c2/zBarrierSetC2.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/addnode.hpp"
#include "opto/block.hpp"
#include "opto/compile.hpp"
#include "opto/graphKit.hpp"
#include "opto/machnode.hpp"
#include "opto/macro.hpp"
#include "opto/memnode.hpp"
#include "opto/node.hpp"
#include "opto/output.hpp"
#include "opto/regalloc.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/type.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

template<typename K, typename V, size_t _table_size>
class ZArenaHashtable : public ResourceObj {
  class ZArenaHashtableEntry : public ResourceObj {
  public:
    ZArenaHashtableEntry* _next;
    K _key;
    V _value;
  };

  static const size_t _table_mask = _table_size - 1;

  Arena* _arena;
  ZArenaHashtableEntry* _table[_table_size];

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
      while (_current_entry == nullptr && ++_current_index < _table_size) {
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
    entry->_next = _table[key & _table_mask];
    _table[key & _table_mask] = entry;
  }

  V* get(K key) const {
    for (ZArenaHashtableEntry* e = _table[key & _table_mask]; e != nullptr; e = e->_next) {
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

ZStoreBarrierStubC2* ZStoreBarrierStubC2::create(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic) {
  AARCH64_ONLY(fatal("Should use ZStoreBarrierStubC2Aarch64::create"));
  ZStoreBarrierStubC2* const stub = new (Compile::current()->comp_arena()) ZStoreBarrierStubC2(node, ref_addr, new_zaddress, new_zpointer, is_native, is_atomic);
  register_stub(stub);

  return stub;
}

ZStoreBarrierStubC2::ZStoreBarrierStubC2(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_native, bool is_atomic)
  : ZBarrierStubC2(node),
    _ref_addr(ref_addr),
    _new_zaddress(new_zaddress),
    _new_zpointer(new_zpointer),
    _is_native(is_native),
    _is_atomic(is_atomic) {}

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
    CodeBuffer cb(blob->content_begin(), (address)C->output()->scratch_locs_memory() - blob->content_begin());
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
        assert(!UseCompressedClassPointers, "should only happen without compressed class pointers");
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

// == Dominating barrier elision ==

static bool block_has_safepoint(const Block* block, uint from, uint to) {
  for (uint i = from; i < to; i++) {
    if (block->get_node(i)->is_MachSafePoint()) {
      // Safepoint found
      return true;
    }
  }

  // Safepoint not found
  return false;
}

static bool block_has_safepoint(const Block* block) {
  return block_has_safepoint(block, 0, block->number_of_nodes());
}

static uint block_index(const Block* block, const Node* node) {
  for (uint j = 0; j < block->number_of_nodes(); ++j) {
    if (block->get_node(j) == node) {
      return j;
    }
  }
  ShouldNotReachHere();
  return 0;
}

// Look through various node aliases
static const Node* look_through_node(const Node* node) {
  while (node != nullptr) {
    const Node* new_node = node;
    if (node->is_Mach()) {
      const MachNode* const node_mach = node->as_Mach();
      if (node_mach->ideal_Opcode() == Op_CheckCastPP) {
        new_node = node->in(1);
      }
      if (node_mach->is_SpillCopy()) {
        new_node = node->in(1);
      }
    }
    if (new_node == node || new_node == nullptr) {
      break;
    } else {
      node = new_node;
    }
  }

  return node;
}

// Whether the given offset is undefined.
static bool is_undefined(intptr_t offset) {
  return offset == Type::OffsetTop;
}

// Whether the given offset is unknown.
static bool is_unknown(intptr_t offset) {
  return offset == Type::OffsetBot;
}

// Whether the given offset is concrete (defined and compile-time known).
static bool is_concrete(intptr_t offset) {
  return !is_undefined(offset) && !is_unknown(offset);
}

// Compute base + offset components of the memory address accessed by mach.
// Return a node representing the base address, or null if the base cannot be
// found or the offset is undefined or a concrete negative value. If a non-null
// base is returned, the offset is a concrete, nonnegative value or unknown.
static const Node* get_base_and_offset(const MachNode* mach, intptr_t& offset) {
  const TypePtr* adr_type = nullptr;
  offset = 0;
  const Node* base = mach->get_base_and_disp(offset, adr_type);

  if (base == nullptr || base == NodeSentinel) {
    return nullptr;
  }

  if (offset == 0 && base->is_Mach() && base->as_Mach()->ideal_Opcode() == Op_AddP) {
    // The memory address is computed by 'base' and fed to 'mach' via an
    // indirect memory operand (indicated by offset == 0). The ultimate base and
    // offset can be fetched directly from the inputs and Ideal type of 'base'.
    offset = base->bottom_type()->isa_oopptr()->offset();
    // Even if 'base' is not an Ideal AddP node anymore, Matcher::ReduceInst()
    // guarantees that the base address is still available at the same slot.
    base = base->in(AddPNode::Base);
    assert(base != nullptr, "");
  }

  if (is_undefined(offset) || (is_concrete(offset) && offset < 0)) {
    return nullptr;
  }

  return look_through_node(base);
}

// Whether a phi node corresponds to an array allocation.
// This test is incomplete: in some edge cases, it might return false even
// though the node does correspond to an array allocation.
static bool is_array_allocation(const Node* phi) {
  precond(phi->is_Phi());
  // Check whether phi has a successor cast (CheckCastPP) to Java array pointer,
  // possibly below spill copies and other cast nodes. Limit the exploration to
  // a single path from the phi node consisting of these node types.
  const Node* current = phi;
  while (true) {
    const Node* next = nullptr;
    for (DUIterator_Fast imax, i = current->fast_outs(imax); i < imax; i++) {
      if (!current->fast_out(i)->isa_Mach()) {
        continue;
      }
      const MachNode* succ = current->fast_out(i)->as_Mach();
      if (succ->ideal_Opcode() == Op_CheckCastPP) {
        if (succ->get_ptr_type()->isa_aryptr()) {
          // Cast to Java array pointer: phi corresponds to an array allocation.
          return true;
        }
        // Other cast: record as candidate for further exploration.
        next = succ;
      } else if (succ->is_SpillCopy() && next == nullptr) {
        // Spill copy, and no better candidate found: record as candidate.
        next = succ;
      }
    }
    if (next == nullptr) {
      // No evidence found that phi corresponds to an array allocation, and no
      // candidates available to continue exploring.
      return false;
    }
    // Continue exploring from the best candidate found.
    current = next;
  }
  ShouldNotReachHere();
}

// Match the phi node that connects a TLAB allocation fast path with its slowpath
static bool is_allocation(const Node* node) {
  if (node->req() != 3) {
    return false;
  }
  const Node* const fast_node = node->in(2);
  if (!fast_node->is_Mach()) {
    return false;
  }
  const MachNode* const fast_mach = fast_node->as_Mach();
  if (fast_mach->ideal_Opcode() != Op_LoadP) {
    return false;
  }
  const TypePtr* const adr_type = nullptr;
  intptr_t offset;
  const Node* const base = get_base_and_offset(fast_mach, offset);
  if (base == nullptr || !base->is_Mach() || !is_concrete(offset)) {
    return false;
  }
  const MachNode* const base_mach = base->as_Mach();
  if (base_mach->ideal_Opcode() != Op_ThreadLocal) {
    return false;
  }
  return offset == in_bytes(Thread::tlab_top_offset());
}

static void elide_mach_barrier(MachNode* mach) {
  mach->set_barrier_data(ZBarrierElided);
}

void ZBarrierSetC2::analyze_dominating_barriers_impl(Node_List& accesses, Node_List& access_dominators) const {
  Compile* const C = Compile::current();
  PhaseCFG* const cfg = C->cfg();

  for (uint i = 0; i < accesses.size(); i++) {
    MachNode* const access = accesses.at(i)->as_Mach();
    intptr_t access_offset;
    const Node* const access_obj = get_base_and_offset(access, access_offset);
    Block* const access_block = cfg->get_block_for_node(access);
    const uint access_index = block_index(access_block, access);

    if (access_obj == nullptr) {
      // No information available
      continue;
    }

    for (uint j = 0; j < access_dominators.size(); j++) {
     const  Node* const mem = access_dominators.at(j);
      if (mem->is_Phi()) {
        // Allocation node
        if (mem != access_obj) {
          continue;
        }
        if (is_unknown(access_offset) && !is_array_allocation(mem)) {
          // The accessed address has an unknown offset, but the allocated
          // object cannot be determined to be an array. Avoid eliding in this
          // case, to be on the safe side.
          continue;
        }
        assert((is_concrete(access_offset) && access_offset >= 0) || (is_unknown(access_offset) && is_array_allocation(mem)),
               "candidate allocation-dominated access offsets must be either concrete and nonnegative, or unknown (for array allocations only)");
      } else {
        // Access node
        const MachNode* const mem_mach = mem->as_Mach();
        intptr_t mem_offset;
        const Node* const mem_obj = get_base_and_offset(mem_mach, mem_offset);

        if (mem_obj == nullptr ||
            !is_concrete(access_offset) ||
            !is_concrete(mem_offset)) {
          // No information available
          continue;
        }

        if (mem_obj != access_obj || mem_offset != access_offset) {
          // Not the same addresses, not a candidate
          continue;
        }
        assert(is_concrete(access_offset) && access_offset >= 0,
               "candidate non-allocation-dominated access offsets must be concrete and nonnegative");
      }

      Block* mem_block = cfg->get_block_for_node(mem);
      const uint mem_index = block_index(mem_block, mem);

      if (access_block == mem_block) {
        // Earlier accesses in the same block
        if (mem_index < access_index && !block_has_safepoint(mem_block, mem_index + 1, access_index)) {
          elide_mach_barrier(access);
        }
      } else if (mem_block->dominates(access_block)) {
        // Dominating block? Look around for safepoints
        ResourceMark rm;
        Block_List stack;
        VectorSet visited;
        stack.push(access_block);
        bool safepoint_found = block_has_safepoint(access_block);
        while (!safepoint_found && stack.size() > 0) {
          const Block* const block = stack.pop();
          if (visited.test_set(block->_pre_order)) {
            continue;
          }
          if (block_has_safepoint(block)) {
            safepoint_found = true;
            break;
          }
          if (block == mem_block) {
            continue;
          }

          // Push predecessor blocks
          for (uint p = 1; p < block->num_preds(); ++p) {
            Block* const pred = cfg->get_block_for_node(block->pred(p));
            stack.push(pred);
          }
        }

        if (!safepoint_found) {
          elide_mach_barrier(access);
        }
      }
    }
  }
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
  analyze_dominating_barriers_impl(loads, load_dominators);
  analyze_dominating_barriers_impl(stores, store_dominators);
  analyze_dominating_barriers_impl(atomics, atomic_dominators);
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
