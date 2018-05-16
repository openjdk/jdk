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

#ifndef SHARE_VM_LEAKPROFILER_CHAINS_EDGEUTILS_HPP
#define SHARE_VM_LEAKPROFILER_CHAINS_EDGEUTILS_HPP

#include "memory/allocation.hpp"

class Edge;
class RoutableEdge;
class Symbol;

class EdgeUtils : public AllStatic {
 public:
  static bool is_leak_edge(const Edge& edge);

  static const Edge* root(const Edge& edge);
  static bool is_root(const Edge& edge);

  static bool is_array_element(const Edge& edge);
  static int array_index(const Edge& edge);
  static int array_size(const Edge& edge);

  static const Symbol* field_name_symbol(const Edge& edge);
  static jshort field_modifiers(const Edge& edge);

  static void collapse_chain(const RoutableEdge& edge);
};

#endif // SHARE_VM_LEAKPROFILER_CHAINS_EDGEUTILS_HPP
