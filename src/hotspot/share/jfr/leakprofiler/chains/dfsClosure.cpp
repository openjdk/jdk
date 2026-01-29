/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/leakprofiler/chains/dfsClosure.hpp"
#include "jfr/leakprofiler/chains/edge.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/jfrbitset.hpp"
#include "jfr/leakprofiler/chains/rootSetClosure.hpp"
#include "jfr/leakprofiler/utilities/granularTimer.hpp"
#include "jfr/leakprofiler/utilities/rootType.hpp"
#include "jfr/leakprofiler/utilities/unifiedOopRef.inline.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/stack.inline.hpp"

#ifdef ASSERT
void DFSClosure::log_reference_stack() {
  LogTarget(Trace, jfr, system, oldobject) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("--- ref stack ---");
    for (size_t i = 0; i <= _current_depth; i++) {
      const void* refaddr = (void*)_reference_stack[i].addr<uintptr_t>();
      if (refaddr != 0) {
        const oop pointee = _reference_stack[i].dereference();
        ls.print(PTR_FORMAT " " PTR_FORMAT " : ", p2i(refaddr), p2i(pointee));
        if (pointee != nullptr) {
          pointee->klass()->name()->print_value_on(&ls);
        }
      } else {
        ls.print(PTR_FORMAT " ??? : ", p2i(refaddr));
      }
      ls.cr();
    }
    ls.print_cr("--- /ref stack ---");
  }
}
#endif // ASSERT

UnifiedOopRef DFSClosure::_reference_stack[max_dfs_depth];

void DFSClosure::find_leaks_from_edge(EdgeStore* edge_store,
                                      JFRBitSet* mark_bits,
                                      const Edge* start_edge) {
  assert(edge_store != nullptr, "invariant");
  assert(mark_bits != nullptr," invariant");
  assert(start_edge != nullptr, "invariant");

  // Depth-first search, starting from a BFS edge
  DFSClosure dfs(edge_store, mark_bits, start_edge);
  log_debug(jfr, system, oldobject)("DFS: scanning from edge");
  const UnifiedOopRef ref = start_edge->reference();
  const oop obj = ref.dereference();
  dfs.probe_stack_push(ref, obj, 0);
  dfs.drain_probe_stack();
  log_debug(jfr, system, oldobject)("DFS: done");
}

void DFSClosure::find_leaks_from_root_set(EdgeStore* edge_store,
                                          JFRBitSet* mark_bits) {
  assert(edge_store != nullptr, "invariant");
  assert(mark_bits != nullptr, "invariant");

  // Mark root set, to avoid going sideways. The intent here is to prevent
  // long reference chains that would be caused by tracing through multiple root
  // objects.
  DFSClosure dfs(edge_store, mark_bits, nullptr);
  dfs._max_depth = 1;
  RootSetClosure<DFSClosure> rs(&dfs);
  log_debug(jfr, system, oldobject)("DFS: scanning roots...");
  rs.process();
  dfs.drain_probe_stack();

  // Depth-first search
  dfs._max_depth = max_dfs_depth;
  dfs._ignore_root_set = true;
  log_debug(jfr, system, oldobject)("DFS: scanning in depth ...");
  rs.process();
  dfs.drain_probe_stack();
  log_debug(jfr, system, oldobject)("DFS: done");
}

// Memory usage of DFS search is dominated by probe stack usage, which is
// (avg. number of outgoing references per oop) * depth.
//
// We disregard objArrayOop: deep graphs of broad object arrays are rare.
// But we also use array chunking, to be on the safe side.
//
// Statistically, instanceKlass oopmaps are very small. Moreover, deep
// graphs will typically consist of heavy hitters like LinkedListNode,
// which has just two references that need to be pushed onto the probe stack
// (its backward reference is typically already marked).
//
// Hence, at max_dfs_depth of 4000, on average we have a probe stack depth of
// ~10000, which costs us <~160KB. In practice, these numbers seem to be even
// smaller. Not a problem at all.
//
// But we could run into weird pathological object graphs. Therefore we also
// cap the max size of the probe stack. When we hit it, we deal with it the same
// way we deal with reaching max_dfs_depth - by aborting the trace of that
// particular graph edge.
static constexpr size_t max_probe_stack_elems = 256 * K; // 4 MB

static constexpr int array_chunk_size = 64;

DFSClosure::DFSClosure(EdgeStore* edge_store, JFRBitSet* mark_bits, const Edge* start_edge)
  :_edge_store(edge_store), _mark_bits(mark_bits), _start_edge(start_edge),
  _max_depth(max_dfs_depth), _ignore_root_set(false),
  _probe_stack(1024, 4, max_probe_stack_elems),
  _current_ref(UnifiedOopRef::encode_null()),
  _current_pointee(nullptr),
  _current_depth(0),
  _current_chunkindex(0),
  _num_objects_processed(0),
  _num_sampled_objects_found(0),
  _times_max_depth_reached(0),
  _times_probe_stack_full(0)
{
}

DFSClosure::~DFSClosure() {
  if (!GranularTimer::is_finished()) {
    assert(_probe_stack.is_empty(), "We should have drained the probe stack?");
  }
  log_info(jfr, system, oldobject)("DFS: objects processed: " UINT64_FORMAT ","
       " sampled objects found: " UINT64_FORMAT ","
       " reached max graph depth: " UINT64_FORMAT ","
       " reached max probe stack depth: " UINT64_FORMAT,
      _num_objects_processed, _num_sampled_objects_found,
      _times_max_depth_reached, _times_probe_stack_full);
}

void DFSClosure::probe_stack_push(UnifiedOopRef ref, oop pointee, size_t depth) {

  assert(!ref.is_null(), "invariant");

  if (_probe_stack.is_full()) {
    _times_probe_stack_full ++;
    return;
  }

  if (pointee == nullptr) {
    return;
  }

  if (depth > 0 && pointee_was_visited(pointee)) {
    // Optimization: don't push if marked (special handling for root oops)
    return;
  }

  ProbeStackItem item { ref, checked_cast<unsigned>(depth), 0 };
  _probe_stack.push(item);
}

void DFSClosure::probe_stack_push_followup_chunk(UnifiedOopRef ref, oop pointee, size_t depth, int chunkindex) {

  assert(!ref.is_null(), "invariant");
  assert(pointee != nullptr, "invariant");
  assert(chunkindex > 0, "invariant");

  if (_probe_stack.is_full()) {
    _times_probe_stack_full ++;
    return;
  }

  ProbeStackItem item { ref, checked_cast<unsigned>(depth), chunkindex };
  _probe_stack.push(item);
}

bool DFSClosure::probe_stack_pop() {

  if (_probe_stack.is_empty()) {
    _current_ref = UnifiedOopRef::encode_null();
    _current_pointee = nullptr;
    _current_depth = 0;
    _current_chunkindex = 0;
    return false;
  }

  ProbeStackItem item = _probe_stack.pop();
  _current_ref = item.r;
  assert(!_current_ref.is_null(), "invariant");
  _current_depth = item.depth;
  assert(_current_depth < _max_depth, "invariant");
  _current_chunkindex = item.chunkindex;
  assert(_current_chunkindex >= 0, "invariant");

  _current_pointee = _current_ref.dereference();

  return true;
}

void DFSClosure::handle_oop() {

  if (_current_depth == 0 && _ignore_root_set) {
    assert(pointee_was_visited(_current_pointee), "We should have already visited roots");
    _reference_stack[_current_depth] = _current_ref;
    // continue since we want to process children, too
  } else {
    if (pointee_was_visited(_current_pointee)) {
      return; // already processed
    }
    mark_pointee_as_visited(_current_pointee);
    _reference_stack[_current_depth] = _current_ref;
    if (pointee_was_sampled(_current_pointee)) {
      add_chain();
    }
  }

  // trace children if needed
  if (_current_depth == _max_depth - 1) {
    _times_max_depth_reached ++;
    return; // stop following this chain
  }

  _current_depth ++;
  _current_pointee->oop_iterate(this);
  _current_depth --;

  _num_objects_processed++;
}

void DFSClosure::handle_objarrayoop() {

  if (_current_depth == 0 && _ignore_root_set) {
    assert(pointee_was_visited(_current_pointee), "We should have already visited roots");
    _reference_stack[_current_depth] = _current_ref;
    // continue since we want to process children, too
  } else {

    if (_current_chunkindex == 0) {
      // For the first chunk only, check, process and mark the array oop itself.
      if (pointee_was_visited(_current_pointee)) {
        return; // already processed
      }
      mark_pointee_as_visited(_current_pointee);
      _reference_stack[_current_depth] = _current_ref;

      if (pointee_was_sampled(_current_pointee)) {
        add_chain();
      }

      _num_objects_processed++;
    }
  }

  // trace children if needed

  if (_current_depth == _max_depth - 1) {
    _times_max_depth_reached ++;
    return; // stop following this chain
  }

  const objArrayOop pointee_oa = (objArrayOop) _current_pointee;
  const int array_len = pointee_oa->length();
  if (array_len == 0) {
    return;
  }
  const int begidx = _current_chunkindex * array_chunk_size;
  const int endidx = MIN2(array_len, (_current_chunkindex + 1) * array_chunk_size);
  assert(begidx < endidx, "invariant");

  // Push follow-up chunk
  if (endidx < array_len) {
    probe_stack_push_followup_chunk(_current_ref, _current_pointee, _current_depth, _current_chunkindex + 1);
  }

  // push child references
  _current_depth ++;
  pointee_oa->oop_iterate_elements_range(this, begidx, endidx);
  _current_depth --;
}

void DFSClosure::drain_probe_stack() {

  DEBUG_ONLY(size_t last_depth = 0;)

  while (probe_stack_pop() && !GranularTimer::is_finished()) {

    // We should not dive downward more than 1 indirection.
    assert(_current_depth <= (last_depth + 1), "invariant");

    if (_current_pointee->is_objArray()) {
      handle_objarrayoop();
    } else {
      handle_oop();
    }

    DEBUG_ONLY(last_depth = _current_depth;)
  }
}

void DFSClosure::add_chain() {
  const size_t array_length = _current_depth + 2;

  ResourceMark rm;
  Edge* const chain = NEW_RESOURCE_ARRAY(Edge, array_length);
  size_t idx = 0;

  _num_sampled_objects_found++;

#ifdef ASSERT
  log_trace(jfr, system, oldobject)("Sample object found (" UINT64_FORMAT " so far)", _num_sampled_objects_found);
  log_reference_stack();
#endif

  // aggregate from depth-first search
  for (size_t i = 0; i <= _current_depth; i++) {
    const size_t next = idx + 1;
    const size_t d = _current_depth - i;
    chain[idx++] = Edge(&chain[next], _reference_stack[d]);
  }
  assert(_current_depth + 1 == idx, "invariant");
  assert(array_length == idx + 1, "invariant");

  // aggregate from breadth-first search
  if (_start_edge != nullptr) {
    chain[idx++] = *_start_edge;
  } else {
    chain[idx - 1] = Edge(nullptr, chain[idx - 1].reference());
  }
  _edge_store->put_chain(chain, idx + (_start_edge != nullptr ? _start_edge->distance_to_root() : 0));
}

void DFSClosure::do_oop(oop* ref) {
  assert(ref != nullptr, "invariant");
  assert(is_aligned(ref, HeapWordSize), "invariant");
  const oop pointee = HeapAccess<AS_NO_KEEPALIVE>::oop_load(ref);
  probe_stack_push(UnifiedOopRef::encode_in_heap(ref), pointee, _current_depth);
}

void DFSClosure::do_oop(narrowOop* ref) {
  assert(ref != nullptr, "invariant");
  assert(is_aligned(ref, sizeof(narrowOop)), "invariant");
  const oop pointee = HeapAccess<AS_NO_KEEPALIVE>::oop_load(ref);
  probe_stack_push(UnifiedOopRef::encode_in_heap(ref), pointee, _current_depth);
}

void DFSClosure::do_root(UnifiedOopRef ref) {
  assert(!ref.is_null(), "invariant");
  const oop pointee = ref.dereference();
  assert(pointee != nullptr, "invariant");
  probe_stack_push(ref, pointee, 0);
}
