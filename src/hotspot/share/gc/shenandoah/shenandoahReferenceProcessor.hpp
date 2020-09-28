/*
 * Copyright (c) 2020, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHREFERENCEPROCESSOR_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHREFERENCEPROCESSOR_HPP

#include "gc/shared/referenceDiscoverer.hpp"
#include "memory/allocation.hpp"

class WorkGang;

class ShenandoahRefProcThreadLocal : public CHeapObj<mtGC> {
private:
  void* _discovered_list;

public:
  ShenandoahRefProcThreadLocal();
  void reset();

  template<typename T>
  T* discovered_list_addr();
  template<typename T>
  T discovered_list_head() const;
  template<typename T>
  void set_discovered_list_head(oop head);
};

class ShenandoahReferenceProcessor : public ReferenceDiscoverer {
private:
  ReferencePolicy* _soft_reference_policy;

  ShenandoahRefProcThreadLocal* _ref_proc_thread_locals;

  oop _pending_list;
  void* _pending_list_tail; // T*

  volatile uint _iterate_discovered_list_id;

  template <typename T>
  bool is_inactive(oop reference, oop referent, ReferenceType type) const;
  bool is_strongly_live(oop referent) const;
  bool is_softly_live(oop reference, ReferenceType type) const;

  template <typename T>
  bool should_discover(oop reference, ReferenceType type) const;
  template <typename T>
  bool should_drop(oop reference, ReferenceType type) const;

  // template <typename T>
  // void keep_alive(oop reference, ReferenceType type) const;

  template <typename T>
  void make_inactive(oop reference, ReferenceType type) const;

  template <typename T>
  bool discover(oop reference, ReferenceType type);

  template <typename T>
  T drop(oop reference, ReferenceType type);
  template <typename T>
  T* keep(oop reference, ReferenceType type);

  template <typename T>
  void process_references(ShenandoahRefProcThreadLocal& refproc_data);
  void enqueue_references();

public:
  ShenandoahReferenceProcessor(uint max_workers);

  void reset_thread_locals();

  void set_soft_reference_policy(bool clear);

  bool discover_reference(oop obj, ReferenceType type) override;

  void process_references(WorkGang* workers);

  void work();

  // TODO: Temporary methods to allow transition.
  void set_active_mt_degree(uint num_workers) {};
  void enable_discovery(bool verify_no_refs) {};
  void disable_discovery() {}
  void abandon_partial_discovery() {}
  void verify_no_references_recorded() {}
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHREFERENCEPROCESSOR_HPP
