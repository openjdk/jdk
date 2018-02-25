/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/ostream.hpp"
#include "logging/logSelection.hpp"
#include "logging/logTagSet.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

const LogSelection LogSelection::Invalid;

LogSelection::LogSelection() : _ntags(0), _wildcard(false), _level(LogLevel::Invalid), _tag_sets_selected(0) {
}

LogSelection::LogSelection(const LogTagType tags[LogTag::MaxTags], bool wildcard, LogLevelType level)
    : _ntags(0), _wildcard(wildcard), _level(level), _tag_sets_selected(0) {
  while (_ntags < LogTag::MaxTags && tags[_ntags] != LogTag::__NO_TAG) {
    _tags[_ntags] = tags[_ntags];
    _ntags++;
  }

  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    if (selects(*ts)) {
      _tag_sets_selected++;
    }
  }
}

bool LogSelection::operator==(const LogSelection& ref) const {
  if (_ntags != ref._ntags ||
      _wildcard != ref._wildcard ||
      _level != ref._level ||
      _tag_sets_selected != ref._tag_sets_selected) {
    return false;
  }
  for (size_t i = 0; i < _ntags; i++) {
    if (_tags[i] != ref._tags[i]) {
      return false;
    }
  }
  return true;
}

bool LogSelection::operator!=(const LogSelection& ref) const {
  return !operator==(ref);
}

static LogSelection parse_internal(char *str, outputStream* errstream) {
  // Parse the level, if specified
  LogLevelType level = LogLevel::Unspecified;
  char* equals = strchr(str, '=');
  if (equals != NULL) {
    level = LogLevel::from_string(equals + 1);
    if (level == LogLevel::Invalid) {
      if (errstream != NULL) {
        errstream->print_cr("Invalid level '%s' in log selection.", equals + 1);
      }
      return LogSelection::Invalid;
    }
    *equals = '\0';
  }

  size_t ntags = 0;
  LogTagType tags[LogTag::MaxTags] = { LogTag::__NO_TAG };

  // Parse special tags such as 'all'
  if (strcmp(str, "all") == 0) {
    return LogSelection(tags, true, level);
  }

  // Check for '*' suffix
  bool wildcard = false;
  char* asterisk_pos = strchr(str, '*');
  if (asterisk_pos != NULL && asterisk_pos[1] == '\0') {
    wildcard = true;
    *asterisk_pos = '\0';
  }

  // Parse the tag expression (t1+t2+...+tn)
  char* plus_pos;
  char* cur_tag = str;
  do {
    plus_pos = strchr(cur_tag, '+');
    if (plus_pos != NULL) {
      *plus_pos = '\0';
    }
    LogTagType tag = LogTag::from_string(cur_tag);
    if (tag == LogTag::__NO_TAG) {
      if (errstream != NULL) {
        errstream->print_cr("Invalid tag '%s' in log selection.", cur_tag);
      }
      return LogSelection::Invalid;
    }
    if (ntags == LogTag::MaxTags) {
      if (errstream != NULL) {
        errstream->print_cr("Too many tags in log selection '%s' (can only have up to " SIZE_FORMAT " tags).",
                               str, LogTag::MaxTags);
      }
      return LogSelection::Invalid;
    }
    tags[ntags++] = tag;
    cur_tag = plus_pos + 1;
  } while (plus_pos != NULL);

  for (size_t i = 0; i < ntags; i++) {
    for (size_t j = 0; j < ntags; j++) {
      if (i != j && tags[i] == tags[j]) {
        if (errstream != NULL) {
          errstream->print_cr("Log selection contains duplicates of tag %s.", LogTag::name(tags[i]));
        }
        return LogSelection::Invalid;
      }
    }
  }

  return LogSelection(tags, wildcard, level);
}

LogSelection LogSelection::parse(const char* str, outputStream* error_stream) {
  char* copy = os::strdup_check_oom(str, mtLogging);
  LogSelection s = parse_internal(copy, error_stream);
  os::free(copy);
  return s;
}

bool LogSelection::selects(const LogTagSet& ts) const {
  if (!_wildcard && _ntags != ts.ntags()) {
    return false;
  }
  for (size_t i = 0; i < _ntags; i++) {
    if (!ts.contains(_tags[i])) {
      return false;
    }
  }
  return true;
}

size_t LogSelection::ntags() const {
  return _ntags;
}

LogLevelType LogSelection::level() const {
  return _level;
}

size_t LogSelection::tag_sets_selected() const {
  return _tag_sets_selected;
}

int LogSelection::describe_tags(char* buf, size_t bufsize) const {
  int tot_written = 0;
  for (size_t i = 0; i < _ntags; i++) {
    int written = jio_snprintf(buf + tot_written, bufsize - tot_written,
                               "%s%s", (i == 0 ? "" : "+"), LogTag::name(_tags[i]));
    if (written == -1) {
      return written;
    }
    tot_written += written;
  }

  if (_wildcard) {
    int written = jio_snprintf(buf + tot_written, bufsize - tot_written, "*");
    if (written == -1) {
      return written;
    }
    tot_written += written;
  }
  return tot_written;
}

int LogSelection::describe(char* buf, size_t bufsize) const {
  int tot_written = describe_tags(buf, bufsize);

  int written = jio_snprintf(buf + tot_written, bufsize - tot_written, "=%s", LogLevel::name(_level));
  if (written == -1) {
    return -1;
  }
  tot_written += written;
  return tot_written;
}
