/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm_io.h"
#include "logging/log.hpp"
#include "logging/logSelection.hpp"
#include "logging/logTagSet.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/quickSort.hpp"

const LogSelection LogSelection::Invalid;

LogSelection::LogSelection() : _ntags(0), _tags(), _wildcard(false), _level(LogLevel::Invalid), _tag_sets_selected(0) {
}

LogSelection::LogSelection(const LogTagType tags[LogTag::MaxTags], bool wildcard, LogLevelType level)
  : _ntags(0), _tags(), _wildcard(wildcard), _level(level), _tag_sets_selected(0) {
  while (_ntags < LogTag::MaxTags && tags[_ntags] != LogTag::__NO_TAG) {
    _tags[_ntags] = tags[_ntags];
    _ntags++;
  }

  for (LogTagSet* ts = LogTagSet::first(); ts != nullptr; ts = ts->next()) {
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

bool LogSelection::superset_of(const LogSelection& other) const {
  bool match;
  for (size_t i = 0; i < other.ntags(); ++i) {
    match = false;
    for (size_t j = 0; j < _ntags; ++j) {
      if (other._tags[i] == _tags[j]) {
        match = true;
        break;
      }
    }

    if (!match) return false;
  }

  return true;
}

static LogSelection parse_internal(char *str, outputStream* errstream) {
  // Parse the level, if specified
  LogLevelType level = LogLevel::Unspecified;
  char* equals = strchr(str, '=');
  if (equals != nullptr) {
    const char* levelstr = equals + 1;
    level = LogLevel::from_string(levelstr);
    if (level == LogLevel::Invalid) {
      if (errstream != nullptr) {
        errstream->print("Invalid level '%s' in log selection.", levelstr);
        LogLevelType match = LogLevel::fuzzy_match(levelstr);
        if (match != LogLevel::Invalid) {
          errstream->print(" Did you mean '%s'?", LogLevel::name(match));
        }
        errstream->cr();
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
  if (asterisk_pos != nullptr && asterisk_pos[1] == '\0') {
    wildcard = true;
    *asterisk_pos = '\0';
  }

  // Parse the tag expression (t1+t2+...+tn)
  char* plus_pos;
  char* cur_tag = str;
  do {
    plus_pos = strchr(cur_tag, '+');
    if (plus_pos != nullptr) {
      *plus_pos = '\0';
    }
    LogTagType tag = LogTag::from_string(cur_tag);
    if (tag == LogTag::__NO_TAG) {
      if (errstream != nullptr) {
        errstream->print("Invalid tag '%s' in log selection.", cur_tag);
        LogTagType match =  LogTag::fuzzy_match(cur_tag);
        if (match != LogTag::__NO_TAG) {
          errstream->print(" Did you mean '%s'?", LogTag::name(match));
        }
        errstream->cr();
      }
      return LogSelection::Invalid;
    }
    if (ntags == LogTag::MaxTags) {
      if (errstream != nullptr) {
        errstream->print_cr("Too many tags in log selection '%s' (can only have up to " SIZE_FORMAT " tags).",
                               str, LogTag::MaxTags);
      }
      return LogSelection::Invalid;
    }
    tags[ntags++] = tag;
    cur_tag = plus_pos + 1;
  } while (plus_pos != nullptr);

  for (size_t i = 0; i < ntags; i++) {
    for (size_t j = 0; j < ntags; j++) {
      if (i != j && tags[i] == tags[j]) {
        if (errstream != nullptr) {
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

static bool contains(LogTagType tag, const LogTagType tags[LogTag::MaxTags], size_t ntags) {
  for (size_t i = 0; i < ntags; i++) {
    if (tags[i] == tag) {
      return true;
    }
  }
  return false;
}

bool LogSelection::consists_of(const LogTagType tags[LogTag::MaxTags]) const {
  size_t i;
  for (i = 0; tags[i] != LogTag::__NO_TAG; i++) {
    if (!contains(tags[i], _tags, _ntags)) {
      return false;
    }
  }
  return i == _ntags;
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

void LogSelection::describe_tags_on(outputStream* out) const {
  for (size_t i = 0; i < _ntags; i++) {
    out->print("%s%s", (i == 0 ? "" : "+"), LogTag::name(_tags[i]));
  }
  if (_wildcard) {
    out->print("*");
  }
}

void LogSelection::describe_on(outputStream* out) const {
  describe_tags_on(out);
  out->print("=%s", LogLevel::name(_level));
}

double LogSelection::similarity(const LogSelection& other) const {
  // Compute Soerensen-Dice coefficient as the similarity measure
  size_t intersecting = 0;
  for (size_t i = 0; i < _ntags; i++) {
    for (size_t j = 0; j < other._ntags; j++) {
      if (_tags[i] == other._tags[j]) {
        intersecting++;
        break;
      }
    }
  }
  return 2.0 * (double)intersecting / (double)(_ntags + other._ntags);
}

// Comparator used for sorting LogSelections based on their similarity to a specific LogSelection.
// A negative return value means that 'a' is more similar to 'ref' than 'b' is, while a positive
// return value means that 'b' is more similar.
// For the sake of giving short and effective suggestions, when two selections have an equal
// similarity score, the selection with the fewer tags (selecting the most tag sets) is considered
// more similar.
class SimilarityComparator {
  const LogSelection& _ref;
 public:
  SimilarityComparator(const LogSelection& ref) : _ref(ref) {
  }
  int operator()(const LogSelection& a, const LogSelection& b) const {
    const double epsilon = 1.0e-6;

    // Sort by similarity (descending)
    double s = _ref.similarity(b) - _ref.similarity(a);
    if (fabs(s) > epsilon) {
      return s < 0 ? -1 : 1;
    }

    // Then by number of tags (ascending)
    int t = static_cast<int>(a.ntags() - (int)b.ntags());
    if (t != 0) {
      return t;
    }

    // Lastly by tag sets selected (descending)
    return static_cast<int>(b.tag_sets_selected() - a.tag_sets_selected());
  }
};

static const size_t suggestion_cap = 5;
static const double similarity_requirement = 0.3;
void LogSelection::suggest_similar_matching(outputStream* out) const {
  LogSelection suggestions[suggestion_cap];
  uint nsuggestions = 0;

  // See if simply adding a wildcard would make the selection match
  if (!_wildcard) {
    LogSelection sel(_tags, true, _level);
    if (sel.tag_sets_selected() > 0) {
      suggestions[nsuggestions++] = sel;
    }
  }

  // Check for matching tag sets with a single tag mismatching (a tag too many or short a tag)
  for (LogTagSet* ts = LogTagSet::first(); ts != nullptr; ts = ts->next()) {
    LogTagType tags[LogTag::MaxTags] = { LogTag::__NO_TAG };
    for (size_t i = 0; i < ts->ntags(); i++) {
      tags[i] = ts->tag(i);
    }

    // Suggest wildcard selection unless the wildcard doesn't match anything extra
    LogSelection sel(tags, true, _level);
    if (sel.tag_sets_selected() == 1) {
      sel = LogSelection(tags, false, _level);
    }

    double score = similarity(sel);

    // Ignore suggestions with too low similarity
    if (score < similarity_requirement) {
      continue;
    }

    // Cap not reached, simply add the new suggestion and continue searching
    if (nsuggestions < suggestion_cap) {
      suggestions[nsuggestions++] = sel;
      continue;
    }

    // Find the least matching suggestion already found, and if the new suggestion is a better match, replace it
    double min = 1.0;
    size_t pos = -1;
    for (size_t i = 0; i < nsuggestions; i++) {
      double score = similarity(suggestions[i]);
      if (score < min) {
        min = score;
        pos = i;
      }
    }
    if (score > min) {
      suggestions[pos] = sel;
    }
  }

  if (nsuggestions == 0) {
    // Found no similar enough selections to suggest.
    return;
  }

  // Sort found suggestions to suggest the best one first
  SimilarityComparator sc(*this);
  QuickSort::sort(suggestions, nsuggestions, sc);

  out->print("Did you mean any of the following?");
  for (size_t i = 0; i < nsuggestions; i++) {
    out->print(" ");
    suggestions[i].describe_tags_on(out);
  }
}
