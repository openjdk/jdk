/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XREFERENCEPROCESSOR_HPP
#define SHARE_GC_X_XREFERENCEPROCESSOR_HPP

#include "gc/shared/referenceDiscoverer.hpp"
#include "gc/x/xValue.hpp"

class ReferencePolicy;
class XWorkers;

class XReferenceProcessor : public ReferenceDiscoverer {
  friend class XReferenceProcessorTask;

private:
  static const size_t reference_type_count = REF_PHANTOM + 1;
  typedef size_t Counters[reference_type_count];

  XWorkers* const      _workers;
  ReferencePolicy*     _soft_reference_policy;
  XPerWorker<Counters> _encountered_count;
  XPerWorker<Counters> _discovered_count;
  XPerWorker<Counters> _enqueued_count;
  XPerWorker<oop>      _discovered_list;
  XContended<oop>      _pending_list;
  oop*                 _pending_list_tail;

  bool is_inactive(oop reference, oop referent, ReferenceType type) const;
  bool is_strongly_live(oop referent) const;
  bool is_softly_live(oop reference, ReferenceType type) const;

  bool should_discover(oop reference, ReferenceType type) const;
  bool should_drop(oop reference, ReferenceType type) const;
  void keep_alive(oop reference, ReferenceType type) const;
  void make_inactive(oop reference, ReferenceType type) const;

  void discover(oop reference, ReferenceType type);

  oop drop(oop reference, ReferenceType type);
  oop* keep(oop reference, ReferenceType type);

  bool is_empty() const;

  void work();
  void collect_statistics();

public:
  XReferenceProcessor(XWorkers* workers);

  void set_soft_reference_policy(bool clear);
  void reset_statistics();

  virtual bool discover_reference(oop reference, ReferenceType type);
  void process_references();
  void enqueue_references();
};

#endif // SHARE_GC_X_XREFERENCEPROCESSOR_HPP
