/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "jfr/leakprofiler/chains/edge.hpp"
#include "jfr/leakprofiler/utilities/unifiedOop.hpp"

Edge::Edge() : _parent(NULL), _reference(NULL) {}

Edge::Edge(const Edge* parent, const oop* reference) : _parent(parent),
                                                       _reference(reference) {}

const oop Edge::pointee() const {
  return UnifiedOop::dereference(_reference);
}

const oop Edge::reference_owner() const {
  return is_root() ? (oop)NULL : UnifiedOop::dereference(_parent->reference());
}

static const Klass* resolve_klass(const oop obj) {
  assert(obj != NULL, "invariant");
  return java_lang_Class::is_instance(obj) ?
    java_lang_Class::as_Klass(obj) : obj->klass();
}

const Klass* Edge::pointee_klass() const {
  return resolve_klass(pointee());
}

const Klass* Edge::reference_owner_klass() const {
  const oop ref_owner = reference_owner();
  return ref_owner != NULL ? resolve_klass(ref_owner) : NULL;
}

size_t Edge::distance_to_root() const {
  size_t depth = 0;
  const Edge* current = _parent;
  while (current != NULL) {
    depth++;
    current = current->parent();
  }
  return depth;
}
