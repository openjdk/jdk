/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZBARRIERSETNMETHOD_HPP
#define SHARE_GC_Z_ZBARRIERSETNMETHOD_HPP

#include "gc/shared/barrierSetNMethod.hpp"
#include "memory/allocation.hpp"

class nmethod;

class ZBarrierSetNMethod : public BarrierSetNMethod {
  enum : int {
    not_entrant = 1 << 31, // armed sticky bit, see make_not_entrant
  };

protected:
  virtual bool nmethod_entry_barrier(nmethod* nm);

public:
  uintptr_t color(nmethod* nm);

  virtual ByteSize thread_disarmed_guard_value_offset() const;
  virtual int* disarmed_guard_value_address() const;

  virtual oop oop_load_no_keepalive(const nmethod* nm, int index);
  virtual oop oop_load_phantom(const nmethod* nm, int index);

  virtual void make_not_entrant(nmethod* nm);
  virtual bool is_not_entrant(nmethod* nm);
  virtual void guard_with(nmethod* nm, int value);
  virtual bool is_armed(nmethod* nm);
  virtual void arm_all_nmethods() { ShouldNotCallThis(); }
};

#endif // SHARE_GC_Z_ZBARRIERSETNMETHOD_HPP
