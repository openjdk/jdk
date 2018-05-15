/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_JFR_CHECKPOINT_TYPES_JFRTYPEMANAGER_HPP
#define SHARE_VM_JFR_CHECKPOINT_TYPES_JFRTYPEMANAGER_HPP

#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrDoublyLinkedList.hpp"
#include "jfr/utilities/jfrIterator.hpp"

class JfrSerializerRegistration : public JfrCHeapObj {
 private:
  JfrSerializerRegistration* _next;
  JfrSerializerRegistration* _prev;
  JfrSerializer* _serializer;
  mutable JfrCheckpointBlobHandle _cache;
  JfrTypeId _id;
  bool _permit_cache;

 public:
  JfrSerializerRegistration(JfrTypeId id, bool permit_cache, JfrSerializer* serializer);
  ~JfrSerializerRegistration();
  JfrSerializerRegistration* next() const;
  void set_next(JfrSerializerRegistration* next);
  JfrSerializerRegistration* prev() const;
  void set_prev(JfrSerializerRegistration* prev);
  void invoke_serializer(JfrCheckpointWriter& writer) const;
  JfrTypeId id() const;
};

class JfrTypeManager : public JfrCHeapObj {
  friend class JfrCheckpointManager;
 public:
  typedef JfrDoublyLinkedList<JfrSerializerRegistration> List;
  typedef StopOnNullIterator<const List> Iterator;
 private:
  List _types;
  List _safepoint_types;

  ~JfrTypeManager();
  bool initialize();
  size_t number_of_registered_types() const;
  void write_types(JfrCheckpointWriter& writer) const;
  void write_safepoint_types(JfrCheckpointWriter& writer) const;
  void write_type_set() const;
  void write_type_set_for_unloaded_classes() const;
  void create_thread_checkpoint(JavaThread* jt) const;
  void write_thread_checkpoint(JavaThread* jt) const;
  bool register_serializer(JfrTypeId id, bool require_safepoint, bool permit_cache, JfrSerializer* serializer);
};
#endif // SHARE_VM_JFR_CHECKPOINT_TYPES_JFRTYPEMANAGER_HPP
