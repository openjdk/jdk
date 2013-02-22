/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/binaryTreeDictionary.hpp"
#include "memory/defNewGeneration.hpp"
#include "memory/filemap.hpp"
#include "memory/genRemSet.hpp"
#include "memory/generationSpec.hpp"
#include "memory/tenuredGeneration.hpp"
#include "runtime/java.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/parNew/asParNewGeneration.hpp"
#include "gc_implementation/concurrentMarkSweep/concurrentMarkSweepGeneration.hpp"
#include "gc_implementation/parNew/parNewGeneration.hpp"
#endif // INCLUDE_ALL_GCS

Generation* GenerationSpec::init(ReservedSpace rs, int level,
                                 GenRemSet* remset) {
  switch (name()) {
    case Generation::DefNew:
      return new DefNewGeneration(rs, init_size(), level);

    case Generation::MarkSweepCompact:
      return new TenuredGeneration(rs, init_size(), level, remset);

#if INCLUDE_ALL_GCS
    case Generation::ParNew:
      return new ParNewGeneration(rs, init_size(), level);

    case Generation::ASParNew:
      return new ASParNewGeneration(rs,
                                    init_size(),
                                    init_size() /* min size */,
                                    level);

    case Generation::ConcurrentMarkSweep: {
      assert(UseConcMarkSweepGC, "UseConcMarkSweepGC should be set");
      CardTableRS* ctrs = remset->as_CardTableRS();
      if (ctrs == NULL) {
        vm_exit_during_initialization("Rem set incompatibility.");
      }
      // Otherwise
      // The constructor creates the CMSCollector if needed,
      // else registers with an existing CMSCollector

      ConcurrentMarkSweepGeneration* g = NULL;
      g = new ConcurrentMarkSweepGeneration(rs,
                 init_size(), level, ctrs, UseCMSAdaptiveFreeLists,
                 (FreeBlockDictionary<FreeChunk>::DictionaryChoice)CMSDictionaryChoice);

      g->initialize_performance_counters();

      return g;
    }

    case Generation::ASConcurrentMarkSweep: {
      assert(UseConcMarkSweepGC, "UseConcMarkSweepGC should be set");
      CardTableRS* ctrs = remset->as_CardTableRS();
      if (ctrs == NULL) {
        vm_exit_during_initialization("Rem set incompatibility.");
      }
      // Otherwise
      // The constructor creates the CMSCollector if needed,
      // else registers with an existing CMSCollector

      ASConcurrentMarkSweepGeneration* g = NULL;
      g = new ASConcurrentMarkSweepGeneration(rs,
                 init_size(), level, ctrs, UseCMSAdaptiveFreeLists,
                 (FreeBlockDictionary<FreeChunk>::DictionaryChoice)CMSDictionaryChoice);

      g->initialize_performance_counters();

      return g;
    }
#endif // INCLUDE_ALL_GCS

    default:
      guarantee(false, "unrecognized GenerationName");
      return NULL;
  }
}
