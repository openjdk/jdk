/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotMetaspace.hpp"
#include "cds/aotStreamedHeapLoader.hpp"
#include "cds/aotThread.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.inline.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/vmClasses.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/oopFactory.hpp"
#include "oops/access.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/mutex.hpp"
#include "runtime/thread.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/stack.inline.hpp"

#include <type_traits>

#if INCLUDE_CDS_JAVA_HEAP

FileMapRegion* AOTStreamedHeapLoader::_heap_region;
FileMapRegion* AOTStreamedHeapLoader::_bitmap_region;
int* AOTStreamedHeapLoader::_roots_archive;
oop* AOTStreamedHeapLoader::_roots_heap;
BitMapView AOTStreamedHeapLoader::_oopmap;
bool AOTStreamedHeapLoader::_is_loaded;
int AOTStreamedHeapLoader::_previous_batch_last_object_index;
int AOTStreamedHeapLoader::_current_batch_last_object_index;
int AOTStreamedHeapLoader::_current_root_index;
size_t AOTStreamedHeapLoader::_allocated_words;
bool AOTStreamedHeapLoader::_allow_gc;
bool AOTStreamedHeapLoader::_objects_are_handles;
size_t AOTStreamedHeapLoader::_num_archived_objects;
int AOTStreamedHeapLoader::_num_roots;
size_t AOTStreamedHeapLoader::_heap_region_used;

size_t* AOTStreamedHeapLoader::_object_index_to_buffer_offset_table;
void** AOTStreamedHeapLoader::_object_index_to_heap_object_table;
int* AOTStreamedHeapLoader::_root_highest_object_index_table;

bool AOTStreamedHeapLoader::_waiting_for_iterator;
bool AOTStreamedHeapLoader::_swapping_root_format;

OopHandle AOTStreamedHeapLoader::_roots;

static jlong _early_materialization_time_ns = 0;
static jlong _late_materialization_time_ns = 0;
static jlong _final_materialization_time_ns = 0;
static jlong _cleanup_materialization_time_ns = 0;
static volatile jlong _accumulated_lazy_materialization_time_ns = 0;

int AOTStreamedHeapLoader::object_index_for_root_index(int root_index) {
  return _roots_archive[root_index];
}

int AOTStreamedHeapLoader::highest_object_index_for_root_index(int root_index) {
  return _root_highest_object_index_table[root_index];
}

size_t AOTStreamedHeapLoader::buffer_offset_for_object_index(int object_index) {
  return _object_index_to_buffer_offset_table[object_index];
}

oopDesc* AOTStreamedHeapLoader::archive_object_for_object_index(int object_index) {
  size_t buffer_offset = buffer_offset_for_object_index(object_index);
  address bottom = (address)_heap_region->mapped_base();
  return (oopDesc*)(bottom + buffer_offset);
}

size_t AOTStreamedHeapLoader::buffer_offset_for_archive_object(oopDesc* archive_object) {
  address bottom = (address)_heap_region->mapped_base();
  return size_t(archive_object) - size_t(bottom);
}

template <bool use_coops>
BitMap::idx_t AOTStreamedHeapLoader::obj_bit_idx_for_buffer_offset(size_t buffer_offset) {
  if constexpr (use_coops) {
    return BitMap::idx_t(buffer_offset / sizeof(narrowOop));
  } else {
    return BitMap::idx_t(buffer_offset / sizeof(HeapWord));
  }
}

oop AOTStreamedHeapLoader::heap_object_for_object_index(int object_index) {
  assert(object_index >= 0 && object_index <= (int)_num_archived_objects,
         "Heap object reference out of index: %d", object_index);

  if (_objects_are_handles) {
    oop* handle = (oop*)_object_index_to_heap_object_table[object_index];
    if (handle == nullptr) {
      return nullptr;
    }
    return NativeAccess<>::oop_load(handle);
  } else {
    return cast_to_oop(_object_index_to_heap_object_table[object_index]);
  }
}

void AOTStreamedHeapLoader::set_heap_object_for_object_index(int object_index, oop heap_object) {
  assert(heap_object_for_object_index(object_index) == nullptr, "Should only set once with this API");
  if (_objects_are_handles) {
    oop* handle = Universe::vm_global()->allocate();
    NativeAccess<>::oop_store(handle, heap_object);
    _object_index_to_heap_object_table[object_index] = (void*)handle;
  } else {
    _object_index_to_heap_object_table[object_index] = cast_from_oop<void*>(heap_object);
  }
}

void AOTStreamedHeapLoader::replace_heap_object_for_object_index(int object_index, oop heap_object) {
  if (_objects_are_handles) {
    oop* handle = (oop*)_object_index_to_heap_object_table[object_index];
    NativeAccess<>::oop_store(handle, heap_object);
  } else {
    _object_index_to_heap_object_table[object_index] = cast_from_oop<void*>(heap_object);
  }
}

static int archive_array_length(oopDesc* archive_array) {
  return *(int*)(address(archive_array) + arrayOopDesc::length_offset_in_bytes());
}

static size_t archive_object_size(oopDesc* archive_object) {
  Klass* klass = archive_object->klass();
  int lh = klass->layout_helper();

  if (Klass::layout_helper_is_instance(lh)) {
    // Instance
    if (Klass::layout_helper_needs_slow_path(lh)) {
      return ((size_t*)(archive_object))[-1];
    } else {
      return (size_t)Klass::layout_helper_size_in_bytes(lh) >> LogHeapWordSize;
    }
  } else if (Klass::layout_helper_is_array(lh)) {
    // Array
    size_t size_in_bytes;
    size_t array_length = (size_t)archive_array_length(archive_object);
    size_in_bytes = array_length << Klass::layout_helper_log2_element_size(lh);
    size_in_bytes += (size_t)Klass::layout_helper_header_size(lh);

    return align_up(size_in_bytes, (size_t)MinObjAlignmentInBytes) / HeapWordSize;
  } else {
    // Other
    return ((size_t*)(archive_object))[-1];
  }
}

oop AOTStreamedHeapLoader::allocate_object(oopDesc* archive_object, markWord mark, size_t size, TRAPS) {
  assert(!archive_object->is_stackChunk(), "no such objects are archived");

  oop heap_object;

 Klass* klass = archive_object->klass();
  if (klass->is_mirror_instance_klass()) {
    heap_object = Universe::heap()->class_allocate(klass, size, CHECK_NULL);
  } else if (klass->is_instance_klass()) {
    heap_object = Universe::heap()->obj_allocate(klass, size, CHECK_NULL);
  } else {
    assert(klass->is_array_klass(), "must be");
    int length = archive_array_length(archive_object);
    bool do_zero = klass->is_objArray_klass();
    heap_object = Universe::heap()->array_allocate(klass, size, length, do_zero, CHECK_NULL);
  }

  heap_object->set_mark(mark);

  return heap_object;
}

void AOTStreamedHeapLoader::install_root(int root_index, oop heap_object) {
  objArrayOop roots = objArrayOop((oop)NativeAccess<>::oop_load(_roots_heap));
  OrderAccess::release(); // Once the store below publishes an object, it can be concurrently picked up by another thread without using the lock
  roots->obj_at_put(root_index, heap_object);
}

void AOTStreamedHeapLoader::TracingObjectLoader::wait_for_iterator() {
  if (JavaThread::current()->is_active_Java_thread()) {
    // When the main thread has bootstrapped past the point of allowing safepoints,
    // we can and indeed have to use safepoint checking waiting.
    AOTHeapLoading_lock->wait();
  } else {
    // If we have no bootstrapped the main thread far enough, then we cannot and
    // indeed also don't need to perform safepoint checking waiting.
    AOTHeapLoading_lock->wait_without_safepoint_check();
  }
}

// Link object after copying in-place
template <typename LinkerT>
class AOTStreamedHeapLoader::CopyConjointLinkingOopClosure : public BasicOopIterateClosure {
private:
  oop _obj;
  LinkerT _linker;

public:
  CopyConjointLinkingOopClosure(oop obj, LinkerT linker)
    : _obj(obj),
      _linker(linker) {
  }

  virtual void do_oop(oop* p) { do_oop_work(p, (int)*(intptr_t*)p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p, *(int*)p); }

  template <typename T>
  void do_oop_work(T* p, int object_index) {
    int p_offset = pointer_delta_as_int((address)p, cast_from_oop<address>(_obj));
    oop pointee = _linker(p_offset, object_index);
    if (pointee != nullptr) {
      _obj->obj_field_put_access<IS_DEST_UNINITIALIZED>((int)p_offset, pointee);
    }
  }
};

template <bool use_coops, typename LinkerT>
void AOTStreamedHeapLoader::copy_payload_carefully(oopDesc* archive_object,
                                                   oop heap_object,
                                                   BitMap::idx_t header_bit,
                                                   BitMap::idx_t start_bit,
                                                   BitMap::idx_t end_bit,
                                                   LinkerT linker) {
  using RawElementT = std::conditional_t<use_coops, int32_t, int64_t>;
  using OopElementT = std::conditional_t<use_coops, narrowOop, oop>;

  BitMap::idx_t unfinished_bit = start_bit;
  BitMap::idx_t next_reference_bit = _oopmap.find_first_set_bit(unfinished_bit, end_bit);

  // Fill in heap object bytes
  while (unfinished_bit < end_bit) {
    assert(unfinished_bit >= start_bit && unfinished_bit < end_bit, "out of bounds copying");

    // This is the address of the pointee inside the input stream
    size_t payload_offset = unfinished_bit - header_bit;
    RawElementT* archive_payload_addr = ((RawElementT*)archive_object) + payload_offset;
    RawElementT* heap_payload_addr = cast_from_oop<RawElementT*>(heap_object) + payload_offset;

    assert(heap_payload_addr >= cast_from_oop<RawElementT*>(heap_object) &&
           (HeapWord*)heap_payload_addr < cast_from_oop<HeapWord*>(heap_object) + heap_object->size(),
           "Out of bounds copying");

    if (next_reference_bit > unfinished_bit) {
      // Primitive bytes available
      size_t primitive_elements = next_reference_bit - unfinished_bit;
      size_t primitive_bytes = primitive_elements * sizeof(RawElementT);
      ::memcpy(heap_payload_addr, archive_payload_addr, primitive_bytes);

      unfinished_bit = next_reference_bit;
    } else {
      // Encountered reference
      RawElementT* archive_p = (RawElementT*)archive_payload_addr;
      OopElementT* heap_p = (OopElementT*)heap_payload_addr;
      int pointee_object_index = (int)*archive_p;
      int heap_p_offset = pointer_delta_as_int((address)heap_p, cast_from_oop<address>(heap_object));

      // Disjoint linking in the heap object.
      oop obj = linker(heap_p_offset, pointee_object_index);
      if (obj != nullptr) {
        heap_object->obj_field_put(heap_p_offset, obj);
      }

      unfinished_bit++;
      next_reference_bit = _oopmap.find_first_set_bit(unfinished_bit, end_bit);
    }
  }
}

template <bool use_coops, typename LinkerT>
void AOTStreamedHeapLoader::copy_object_impl(oopDesc* archive_object,
                                             oop heap_object,
                                             size_t size,
                                             LinkerT linker) {
  if (!_allow_gc) {
    // Without concurrent GC running, we can copy incorrect object references
    // and metadata references into the heap object and then fix them up in-place.
    size_t payload_size = size - 1;
    HeapWord* archive_start = ((HeapWord*)archive_object) + 1;
    HeapWord* heap_start = cast_from_oop<HeapWord*>(heap_object) + 1;

    Copy::disjoint_words(archive_start, heap_start, payload_size);

    CopyConjointLinkingOopClosure cl(heap_object, linker);
    heap_object->oop_iterate(&cl);
    HeapShared::remap_loaded_metadata(heap_object);
    return;
  }

  // When a concurrent GC may be running, we take care not to copy incorrect oops,
  // narrowOops or Metadata* into the heap objects. Transitions go from 0 to the
  // intended runtime linked values only.
  size_t word_scale = use_coops ? 2 : 1;
  using RawElementT = std::conditional_t<use_coops, int32_t, int64_t>;

  // Skip the markWord; it is set at allocation time
  size_t header_size = word_scale;

  size_t buffer_offset = buffer_offset_for_archive_object(archive_object);
  const BitMap::idx_t header_bit = obj_bit_idx_for_buffer_offset<use_coops>(buffer_offset);
  const BitMap::idx_t start_bit = header_bit + header_size;
  const BitMap::idx_t end_bit = header_bit + size * word_scale;

  BitMap::idx_t curr_bit = start_bit;

  // We are a bit paranoid about GC or other safepointing operations observing
  // shady metadata fields from the archive that do not point at real metadata.
  // We deal with this by explicitly reading the requested address from the
  // archive and fixing it to real Metadata before writing it into the heap object.
  HeapShared::do_metadata_offsets(heap_object, [&](int metadata_offset) {
    BitMap::idx_t metadata_field_idx = header_bit + (size_t)metadata_offset / sizeof(RawElementT);
    BitMap::idx_t skip = word_scale;
    assert(metadata_field_idx >= start_bit && metadata_field_idx + skip <= end_bit,
           "Metadata field out of bounds");

    // Copy payload before metadata field
    copy_payload_carefully<use_coops>(archive_object,
                                      heap_object,
                                      header_bit,
                                      curr_bit,
                                      metadata_field_idx,
                                      linker);

    // Copy metadata field
    Metadata* const archive_metadata = *(Metadata**)(uintptr_t(archive_object) + (size_t)metadata_offset);
    Metadata* const runtime_metadata = archive_metadata != nullptr
        ? (Metadata*)(address(archive_metadata) + AOTMetaspace::relocation_delta())
        : nullptr;
    assert(runtime_metadata == nullptr || AOTMetaspace::in_aot_cache(runtime_metadata), "Invalid metadata pointer");
    DEBUG_ONLY(Metadata* const previous_metadata = heap_object->metadata_field(metadata_offset);)
    assert(previous_metadata == nullptr || previous_metadata == runtime_metadata, "Should not observe transient values");
    heap_object->metadata_field_put(metadata_offset, runtime_metadata);
    curr_bit = metadata_field_idx + skip;
  });

  // Copy trailing metadata after the last metadata word. This is usually doing
  // all the copying.
  copy_payload_carefully<use_coops>(archive_object,
                                    heap_object,
                                    header_bit,
                                    curr_bit,
                                    end_bit,
                                    linker);
}

void AOTStreamedHeapLoader::TracingObjectLoader::copy_object(int object_index,
                                                             oopDesc* archive_object,
                                                             oop heap_object,
                                                             size_t size,
                                                             Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack) {
  auto linker = [&](int p_offset, int pointee_object_index) {
    dfs_stack.push({pointee_object_index, object_index, p_offset});

    // The tracing linker is a bit lazy and mutates the reference fields in its traversal.
    // Returning null means don't link now.
    return oop(nullptr);
  };
  if (UseCompressedOops) {
    copy_object_impl<true>(archive_object, heap_object, size, linker);
  } else {
    copy_object_impl<false>(archive_object, heap_object, size, linker);
  }
}

oop AOTStreamedHeapLoader::TracingObjectLoader::materialize_object_inner(int object_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS) {
  // Allocate object
  oopDesc* archive_object = archive_object_for_object_index(object_index);
  size_t size = archive_object_size(archive_object);
  markWord mark = archive_object->mark();
  bool string_intern = mark.is_marked();
  mark = mark.set_unmarked();
  oop heap_object = allocate_object(archive_object, mark, size, CHECK_NULL);

  // Install forwarding
  set_heap_object_for_object_index(object_index, heap_object);

  // Fill in object contents
  copy_object(object_index, archive_object, heap_object, size, dfs_stack);

  if (string_intern) {
    // Interned string. Because the objects are laid out in DFS order, the value
    // array will always be the next object in iteration order. Finish materializing
    // and link it to the string table.
    int value_object_index = object_index + 1;
    heap_object = nullptr; // Materializing the value array might invalidate this oop.
    oop value_heap_object = materialize_object(value_object_index, dfs_stack, CHECK_NULL);

    heap_object = heap_object_for_object_index(object_index);
    if (_allow_gc) {
      heap_object->obj_field_put(java_lang_String::value_offset(), value_heap_object);
    } else {
      // Allocated objects are not properly initialized when GC isn't allowed
      heap_object->obj_field_put_access<IS_DEST_UNINITIALIZED>(java_lang_String::value_offset(), value_heap_object);
    }

    // Replace string with interned string
    heap_object = StringTable::intern(heap_object, CHECK_NULL);
    replace_heap_object_for_object_index(object_index, heap_object);
  }

  return heap_object;
}

oop AOTStreamedHeapLoader::TracingObjectLoader::materialize_object(int object_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS) {
  oop heap_object = heap_object_for_object_index(object_index);

  if (object_index <= _previous_batch_last_object_index) {
    // The transitive closure of this object has been materialized; no need to do anything
    return heap_object;
  }

  if (object_index <= _current_batch_last_object_index) {
    // The AOTThread is currently materializing this object and its transitive closure; only need to wait for it to complete
    _waiting_for_iterator = true;
    while (object_index > _previous_batch_last_object_index) {
      wait_for_iterator();
    }
    _waiting_for_iterator = false;
    heap_object = heap_object_for_object_index(object_index);
    return heap_object;
  }

  if (heap_object != nullptr) {
    // Already materialized by mutator
    return heap_object;
  }

  return materialize_object_inner(object_index, dfs_stack, THREAD);
}

void AOTStreamedHeapLoader::TracingObjectLoader::drain_stack(Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS) {
  while (!dfs_stack.is_empty()) {
    AOTHeapTraversalEntry entry = dfs_stack.pop();
    int pointee_object_index = entry._pointee_object_index;
    oop pointee_heap_object = materialize_object(pointee_object_index, dfs_stack, CHECK);
    oop heap_object = heap_object_for_object_index(entry._base_object_index);
    if (_allow_gc) {
      heap_object->obj_field_put(entry._heap_field_offset_bytes, pointee_heap_object);
    } else {
      heap_object->obj_field_put_access<IS_DEST_UNINITIALIZED>(entry._heap_field_offset_bytes, pointee_heap_object);
    }
  }
}

oop AOTStreamedHeapLoader::TracingObjectLoader::materialize_object_transitive(int object_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS) {
  assert_locked_or_safepoint(AOTHeapLoading_lock);
  while (_waiting_for_iterator) {
    wait_for_iterator();
  }

  Handle result(THREAD, materialize_object(object_index, dfs_stack, THREAD));
  (void)(CHECK_NULL);
  drain_stack(dfs_stack, CHECK_NULL);

  return result();
}

oop AOTStreamedHeapLoader::TracingObjectLoader::materialize_root(int root_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS) {
  int root_object_index = object_index_for_root_index(root_index);
  oop root = materialize_object_transitive(root_object_index, dfs_stack, CHECK_NULL);
  install_root(root_index, root);

  return root;
}

int oop_handle_cmp(const void* left, const void* right) {
  oop* left_handle = *(oop**)left;
  oop* right_handle = *(oop**)right;

  if (right_handle > left_handle) {
    return -1;
  } else if (left_handle > right_handle) {
    return 1;
  }

  return 0;
}

void AOTStreamedHeapLoader::IterativeObjectLoader::copy_object(oopDesc* archive_object, oop heap_object, size_t size) {
  auto linker = [&](int p_offset, int pointee_object_index) {
    return AOTStreamedHeapLoader::heap_object_for_object_index(pointee_object_index);
  };
  if (UseCompressedOops) {
    copy_object_impl<true>(archive_object, heap_object, size, linker);
  } else {
    copy_object_impl<false>(archive_object, heap_object, size, linker);
  }
}

// The range is inclusive
void AOTStreamedHeapLoader::IterativeObjectLoader::initialize_range(int first_object_index, int last_object_index, TRAPS) {
  bool last_object_was_interned_string = false;

  for (int i = first_object_index; i <= last_object_index; ++i) {
    oopDesc* archive_object = archive_object_for_object_index(i);
    markWord mark = archive_object->mark();
    bool string_intern = mark.is_marked();
    size_t size = archive_object_size(archive_object);
    oop heap_object = heap_object_for_object_index(i);
    copy_object(archive_object, heap_object, size);

    // Link interned strings if necessary
    if (last_object_was_interned_string) {
      int string_object_index = i - 1;
      oop string_object = heap_object_for_object_index(string_object_index);

      // Replace string with interned string
      string_object = StringTable::intern(string_object, CHECK);
      replace_heap_object_for_object_index(string_object_index, string_object);

      last_object_was_interned_string = false;
    } else if (string_intern) {
      // Because the objects are laid out in DFS order, the value array will always
      // be the next object in iteration order.
      last_object_was_interned_string = true;
    }
  }
}

// The range is inclusive
size_t AOTStreamedHeapLoader::IterativeObjectLoader::materialize_range(int first_object_index, int last_object_index, TRAPS) {
  GrowableArrayCHeap<int, mtClassShared> lazy_object_indices(0);
  size_t materialized_words = 0;

  for (int i = first_object_index; i <= last_object_index; ++i) {
    oopDesc* archive_object = archive_object_for_object_index(i);
    markWord mark = archive_object->mark().set_unmarked();
    size_t size = archive_object_size(archive_object);
    materialized_words += size;
    oop heap_object = heap_object_for_object_index(i);
    if (heap_object == nullptr) {
      // The normal case; no lazy loading have loaded the object yet
      heap_object = allocate_object(archive_object, mark, size, CHECK_0);
      set_heap_object_for_object_index(i, heap_object);
    } else {
      // Lazy loading has already initialized the object; we must not mutate it
      lazy_object_indices.append(i);
    }
  }

  if (lazy_object_indices.is_empty()) {
    // Normal case; no sprinkled lazy objects in the root subgraph
    initialize_range(first_object_index, last_object_index, CHECK_0);
  } else {
    // The user lazy initialized some objects that are already initialized; we have to initialize around them
    // to make sure they are not mutated.
    int previous_object_index = first_object_index - 1; // Exclusive start of initialization slice
    for (int i = 0; i < lazy_object_indices.length(); ++i) {
      int lazy_object_index = lazy_object_indices.at(i);
      int slice_start_object_index = previous_object_index;
      int slice_end_object_index = lazy_object_index;

      if (slice_end_object_index - slice_start_object_index > 1) { // Both markers are exclusive
        initialize_range(slice_start_object_index + 1, slice_end_object_index - 1, CHECK_0);
      }
      oop heap_object = heap_object_for_object_index(lazy_object_index);
      previous_object_index = lazy_object_index;
    }
    // Process tail range
    if (last_object_index - previous_object_index > 0) {
      initialize_range(previous_object_index + 1, last_object_index, CHECK_0);
    }
  }

  return materialized_words;
}

bool AOTStreamedHeapLoader::IterativeObjectLoader::has_more() {
  return _current_root_index < _num_roots;
}

void AOTStreamedHeapLoader::IterativeObjectLoader::materialize_next_batch(TRAPS) {
  assert(has_more(), "only materialize if there is something to materialize");

  int min_batch_objects = 128;
  int from_root_index = _current_root_index;
  int max_to_root_index = _num_roots - 1;
  int until_root_index = from_root_index;
  int highest_object_index;

  // Expand the batch size from one root, to N roots until we cross 128 objects in total
  for (;;) {
    highest_object_index = highest_object_index_for_root_index(until_root_index);
    if (highest_object_index - _previous_batch_last_object_index >= min_batch_objects) {
      break;
    }
    if (until_root_index == max_to_root_index) {
      break;
    }
    until_root_index++;
  }

  oop root = nullptr;

  // Materialize objects of necessary, representing the transitive closure of the root
  if (highest_object_index > _previous_batch_last_object_index) {
    while (_swapping_root_format) {
      // When the roots are being upgraded to use handles, it is not safe to racingly
      // iterate over the object; we must wait. Setting the current batch last object index
      // to something other than the previous batch last object index indicates to the
      // root swapping that there is current iteration ongoing.
      AOTHeapLoading_lock->wait();
    }
    int first_object_index = _previous_batch_last_object_index + 1;
    _current_batch_last_object_index = highest_object_index;
    size_t allocated_words;
    {
      MutexUnlocker ml(AOTHeapLoading_lock, Mutex::_safepoint_check_flag);
      allocated_words = materialize_range(first_object_index, highest_object_index, CHECK);
    }
    _allocated_words += allocated_words;
    _previous_batch_last_object_index = _current_batch_last_object_index;
    if (_waiting_for_iterator) {
      // If tracer is waiting, let it know at the next point of unlocking that the root
      // set it waited for has been processed now.
      AOTHeapLoading_lock->notify_all();
    }
  }

  // Install the root
  for (int i = from_root_index; i <= until_root_index; ++i) {
    int root_object_index = object_index_for_root_index(i);
    root = heap_object_for_object_index(root_object_index);
    install_root(i, root);
    ++_current_root_index;
  }
}

bool AOTStreamedHeapLoader::materialize_early() {
  jlong start = os::javaTimeNanos();
  // We cannot handle any exception when materializing roots. Exits the VM.
  EXCEPTION_MARK

  // Only help with early materialization from the AOT thread if the heap archive can be allocated
  // without the need for a GC. Otherwise, do lazy loading until GC is enabled later in the bootstrapping.
  size_t bootstrap_max_memory = Universe::heap()->bootstrap_max_memory();
  size_t bootstrap_min_memory = MAX2(_heap_region_used, 2 * M);

  size_t before_gc_materialize_budget_bytes = (bootstrap_max_memory > bootstrap_min_memory) ? bootstrap_max_memory - bootstrap_min_memory : 0;
  size_t before_gc_materialize_budget_words = before_gc_materialize_budget_bytes / HeapWordSize;

  log_info(aot, heap)("Max bootstrapping memory: %zuM, min bootstrapping memory: %zuM, selected budget: %zuM",
                      bootstrap_max_memory / M, bootstrap_min_memory / M, before_gc_materialize_budget_bytes / M);

  while (IterativeObjectLoader::has_more()) {
    if (_allow_gc || _allocated_words > before_gc_materialize_budget_words) {
      log_info(aot, heap)("Early object materialization interrupted at root %d", _current_root_index);
      break;
    }

    IterativeObjectLoader::materialize_next_batch(CHECK_false);
  }

  _early_materialization_time_ns = os::javaTimeNanos() - start;

  bool finished_before_gc_allowed = !_allow_gc && !IterativeObjectLoader::has_more();

  return finished_before_gc_allowed;
}

void AOTStreamedHeapLoader::materialize_late() {
  jlong start = os::javaTimeNanos();

  // Continue materializing with GC allowed

  // We cannot handle any exception when materializing roots. Exits the VM.
  EXCEPTION_MARK

  while (IterativeObjectLoader::has_more()) {
    IterativeObjectLoader::materialize_next_batch(CHECK);
  }

  _late_materialization_time_ns = os::javaTimeNanos() - start;
}

void AOTStreamedHeapLoader::cleanup() {
  // First ensure there is no concurrent tracing going on
  while (_waiting_for_iterator) {
    AOTHeapLoading_lock->wait();
  }

  jlong start = os::javaTimeNanos();

  // Remove OopStorage roots
  if (_objects_are_handles) {
    size_t num_handles = _num_archived_objects;
    // Skip the null entry
    oop** handles = ((oop**)_object_index_to_heap_object_table) + 1;
    // Sort the handles so that oop storage can release them faster
    qsort(handles, num_handles, sizeof(oop*), (int (*)(const void*, const void*))oop_handle_cmp);
    size_t num_null_handles = 0;
    for (size_t handles_remaining = num_handles; handles_remaining != 0; --handles_remaining) {
      oop* handle = handles[handles_remaining - 1];
      if (handle == nullptr) {
        num_null_handles = handles_remaining;
        break;
      }
      NativeAccess<>::oop_store(handle, nullptr);
    }
    Universe::vm_global()->release(&handles[num_null_handles], num_handles - num_null_handles);
  }

  FREE_C_HEAP_ARRAY(void*, _object_index_to_heap_object_table);

  // Unmap regions
  FileMapInfo::current_info()->unmap_region(AOTMetaspace::hp);
  FileMapInfo::current_info()->unmap_region(AOTMetaspace::bm);

  _cleanup_materialization_time_ns = os::javaTimeNanos() - start;

  log_statistics();
}

void AOTStreamedHeapLoader::log_statistics() {
  const bool is_async = CDSConfig::is_using_full_module_graph() && !AOTEagerlyLoadObjects;
  const char* const async_or_sync = is_async ? "async" : "sync";
  log_info(aot, heap)("early object materialization time (%s): %zuus",
                      async_or_sync, _early_materialization_time_ns / 1000);
  log_info(aot, heap)("late object materialization time (%s): %zuus",
                      async_or_sync, _late_materialization_time_ns / 1000);
  log_info(aot, heap)("object materialization cleanup time (%s): %zuus",
                      async_or_sync, _cleanup_materialization_time_ns / 1000);
  log_info(aot, heap)("final object materialization time stall (sync): %zuus",
                      _final_materialization_time_ns / 1000);
  log_info(aot, heap)("bootstrapping lazy materialization time (sync): %zuus",
                      _accumulated_lazy_materialization_time_ns / 1000);

  jlong sync_time = _final_materialization_time_ns + _accumulated_lazy_materialization_time_ns;
  jlong async_time = _early_materialization_time_ns + _late_materialization_time_ns + _cleanup_materialization_time_ns;

  if (!is_async) {
    sync_time += async_time;
    async_time = 0;
  }

  log_info(aot, heap)("sync materialization time: %zuus",
                      sync_time / 1000);

  log_info(aot, heap)("async materialization time: %zuus",
                      async_time / 1000);

  size_t iterative_time = (size_t)(is_async ? async_time : sync_time);
  size_t materialized_bytes = _allocated_words * HeapWordSize;
  log_info(aot, heap)("%s materialized %zuK (%zuM/s)", async_or_sync,
                      materialized_bytes / 1024, size_t(materialized_bytes * UCONST64(1'000'000'000) / M / iterative_time));
}

void AOTStreamedHeapLoader::materialize_objects() {
  JavaThread* thread = JavaThread::current();
  // Objects are laid out in DFS order; DFS traverse the roots by linearly walking all objects
  HandleMark hm(thread);
  // Early materialization with a budget before GC is allowed
  MutexLocker ml(AOTHeapLoading_lock, Mutex::_safepoint_check_flag);
  materialize_early();
  await_gc_enabled();
  materialize_late();
  // Notify materialization is done
  AOTHeapLoading_lock->notify_all();
  cleanup();
}

void AOTStreamedHeapLoader::switch_object_index_to_handle(int object_index) {
  oop heap_object = cast_to_oop(_object_index_to_heap_object_table[object_index]);
  if (heap_object == nullptr) {
    return;
  }

  oop* handle = Universe::vm_global()->allocate();
  NativeAccess<>::oop_store(handle, heap_object);
  _object_index_to_heap_object_table[object_index] = handle;
}

void AOTStreamedHeapLoader::enable_gc() {
  if (AOTEagerlyLoadObjects && !IterativeObjectLoader::has_more()) {
    // Everything was loaded eagerly at early startup
    return;
  }

  MutexLocker ml(AOTHeapLoading_lock, Mutex::_safepoint_check_flag);

  // First wait until no tracing is active
  while (_waiting_for_iterator) {
    AOTHeapLoading_lock->wait();
  }

  // Lock further tracing from starting
  _waiting_for_iterator = true;

  // Record iterator progress
  int num_handles = (int)_num_archived_objects;

  // Lock further iteration from starting
  _swapping_root_format = true;

  // Then wait for the iterator to stop
  while (_previous_batch_last_object_index != _current_batch_last_object_index) {
    AOTHeapLoading_lock->wait();
  }

  if (IterativeObjectLoader::has_more()) {
    // If there is more to be materialized, we have to upgrade the object index
    // to object mapping to use handles. If there isn't more to materialize, the
    // handle will no longer e used; they are only used to materialize objects.

    for (int i = 1; i <= num_handles; ++i) {
      // Upgrade the roots to use handles
      switch_object_index_to_handle(i);
    }

    // From now on, accessing the object table must be done through a handle.
    _objects_are_handles = true;
  }

  // Unlock tracing
  _waiting_for_iterator = false;

  // Unlock iteration
  _swapping_root_format = false;

  _allow_gc = true;

  AOTHeapLoading_lock->notify_all();

  if (AOTEagerlyLoadObjects && IterativeObjectLoader::has_more()) {
    materialize_late();
    cleanup();
  }
}

void AOTStreamedHeapLoader::finish_materialize_objects() {
  if (!_is_loaded) {
    // No roots to materialize
    return;
  }

  jlong start = os::javaTimeNanos();

  if (CDSConfig::is_using_full_module_graph()) {
    MutexLocker ml(AOTHeapLoading_lock, Mutex::_safepoint_check_flag);
    // Wait for the AOT thread to finish
    while (IterativeObjectLoader::has_more()) {
      AOTHeapLoading_lock->wait();
    }
  } else {
    assert(!AOTEagerlyLoadObjects, "sanity");
    assert(AOTThread::aot_thread() == nullptr, "sanity");
    assert(_current_root_index == 0, "sanity");
    // Without the full module graph we have done only lazy tracing materialization.
    // Ensure all roots are processed here by triggering root loading on every root.
    for (int i = 0; i < _num_roots; ++i) {
      get_root(i);
    }
    cleanup();
  }

  _final_materialization_time_ns = os::javaTimeNanos() - start;
}

void account_lazy_materialization_time_ns(jlong time, const char* description, int index) {
  AtomicAccess::add(&_accumulated_lazy_materialization_time_ns, time);
  log_debug(aot, heap)("Lazy materialization of %s: %d end (%ld us of %ld us)", description, index, time / 1000, _accumulated_lazy_materialization_time_ns / 1000);
}

// Initialize an empty array of AOT heap roots; materialize them lazily
void AOTStreamedHeapLoader::initialize() {
  EXCEPTION_MARK

  FileMapInfo::current_info()->map_bitmap_region();

  _heap_region = FileMapInfo::current_info()->region_at(AOTMetaspace::hp);
  _bitmap_region = FileMapInfo::current_info()->region_at(AOTMetaspace::bm);

  assert(_heap_region->used() > 0, "empty heap archive?");

  _is_loaded = true;

  // archived roots are at this offset in the stream.
  size_t roots_offset = FileMapInfo::current_info()->streamed_heap()->roots_offset();
  size_t forwarding_offset = FileMapInfo::current_info()->streamed_heap()->forwarding_offset();
  size_t root_highest_object_index_table_offset = FileMapInfo::current_info()->streamed_heap()->root_highest_object_index_table_offset();
  _num_archived_objects = FileMapInfo::current_info()->streamed_heap()->num_archived_objects();

  // The first int is the length of the array
  _roots_archive = ((int*)(((address)_heap_region->mapped_base()) + roots_offset)) + 1;
  _num_roots = _roots_archive[-1];
  _heap_region_used = _heap_region->used();

  // We can't retire a TLAB until the filler klass is set; set it to the archived object klass.
  CollectedHeap::set_filler_object_klass(vmClasses::Object_klass());

  objArrayOop roots = oopFactory::new_objectArray(_num_roots, CHECK);
  _roots_heap = Universe::vm_global()->allocate();
  NativeAccess<>::oop_store(_roots_heap, roots);

  _object_index_to_buffer_offset_table = (size_t*)(((address)_heap_region->mapped_base()) + forwarding_offset);
  // We allocate the first entry for "null"
  _object_index_to_heap_object_table = NEW_C_HEAP_ARRAY(void*, _num_archived_objects + 1, mtClassShared);
  Copy::zero_to_bytes(_object_index_to_heap_object_table, (_num_archived_objects + 1) * sizeof(void*));

  _root_highest_object_index_table = (int*)(((address)_heap_region->mapped_base()) + root_highest_object_index_table_offset);

  address start = (address)(_bitmap_region->mapped_base()) + _heap_region->oopmap_offset();
  _oopmap = BitMapView((BitMap::bm_word_t*)start, _heap_region->oopmap_size_in_bits());

  _roots = OopHandle(Universe::vm_global(), roots);

  if (FLAG_IS_DEFAULT(AOTEagerlyLoadObjects)) {
    // Concurrency will not help much if there are no extra cores available.
    FLAG_SET_ERGO(AOTEagerlyLoadObjects, os::initial_active_processor_count() <= 1);
  }

  if (!CDSConfig::is_using_full_module_graph()) {
    // When not using FMG, fall back to tracing materialization
    FLAG_SET_ERGO(AOTEagerlyLoadObjects, false);
    return;
  }

  if (AOTEagerlyLoadObjects) {
    // Objects are laid out in DFS order; DFS traverse the roots by linearly walking all objects
    HandleMark hm(THREAD);
    // Early materialization with a budget before GC is allowed
    MutexLocker ml(AOTHeapLoading_lock, Mutex::_safepoint_check_flag);
    bool finished_before_gc_allowed = materialize_early();
    if (finished_before_gc_allowed) {
      cleanup();
    }
  } else {
    AOTThread::initialize();
  }
}

oop AOTStreamedHeapLoader::materialize_root(int root_index) {
  jlong start = os::javaTimeNanos();
  // We cannot handle any exception when materializing a root. Exits the VM.
  EXCEPTION_MARK
  Stack<AOTHeapTraversalEntry, mtClassShared> dfs_stack;
  HandleMark hm(THREAD);

  oop result;
  {
    MutexLocker ml(AOTHeapLoading_lock, Mutex::_safepoint_check_flag);

    oop root = objArrayOop(_roots.resolve())->obj_at(root_index);

    if (root != nullptr) {
      // The root has already been materialized
      result = root;
    } else {
      // The root has not been materialized, start tracing materialization
      result = TracingObjectLoader::materialize_root(root_index, dfs_stack, CHECK_NULL);
    }
  }

  account_lazy_materialization_time_ns(os::javaTimeNanos() - start, "root", root_index);

  return result;
}

oop AOTStreamedHeapLoader::get_root(int index) {
  oop result = objArrayOop(_roots.resolve())->obj_at(index);
  if (result == nullptr) {
    // Materialize root
    result = materialize_root(index);
  }
  if (result == _roots.resolve()) {
    // Materialized null sentinel object
    result = nullptr;
  }
  // Acquire the root transitive object payload
  OrderAccess::acquire();
  return result;
}

void AOTStreamedHeapLoader::clear_root(int index) {
  // The root acts as a sentinel object for null
  objArrayOop(_roots.resolve())->obj_at_put(index, _roots.resolve());
}

void AOTStreamedHeapLoader::await_gc_enabled() {
  while (!_allow_gc) {
    AOTHeapLoading_lock->wait();
  }
}

AOTMapLogger::OopDataIterator* AOTStreamedHeapLoader::oop_iterator(FileMapInfo* info, address buffer_start, address buffer_end) {
  class StreamedLoaderOopIterator : public AOTMapLogger::OopDataIterator {
  private:
    int _current;
    int _next;

    address _buffer_start;

    int _num_archived_objects;

  public:
    StreamedLoaderOopIterator(address buffer_start,
                              int num_archived_objects)
      : _current(0),
        _next(1),
        _buffer_start(buffer_start),
        _num_archived_objects(num_archived_objects) {
    }

    AOTMapLogger::OopData capture(int dfs_index) {
      size_t buffered_offset = buffer_offset_for_object_index(dfs_index);
      address buffered_addr = _buffer_start + buffered_offset;
      oopDesc* raw_oop = (oopDesc*)buffered_addr;
      size_t size = archive_object_size(raw_oop);

      intptr_t target_location = (intptr_t)buffered_offset;
      uint32_t narrow_location = checked_cast<uint32_t>(dfs_index);
      Klass* klass = raw_oop->klass();

      address requested_addr = (address)buffered_offset;

      return { buffered_addr,
               requested_addr,
               target_location,
               narrow_location,
               raw_oop,
               klass,
               size,
               false };
    }

    bool has_next() override {
      return _next <= _num_archived_objects;
    }

    AOTMapLogger::OopData next() override {
      _current = _next;
      AOTMapLogger::OopData result = capture(_current);
      _next = _current + 1;
      return result;
    }

    AOTMapLogger::OopData obj_at(narrowOop* addr) override {
      int dfs_index = (int)(*addr);
      if (dfs_index == 0) {
        return null_data();
      } else {
        return capture(dfs_index);
      }
    }

    AOTMapLogger::OopData obj_at(oop* addr) override {
      int dfs_index = (int)cast_from_oop<uintptr_t>(*addr);
      if (dfs_index == 0) {
        return null_data();
      } else {
        return capture(dfs_index);
      }
    }

    GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>* roots() override {
      GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>* result = new GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>();

      for (int i = 0; i < _num_roots; ++i) {
        int object_index = object_index_for_root_index(i);
        result->append(capture(object_index));
      }

      return result;
    }
  };

  assert(_is_loaded, "printing before initial loading?");

  return new StreamedLoaderOopIterator(buffer_start, (int)info->streamed_heap()->num_archived_objects());
}

#endif // INCLUDE_CDS_JAVA_HEAP
