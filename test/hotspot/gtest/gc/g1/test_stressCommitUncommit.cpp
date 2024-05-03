/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BlockOffsetTable.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

class G1MapperWorkers : AllStatic {
  static WorkerThreads* _workers;
  static WorkerThreads* workers() {
    if (_workers == nullptr) {
      _workers = new WorkerThreads("G1 Small Workers", MaxWorkers);
      _workers->initialize_workers();
      _workers->set_active_workers(MaxWorkers);
    }
    return _workers;
  }

public:
  static const uint MaxWorkers = 4;
  static void run_task(WorkerTask* task) {
    workers()->run_task(task);
  }
};
WorkerThreads* G1MapperWorkers::_workers = nullptr;

class G1TestCommitUncommit : public WorkerTask {
  G1RegionToSpaceMapper* _mapper;
  uint _claim_id;
public:
  G1TestCommitUncommit(G1RegionToSpaceMapper* mapper) :
      WorkerTask("Stress mapper"),
      _mapper(mapper),
      _claim_id(0) { }

  void work(uint worker_id) {
    uint index = Atomic::fetch_then_add(&_claim_id, 1u);

    for (int i = 0; i < 100000; i++) {
      // Stress commit and uncommit of a single region. The same
      // will be done for multiple adjacent region to make sure
      // we properly handle bitmap updates as well as updates for
      // regions sharing the same underlying OS page.
      _mapper->commit_regions(index);
      _mapper->uncommit_regions(index);
    }
  }
};

TEST_VM(G1RegionToSpaceMapper, smallStressAdjacent) {
  // Fake a heap with 1m regions and create a BOT like mapper. This
  // will give a G1RegionsSmallerThanCommitSizeMapper to stress.
  uint num_regions = G1MapperWorkers::MaxWorkers;
  size_t region_size = 1*M;
  size_t size = G1BlockOffsetTable::compute_size(num_regions * region_size / HeapWordSize);
  size_t page_size = os::vm_page_size();

  ReservedSpace rs(size, os::vm_page_size(), mtTest);

  G1RegionToSpaceMapper* small_mapper  =
    G1RegionToSpaceMapper::create_mapper(rs,
                                         size,
                                         page_size,
                                         region_size,
                                         G1BlockOffsetTable::heap_map_factor(),
                                         mtGC);



  G1TestCommitUncommit task(small_mapper);
  G1MapperWorkers::run_task(&task);
}

TEST_VM(G1RegionToSpaceMapper, largeStressAdjacent) {
  // Fake a heap with 2m regions and create a BOT like mapper. This
  // will give a G1RegionsLargerThanCommitSizeMapper to stress.
  uint num_regions = G1MapperWorkers::MaxWorkers;
  size_t region_size = 2*M;
  size_t size = G1BlockOffsetTable::compute_size(num_regions * region_size / HeapWordSize);
  size_t page_size = os::vm_page_size();

  ReservedSpace rs(size, page_size, mtTest);

  G1RegionToSpaceMapper* large_mapper  =
    G1RegionToSpaceMapper::create_mapper(rs,
                                         size,
                                         page_size,
                                         region_size,
                                         G1BlockOffsetTable::heap_map_factor(),
                                         mtGC);

  G1TestCommitUncommit task(large_mapper);
  G1MapperWorkers::run_task(&task);
}
