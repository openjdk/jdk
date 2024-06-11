/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STORAGE_JFRREFERENCECOUNTEDSTORAGE_HPP
#define SHARE_JFR_RECORDER_STORAGE_JFRREFERENCECOUNTEDSTORAGE_HPP

#include "jfr/utilities/jfrBlob.hpp"
#include "memory/allocation.hpp"
#include "utilities/macros.hpp"

class JfrCheckpointWriter;

// RAII helper class for adding blobs to the storage.
class JfrAddRefCountedBlob : public StackObj {
 private:
  bool _reset;
 public:
  JfrAddRefCountedBlob(JfrCheckpointWriter& writer, bool move = true, bool reset = true);
  ~JfrAddRefCountedBlob();
};

// The debug aid 'scope' implies the proper RAII save construct is placed on stack.
// This is a necessary condition for installing reference counted storage to nodes.
class JfrReferenceCountedStorage : AllStatic {
  friend class JfrAddRefCountedBlob;
 private:
  static JfrBlobHandle _type_sets; // linked-list of blob handles saved during epoch.
  DEBUG_ONLY(static bool _scope;)

  static void save_blob(JfrCheckpointWriter& writer, bool move = false);
  static void reset();
  DEBUG_ONLY(static void set_scope();)

 public:
  template <typename T>
  static void install(T* node, const T* end) {
    assert(_scope, "invariant");
    if (_type_sets.valid()) {
      while (node != end) {
        node->install_type_set(_type_sets);
        node = node->next();
      }
    }
  }
};

#endif // SHARE_JFR_RECORDER_STORAGE_JFRREFERENCECOUNTEDSTORAGE_HPP
