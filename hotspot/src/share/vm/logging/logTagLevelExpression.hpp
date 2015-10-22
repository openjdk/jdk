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
#ifndef SHARE_VM_LOGGING_LOGTAGLEVELEXPRESSION_HPP
#define SHARE_VM_LOGGING_LOGTAGLEVELEXPRESSION_HPP

#include "logging/logLevel.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class LogTagSet;

// Class used to temporary encode a 'what'-expression during log configuration.
// Consists of a combination of tags and levels, e.g. "tag1+tag2=level1,tag3*=level2".
class LogTagLevelExpression : public StackObj {
 private:
  static const size_t MaxCombinations = 32;
  static const char* DefaultExpressionString;

  size_t        _ntags, _ncombinations;
  LogTagType    _tags[MaxCombinations][LogTag::MaxTags];
  LogLevelType  _level[MaxCombinations];
  bool          _allow_other_tags[MaxCombinations];
  char*         _string;

  void new_combination() {
    _ncombinations++;
    _ntags = 0;
  }

  void add_tag(LogTagType tag) {
    assert(_ntags < LogTag::MaxTags, "Can't have more tags than MaxTags!");
    _tags[_ncombinations][_ntags++] = tag;
  }

  void set_level(LogLevelType level) {
    _level[_ncombinations] = level;
  }

  void set_allow_other_tags() {
    _allow_other_tags[_ncombinations] = true;
  }

  void clear();

 public:
  LogTagLevelExpression() : _ntags(0), _ncombinations(0), _string(NULL) {
  }

  const char* to_string() const {
    return _string;
  }

  ~LogTagLevelExpression();
  bool parse(const char* str, outputStream* errstream = NULL);
  LogLevelType level_for(const LogTagSet& ts) const;
};

#endif // SHARE_VM_LOGGING_LOGTAGLEVELEXPRESSION_HPP
