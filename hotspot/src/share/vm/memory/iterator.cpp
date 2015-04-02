/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

void KlassToOopClosure::do_klass(Klass* k) {
  assert(_oop_closure != NULL, "Not initialized?");
  k->oops_do(_oop_closure);
}

void CLDToOopClosure::do_cld(ClassLoaderData* cld) {
  cld->oops_do(_oop_closure, &_klass_closure, _must_claim_cld);
}

void CLDToKlassAndOopClosure::do_cld(ClassLoaderData* cld) {
  cld->oops_do(_oop_closure, _klass_closure, _must_claim_cld);
}

void ObjectToOopClosure::do_object(oop obj) {
  obj->oop_iterate(_cl);
}

void VoidClosure::do_void() {
  ShouldNotCallThis();
}

void CodeBlobToOopClosure::do_nmethod(nmethod* nm) {
  nm->oops_do(_cl);
  if (_fix_relocations) {
    nm->fix_oop_relocations();
  }
}

void CodeBlobToOopClosure::do_code_blob(CodeBlob* cb) {
  nmethod* nm = cb->as_nmethod_or_null();
  if (nm != NULL) {
    do_nmethod(nm);
  }
}

void MarkingCodeBlobClosure::do_code_blob(CodeBlob* cb) {
  nmethod* nm = cb->as_nmethod_or_null();
  if (nm != NULL && !nm->test_set_oops_do_mark()) {
    do_nmethod(nm);
  }
}

// Generate the *Klass::oop_oop_iterate functions for the base class
// of the oop closures. These versions use the virtual do_oop calls,
// instead of the devirtualized do_oop_nv version.
ALL_KLASS_OOP_OOP_ITERATE_DEFN(ExtendedOopClosure,  _v)

// Generate the *Klass::oop_oop_iterate functions
// for the NoHeaderExtendedOopClosure helper class.
ALL_KLASS_OOP_OOP_ITERATE_DEFN(NoHeaderExtendedOopClosure, _nv)
