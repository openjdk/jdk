/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_LEAKPROFILER_CHECKPOINT_LOGICALEDGESTORE_HPP
#define SHARE_JFR_LEAKPROFILER_CHECKPOINT_LOGICALEDGESTORE_HPP

#include "jfr/utilities/jfrHashtable.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class InstanceKlass;
class Klass;

// Parent identities are physical holder addresses or
// synthetic (logical) identities assigned by LogicalEdgeStore.
// The flattened value is embedded directly in its parent,
// with no object-reference slot connecting them,
// so the edge is logical rather than physical.
class LogicalEdge {
 public:
  enum Kind { Field, ArrayElement };

 private:
  u8 _parent_identity;
  Kind _kind;
  const InstanceKlass* _field_holder;
  int _index;
  const Klass* _value_klass;

 public:
  LogicalEdge(u8 parent_identity, Kind kind,
              const InstanceKlass* field_holder,
              int index, const Klass* value_klass);

  uintptr_t hash() const;
  bool equals(const LogicalEdge& other) const;
  const Klass* value_klass() const { return _value_klass; }
};

// Interns flattened occurrences.
class LogicalEdgeStore : public CHeapObj<mtTracing> {
  template <typename, typename, template <typename, typename> class, typename, size_t>
  friend class HashTableHost;

  typedef HashTableHost<LogicalEdge, u8, JfrHashtableEntry,
                        LogicalEdgeStore> Table;
  typedef Table::HashEntry Entry;

  static u8 _last_id;
  const LogicalEdge* _lookup;
  Table _table;

  NONCOPYABLE(LogicalEdgeStore);

  u8 next_id() const;
  void on_link(Entry* entry);
  bool on_equals(uintptr_t hash, const Entry* entry);
  void on_unlink(Entry* entry);

 public:
  LogicalEdgeStore();

  // Returns an existing or newly assigned logical identity, or zero on exhaustion.
  u8 get_id(const LogicalEdge& edge);
};

#endif // SHARE_JFR_LEAKPROFILER_CHECKPOINT_LOGICALEDGESTORE_HPP
