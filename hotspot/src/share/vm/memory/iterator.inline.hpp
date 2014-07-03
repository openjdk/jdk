/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_ITERATOR_INLINE_HPP
#define SHARE_VM_MEMORY_ITERATOR_INLINE_HPP

#include "classfile/classLoaderData.hpp"
#include "memory/iterator.hpp"
#include "oops/klass.hpp"
#include "utilities/debug.hpp"

inline void MetadataAwareOopClosure::do_class_loader_data(ClassLoaderData* cld) {
  assert(_klass_closure._oop_closure == this, "Must be");

  bool claim = true;  // Must claim the class loader data before processing.
  cld->oops_do(_klass_closure._oop_closure, &_klass_closure, claim);
}

inline void MetadataAwareOopClosure::do_klass_nv(Klass* k) {
  ClassLoaderData* cld = k->class_loader_data();
  do_class_loader_data(cld);
}

inline void MetadataAwareOopClosure::do_klass(Klass* k)       { do_klass_nv(k); }

#endif // SHARE_VM_MEMORY_ITERATOR_INLINE_HPP
