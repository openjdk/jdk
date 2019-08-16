/*
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "aot/aotLoader.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/pcTasks.hpp"
#include "gc/parallel/psCompactionManager.inline.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "services/management.hpp"
#include "utilities/stack.inline.hpp"

//
// CompactionWithStealingTask
//

CompactionWithStealingTask::CompactionWithStealingTask(ParallelTaskTerminator* t):
  _terminator(t) {}

void CompactionWithStealingTask::do_it(GCTaskManager* manager, uint which) {
  assert(ParallelScavengeHeap::heap()->is_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  // Drain the stacks that have been preloaded with regions
  // that are ready to fill.

  cm->drain_region_stacks();

  guarantee(cm->region_stack()->is_empty(), "Not empty");

  size_t region_index = 0;

  while(true) {
    if (ParCompactionManager::steal(which, region_index)) {
      PSParallelCompact::fill_and_update_region(cm, region_index);
      cm->drain_region_stacks();
    } else {
      if (terminator()->offer_termination()) {
        break;
      }
      // Go around again.
    }
  }
  return;
}

UpdateDensePrefixTask::UpdateDensePrefixTask(
                                   PSParallelCompact::SpaceId space_id,
                                   size_t region_index_start,
                                   size_t region_index_end) :
  _space_id(space_id), _region_index_start(region_index_start),
  _region_index_end(region_index_end) {}

void UpdateDensePrefixTask::do_it(GCTaskManager* manager, uint which) {

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  PSParallelCompact::update_and_deadwood_in_dense_prefix(cm,
                                                         _space_id,
                                                         _region_index_start,
                                                         _region_index_end);
}
