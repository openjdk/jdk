/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zValue.hpp"

class ReferencePolicy;
class ZWorkers;

class ZReferenceProcessor : public ReferenceDiscoverer {
  friend class ZReferenceProcessorTask;

private:
  static const size_t reference_type_count = REF_PHANTOM + 1;
  typedef size_t Counters[reference_type_count];

  ZWorkers* const      _workers;
  ReferencePolicy*     _soft_reference_policy;
  ZPerWorker<Counters> _encountered_count;
  ZPerWorker<Counters> _discovered_count;
  ZPerWorker<Counters> _enqueued_count;
  ZPerWorker<oop>      _discovered_list;
  ZContended<oop>      _pending_list;
  oop*                 _pending_list_tail;

  void update_soft_reference_clock() const;

  ReferenceType reference_type(oop obj) const;
  const char* reference_type_name(ReferenceType type) const;
  volatile oop* reference_referent_addr(oop obj) const;
  oop reference_referent(oop obj) const;
  bool is_reference_inactive(oop obj) const;
  bool is_referent_strongly_alive_or_null(oop obj, ReferenceType type) const;
  bool is_referent_softly_alive(oop obj, ReferenceType type) const;
  bool should_drop_reference(oop obj, ReferenceType type) const;
  bool should_mark_referent(ReferenceType type) const;
  bool should_clear_referent(ReferenceType type) const;
  void keep_referent_alive(oop obj, ReferenceType type) const;

  void discover(oop obj, ReferenceType type);
  oop drop(oop obj, ReferenceType type);
  oop* keep(oop obj, ReferenceType type);

  bool is_empty() const;

  void work();
  void collect_statistics();

public:
  ZReferenceProcessor(ZWorkers* workers);

  void set_soft_reference_policy(bool clear);
  void reset_statistics();

  virtual bool discover_reference(oop reference, ReferenceType type);
  void process_references();
  void enqueue_references();
};

#endif // SHARE_GC_Z_ZREFERENCEPROCESSOR_HPP
