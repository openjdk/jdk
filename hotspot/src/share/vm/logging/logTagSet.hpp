/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_LOGGING_LOGTAGSET_HPP
#define SHARE_VM_LOGGING_LOGTAGSET_HPP

#include "logging/logDecorators.hpp"
#include "logging/logLevel.hpp"
#include "logging/logOutputList.hpp"
#include "logging/logTag.hpp"
#include "utilities/globalDefinitions.hpp"

// The tagset represents a combination of tags that occur in a log call somewhere.
// Tagsets are created automatically by the LogTagSetMappings and should never be
// instantiated directly somewhere else.
class LogTagSet VALUE_OBJ_CLASS_SPEC {
 private:
  static LogTagSet* _list;
  static size_t     _ntagsets;

  LogTagSet* const  _next;
  size_t            _ntags;
  LogTagType        _tag[LogTag::MaxTags];

  LogOutputList     _output_list;
  LogDecorators     _decorators;

  // Keep constructor private to prevent incorrect instantiations of this class.
  // Only LogTagSetMappings can create/contain instances of this class.
  // The constructor links all tagsets together in a global list of tagsets.
  // This list is used during configuration to be able to update all tagsets
  // and their configurations to reflect the new global log configuration.
  LogTagSet(LogTagType t0, LogTagType t1, LogTagType t2, LogTagType t3, LogTagType t4);

  template <LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4>
  friend class LogTagSetMapping;

 public:
  static LogTagSet* first() {
    return _list;
  }

  LogTagSet* next() {
    return _next;
  }

  size_t ntags() const {
    return _ntags;
  }

  bool contains(LogTagType tag) const {
    for (size_t i = 0; _tag[i] != LogTag::__NO_TAG; i++) {
      if (tag == _tag[i]) {
        return true;
      }
    }
    return false;
  }

  void set_output_level(LogOutput* output, LogLevelType level) {
    _output_list.set_output_level(output, level);
  }

  // Refresh the decorators for this tagset to contain the decorators for all
  // of its current outputs combined with the given decorators.
  void update_decorators(const LogDecorators& decorator);

  int label(char *buf, size_t len);
  bool has_output(const LogOutput* output);
  bool is_level(LogLevelType level);
  void log(LogLevelType level, const char* msg);
};

template <LogTagType T0, LogTagType T1 = LogTag::__NO_TAG, LogTagType T2 = LogTag::__NO_TAG,
          LogTagType T3 = LogTag::__NO_TAG, LogTagType T4 = LogTag::__NO_TAG>
class LogTagSetMapping : public AllStatic {
private:
  static LogTagSet _tagset;

public:
  static LogTagSet& tagset() {
    return _tagset;
  }
};

// Instantiate the static field _tagset for all tagsets that are used for logging somewhere.
// (This must be done here rather than the .cpp file because it's a template.)
// Each combination of tags used as template arguments to the Log class somewhere (via macro or not)
// will instantiate the LogTagSetMapping template, which in turn creates the static field for that
// tagset. This _tagset contains the configuration for those tags.
template <LogTagType T0, LogTagType T1, LogTagType T2, LogTagType T3, LogTagType T4>
LogTagSet LogTagSetMapping<T0, T1, T2, T3, T4>::_tagset(T0, T1, T2, T3, T4);

#endif // SHARE_VM_LOGGING_LOGTAGSET_HPP
