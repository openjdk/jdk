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
#include "precompiled.hpp"
#include "logging/logTagLevelExpression.hpp"
#include "logging/logTagSet.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.inline.hpp"

const char* LogTagLevelExpression::DefaultExpressionString = "all";

static bool matches_tagset(const LogTagType tags[],
                           bool allow_other_tags,
                           const LogTagSet& ts) {
  bool contains_all = true;
  size_t tag_idx;
  for (tag_idx = 0; tag_idx < LogTag::MaxTags && tags[tag_idx] != LogTag::__NO_TAG; tag_idx++) {
    if (!ts.contains(tags[tag_idx])) {
      contains_all = false;
      break;
    }
  }
  // All tags in the expression must be part of the tagset,
  // and either the expression allows other tags (has a wildcard),
  // or the number of tags in the expression and tagset must match.
  return contains_all && (allow_other_tags || tag_idx == ts.ntags());
}

bool LogTagLevelExpression::verify_tagsets(outputStream* out) const {
  bool valid = true;

  for (size_t i = 0; i < _ncombinations; i++) {
    bool matched = false;
    for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
      if (matches_tagset(_tags[i], _allow_other_tags[i], *ts)) {
        matched = true;
        break;
      }
    }

    if (!matched) {
      // If this was the first invalid combination, write the message header
      if (valid && out != NULL) {
        out->print("No tag set matches selection(s): ");
      }
      valid = false;

      // Break as soon as possible unless listing all invalid combinations
      if (out == NULL) {
        break;
      }

      // List the combination on the outputStream
      for (size_t t = 0; t < LogTag::MaxTags && _tags[i][t] != LogTag::__NO_TAG; t++) {
        out->print("%s%s", (t == 0 ? "" : "+"), LogTag::name(_tags[i][t]));
      }
      if (_allow_other_tags[i]) {
        out->print("*");
      }
      out->print(" ");
    }
  }

  if (!valid && out != NULL) {
    out->cr();
  }

  return valid;
}

bool LogTagLevelExpression::parse(const char* str, outputStream* errstream) {
  bool success = true;
  if (str == NULL || strcmp(str, "") == 0) {
    str = DefaultExpressionString;
  }
  char* copy = os::strdup_check_oom(str, mtLogging);
  // Split string on commas
  for (char *comma_pos = copy, *cur = copy; success && comma_pos != NULL; cur = comma_pos + 1) {
    if (_ncombinations == MaxCombinations) {
      if (errstream != NULL) {
        errstream->print_cr("Can not have more than " SIZE_FORMAT " tag combinations in a what-expression.",
                            MaxCombinations);
      }
      success = false;
      break;
    }

    comma_pos = strchr(cur, ',');
    if (comma_pos != NULL) {
      *comma_pos = '\0';
    }

    // Parse the level, if specified
    LogLevelType level = LogLevel::Unspecified;
    char* equals = strchr(cur, '=');
    if (equals != NULL) {
      level = LogLevel::from_string(equals + 1);
      if (level == LogLevel::Invalid) {
        if (errstream != NULL) {
          errstream->print_cr("Invalid level '%s' in what-expression.", equals + 1);
        }
        success = false;
        break;
      }
      *equals = '\0'; // now ignore "=level" part of substr
    }
    set_level(level);

    // Parse special tags such as 'all'
    if (strcmp(cur, "all") == 0) {
      set_allow_other_tags();
      new_combination();
      continue;
    }

    // Check for '*' suffix
    char* asterisk_pos = strchr(cur, '*');
    if (asterisk_pos != NULL && asterisk_pos[1] == '\0') {
      set_allow_other_tags();
      *asterisk_pos = '\0';
    }

    // Parse the tag expression (t1+t2+...+tn)
    char* plus_pos;
    char* cur_tag = cur;
    do {
      plus_pos = strchr(cur_tag, '+');
      if (plus_pos != NULL) {
        *plus_pos = '\0';
      }
      LogTagType tag = LogTag::from_string(cur_tag);
      if (tag == LogTag::__NO_TAG) {
        if (errstream != NULL) {
          errstream->print_cr("Invalid tag '%s' in what-expression.", cur_tag);
        }
        success = false;
        break;
      }
      if (_ntags == LogTag::MaxTags) {
        if (errstream != NULL) {
          errstream->print_cr("Tag combination exceeds the maximum of " SIZE_FORMAT " tags.",
                              LogTag::MaxTags);
        }
        success = false;
        break;
      }
      if (!add_tag(tag)) {
        if (errstream != NULL) {
          errstream->print_cr("Tag combination have duplicate tag '%s' in what-expression.",
                              cur_tag);
        }
        success = false;
        break;
      }
      cur_tag = plus_pos + 1;
    } while (plus_pos != NULL);

    new_combination();
  }

  os::free(copy);
  return success;
}

LogLevelType LogTagLevelExpression::level_for(const LogTagSet& ts) const {
  // Return NotMentioned if the given tagset isn't covered by this expression.
  LogLevelType level = LogLevel::NotMentioned;
  for (size_t combination = 0; combination < _ncombinations; combination++) {
    if (matches_tagset(_tags[combination], _allow_other_tags[combination], ts)) {
      level = _level[combination];
    }
  }
  return level;
}

