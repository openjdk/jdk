/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/castnode.hpp"
#include "opto/convertnode.hpp"
#include "opto/vectornode.hpp"
#include "opto/vtransform.hpp"

void VTransformGraph::add_vtnode(VTransformNode* vtnode) {
  assert(vtnode->_idx == _vtnodes.length(), "position must match idx");
  _vtnodes.push(vtnode);
}

// Compute a linearization of the graph. We do this with a reverse-post-order of a DFS.
// This only works if the graph is a directed acyclic graph (DAG). The C2 graph, and
// the VLoopDependencyGraph are both DAGs, but after introduction of vectors/packs, the
// graph has additional constraints which can introduce cycles. Example:
//
//                                                       +--------+
//  A -> X                                               |        v
//                     Pack [A,B] and [X,Y]             [A,B]    [X,Y]
//  Y -> B                                                 ^        |
//                                                         +--------+
//
// We return "true" IFF we find no cycle, i.e. if the linearization succeeds.
bool VTransformGraph::schedule() {
  assert(!is_scheduled(), "not yet scheduled");

#ifndef PRODUCT
  if (_trace._verbose) {
    print_vtnodes();
  }
#endif

  ResourceMark rm;
  GrowableArray<VTransformNode*> stack;
  VectorSet pre_visited;
  VectorSet post_visited;

  collect_nodes_without_req_or_dependency(stack);

  // We create a reverse-post-visit order. This gives us a linearization, if there are
  // no cycles. Then, we simply reverse the order, and we have a schedule.
  int rpo_idx = _vtnodes.length() - 1;
  while (!stack.is_empty()) {
    VTransformNode* vtn = stack.top();
    if (!pre_visited.test_set(vtn->_idx)) {
      // Forward arc in graph (pre-visit).
    } else if (!post_visited.test(vtn->_idx)) {
      // Forward arc in graph. Check if all uses were already visited:
      //   Yes -> post-visit.
      //   No  -> we are mid-visit.
      bool all_uses_already_visited = true;

      for (int i = 0; i < vtn->outs(); i++) {
        VTransformNode* use = vtn->out(i);
        if (post_visited.test(use->_idx)) { continue; }
        if (pre_visited.test(use->_idx)) {
          // Cycle detected!
          // The nodes that are pre_visited but not yet post_visited form a path from
          // the "root" to the current vtn. Now, we are looking at an edge (vtn, use),
          // and discover that use is also pre_visited but not post_visited. Thus, use
          // lies on that path from "root" to vtn, and the edge (vtn, use) closes a
          // cycle.
          NOT_PRODUCT(if (_trace._rejections) { trace_schedule_cycle(stack, pre_visited, post_visited); } )
          return false;
        }
        stack.push(use);
        all_uses_already_visited = false;
      }

      if (all_uses_already_visited) {
        stack.pop();
        post_visited.set(vtn->_idx);           // post-visit
        _schedule.at_put_grow(rpo_idx--, vtn); // assign rpo_idx
      }
    } else {
      stack.pop(); // Already post-visited. Ignore secondary edge.
    }
  }

#ifndef PRODUCT
  if (_trace._verbose) {
    print_schedule();
  }
#endif

  assert(rpo_idx == -1, "used up all rpo_idx, rpo_idx=%d", rpo_idx);
  return true;
}

// Push all "root" nodes, i.e. those that have no inputs (req or dependency):
void VTransformGraph::collect_nodes_without_req_or_dependency(GrowableArray<VTransformNode*>& stack) const {
  for (int i = 0; i < _vtnodes.length(); i++) {
    VTransformNode* vtn = _vtnodes.at(i);
    if (!vtn->has_req_or_dependency()) {
      stack.push(vtn);
    }
  }
}

#ifndef PRODUCT
void VTransformGraph::trace_schedule_cycle(const GrowableArray<VTransformNode*>& stack,
                                           const VectorSet& pre_visited,
                                           const VectorSet& post_visited) const {
  tty->print_cr("\nVTransform::schedule found a cycle on path (P), vectorization attempt fails.");
  for (int j = 0; j < stack.length(); j++) {
    VTransformNode* n = stack.at(j);
    bool on_path = pre_visited.test(n->_idx) && !post_visited.test(n->_idx);
    tty->print("  %s ", on_path ? "P" : "_");
    n->print();
  }
}

void VTransformApplyResult::trace(VTransformNode* vtnode) const {
  tty->print("  apply: ");
  vtnode->print();
  tty->print("    ->   ");
  if (_node == nullptr) {
    tty->print_cr("nullptr");
  } else {
    _node->dump();
  }
}
#endif

void VTransform::apply_speculative_runtime_checks() {
  if (VLoop::vectors_should_be_aligned()) {
#ifdef ASSERT
    if (_trace._align_vector || _trace._speculative_runtime_checks) {
      tty->print_cr("\nVTransform::apply_speculative_runtime_checks: native memory alignment");
    }
#endif

    const GrowableArray<VTransformNode*>& vtnodes = _graph.vtnodes();
    for (int i = 0; i < vtnodes.length(); i++) {
      VTransformVectorNode* vtn = vtnodes.at(i)->isa_Vector();
      if (vtn == nullptr) { continue; }
      MemNode* p0 = vtn->nodes().at(0)->isa_Mem();
      if (p0 == nullptr) { continue; }
      const VPointer& vp = vpointer(p0);
      if (vp.mem_pointer().base().is_object()) { continue; }
      assert(vp.mem_pointer().base().is_native(), "VPointer base must be object or native");

      // We have a native memory reference. Build a runtime check for it.
      // See: AlignmentSolver::solve
      // In a future RFE we may be able to speculate on invar alignment as
      // well, and allow vectorization of more cases.
      add_speculative_alignment_check(vp.mem_pointer().base().native(), ObjectAlignmentInBytes);
    }
  }
}

#define TRACE_SPECULATIVE_ALIGNMENT_CHECK(node) {                     \
  DEBUG_ONLY(                                                         \
    if (_trace._align_vector || _trace._speculative_runtime_checks) { \
      tty->print("  " #node ": ");                                    \
      node->dump();                                                   \
    }                                                                 \
  )                                                                   \
}                                                                     \

// Check: (node % alignment) == 0.
void VTransform::add_speculative_alignment_check(Node* node, juint alignment) {
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(node);
  Node* ctrl = phase()->get_ctrl(node);

  // Cast adr/long -> int
  if (node->bottom_type()->basic_type() == T_ADDRESS) {
    // adr -> int/long
    node = new CastP2XNode(nullptr, node);
    phase()->register_new_node(node, ctrl);
    TRACE_SPECULATIVE_ALIGNMENT_CHECK(node);
  }
  if (node->bottom_type()->basic_type() == T_LONG) {
    // long -> int
    node  = new ConvL2INode(node);
    phase()->register_new_node(node, ctrl);
    TRACE_SPECULATIVE_ALIGNMENT_CHECK(node);
  }

  Node* mask_alignment = igvn().intcon(alignment-1);
  Node* base_alignment = new AndINode(node, mask_alignment);
  phase()->register_new_node(base_alignment, ctrl);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(mask_alignment);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(base_alignment);

  Node* zero = igvn().intcon(0);
  Node* cmp_alignment = CmpNode::make(base_alignment, zero, T_INT, false);
  BoolNode* bol_alignment = new BoolNode(cmp_alignment, BoolTest::eq);
  phase()->register_new_node(cmp_alignment, ctrl);
  phase()->register_new_node(bol_alignment, ctrl);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(cmp_alignment);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(bol_alignment);

  add_speculative_check(bol_alignment);
}

void VTransform::add_speculative_check(BoolNode* bol) {
  assert(_vloop.are_speculative_checks_possible(), "otherwise we cannot make speculative assumptions");
  ParsePredicateSuccessProj* parse_predicate_proj = _vloop.auto_vectorization_parse_predicate_proj();
  IfTrueNode* new_check_proj = nullptr;
  if (parse_predicate_proj != nullptr) {
    new_check_proj = phase()->create_new_if_for_predicate(parse_predicate_proj, nullptr,
                                                          Deoptimization::Reason_auto_vectorization_check,
                                                          Op_If);
  } else {
    new_check_proj = phase()->create_new_if_for_multiversion(_vloop.multiversioning_fast_proj());
  }
  Node* iff_speculate = new_check_proj->in(0);
  igvn().replace_input_of(iff_speculate, 1, bol);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(iff_speculate);
}

// Helper-class for VTransformGraph::has_store_to_load_forwarding_failure.
// It wraps a VPointer. The VPointer has an iv_offset applied, which
// simulates a virtual unrolling. They represent the memory region:
//   [adr, adr + size)
//   adr = base + invar + iv_scale * (iv + iv_offset) + con
class VMemoryRegion : public ResourceObj {
private:
  // Note: VPointer has no default constructor, so we cannot use VMemoryRegion
  //       in-place in a GrowableArray. Hence, we make VMemoryRegion a resource
  //       allocated object, so the GrowableArray of VMemoryRegion* has a default
  //       nullptr element.
  const VPointer _vpointer;
  bool _is_load;      // load or store?
  uint _schedule_order;

public:
  VMemoryRegion(const VPointer& vpointer, bool is_load, uint schedule_order) :
    _vpointer(vpointer),
    _is_load(is_load),
    _schedule_order(schedule_order) {}

    const VPointer& vpointer() const { return _vpointer; }
    bool is_load()        const { return _is_load; }
    uint schedule_order() const { return _schedule_order; }

    static int cmp_for_sort_by_group(VMemoryRegion* r1, VMemoryRegion* r2) {
      // Sort by mem_pointer (base, invar, iv_scale), except for the con.
      return MemPointer::cmp_summands(r1->vpointer().mem_pointer(),
                                      r2->vpointer().mem_pointer());
    }

    static int cmp_for_sort(VMemoryRegion** r1, VMemoryRegion** r2) {
      int cmp_group = cmp_for_sort_by_group(*r1, *r2);
      if (cmp_group != 0) { return cmp_group; }

      // We use two comparisons, because a subtraction could underflow.
      jint con1 = (*r1)->vpointer().con();
      jint con2 = (*r2)->vpointer().con();
      if (con1 < con2) { return -1; }
      if (con1 > con2) { return  1; }
      return 0;
    }

    enum Aliasing { DIFFERENT_GROUP, BEFORE, EXACT_OVERLAP, PARTIAL_OVERLAP, AFTER };

    Aliasing aliasing(VMemoryRegion& other) {
      VMemoryRegion* p1 = this;
      VMemoryRegion* p2 = &other;
      if (cmp_for_sort_by_group(p1, p2) != 0) { return DIFFERENT_GROUP; }

      jlong con1 = p1->vpointer().con();
      jlong con2 = p2->vpointer().con();
      jlong size1 = p1->vpointer().size();
      jlong size2 = p2->vpointer().size();

      if (con1 >= con2 + size2) { return AFTER; }
      if (con2 >= con1 + size1) { return BEFORE; }
      if (con1 == con2 && size1 == size2) { return EXACT_OVERLAP; }
      return PARTIAL_OVERLAP;
    }

#ifndef PRODUCT
  void print() const {
    tty->print("VMemoryRegion[%s schedule_order(%4d), ",
               _is_load ? "load, " : "store,", _schedule_order);
    vpointer().print_on(tty, false);
    tty->print_cr("]");
  }
#endif
};

// Store-to-load-forwarding is a CPU memory optimization, where a load can directly fetch
// its value from the store-buffer, rather than from the L1 cache. This is many CPU cycles
// faster. However, this optimization comes with some restrictions, depending on the CPU.
// Generally, store-to-load-forwarding works if the load and store memory regions match
// exactly (same start and width). Generally problematic are partial overlaps - though
// some CPU's can handle even some subsets of these cases. We conservatively assume that
// all such partial overlaps lead to a store-to-load-forwarding failures, which means the
// load has to stall until the store goes from the store-buffer into the L1 cache, incurring
// a penalty of many CPU cycles.
//
// Example (with "iteration distance" 2):
//   for (int i = 10; i < SIZE; i++) {
//       aI[i] = aI[i - 2] + 1;
//   }
//
//   load_4_bytes( ptr +  -8)
//   store_4_bytes(ptr +   0)    *
//   load_4_bytes( ptr +  -4)    |
//   store_4_bytes(ptr +   4)    | *
//   load_4_bytes( ptr +   0)  <-+ |
//   store_4_bytes(ptr +   8)      |
//   load_4_bytes( ptr +   4)  <---+
//   store_4_bytes(ptr +  12)
//   ...
//
//   In the scalar loop, we can forward the stores from 2 iterations back.
//
// Assume we have 2-element vectors (2*4 = 8 bytes), with the "iteration distance" 2
// example. This gives us this machine code:
//   load_8_bytes( ptr +  -8)
//   store_8_bytes(ptr +   0) |
//   load_8_bytes( ptr +   0) v
//   store_8_bytes(ptr +   8)   |
//   load_8_bytes( ptr +   8)   v
//   store_8_bytes(ptr +  16)
//   ...
//
//   We packed 2 iterations, and the stores can perfectly forward to the loads of
//   the next 2 iterations.
//
// Example (with "iteration distance" 3):
//   for (int i = 10; i < SIZE; i++) {
//       aI[i] = aI[i - 3] + 1;
//   }
//
//   load_4_bytes( ptr + -12)
//   store_4_bytes(ptr +   0)    *
//   load_4_bytes( ptr +  -8)    |
//   store_4_bytes(ptr +   4)    |
//   load_4_bytes( ptr +  -4)    |
//   store_4_bytes(ptr +   8)    |
//   load_4_bytes( ptr +   0)  <-+
//   store_4_bytes(ptr +  12)
//   ...
//
//   In the scalar loop, we can forward the stores from 3 iterations back.
//
// Unfortunately, vectorization can introduce such store-to-load-forwarding failures.
// Assume we have 2-element vectors (2*4 = 8 bytes), with the "iteration distance" 3
// example. This gives us this machine code:
//   load_8_bytes( ptr + -12)
//   store_8_bytes(ptr +   0)  |   |
//   load_8_bytes( ptr +  -4)  x   |
//   store_8_bytes(ptr +   8)     ||
//   load_8_bytes( ptr +   4)     xx  <-- partial overlap with 2 stores
//   store_8_bytes(ptr +  16)
//   ...
//
// We see that eventually all loads are dependent on earlier stores, but the values cannot
// be forwarded because there is some partial overlap.
//
// Preferably, we would have some latency-based cost-model that accounts for such forwarding
// failures, and decide if vectorization with forwarding failures is still profitable. For
// now we go with a simpler heuristic: we simply forbid vectorization if we can PROVE that
// there will be a forwarding failure. This approach has at least 2 possible weaknesses:
//
//  (1) There may be forwarding failures in cases where we cannot prove it.
//      Example:
//        for (int i = 10; i < SIZE; i++) {
//            bI[i] = aI[i - 3] + 1;
//        }
//
//      We do not know if aI and bI refer to the same array or not. However, it is reasonable
//      to assume that if we have two different array references, that they most likely refer
//      to different arrays (i.e. no aliasing), where we would have no forwarding failures.
//  (2) There could be some loops where vectorization introduces forwarding failures, and thus
//      the latency of the loop body is high, but this does not matter because it is dominated
//      by other latency/throughput based costs in the loop body.
//
// Performance measurements with the JMH benchmark StoreToLoadForwarding.java have indicated
// that there is some iteration threshold: if the failure happens between a store and load that
// have an iteration distance below this threshold, the latency is the limiting factor, and we
// should not vectorize to avoid the latency penalty of store-to-load-forwarding failures. If
// the iteration distance is larger than this threshold, the throughput is the limiting factor,
// and we should vectorize in these cases to improve throughput.
//
bool VTransformGraph::has_store_to_load_forwarding_failure(const VLoopAnalyzer& vloop_analyzer) const {
  if (SuperWordStoreToLoadForwardingFailureDetection == 0) { return false; }

  // Collect all pointers for scalar and vector loads/stores.
  ResourceMark rm;
  // Use pointers because no default constructor for elements available.
  GrowableArray<VMemoryRegion*> memory_regions;

  // To detect store-to-load-forwarding failures at the iteration threshold or below, we
  // simulate a super-unrolling to reach SuperWordStoreToLoadForwardingFailureDetection
  // iterations at least. This is a heuristic, and we are not trying to be very precise
  // with the iteration distance. If we have already unrolled more than the iteration
  // threshold, i.e. if "SuperWordStoreToLoadForwardingFailureDetection < unrolled_count",
  // then we simply check if there are any store-to-load-forwarding failures in the unrolled
  // loop body, which may be at larger distance than the desired threshold. We cannot do any
  // more fine-grained analysis, because the unrolling has lost the information about the
  // iteration distance.
  int simulated_unrolling_count = SuperWordStoreToLoadForwardingFailureDetection;
  int unrolled_count = vloop_analyzer.vloop().cl()->unrolled_count();
  uint simulated_super_unrolling_count = MAX2(1, simulated_unrolling_count / unrolled_count);
  int iv_stride = vloop_analyzer.vloop().iv_stride();
  int schedule_order = 0;
  for (uint k = 0; k < simulated_super_unrolling_count; k++) {
    int iv_offset = k * iv_stride; // virtual super-unrolling
    for (int i = 0; i < _schedule.length(); i++) {
      VTransformNode* vtn = _schedule.at(i);
      if (vtn->is_load_or_store_in_loop()) {
        const VPointer& p = vtn->vpointer(vloop_analyzer);
        if (p.is_valid()) {
          VTransformVectorNode* vector = vtn->isa_Vector();
          bool is_load = vtn->is_load_in_loop();
          const VPointer iv_offset_p(p.make_with_iv_offset(iv_offset));
          if (iv_offset_p.is_valid()) {
            // The iv_offset may lead to overflows. This is a heuristic, so we do not
            // care too much about those edge cases.
            memory_regions.push(new VMemoryRegion(iv_offset_p, is_load, schedule_order++));
          }
        }
      }
    }
  }

  // Sort the pointers by group (same base, invar and stride), and then by offset.
  memory_regions.sort(VMemoryRegion::cmp_for_sort);

#ifndef PRODUCT
  if (_trace._verbose) {
    tty->print_cr("VTransformGraph::has_store_to_load_forwarding_failure:");
    tty->print_cr("  simulated_unrolling_count = %d", simulated_unrolling_count);
    tty->print_cr("  simulated_super_unrolling_count = %d", simulated_super_unrolling_count);
    for (int i = 0; i < memory_regions.length(); i++) {
      VMemoryRegion& region = *memory_regions.at(i);
      region.print();
    }
  }
#endif

  // For all pairs of pointers in the same group, check if they have a partial overlap.
  for (int i = 0; i < memory_regions.length(); i++) {
    VMemoryRegion& region1 = *memory_regions.at(i);

    for (int j = i + 1; j < memory_regions.length(); j++) {
      VMemoryRegion& region2 = *memory_regions.at(j);

      const VMemoryRegion::Aliasing aliasing = region1.aliasing(region2);
      if (aliasing == VMemoryRegion::Aliasing::DIFFERENT_GROUP ||
          aliasing == VMemoryRegion::Aliasing::BEFORE) {
        break; // We have reached the next group or pointers that are always after.
      } else if (aliasing == VMemoryRegion::Aliasing::EXACT_OVERLAP) {
        continue;
      } else {
        assert(aliasing == VMemoryRegion::Aliasing::PARTIAL_OVERLAP, "no other case can happen");
        if ((region1.is_load() && !region2.is_load() && region1.schedule_order() > region2.schedule_order()) ||
            (!region1.is_load() && region2.is_load() && region1.schedule_order() < region2.schedule_order())) {
          // We predict that this leads to a store-to-load-forwarding failure penalty.
#ifndef PRODUCT
          if (_trace._rejections) {
            tty->print_cr("VTransformGraph::has_store_to_load_forwarding_failure:");
            tty->print_cr("  Partial overlap of store->load. We predict that this leads to");
            tty->print_cr("  a store-to-load-forwarding failure penalty which makes");
            tty->print_cr("  vectorization unprofitable. These are the two pointers:");
            region1.print();
            region2.print();
          }
#endif
          return true;
        }
      }
    }
  }

  return false;
}

Node* VTransformNode::find_transformed_input(int i, const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* n = vnode_idx_to_transformed_node.at(in(i)->_idx);
  assert(n != nullptr, "must find input IR node");
  return n;
}

VTransformApplyResult VTransformScalarNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                  const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  // This was just wrapped. Now we simply unwap without touching the inputs.
  return VTransformApplyResult::make_scalar(_node);
}

VTransformApplyResult VTransformReplicateNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                     const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* val = find_transformed_input(1, vnode_idx_to_transformed_node);
  VectorNode* vn = VectorNode::scalar2vector(val, _vlen, _element_type);
  register_new_node_from_vectorization(vloop_analyzer, vn, val);
  return VTransformApplyResult::make_vector(vn, _vlen, vn->length_in_bytes());
}

VTransformApplyResult VTransformConvI2LNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                   const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* val = find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* n = new ConvI2LNode(val);
  register_new_node_from_vectorization(vloop_analyzer, n, val);
  return VTransformApplyResult::make_scalar(n);
}

VTransformApplyResult VTransformShiftCountNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  Node* shift_count_in = find_transformed_input(1, vnode_idx_to_transformed_node);
  assert(shift_count_in->bottom_type()->isa_int(), "int type only for shift count");
  // The shift_count_in would be automatically truncated to the lowest _mask
  // bits in a scalar shift operation. But vector shift does not truncate, so
  // we must apply the mask now.
  Node* shift_count_masked = new AndINode(shift_count_in, phase->igvn().intcon(_mask));
  register_new_node_from_vectorization(vloop_analyzer, shift_count_masked, shift_count_in);
  // Now that masked value is "boadcast" (some platforms only set the lowest element).
  VectorNode* vn = VectorNode::shift_count(_shift_opcode, shift_count_masked, _vlen, _element_bt);
  register_new_node_from_vectorization(vloop_analyzer, vn, shift_count_in);
  return VTransformApplyResult::make_vector(vn, _vlen, vn->length_in_bytes());
}


VTransformApplyResult VTransformPopulateIndexNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                         const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  Node* val = find_transformed_input(1, vnode_idx_to_transformed_node);
  assert(val->is_Phi(), "expected to be iv");
  assert(VectorNode::is_populate_index_supported(_element_bt), "should support");
  const TypeVect* vt = TypeVect::make(_element_bt, _vlen);
  VectorNode* vn = new PopulateIndexNode(val, phase->igvn().intcon(1), vt);
  register_new_node_from_vectorization(vloop_analyzer, vn, val);
  return VTransformApplyResult::make_vector(vn, _vlen, vn->length_in_bytes());
}

VTransformApplyResult VTransformElementWiseVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                             const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* first = nodes().at(0);
  uint  vlen = nodes().length();
  int   opc  = first->Opcode();
  BasicType bt = vloop_analyzer.types().velt_basic_type(first);

  if (first->is_Cmp()) {
    // Cmp + Bool -> VectorMaskCmp
    // Handled by Bool / VTransformBoolVectorNode, so we do not generate any nodes here.
    return VTransformApplyResult::make_empty();
  }

  assert(2 <= req() && req() <= 4, "Must have 1-3 inputs");
  VectorNode* vn = nullptr;
  Node* in1 =                find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* in2 = (req() >= 3) ? find_transformed_input(2, vnode_idx_to_transformed_node) : nullptr;
  Node* in3 = (req() >= 4) ? find_transformed_input(3, vnode_idx_to_transformed_node) : nullptr;

  if (first->is_CMove()) {
    assert(req() == 4, "three inputs expected: mask, blend1, blend2");
    vn = new VectorBlendNode(/* blend1 */ in2, /* blend2 */ in3, /* mask */ in1);
  } else if (VectorNode::is_convert_opcode(opc)) {
    assert(first->req() == 2 && req() == 2, "only one input expected");
    int vopc = VectorCastNode::opcode(opc, in1->bottom_type()->is_vect()->element_basic_type());
    vn = VectorCastNode::make(vopc, in1, bt, vlen);
  } else if (VectorNode::is_reinterpret_opcode(opc)) {
    assert(first->req() == 2 && req() == 2, "only one input expected");
    const TypeVect* vt = TypeVect::make(bt, vlen);
    vn = new VectorReinterpretNode(in1, vt, in1->bottom_type()->is_vect());
  } else if (VectorNode::can_use_RShiftI_instead_of_URShiftI(first, bt)) {
    opc = Op_RShiftI;
    vn = VectorNode::make(opc, in1, in2, vlen, bt);
  } else if (VectorNode::is_scalar_op_that_returns_int_but_vector_op_returns_long(opc)) {
    // The scalar operation was a long -> int operation.
    // However, the vector operation is long -> long.
    VectorNode* long_vn = VectorNode::make(opc, in1, nullptr, vlen, T_LONG);
    register_new_node_from_vectorization(vloop_analyzer, long_vn, first);
    // Cast long -> int, to mimic the scalar long -> int operation.
    vn = VectorCastNode::make(Op_VectorCastL2X, long_vn, T_INT, vlen);
  } else if (req() == 3 ||
             VectorNode::is_scalar_unary_op_with_equal_input_and_output_types(opc)) {
    assert(!VectorNode::is_roundopD(first) || in2->is_Con(), "rounding mode must be constant");
    vn = VectorNode::make(opc, in1, in2, vlen, bt); // unary and binary
  } else {
    assert(req() == 4, "three inputs expected");
    assert(opc == Op_FmaD  ||
           opc == Op_FmaF  ||
           opc == Op_FmaHF ||
           opc == Op_SignumF ||
           opc == Op_SignumD,
           "element wise operation must be from this list");
    vn = VectorNode::make(opc, in1, in2, in3, vlen, bt); // ternary
  }

  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->length_in_bytes());
}

VTransformApplyResult VTransformBoolVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  BoolNode* first = nodes().at(0)->as_Bool();
  uint  vlen = nodes().length();
  BasicType bt = vloop_analyzer.types().velt_basic_type(first);

  // Cmp + Bool -> VectorMaskCmp
  VTransformElementWiseVectorNode* vtn_cmp = in(1)->isa_ElementWiseVector();
  assert(vtn_cmp != nullptr && vtn_cmp->nodes().at(0)->is_Cmp(),
         "bool vtn expects cmp vtn as input");

  Node* cmp_in1 = vtn_cmp->find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* cmp_in2 = vtn_cmp->find_transformed_input(2, vnode_idx_to_transformed_node);
  BoolTest::mask mask = test()._mask;

  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  ConINode* mask_node  = phase->igvn().intcon((int)mask);
  const TypeVect* vt = TypeVect::make(bt, vlen);
  VectorNode* vn = new VectorMaskCmpNode(mask, cmp_in1, cmp_in2, mask_node, vt);
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->vect_type()->length_in_bytes());
}

VTransformApplyResult VTransformReductionVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                           const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* first = nodes().at(0);
  uint  vlen = nodes().length();
  int   opc  = first->Opcode();
  BasicType bt = first->bottom_type()->basic_type();

  Node* init = find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* vec  = find_transformed_input(2, vnode_idx_to_transformed_node);

  ReductionNode* vn = ReductionNode::make(opc, nullptr, init, vec, bt);
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->vect_type()->length_in_bytes());
}

VTransformApplyResult VTransformLoadVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  LoadNode* first = nodes().at(0)->as_Load();
  uint  vlen = nodes().length();
  Node* ctrl = first->in(MemNode::Control);
  Node* mem  = first->in(MemNode::Memory);
  Node* adr  = first->in(MemNode::Address);
  int   opc  = first->Opcode();
  const TypePtr* adr_type = first->adr_type();
  BasicType bt = vloop_analyzer.types().velt_basic_type(first);

  // Set the memory dependency of the LoadVector as early as possible.
  // Walk up the memory chain, and ignore any StoreVector that provably
  // does not have any memory dependency.
  const VPointer& load_p = vpointer(vloop_analyzer);
  while (mem->is_StoreVector()) {
    VPointer store_p(mem->as_Mem(), vloop_analyzer.vloop());
    if (store_p.never_overlaps_with(load_p)) {
      mem = mem->in(MemNode::Memory);
    } else {
      break;
    }
  }

  LoadVectorNode* vn = LoadVectorNode::make(opc, ctrl, mem, adr, adr_type, vlen, bt,
                                            control_dependency());
  DEBUG_ONLY( if (VerifyAlignVector) { vn->set_must_verify_alignment(); } )
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->memory_size());
}

VTransformApplyResult VTransformStoreVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                       const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  StoreNode* first = nodes().at(0)->as_Store();
  uint  vlen = nodes().length();
  Node* ctrl = first->in(MemNode::Control);
  Node* mem  = first->in(MemNode::Memory);
  Node* adr  = first->in(MemNode::Address);
  int   opc  = first->Opcode();
  const TypePtr* adr_type = first->adr_type();

  Node* value = find_transformed_input(MemNode::ValueIn, vnode_idx_to_transformed_node);
  StoreVectorNode* vn = StoreVectorNode::make(opc, ctrl, mem, adr, adr_type, value, vlen);
  DEBUG_ONLY( if (VerifyAlignVector) { vn->set_must_verify_alignment(); } )
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->memory_size());
}

void VTransformVectorNode::register_new_node_from_vectorization_and_replace_scalar_nodes(const VLoopAnalyzer& vloop_analyzer, Node* vn) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  Node* first = nodes().at(0);

  register_new_node_from_vectorization(vloop_analyzer, vn, first);

  for (int i = 0; i < _nodes.length(); i++) {
    Node* n = _nodes.at(i);
    phase->igvn().replace_node(n, vn);
  }
}

void VTransformNode::register_new_node_from_vectorization(const VLoopAnalyzer& vloop_analyzer, Node* vn, Node* old_node) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  phase->register_new_node_with_ctrl_of(vn, old_node);
  phase->igvn()._worklist.push(vn);
  VectorNode::trace_new_vector(vn, "AutoVectorization");
}

#ifndef PRODUCT
void VTransformGraph::print_vtnodes() const {
  tty->print_cr("\nVTransformGraph::print_vtnodes:");
  for (int i = 0; i < _vtnodes.length(); i++) {
    _vtnodes.at(i)->print();
  }
}

void VTransformGraph::print_schedule() const {
  tty->print_cr("\nVTransformGraph::print_schedule:");
  for (int i = 0; i < _schedule.length(); i++) {
    tty->print(" %3d: ", i);
    VTransformNode* vtn = _schedule.at(i);
    if (vtn == nullptr) {
      tty->print_cr("nullptr");
    } else {
      vtn->print();
    }
  }
}

void VTransformGraph::print_memops_schedule() const {
  tty->print_cr("\nVTransformGraph::print_memops_schedule:");
  int i = 0;
  for_each_memop_in_schedule([&] (MemNode* mem) {
    tty->print(" %3d: ", i++);
    mem->dump();
  });
}

void VTransformNode::print() const {
  tty->print("%3d %s (", _idx, name());
  for (uint i = 0; i < _req; i++) {
    print_node_idx(_in.at(i));
  }
  if ((uint)_in.length() > _req) {
    tty->print(" |");
    for (int i = _req; i < _in.length(); i++) {
      print_node_idx(_in.at(i));
    }
  }
  tty->print(") [");
  for (int i = 0; i < _out.length(); i++) {
    print_node_idx(_out.at(i));
  }
  tty->print("] ");
  print_spec();
  tty->cr();
}

void VTransformNode::print_node_idx(const VTransformNode* vtn) {
  if (vtn == nullptr) {
    tty->print(" _");
  } else {
    tty->print(" %d", vtn->_idx);
  }
}

void VTransformScalarNode::print_spec() const {
  tty->print("node[%d %s]", _node->_idx, _node->Name());
}

void VTransformReplicateNode::print_spec() const {
  tty->print("vlen=%d element_type=%s", _vlen, type2name(_element_type));
}

void VTransformShiftCountNode::print_spec() const {
  tty->print("vlen=%d element_bt=%s mask=%d shift_opcode=%s",
             _vlen, type2name(_element_bt), _mask,
             NodeClassNames[_shift_opcode]);
}

void VTransformPopulateIndexNode::print_spec() const {
  tty->print("vlen=%d element_bt=%s", _vlen, type2name(_element_bt));
}

void VTransformVectorNode::print_spec() const {
  tty->print("%d-pack[", _nodes.length());
  for (int i = 0; i < _nodes.length(); i++) {
    Node* n = _nodes.at(i);
    if (i > 0) {
      tty->print(", ");
    }
    tty->print("%d %s", n->_idx, n->Name());
  }
  tty->print("]");
}
#endif
