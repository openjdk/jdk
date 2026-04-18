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

#ifndef SHARE_CDS_AOTSTREAMEDHEAPLOADER_HPP
#define SHARE_CDS_AOTSTREAMEDHEAPLOADER_HPP

#include "cds/aotMapLogger.hpp"
#include "cds/filemap.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/stack.hpp"

#if INCLUDE_CDS_JAVA_HEAP

// The streaming archive heap loader loads Java objects using normal allocations. It requires the objects
// to be ordered in DFS order already at dump time, given the set of roots into the archived heap.
// Since the objects are ordered in DFS order, that means that walking them linearly through the archive
// is equivalent to performing a DFS traversal, but without pushing and popping anything.
//
// The advantage of this pre-ordering, other than the obvious locality improvement, is that we can have
// a separate thread, the AOTThread, perform this walk, in a way that allows us to split the archived
// heap into three separate zones. The first zone contains objects that have been transitively materialized,
// the second zone contains objects that are currently being materialized, and the last zone contains
// objects that have not and are not about to be touched by the AOT thread.
// Whenever a new root is traversed by the AOT thread, the zones are shifted atomically under a lock.
//
// Visualization of the three zones:
//
// +--------------------------------------+-------------------------+----------------------------------+
// |      transitively materialized       | currently materializing |        not yet materialized      |
// +--------------------------------------+-------------------------+----------------------------------+
//
// Being able to split the memory into these three zones, allows the bootstrapping thread and potential
// other threads to be able to, under a lock, traverse a root, and know how to coordinate with the
// concurrent AOT thread. Whenever the traversal finds an object in the "transitively materialized"
// zone, then we know such objects don't need any processing at all. As for "currently materializing",
// we know that if we just stay out of the way and let the AOT thread finish its current root, then
// the transitive closure of such objects will be materialized. And the AOT thread can materialize faster
// then the rest as it doesn't need to perform any traversal. Finally, as for objects in the "not yet
// materialized" zone, we know that we can trace through it without stepping on the feed of the AOT thread
// which has published it won't be tracing anything in there.
//
// What we get from this, is fast iterative traversal from the AOT thread (IterativeObjectLoader)
// while allowing lazyness and concurrency with the rest of the program (TracingObjectLoader).
// This way the AOT thread can remove the bulk of the work of materializing the Java objects from
// the critical bootstrapping thread.
//
// When we start materializing objects, we have not yet come to the point in the bootstrapping where
// GC is allowed. This is a two edged sword. On the one hand side, we can materialize objects faster
// when we know there is no GC to coordinate with, but on the other hand side, if we need to perform
// a GC when allocating memory for archived objects, we will bring down the entire JVM. To deal with this,
// the AOT thread asks the GC for a budget of bytes it is allowed to allocate before GC is allowed.
// When we get to the point in the bootstrapping where GC is allowed, we resume materializing objects
// that didn't fit in the budget. Before we let the application run, we force materialization of any
// remaining objects that have not been materialized by the AOT thread yet, so that we don't get
// surprising OOMs due to object materialization while the program is running.
//
// The object format of the archived heap is similar to a normal object. However, references are encoded
// as DFS indices, which in the end map to what index the object is in the buffer, as they are laid out
// in DFS order. The DFS indices start at 1 for the first object, and hence the number 0 represents
// null. The DFS index of objects is a core identifier of objects in this approach. From this index
// it is possible to find out what offset the archived object has into the buffer, as well as finding
// mappings to Java heap objects that have been materialized.
//
// The table mapping DFS indices to Java heap objects is filled in when an object is allocated.
// Materializing objects involves allocating the object, initializing it, and linking it with other
// objects. Since linking the object requires whatever is being referenced to be at least allocated,
// the iterative traversal will first allocate all of the objects in its zone being worked on, and then
// perform initialization and linking in a second pass. What these passes have in common is that they
// are trivially parallelizable, should we ever need to do that. The tracing materialization links
// objects when going "back" in the DFS traversal.
//
// The forwarding information for the mechanism contains raw oops before GC is allowed, and as we
// enable GC in the bootstrapping, all raw oops are handleified using OopStorage. All handles are
// handed back from the AOT thread when materialization has finished. The switch from raw oops to
// using OopStorage handles, happens under a lock while no iteration nor tracing is allowed.
//
// The initialization code is also performed in a faster way when the GC is not allowed. In particular,
// before GC is allowed, we perform raw memcpy of the archived object into the Java heap. Then the
// object is initialized with IS_DEST_UNINITIALIZED stores. The assumption made here is that before
// any GC activity is allowed, we shouldn't have to worry about concurrent GC threads scanning the
// memory and getting tripped up by that. Once GC is enabled, we revert to a bit more careful approach
// that uses a pre-computed bitmap to find the holes where oops go, and carefully copy only the
// non-oop information with memcpy, while the oops are set separately with HeapAccess stores that
// should be able to cope well with concurrent activity.
//
// The marked bit pattern of the mark word of archived heap objects is used for signalling which string
// objects should be interned. From the dump, some referenced strings were interned. This is
// really an identity property. We don't need to dump the entire string table as a way of communicating
// this identity property. Instead we intern strings on-the-fly, exploiting the dynamic object
// level linking that this approach has chosen to our advantage.

class FileMapInfo;
class OopStorage;
class Thread;
struct AOTHeapTraversalEntry;

struct alignas(AOTHeapTraversalEntry* /* Requirement of Stack<AOTHeapTraversalEntry> */) AOTHeapTraversalEntry {
  int _pointee_object_index;
  int _base_object_index;
  int _heap_field_offset_bytes;
};

class AOTStreamedHeapLoader {
  friend class InflateReferenceOopClosure;
private:
  static FileMapRegion* _heap_region;
  static FileMapRegion* _bitmap_region;
  static OopStorage* _oop_storage;
  static int* _roots_archive;
  static OopHandle _roots;
  static BitMapView _oopmap;
  static bool _is_in_use;
  static bool _allow_gc;
  static bool _objects_are_handles;
  static int _previous_batch_last_object_index;
  static int _current_batch_last_object_index;
  static size_t _allocated_words;
  static int _current_root_index;
  static size_t _num_archived_objects;
  static int _num_roots;
  static size_t _heap_region_used;
  static bool _loading_all_objects;

  static size_t* _object_index_to_buffer_offset_table;
  static void** _object_index_to_heap_object_table;
  static int* _root_highest_object_index_table;

  static bool _waiting_for_iterator;
  static bool _swapping_root_format;


  template <typename LinkerT>
  class InPlaceLinkingOopClosure;

  static oop allocate_object(oopDesc* archive_object, markWord mark, size_t size, TRAPS);
  static int object_index_for_root_index(int root_index);
  static int highest_object_index_for_root_index(int root_index);
  static size_t buffer_offset_for_object_index(int object_index);
  static oopDesc* archive_object_for_object_index(int object_index);
  static size_t buffer_offset_for_archive_object(oopDesc* archive_object);
  template <bool use_coops>
  static BitMap::idx_t obj_bit_idx_for_buffer_offset(size_t buffer_offset);

  template <bool use_coops, typename LinkerT>
  static void copy_payload_carefully(oopDesc* archive_object,
                                     oop heap_object,
                                     BitMap::idx_t header_bit,
                                     BitMap::idx_t start_bit,
                                     BitMap::idx_t end_bit,
                                     LinkerT linker);

  template <bool use_coops, typename LinkerT>
  static void copy_object_impl(oopDesc* archive_object,
                               oop heap_object,
                               size_t size,
                               LinkerT linker);

  static void copy_object_eager_linking(oopDesc* archive_object, oop heap_object, size_t size);

  static void switch_object_index_to_handle(int object_index);
  static oop heap_object_for_object_index(int object_index);
  static void set_heap_object_for_object_index(int object_index, oop heap_object);

  static int archived_string_value_object_index(oopDesc* archive_object);

  static bool materialize_early(TRAPS);
  static void materialize_late(TRAPS);
  static void cleanup();
  static void log_statistics();

  class TracingObjectLoader {
    static oop materialize_object(int object_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS);
    static oop materialize_object_inner(int object_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS);
    static void copy_object_lazy_linking(int object_index,
                                         oopDesc* archive_object,
                                         oop heap_object,
                                         size_t size,
                                         Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack);
    static void drain_stack(Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS);
    static oop materialize_object_transitive(int object_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS);

    static void wait_for_iterator();

  public:
    static oop materialize_root(int root_index, Stack<AOTHeapTraversalEntry, mtClassShared>& dfs_stack, TRAPS);
  };

  class IterativeObjectLoader {
    static void initialize_range(int first_object_index, int last_object_index, TRAPS);
    static size_t materialize_range(int first_object_index, int last_object_index, TRAPS);

  public:
    static bool has_more();
    static void materialize_next_batch(TRAPS);
  };

  static void install_root(int root_index, oop heap_object);

  static void await_gc_enabled();
  static void await_finished_processing();

public:
  static void initialize();
  static void enable_gc();
  static void materialize_thread_object();
  static oop materialize_root(int root_index);
  static oop get_root(int root_index);
  static void clear_root(int index);
  static void materialize_objects();
  static void finish_materialize_objects();
  static bool is_in_use() { return _is_in_use; }
  static void finish_initialization(FileMapInfo* info);

  static AOTMapLogger::OopDataIterator* oop_iterator(FileMapInfo* info, address buffer_start, address buffer_end);
};

#endif // SHARE_CDS_AOTSTREAMEDHEAPLOADER_HPP
#endif // INCLUDE_CDS_JAVA_HEAP
