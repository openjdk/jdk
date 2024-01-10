/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZREFERENCEPROCESSOR_HPP
#define SHARE_GC_Z_ZREFERENCEPROCESSOR_HPP

#include "gc/shared/referenceDiscoverer.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zValue.hpp"

class ConcurrentGCTimer;
class ReferencePolicy;
class ZWorkers;

class ZReferenceProcessor : public ReferenceDiscoverer {
  friend class ZReferenceProcessorTask;

private:
  static const size_t reference_type_count = REF_PHANTOM + 1;
  typedef size_t Counters[reference_type_count];

  ZWorkers* const      _workers;
  ReferencePolicy*     _soft_reference_policy;
  bool                 _clear_all_soft_refs;
  ZPerWorker<Counters> _encountered_count;
  ZPerWorker<Counters> _discovered_count;
  ZPerWorker<Counters> _enqueued_count;
  ZPerWorker<zaddress> _discovered_list;
  ZContended<zaddress> _pending_list;
  zaddress             _pending_list_tail;

  bool is_inactive(zaddress reference, oop referent, ReferenceType type) const;
  bool is_strongly_live(oop referent) const;
  bool is_softly_live(zaddress reference, ReferenceType type) const;

  bool should_discover(zaddress reference, ReferenceType type) const;
  bool try_make_inactive(zaddress reference, ReferenceType type) const;

  void discover(zaddress reference, ReferenceType type);

  void verify_empty() const;

  void process_worker_discovered_list(zaddress discovered_list);
  void work();
  void collect_statistics();

  zaddress swap_pending_list(zaddress pending_list);

public:
  ZReferenceProcessor(ZWorkers* workers);

  void set_soft_reference_policy(bool clear);
  void reset_statistics();

  virtual bool discover_reference(oop reference, ReferenceType type);
  void process_references();
  void enqueue_references();

  void verify_pending_references();
};

#endif // SHARE_GC_Z_ZREFERENCEPROCESSOR_HPP
