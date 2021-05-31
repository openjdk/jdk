/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZUNCOLOREDROOT_HPP
#define SHARE_GC_Z_ZUNCOLOREDROOT_HPP

#include "gc/z/zAddress.hpp"
#include "memory/allStatic.hpp"
#include "memory/iterator.hpp"
#include "oops/oopsHierarchy.hpp"

class ZUncoloredRoot : public AllStatic {
private:
  template <typename ObjectFunctionT>
  static void barrier(ObjectFunctionT function, zaddress_unsafe* p, uintptr_t color);

  static zaddress make_load_good(zaddress_unsafe addr, uintptr_t color);

  static bool matches_mark_phase(zaddress addr);

  static bool during_minor_mark();
  static bool during_major_mark();

  static void mark_invisible_object(zaddress addr);

  static void process_invisible_object(zaddress addr);

public:
  static void mark_object(zaddress addr);
  static void mark_young_object(zaddress addr);
  static void keep_alive_object(zaddress addr);

  static void mark(zaddress_unsafe* p, uintptr_t color);
  static void mark_young(zaddress_unsafe* p, uintptr_t color);
  static void process(zaddress_unsafe* p, uintptr_t color);
  static void process_no_keepalive(zaddress_unsafe* p, uintptr_t color);
  static void process_invisible(zaddress_unsafe* p, uintptr_t color);

  static zaddress_unsafe* cast(oop* p);

  typedef void (*RootFunction)(zaddress_unsafe*, uintptr_t);
  typedef void (*ObjectFunction)(zaddress);
};

class ZUncoloredRootClosure : public OopClosure {
private:
  void do_oop(oop* p) final;
  void do_oop(narrowOop* p) final;

public:
  virtual void do_root(zaddress_unsafe* p) = 0;
};

class ZUncoloredRootMarkOopClosure : public ZUncoloredRootClosure {
private:
  const uintptr_t _color;

public:
  ZUncoloredRootMarkOopClosure(uintptr_t color);

  virtual void do_root(zaddress_unsafe* p);
};

class ZUncoloredRootMarkYoungOopClosure : public ZUncoloredRootClosure {
private:
  const uintptr_t _color;

public:
  ZUncoloredRootMarkYoungOopClosure(uintptr_t color);

  virtual void do_root(zaddress_unsafe* p);
};

class ZUncoloredRootProcessOopClosure : public ZUncoloredRootClosure {
private:
  const uintptr_t _color;

public:
  ZUncoloredRootProcessOopClosure(uintptr_t color);

  virtual void do_root(zaddress_unsafe* p);
};

class ZUncoloredRootProcessNoKeepaliveOopClosure : public ZUncoloredRootClosure {
private:
  const uintptr_t _color;

public:
  ZUncoloredRootProcessNoKeepaliveOopClosure(uintptr_t color);

  virtual void do_root(zaddress_unsafe* p);
};

#endif // SHARE_GC_Z_ZUNCOLOREDROOT_HPP
