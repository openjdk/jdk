/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CONTEXT_JFRCONTEXT_HPP
#define SHARE_JFR_RECORDER_CONTEXT_JFRCONTEXT_HPP

#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class frame;
class InstanceKlass;
class JavaThread;
class JfrCheckpointWriter;
class JfrChunkWriter;
class JfrContextBinding;

class JfrContextEntry {
  friend class ObjectSampleCheckpoint;
 private:
  char* _name;
  char* _value;

 public:
  JfrContextEntry(const char* name, const char* value);
  ~JfrContextEntry();

  // copy assignement
  JfrContextEntry& operator=(const JfrContextEntry& other);
  // move assignement
  JfrContextEntry& operator=(JfrContextEntry&& other);

  bool equals(const JfrContextEntry& rhs) const;
  void write(JfrChunkWriter& cw) const;
  void write(JfrCheckpointWriter& cpw) const;
};

class JfrContext : public JfrCHeapObj {
  friend class JfrNativeSamplerCallback;
  friend class JfrContextRepository;
  friend class ObjectSampleCheckpoint;
  friend class ObjectSampler;
  friend class OSThreadSampler;
  friend class ContextResolver;
 private:
  const JfrContext* _next;
  JfrContextEntry* _entries;
  traceid _id;
  unsigned int _hash;
  u4 _nr_of_entries;
  u4 _max_entries;
  bool _entries_ownership;
  bool _reached_root;
  mutable bool _written;

  static Symbol* _recordingContext_walkSnapshot_method;
  static Symbol* _recordingContext_walkSnapshot_signature;
  static Klass* _recordingContext_klass;

  const JfrContext* next() const { return _next; }

  bool should_write() const { return !_written; }
  void write(JfrChunkWriter& cw) const;
  void write(JfrCheckpointWriter& cpw) const;
  bool equals(const JfrContext& rhs) const;

  void set_id(traceid id) { _id = id; }
  void set_nr_of_entries(u4 nr_of_entries) { _nr_of_entries = nr_of_entries; }
  void set_hash(unsigned int hash) { _hash = hash; }

  bool record_safe(JavaThread* thread, int skip);

  JfrContext(traceid id, const JfrContext& context, const JfrContext* next);
  JfrContext(JfrContextEntry* entries, u4 max_entries);
  ~JfrContext();

 public:
  unsigned int hash() const { return _hash; }
  traceid id() const { return _id; }

  static bool initialize();
};

#endif // SHARE_JFR_RECORDER_CONTEXT_JFRCONTEXT_HPP
