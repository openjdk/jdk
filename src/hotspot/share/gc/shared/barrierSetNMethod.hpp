/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_BARRIERSETNMETHOD_HPP
#define SHARE_GC_SHARED_BARRIERSETNMETHOD_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/sizes.hpp"

class nmethod;

class BarrierSetNMethod: public CHeapObj<mtGC> {
private:
  int _current_phase;
  enum {
    not_entrant = 1 << 31, // armed sticky bit, see make_not_entrant
    armed = 0,
    initial = 1,
  };

  void deoptimize(nmethod* nm, address* return_addr_ptr);

protected:
  virtual int guard_value(nmethod* nm);
  void set_guard_value(nmethod* nm, int value);

public:
  BarrierSetNMethod() : _current_phase(initial) {}
  bool supports_entry_barrier(nmethod* nm);

  virtual bool nmethod_entry_barrier(nmethod* nm);
  virtual ByteSize thread_disarmed_guard_value_offset() const;
  virtual int* disarmed_guard_value_address() const;

  int disarmed_guard_value() const;

  static int nmethod_stub_entry_barrier(address* return_address_ptr);
  bool nmethod_osr_entry_barrier(nmethod* nm);
  virtual bool is_armed(nmethod* nm);
  void arm(nmethod* nm) { guard_with(nm, armed); }
  void disarm(nmethod* nm);
  virtual void make_not_entrant(nmethod* nm);
  virtual bool is_not_entrant(nmethod* nm);

  virtual void guard_with(nmethod* nm, int value);

  virtual void arm_all_nmethods();

  virtual oop oop_load_no_keepalive(const nmethod* nm, int index);
  virtual oop oop_load_phantom(const nmethod* nm, int index);

#if INCLUDE_JVMCI
  bool verify_barrier(nmethod* nm, FormatBuffer<>& msg);
#endif
};


#endif // SHARE_GC_SHARED_BARRIERSETNMETHOD_HPP
