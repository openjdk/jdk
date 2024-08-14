/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XNMETHOD_HPP
#define SHARE_GC_X_XNMETHOD_HPP

#include "memory/allStatic.hpp"

class nmethod;
class NMethodClosure;
class XReentrantLock;
class XWorkers;

class XNMethod : public AllStatic {
private:
  static void attach_gc_data(nmethod* nm);

  static void log_register(const nmethod* nm);
  static void log_unregister(const nmethod* nm);

public:
  static void register_nmethod(nmethod* nm);
  static void unregister_nmethod(nmethod* nm);

  static bool supports_entry_barrier(nmethod* nm);

  static bool is_armed(nmethod* nm);
  static void disarm(nmethod* nm);
  static void set_guard_value(nmethod* nm, int value);

  static void nmethod_oops_do(nmethod* nm, OopClosure* cl);
  static void nmethod_oops_do_inner(nmethod* nm, OopClosure* cl);

  static void nmethod_oops_barrier(nmethod* nm);

  static void nmethods_do_begin();
  static void nmethods_do_end();
  static void nmethods_do(NMethodClosure* cl);

  static XReentrantLock* lock_for_nmethod(nmethod* nm);
  static XReentrantLock* ic_lock_for_nmethod(nmethod* nm);

  static void unlink(XWorkers* workers, bool unloading_occurred);
  static void purge();
};

#endif // SHARE_GC_X_XNMETHOD_HPP
