/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_SCAVENGABLENMETHODS_HPP
#define SHARE_GC_SHARED_SCAVENGABLENMETHODS_HPP

#include "memory/allocation.hpp"
#include "utilities/macros.hpp"

class BoolObjectClosure;
class CodeBlobClosure;
class CodeBlobToOopClosure;
class nmethod;

class ScavengableNMethods : public AllStatic {
  friend class VMStructs;

  static nmethod*           _head;
  static BoolObjectClosure* _is_scavengable;

public:
  static void initialize(BoolObjectClosure* is_scavengable);

  static void register_nmethod(nmethod* nm);
  static void unregister_nmethod(nmethod* nm);
  static void verify_nmethod(nmethod* nm);
  static void flush_nmethod(nmethod* nm);

  static void prune_nmethods();

  // Apply f to every live code blob in scavengable nmethods. Prune nmethods
  // from the list of scavengable nmethods if f->fix_relocations() and a nmethod
  // no longer has scavengable oops.  If f->fix_relocations(), then f must copy
  // objects to their new location immediately to avoid fixing nmethods on the
  // basis of the old object locations.
  static void scavengable_nmethods_do(CodeBlobToOopClosure* f);

  static void asserted_non_scavengable_nmethods_do(CodeBlobClosure* f = NULL) PRODUCT_RETURN;

private:
  static void unlist_nmethod(nmethod* nm, nmethod* prev);

  static bool has_scavengable_oops(nmethod* nm);

  static void mark_on_list_nmethods() PRODUCT_RETURN;
  static void verify_unlisted_nmethods(CodeBlobClosure* f_or_null) PRODUCT_RETURN;
};

#endif // SHARE_GC_SHARED_SCAVENGABLENMETHODS_HPP
