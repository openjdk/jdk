/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/cardTableRS.hpp"
#include "gc/shared/generationSpec.hpp"
#include "memory/binaryTreeDictionary.hpp"
#include "memory/filemap.hpp"
#include "runtime/java.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_CMSGC
#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/cms/parNewGeneration.hpp"
#endif
#if INCLUDE_SERIALGC
#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/tenuredGeneration.hpp"
#endif

Generation* GenerationSpec::init(ReservedSpace rs, CardTableRS* remset) {
  switch (name()) {
#if INCLUDE_SERIALGC
    case Generation::DefNew:
      return new DefNewGeneration(rs, init_size());

    case Generation::MarkSweepCompact:
      return new TenuredGeneration(rs, init_size(), remset);
#endif

#if INCLUDE_CMSGC
    case Generation::ParNew:
      return new ParNewGeneration(rs, init_size());

    case Generation::ConcurrentMarkSweep: {
      assert(UseConcMarkSweepGC, "UseConcMarkSweepGC should be set");
      if (remset == NULL) {
        vm_exit_during_initialization("Rem set incompatibility.");
      }
      // Otherwise
      // The constructor creates the CMSCollector if needed,
      // else registers with an existing CMSCollector

      ConcurrentMarkSweepGeneration* g = NULL;
      g = new ConcurrentMarkSweepGeneration(rs, init_size(), remset);

      g->initialize_performance_counters();

      return g;
    }
#endif // INCLUDE_CMSGC

    default:
      guarantee(false, "unrecognized GenerationName");
      return NULL;
  }
}
