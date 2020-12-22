/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_UTILITIES_JFREPOCHHASHTABLE_HPP
#define SHARE_JFR_UTILITIES_JFREPOCHHASHTABLE_HPP

#include "jfr/utilities/jfrAllocation.hpp"

 /*
  * A hashtable as a function of epochs, with iteration capabilities for the current and previous epoch.
  *
  * The design caters to use cases having multiple incremental iterations over the current epoch,
  * and a single iteration over the previous epoch.
  *
  * The JfrEpochHashTable can be specialized by the following policies:
  *
  * ListType     the type of list to be used for buckets
  *
  * AllocPolicy  the type of memory allocator
  *
  */
template <typename ListType, typename AllocPolicy = JfrCHeapObj>
class JfrEpochHashTable : public AllocPolicy {
 public:
  typedef typename ListType::Node Node;
  typedef typename ListType::NodePtr NodePtr;
  typedef typename const ListType::Node* ConstNodePtr;
  typedef ListType Bucket;

  // Table size must always be a power of two.
  JfrEpochHashTable(size_t initial_size, double resize_factor, size_t chain_limit);
  ~JfrEpochHashTable();
  void allocate_next_epoch_table();
  bool initialize();
  double load_factor() const;
  size_t elements() const;
  size_t longest_chain() const;
  size_t size(bool previous_epoch = false) const;

  void insert(NodePtr node, uintx hash);

  template <typename SearchPolicy>
  void lookup(SearchPolicy& search);

  template <typename Callback>
  void iterate(Callback& callback, bool previous_epoch = false);

  template <typename Callback>
  void iterate_with_excision(Callback& callback);

 private:
  Bucket* _table_epoch_0;
  Bucket* _table_epoch_1;
  mutable size_t _table_size_epoch_0;
  mutable size_t _table_size_epoch_1;
  size_t _mask;
  const double _resize_factor;
  const size_t _chain_limit;
  size_t _elements;
  volatile size_t _longest_chain;

  size_t idx(uintx hash) const;
  Bucket& bucket(size_t idx);
  void report_chain(size_t length);
  void increment_elements();
  bool recalculate_table_size(size_t* new_size);

  const Bucket* table_selector(u1 epoch) const;
  Bucket** table_addr_selector(u1 epoch);
  Bucket** table_addr(bool previous_epoch = true);
  size_t* table_size_addr_selector(u1 epoch) const;
  size_t* table_size_addr(bool previous_epoch = true) const;
  const Bucket* current_epoch_table() const;
  Bucket* current_epoch_table();
  const Bucket* previous_epoch_table() const;
  Bucket* previous_epoch_table();
  size_t current_epoch_table_size() const;
  size_t previous_epoch_table_size() const;

  template <typename Callback>
  class Lookup {
   private:
    Callback& _callback;
    size_t _seek_length;
   public:
    typedef Node Type;
    Lookup(Callback& callback);
    bool process(ConstNodePtr node);
    size_t seek_length() const;
  };

  template <typename Callback>
  class ElementDispatchDetach {
   private:
    Callback& _callback;
    Bucket& _bucket;
   public:
    typedef Node Type;
    ElementDispatchDetach(Callback& callback, Bucket& bucket);
    ~ElementDispatchDetach();
    bool process(ConstNodePtr node);
  };
};

#endif // SHARE_JFR_UTILITIES_JFREPOCHHASHTABLE_HPP
