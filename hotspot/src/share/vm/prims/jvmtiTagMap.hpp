/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

// JvmtiTagMap

#ifndef SHARE_VM_PRIMS_JVMTITAGMAP_HPP
#define SHARE_VM_PRIMS_JVMTITAGMAP_HPP

#ifndef JVMTI_KERNEL
#include "gc_interface/collectedHeap.hpp"
#include "jvmtifiles/jvmti.h"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "memory/allocation.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/universe.hpp"
#endif

// forward references
class JvmtiTagHashmap;
class JvmtiTagHashmapEntry;
class JvmtiTagHashmapEntryClosure;

class JvmtiTagMap :  public CHeapObj {
 private:

  enum{
    max_free_entries = 4096         // maximum number of free entries per env
  };

  JvmtiEnv*             _env;                       // the jvmti environment
  Mutex                 _lock;                      // lock for this tag map
  JvmtiTagHashmap*      _hashmap;                   // the hashmap

  JvmtiTagHashmapEntry* _free_entries;              // free list for this environment
  int _free_entries_count;                          // number of entries on the free list

  // create a tag map
  JvmtiTagMap(JvmtiEnv* env);

  // accessors
  inline Mutex* lock()                      { return &_lock; }
  inline JvmtiEnv* env() const              { return _env; }

  void do_weak_oops(BoolObjectClosure* is_alive, OopClosure* f);

  // iterate over all entries in this tag map
  void entry_iterate(JvmtiTagHashmapEntryClosure* closure);

 public:

  // indicates if this tag map is locked
  bool is_locked()                          { return lock()->is_locked(); }

  JvmtiTagHashmap* hashmap() { return _hashmap; }

  // create/destroy entries
  JvmtiTagHashmapEntry* create_entry(oop ref, jlong tag);
  void destroy_entry(JvmtiTagHashmapEntry* entry);

  // returns true if the hashmaps are empty
  bool is_empty();

  // return tag for the given environment
  static JvmtiTagMap* tag_map_for(JvmtiEnv* env);

  // destroy tag map
  ~JvmtiTagMap();

  // set/get tag
  void set_tag(jobject obj, jlong tag);
  jlong get_tag(jobject obj);

  // deprecated heap iteration functions
  void iterate_over_heap(jvmtiHeapObjectFilter object_filter,
                         KlassHandle klass,
                         jvmtiHeapObjectCallback heap_object_callback,
                         const void* user_data);

  void iterate_over_reachable_objects(jvmtiHeapRootCallback heap_root_callback,
                                      jvmtiStackReferenceCallback stack_ref_callback,
                                      jvmtiObjectReferenceCallback object_ref_callback,
                                      const void* user_data);

  void iterate_over_objects_reachable_from_object(jobject object,
                                                  jvmtiObjectReferenceCallback object_reference_callback,
                                                  const void* user_data);


  // advanced (JVMTI 1.1) heap iteration functions
  void iterate_through_heap(jint heap_filter,
                            KlassHandle klass,
                            const jvmtiHeapCallbacks* callbacks,
                            const void* user_data);

  void follow_references(jint heap_filter,
                         KlassHandle klass,
                         jobject initial_object,
                         const jvmtiHeapCallbacks* callbacks,
                         const void* user_data);

  // get tagged objects
  jvmtiError get_objects_with_tags(const jlong* tags, jint count,
                                   jint* count_ptr, jobject** object_result_ptr,
                                   jlong** tag_result_ptr);

  static void weak_oops_do(
      BoolObjectClosure* is_alive, OopClosure* f) KERNEL_RETURN;
};

#endif // SHARE_VM_PRIMS_JVMTITAGMAP_HPP
