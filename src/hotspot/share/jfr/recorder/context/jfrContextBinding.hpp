/*
 * Copyright (c) 2021, Datadog, Inc. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CONTEXT_JFRCONTEXTBINDING_HPP
#define SHARE_JFR_RECORDER_CONTEXT_JFRCONTEXTBINDING_HPP

#include "jfr/recorder/context/jfrContext.hpp"
#include "utilities/hashtable.hpp"

class JfrContextBinding : public JfrCHeapObj {
  friend class JfrContextRepository;

 private:
  JfrContextBinding* _previous;
  jsize _entries_len;
  JfrContextEntry* _entries;

 public:
  JfrContextBinding(JfrContextBinding* previous,
    const char** entries /* of size entries_len * 2 */, jsize entries_len);
  ~JfrContextBinding();

  jlong id() { return (jlong)this; }
  static JfrContextBinding* find(jlong id) { return (JfrContextBinding*)id; }

  bool contains_key(const char* key);

  template<class ITER>
  void iterate(ITER* iter) {
    if (_previous != NULL) {
      _previous->iterate(iter);
    }
    for (int i = 0; i < _entries_len; i++) {
      if (!iter->do_entry(&_entries[i])) {
        return;
      }
    }
  }

  static JfrContextBinding* current(jboolean is_inheritable);
  static void set_current(JfrContextBinding* context, jboolean is_inheritable);
};

#endif // SHARE_JFR_RECORDER_CONTEXT_JFRCONTEXTBINDING_HPP
