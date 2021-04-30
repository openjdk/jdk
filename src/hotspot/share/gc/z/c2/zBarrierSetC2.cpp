/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
    Iterator(ZArenaHashtable* table) :
      _table(table),
      _current_entry(table->_table[0]),
      _current_index(0) {
      if (_current_entry == NULL) {
        next();
      }
    }

    bool has_next() { return _current_entry != NULL; }
    K key()         { return _current_entry->_key; }
    V value()       { return _current_entry->_value; }

    void next() {
      if (_current_entry != NULL) {
        _current_entry = _current_entry->_next;
      }
      while (_current_entry == NULL && ++_current_index < _table_size) {
        _current_entry = _table->_table[_current_index];
      }
    }
  };

  ZArenaHashtable(Arena* arena) :
      _arena(arena),
      _table() {
    Copy::zero_to_bytes(&_table, sizeof(ZArenaHashtableEntry*) * _table_size);
  }

  void add(K key, V value) {
    ZArenaHashtableEntry* entry = new (_arena) ZArenaHashtableEntry();
    entry->_key = key;
    entry->_value = value;
    entry->_next = _table[key & _table_mask];
    _table[key & _table_mask] = entry;
  }

  V* get(K key) const {
    for (ZArenaHashtableEntry* e = _table[key & _table_mask]; e != NULL; e = e->_next) {
      if (e->_key == key) {
        return &(e->_value);
      }
    }
    return NULL;
  }

  Iterator iterator() {
    return Iterator(this);
  }
};

typedef ZArenaHashtable<intptr_t, bool, 4> ZOffsetTable;
typedef ZArenaHashtable<node_idx_t, ZOffsetTable*, 128> ZPrefetchTable;

class ZBarrierSetC2State : public ResourceObj {
private:
  GrowableArray<ZBarrierStubC2*>* _stubs;
  Node_Array                      _live;
  ZPrefetchTable*                 _prefetch_table;

public:
  ZBarrierSetC2State(Arena* arena) :
    _stubs(new (arena) GrowableArray<ZBarrierStubC2*>(arena, 8,  0, NULL)),
    _live(arena),
    _prefetch_table(new (arena) ZPrefetchTable(arena)) {}

  GrowableArray<ZBarrierStubC2*>* stubs() {
    return _stubs;
  }

  RegMask* live(const Node* node) {
    if (!node->is_Mach()) {
      // Don't need liveness for non-MachNodes
      return NULL;
    }

    const MachNode* const mach = node->as_Mach();
    if (mach->barrier_data() == ZBarrierElided) {
      // Don't need liveness data for nodes without barriers
      return NULL;
    }

    RegMask* live = (RegMask*)_live[node->_idx];
    if (live == NULL) {
      live = new (Compile::current()->comp_arena()->AmallocWords(sizeof(RegMask))) RegMask();
      _live.map(node->_idx, (Node*)live);
    }

    return live;
  }

  ZPrefetchTable* prefetch_table() {
    return _prefetch_table;
  }

  ZOffsetTable::Iterator prefetch_offsets(const Node* node) {
    ZOffsetTable* offsets = *_prefetch_table->get(node->_idx);
    return offsets->iterator();
  }
};

static ZBarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<ZBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

ZBarrierStubC2::ZBarrierStubC2(const MachNode* node) :
    _node(node),
    _entry(),
    _continuation() {}

Register ZBarrierStubC2::result() const {
  return noreg;
}

RegMask& ZBarrierStubC2::live() const {
  return *barrier_set_state()->live(_node);
}

Label* ZBarrierStubC2::entry() {
  // The _entry will never be bound when in_scratch_emit_size() is true.
  // However, we still need to return a label that is not bound now, but
  // will eventually be bound. Any eventually bound label will do, as it
  // will only act as a placeholder, so we return the _continuation label.
  return Compile::current()->output()->in_scratch_emit_size() ? &_continuation : &_entry;
}

Label* ZBarrierStubC2::continuation() {
  return &_continuation;
}

ZLoadBarrierStubC2* ZLoadBarrierStubC2::create(const MachNode* node, Address ref_addr, Register ref) {
  ZLoadBarrierStubC2* const stub = new (Compile::current()->comp_arena()) ZLoadBarrierStubC2(node, ref_addr, ref);
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(stub);
  }

  return stub;
}

ZLoadBarrierStubC2::ZLoadBarrierStubC2(const MachNode* node, Address ref_addr, Register ref) :
    ZBarrierStubC2(node),
    _ref_addr(ref_addr),
    _ref(ref) {
  assert_different_registers(ref, ref_addr.base());
  assert_different_registers(ref, ref_addr.index());
}

Address ZLoadBarrierStubC2::ref_addr() const {
  return _ref_addr;
}

Register ZLoadBarrierStubC2::ref() const {
  return _ref;
}

Register ZLoadBarrierStubC2::result() const {
  return ref();
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

ZStoreBarrierStubC2* ZStoreBarrierStubC2::create(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_atomic) {
  ZStoreBarrierStubC2* const stub = new (Compile::current()->comp_arena()) ZStoreBarrierStubC2(node, ref_addr, new_zaddress, new_zpointer, is_atomic);
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(stub);
  }

  return stub;
}

ZStoreBarrierStubC2::ZStoreBarrierStubC2(const MachNode* node, Address ref_addr, Register new_zaddress, Register new_zpointer, bool is_atomic) :
    ZBarrierStubC2(node),
    _ref_addr(ref_addr),
    _new_zaddress(new_zaddress),
    _new_zpointer(new_zpointer),
    _is_atomic(is_atomic) {
}

Address ZStoreBarrierStubC2::ref_addr() const {
  return _ref_addr;
}

Register ZStoreBarrierStubC2::new_zaddress() const {
  return _new_zaddress;
}

Register ZStoreBarrierStubC2::new_zpointer() const {
  return _new_zpointer;
}

bool ZStoreBarrierStubC2::is_atomic() const {
  return _is_atomic;
}

Register ZStoreBarrierStubC2::result() const {
  return noreg;
}

void ZStoreBarrierStubC2::emit_code(MacroAssembler& masm) {
  ZBarrierSet::assembler()->generate_c2_store_barrier_stub(&masm, static_cast<ZStoreBarrierStubC2*>(this));
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

  for (int i = 0; i < stubs->length(); i++) {
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(PhaseOutput::MAX_inst_size) && cb.blob() == NULL) {
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

// This TypeFunc assumes a 64bit system
static const TypeFunc* clone_type() {
  // Create input type (domain)
  const Type** domain_fields = TypeTuple::fields(4);
  domain_fields[TypeFunc::Parms + 0] = TypeInstPtr::NOTNULL;  // src
  domain_fields[TypeFunc::Parms + 1] = TypeInstPtr::NOTNULL;  // dst
  domain_fields[TypeFunc::Parms + 2] = TypeLong::LONG;        // size lower
  domain_fields[TypeFunc::Parms + 3] = Type::HALF;            // size upper
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + 4, domain_fields);

  // Create result type (range)
  const Type** range_fields = TypeTuple::fields(0);
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 0, range_fields);

  return TypeFunc::make(domain, range);
}

#define XTOP LP64_ONLY(COMMA phase->top())

void ZBarrierSetC2::clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const {
  Node* const src = ac->in(ArrayCopyNode::Src);
  const TypeAryPtr* ary_ptr = src->get_ptr_type()->isa_aryptr();

  if (ac->is_clone_array() && ary_ptr != NULL) {
    BasicType bt = ary_ptr->elem()->array_element_basic_type();
    if (is_reference_type(bt)) {
      // Clone object array
      bt = T_OBJECT;
    } else {
      // Clone primitive array
      bt = T_LONG;
    }

    Node* ctrl = ac->in(TypeFunc::Control);
    Node* mem = ac->in(TypeFunc::Memory);
    Node* src = ac->in(ArrayCopyNode::Src);
    Node* src_offset = ac->in(ArrayCopyNode::SrcPos);
    Node* dest = ac->in(ArrayCopyNode::Dest);
    Node* dest_offset = ac->in(ArrayCopyNode::DestPos);
    Node* length = ac->in(ArrayCopyNode::Length);

    if (bt == T_OBJECT) {
      // BarrierSetC2::clone sets the offsets via BarrierSetC2::arraycopy_payload_base_offset
      // which 8-byte aligns them to allow for word size copies. Make sure the offsets point
      // to the first element in the array when cloning object arrays. Otherwise, load
      // barriers are applied to parts of the header. Also adjust the length accordingly.
      assert(src_offset == dest_offset, "should be equal");
      jlong offset = src_offset->get_long();
      if (offset != arrayOopDesc::base_offset_in_bytes(T_OBJECT)) {
        assert(!UseCompressedClassPointers, "should only happen without compressed class pointers");
        assert((arrayOopDesc::base_offset_in_bytes(T_OBJECT) - offset) == BytesPerLong, "unexpected offset");
        length = phase->transform_later(new SubLNode(length, phase->longcon(1))); // Size is in longs
        src_offset = phase->longcon(arrayOopDesc::base_offset_in_bytes(T_OBJECT));
        dest_offset = src_offset;
      }
    }
    Node* payload_src = phase->basic_plus_adr(src, src_offset);
    Node* payload_dst = phase->basic_plus_adr(dest, dest_offset);

    const char* copyfunc_name = "arraycopy";
    address     copyfunc_addr = phase->basictype2arraycopy(bt, NULL, NULL, true, copyfunc_name, true);

    const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;
    const TypeFunc* call_type = OptoRuntime::fast_arraycopy_Type();

    Node* call = phase->make_leaf_call(ctrl, mem, call_type, copyfunc_addr, copyfunc_name, raw_adr_type, payload_src, payload_dst, length XTOP);
    phase->transform_later(call);

    phase->igvn().replace_node(ac, call);
    return;
  }

  // Clone instance
  Node* const ctrl       = ac->in(TypeFunc::Control);
  Node* const mem        = ac->in(TypeFunc::Memory);
  Node* const dst        = ac->in(ArrayCopyNode::Dest);
  Node* const size       = ac->in(ArrayCopyNode::Length);

  assert(size->bottom_type()->is_long(), "Should be long");

  // The native clone we are calling here expects the instance size in words
  // Add header/offset size to payload size to get instance size.
  Node* const base_offset = phase->longcon(arraycopy_payload_base_offset(ac->is_clone_array()) >> LogBytesPerLong);
  Node* const full_size = phase->transform_later(new AddLNode(size, base_offset));

  Node* const call = phase->make_leaf_call(ctrl,
                                           mem,
                                           clone_type(),
                                           ZBarrierSetRuntime::clone_addr(),
                                           "ZBarrierSetRuntime::clone",
                                           TypeRawPtr::BOTTOM,
                                           src,
                                           dst,
                                           full_size,
                                           phase->top());
  phase->transform_later(call);
  phase->igvn().replace_node(ac, call);
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
  while (node != NULL) {
    const Node* new_node = node;
    if (node->is_Mach()) {
      const MachNode* node_mach = node->as_Mach();
      if (node_mach->ideal_Opcode() == Op_CheckCastPP) {
        new_node = node->in(1);
      }
      if (node_mach->is_SpillCopy()) {
        new_node = node->in(1);
      }
    }
    if (new_node == node || new_node == NULL) {
      break;
    } else {
      node = new_node;
    }
  }

  return node;
}

static const Node* get_base_and_offset(const MachNode* mach, intptr_t& offset) {
  const TypePtr* adr_type = NULL;
  offset = 0;
  const Node* base = mach->get_base_and_disp(offset, adr_type);

  if (base == NULL || base == NodeSentinel || offset < 0) {
    return NULL;
  }

  return look_through_node(base);
}

// Match the phi node that connects a TLAB allocation fast path with its slowpath
static bool is_allocation(const Node* node) {
  if (node->req() != 3) {
    return false;
  }
  const Node* fast_node = node->in(2);
  if (!fast_node->is_Mach()) {
    return false;
  }
  const MachNode* fast_mach = fast_node->as_Mach();
  if (fast_mach->ideal_Opcode() != Op_LoadP) {
    return false;
  }
  const TypePtr* adr_type = NULL;
  intptr_t offset;
  const Node* base = get_base_and_offset(fast_mach, offset);
  if (base == NULL || !base->is_Mach()) {
    return false;
  }
  const MachNode* base_mach = base->as_Mach();
  if (base_mach->ideal_Opcode() != Op_ThreadLocal) {
    return false;
  }
  return offset == in_bytes(Thread::tlab_top_offset());
}

static void elide_mach_barrier(MachNode* mach) {
  mach->set_barrier_data(ZBarrierElided);
}

void ZBarrierSetC2::analyze_dominating_barriers_impl(Node_List& accesses, Node_List& access_dominators, bool prefetch) const {
  Block_List worklist;

  Compile* const C = Compile::current();
  PhaseCFG* const cfg = C->cfg();

  for (uint i = 0; i < accesses.size(); i++) {
    MachNode* const access = accesses.at(i)->as_Mach();
    intptr_t access_offset;
    const Node* const access_obj = get_base_and_offset(access, access_offset);
    Block* const access_block = cfg->get_block_for_node(access);
    const uint access_index = block_index(access_block, access);

    if (access_obj == NULL) {
      // No information available
      continue;
    }

    if (prefetch) {
      VectorSet visited_phis;
      analyze_prefetching(access, access_block, access_index, access_offset, access_obj, visited_phis);
    }

    for (uint j = 0; j < access_dominators.size(); j++) {
      Node* mem = access_dominators.at(j);
      if (mem->is_Phi()) {
        // Allocation node
        if (mem != access_obj) {
          continue;
        }
      } else {
        // Access node
        MachNode* mem_mach = mem->as_Mach();
        intptr_t mem_offset;
        const Node* mem_obj = get_base_and_offset(mem_mach, mem_offset);

        if (mem_obj == NULL) {
          // No information available
          continue;
        }

        if (mem_obj != access_obj || mem_offset != access_offset) {
          // Not the same addresses, not a candidate
          continue;
        }
      }

      Block* mem_block = cfg->get_block_for_node(mem);
      uint mem_index = block_index(mem_block, mem);

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
          Block* block = stack.pop();
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
            Block* pred = cfg->get_block_for_node(block->pred(p));
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
  analyze_dominating_barriers_impl(loads, load_dominators, false /* prefetch */);
  analyze_dominating_barriers_impl(stores, store_dominators, true /* prefetch */);
  analyze_dominating_barriers_impl(atomics, atomic_dominators, false /* prefetch */);
}

// == Reduced spilling optimization ==

void ZBarrierSetC2::compute_liveness_at_stubs() const {
  ResourceMark rm;
  Compile* const C = Compile::current();
  Arena* const A = Thread::current()->resource_area();
  PhaseCFG* const cfg = C->cfg();
  PhaseRegAlloc* const regalloc = C->regalloc();
  RegMask* const live = NEW_ARENA_ARRAY(A, RegMask, cfg->number_of_blocks() * sizeof(RegMask));
  ZBarrierSetAssembler* const bs = ZBarrierSet::assembler();
  Block_List worklist;

  for (uint i = 0; i < cfg->number_of_blocks(); ++i) {
    new ((void*)(live + i)) RegMask();
    worklist.push(cfg->get_block(i));
  }

  while (worklist.size() > 0) {
    const Block* const block = worklist.pop();
    RegMask& old_live = live[block->_pre_order];
    RegMask new_live;

    // Initialize to union of successors
    for (uint i = 0; i < block->_num_succs; i++) {
      const uint succ_id = block->_succs[i]->_pre_order;
      new_live.OR(live[succ_id]);
    }

    // Walk block backwards, computing liveness
    for (int i = block->number_of_nodes() - 1; i >= 0; --i) {
      const Node* const node = block->get_node(i);

      // Remove def bits
      const OptoReg::Name first = bs->refine_register(node, regalloc->get_reg_first(node));
      const OptoReg::Name second = bs->refine_register(node, regalloc->get_reg_second(node));
      if (first != OptoReg::Bad) {
        new_live.Remove(first);
      }
      if (second != OptoReg::Bad) {
        new_live.Remove(second);
      }

      // Add use bits
      for (uint j = 1; j < node->req(); ++j) {
        const Node* const use = node->in(j);
        const OptoReg::Name first = bs->refine_register(use, regalloc->get_reg_first(use));
        const OptoReg::Name second = bs->refine_register(use, regalloc->get_reg_second(use));
        if (first != OptoReg::Bad) {
          new_live.Insert(first);
        }
        if (second != OptoReg::Bad) {
          new_live.Insert(second);
        }
      }

      // If this node tracks liveness, update it
      RegMask* const regs = barrier_set_state()->live(node);
      if (regs != NULL) {
        regs->OR(new_live);
      }
    }

    // Now at block top, see if we have any changes
    new_live.SUBTRACT(old_live);
    if (new_live.is_NotEmpty()) {
      // Liveness has refined, update and propagate to prior blocks
      old_live.OR(new_live);
      for (uint i = 1; i < block->num_preds(); ++i) {
        Block* const pred = cfg->get_block_for_node(block->pred(i));
        worklist.push(pred);
      }
    }
  }
}

void ZBarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const {
  eliminate_gc_barrier_data(node);
}

void ZBarrierSetC2::eliminate_gc_barrier_data(Node* node) const {
  if (node->is_Mem()) {
    MemNode* mem = node->as_Mem();
    mem->set_barrier_data(ZBarrierElided);
  } else if (node->is_LoadStore()) {
    LoadStoreNode* loadstore = node->as_LoadStore();
    loadstore->set_barrier_data(ZBarrierElided);
  }
}

// == Prefetch optimization ==

void ZBarrierSetC2::register_prefetch(const Node* node, intptr_t offset) const {
  ZPrefetchTable* prefetch_table = barrier_set_state()->prefetch_table();
  ZOffsetTable** offsets_ptr = prefetch_table->get(node->_idx);
  ZOffsetTable* offsets;
  if (offsets_ptr == NULL) {
    Arena* arena = Compile::current()->comp_arena();
    offsets = new (arena) ZOffsetTable(arena);
    prefetch_table->add(node->_idx, offsets);
  } else {
    offsets = *offsets_ptr;
  }
  if (offsets->get(offset) == NULL) {
    offsets->add(offset, true);
  }
}

int z_offset_compare(intptr_t* a, intptr_t* b) {
  return *a - *b;
}

GrowableArray<intptr_t>* ZBarrierSetC2::prefetch_offsets(const Node* node) const {
  Arena* arena = Compile::current()->comp_arena();
  GrowableArray<intptr_t>* result = new (arena) GrowableArray<intptr_t>(arena, 2,  0, 0);
  ZPrefetchTable* prefetch_table = barrier_set_state()->prefetch_table();
  ZOffsetTable** offsets_ptr = prefetch_table->get(node->_idx);
  ZOffsetTable* offsets;
  if (offsets_ptr == NULL) {
    return result;
  } else {
    offsets = *offsets_ptr;
  }
  for (auto it = offsets->iterator(); it.has_next(); it.next()) {
    result->append(it.key());
  }
  result->sort(z_offset_compare);
  return result;
}

void ZBarrierSetC2::analyze_prefetching(const MachNode* access, Block* access_block, uint access_index, intptr_t access_offset, const Node* base, VectorSet& visited_phis) const {
  if (!ZPrefetchStores) {
    return;
  }

  static const float z_prefetch_block_probability = 0.0f;

  if (base->is_Phi()) {
    PhiNode* phi = base->as_Phi();
    if (is_allocation(phi)) {
      // No need to prefetch allocations; they already prefetch
      return;
    }
    if (!visited_phis.test_set(phi->_idx)) {
      for (uint i = 1; i < phi->req(); ++i) {
        const Node* new_base = look_through_node(phi->in(i));
        analyze_prefetching(access, access_block, access_index, access_offset, new_base, visited_phis);
      }
    }
    return;
  }

  if (!base->is_Mach() || base->as_Mach()->barrier_data() == 0) {
    // Not a ZGC load
    return;
  }

  Compile* const C = Compile::current();
  PhaseCFG* const cfg = C->cfg();

  // A load is the base pointer of a subsequent access. Check if it is worthwhile
  // to prefetch the subsequent access.
  Block* const base_block = cfg->get_block_for_node(base);
  if (base_block == access_block) {
    // Same block - prefetch if far enough away
    const uint base_index = block_index(base_block, base);
    if (access_index - base_index > 4) {
      // Far enough ahead; prefetch
      register_prefetch(base, access_offset);
    }
  } else if (access_block->_freq > z_prefetch_block_probability) {
    // The subsequent access is "likely" to be executed. Prefetch it!
    register_prefetch(base, access_offset);
  }
}
