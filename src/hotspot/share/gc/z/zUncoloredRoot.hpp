/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

// ZGC has two types of oops:
//
// Colored oops (zpointer)
//   Metadata explicitly encoded in the pointer bits.
//   Requires normal GC barriers to use.
//   - OopStorage oops.
//
// Uncolored oops (zaddress, zaddress_unsafe)
//   Metadata is either implicit or stored elsewhere
//   Requires specialized GC barriers
//   - nmethod oops - nmethod entry barriers
//   - Thread oops - stack watermark barriers
//
// Even though the uncolored roots lack the color/metadata, ZGC still needs
// that information when processing the roots. Therefore, we store the color
// in the "container" object where the oop is located, and use specialized
// GC barriers, which accepts the external color as an extra argument. These
// roots are handled in this file.
//
// The zaddress_unsafe type is used to hold uncolored oops that the GC needs
// to process before it is safe to use. E.g. the original object might have
// been relocated and the address needs to be updated. The zaddress type
// denotes that this pointer refers to the correct address of the object.

class ZUncoloredRoot : public AllStatic {
private:
  template <typename ObjectFunctionT>
  static void barrier(ObjectFunctionT function, zaddress_unsafe* p, uintptr_t color);

  static zaddress make_load_good(zaddress_unsafe addr, uintptr_t color);

public:
  // Operations to be used on oops that are known to be load good
  static void mark_object(zaddress addr);
  static void mark_invisible_object(zaddress addr);
  static void keep_alive_object(zaddress addr);
  static void mark_young_object(zaddress addr);

  // Operations on roots, with an externally provided color
  static void mark(zaddress_unsafe* p, uintptr_t color);
  static void mark_young(zaddress_unsafe* p, uintptr_t color);
  static void process(zaddress_unsafe* p, uintptr_t color);
  static void process_invisible(zaddress_unsafe* p, uintptr_t color);
  static void process_weak(zaddress_unsafe* p, uintptr_t color);
  static void process_no_keepalive(zaddress_unsafe* p, uintptr_t color);

  // Cast needed when ZGC interfaces with the rest of the JVM,
  // which is agnostic to ZGC's oop type system.
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

class ZUncoloredRootProcessWeakOopClosure : public ZUncoloredRootClosure {
private:
  const uintptr_t _color;

public:
  ZUncoloredRootProcessWeakOopClosure(uintptr_t color);

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
