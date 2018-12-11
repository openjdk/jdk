/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHTRAVERSALHEURISTICS_HPP
#define SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHTRAVERSALHEURISTICS_HPP

#include "gc/shenandoah/shenandoahHeuristics.hpp"

class ShenandoahTraversalHeuristics : public ShenandoahHeuristics {
private:
  uint64_t _last_cset_select;

protected:
  virtual void choose_collection_set_from_regiondata(ShenandoahCollectionSet* set,
                                                     RegionData* data, size_t data_size,
                                                     size_t free);

public:
  ShenandoahTraversalHeuristics();

  virtual bool should_start_normal_gc() const;

  virtual bool is_experimental();

  virtual bool is_diagnostic();

  virtual bool can_do_traversal_gc();

  virtual const char* name();

  virtual void choose_collection_set(ShenandoahCollectionSet* collection_set);

  virtual bool should_start_traversal_gc();
};

#endif // SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHTRAVERSALHEURISTICS_HPP
