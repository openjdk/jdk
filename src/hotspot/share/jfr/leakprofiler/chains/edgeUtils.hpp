/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_LEAKPROFILER_CHAINS_EDGEUTILS_HPP
#define SHARE_JFR_LEAKPROFILER_CHAINS_EDGEUTILS_HPP

#include "memory/allStatic.hpp"
#include "utilities/growableArray.hpp"

class Edge;
class Symbol;
class Klass;
class InstanceKlass;

class EdgeField {
 public:
  const Symbol* _name;
  jshort _modifiers;
  const Klass* _value_klass;
  // Declaration identity for interning flattened occurrences. Names alone do
  // not distinguish a field from a same-named field declared by a superclass.
  const InstanceKlass* _field_holder;
  int _field_index;
  EdgeField() : _name(nullptr), _modifiers(0), _value_klass(nullptr),
                _field_holder(nullptr), _field_index(-1) {}
  EdgeField(const Symbol* name, jshort modifiers, const Klass* value_klass = nullptr,
            const InstanceKlass* field_holder = nullptr, int field_index = -1) :
    _name(name), _modifiers(modifiers), _value_klass(value_klass),
    _field_holder(field_holder), _field_index(field_index) {}
};

class EdgeUtils : public AllStatic {
 public:
  static const size_t leak_context = 100;
  static const size_t root_context = 100;
  static const size_t max_ref_chain_depth = leak_context + root_context;

  static const Edge* root(const Edge& edge);
  static const Edge* ancestor(const Edge& edge, size_t distance);

  static bool is_array_element(const Edge& edge);
  static int array_index(const Edge& edge);
  static int array_size(const Edge& edge);

  // Returns a field path relative to the containing object or flat array element.
  // The containing object or element is NOT included in fields.
  // Entries run from the outermost field to the field storing the reference.
  //
  // For an ordinary reference field, holder.ref, the relative path is [ref], at GrowableArray index 0.
  //
  // For holder.value.nested.ref, where value and nested are flattened,
  // the relative path is [value, nested, ref], at indexes 0, 1 and 2, respectively.
  //
  // For array[i].nested.ref, where nested is flattened,
  // the path relative to array[i] is [nested, ref], at indexes 0 and 1, respectively.
  //
  // Resolution is all or nothing: if value and nested resolve but ref does not,
  // the partial path is cleared. On failure, returns false with fields empty.
  static bool field_path(const Edge& edge, GrowableArray<EdgeField>* fields);
};

#endif // SHARE_JFR_LEAKPROFILER_CHAINS_EDGEUTILS_HPP
