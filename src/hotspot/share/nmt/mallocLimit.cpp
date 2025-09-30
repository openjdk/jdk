/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/mallocLimit.hpp"
#include "nmt/memTag.hpp"
#include "nmt/memTagFactory.hpp"
#include "nmt/nmtCommon.hpp"
#include "runtime/globals.hpp"
#include "runtime/java.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/deferredStatic.hpp"
#include "utilities/parseInteger.hpp"
#include "utilities/ostream.hpp"
#include "utilities/parseInteger.hpp"

DeferredStatic<MallocLimitSet> MallocLimitHandler::_limits;
bool MallocLimitHandler::_have_limit = false;

static const char* const MODE_OOM = "oom";
static const char* const MODE_FATAL = "fatal";

static const char* mode_to_name(MallocLimitMode m) {
  switch (m) {
  case MallocLimitMode::trigger_fatal: return MODE_FATAL;
  case MallocLimitMode::trigger_oom: return MODE_OOM;
  default: ShouldNotReachHere();
  };
  return nullptr;
}

class ParserHelper {
  // Start, end of parsed string.
  const char* const _s;
  const char* const _end;
  // Current parse position.
  const char* _p;

public:
  ParserHelper(const char* s) : _s(s), _end(s + strlen(s)), _p(s) {}

  bool eof() const { return _p >= _end; }

  // Check if string at position matches a malloclimit_mode_t.
  // Advance position on match.
  bool match_mode(MallocLimitMode* out) {
    if (eof()) {
      return false;
    }
    if (strncasecmp(_p, MODE_OOM, strlen(MODE_OOM)) == 0) {
      *out = MallocLimitMode::trigger_oom;
      _p += 3;
      return true;
    } else if (strncasecmp(_p, MODE_FATAL, strlen(MODE_FATAL)) == 0) {
      *out = MallocLimitMode::trigger_fatal;
      _p += 5;
      return true;
    }
    return false;
  }

  // Check if string at position matches a MemTag name.
  // Advances position on match.
  bool match_mem_tag(MemTag* out) {
    if (eof()) {
      return false;
    }
    const char* end = strchr(_p, ':');
    if (end == nullptr) {
      end = _end;
    }
    stringStream ss;
    // Extract the name from the full string.
    ss.print("%.*s", (int)(end - _p), _p);
    // First, try for an exact name match.
    MemTag mem_tag = MemTagFactory::tag_maybe(ss.freeze());
    if (mem_tag != mtNone) {
      *out = mem_tag;
      _p = end;
      return true;
    }
    // Hotspot MemTags are prepended with 'mt', but MallocLimit allows
    // the user to skip them when specifying a name. It also allows matching with the human readable name.
    // Both of these cases forces a linear search.
    MemTag match = mtNone;
    bool matched = false;
    MemTagFactory::iterate_tags([&](MemTag mt) {
      const char* hn_name = MemTagFactory::human_readable_name_of(mt);
      if (strcmp(ss.freeze(), hn_name) == 0) {
        matched = true;
        match = mt;
        return false;
      }

      const char* name = MemTagFactory::name_of(mt);
      const char* position = strstr(name, "mt");
      if (position == nullptr || position == name) {
        // Must be found and be a prefix
        return true;
      }
      if (strcmp(name + 2, ss.freeze()) == 0) {
        matched = true;
        match = mt;
        return false;
      }
      return true;
    });
    if (matched) {
      *out = match;
      _p = end + 2;
      return true;
    }

    return false;
  }

  // Check if string at position matches a memory size (e.g. "100", "100g" etc).
  // Advances position on match.
  bool match_size(size_t* out) {
    if (!eof()) {
      char* remainder = nullptr;
      if (parse_integer<size_t>(_p, &remainder, out)) {
        assert(remainder > _p && remainder <= _end, "sanity");
        _p = remainder;
        return true;
      }
    }
    return false;
  }

  // Check if char at pos matches c; return true and advance pos if so.
  bool match_char(char c) {
    if (!eof() && (*_p) == c) {
      _p ++;
      return true;
    }
    return false;
  }
};

MallocLimitSet::MallocLimitSet() {
  reset();
}

void MallocLimitSet::set_global_limit(size_t s, MallocLimitMode mode) {
  _glob.sz = s; _glob.mode = mode;
}

void MallocLimitSet::set_mem_tag_limit(MemTag mem_tag, size_t s, MallocLimitMode mode) {
  const int i = NMTUtil::tag_to_index(mem_tag);
  malloclimit& tag_limit = _memtag.at_grow(i);
  tag_limit.sz = s;
  tag_limit.mode = mode;
}

void MallocLimitSet::reset() {
  set_global_limit(0, MallocLimitMode::trigger_fatal);
  _glob.sz = 0; _glob.mode = MallocLimitMode::trigger_fatal;
  for (int i = 0; i < MemTagFactory::number_of_tags(); i++) {
    set_mem_tag_limit(NMTUtil::index_to_tag(i), 0, MallocLimitMode::trigger_fatal);
  }
}

void MallocLimitSet::print_on(outputStream* st) {
  if (_glob.sz > 0) {
    st->print_cr("MallocLimit: total limit: " PROPERFMT " (%s)", PROPERFMTARGS(_glob.sz),
                 mode_to_name(_glob.mode));
  } else {
    int tag_count = MemTagFactory::number_of_tags();
    for (int i = 0; i < tag_count; i++) {
      if (_memtag.at_grow(i).sz > 0) {
        st->print_cr("MallocLimit: category \"%s\" limit: " PROPERFMT " (%s)",
                     MemTagFactory::name_of(NMTUtil::index_to_tag(i)),
                     PROPERFMTARGS(_memtag.at_grow(i).sz), mode_to_name(_memtag.at_grow(i).mode));
      }
    }
  }
}

bool MallocLimitSet::parse_malloclimit_option(const char* v, const char** err) {

#define BAIL_UNLESS(condition, errormessage) if (!(condition)) { *err = errormessage; return false; }

  // Global form:
  // MallocLimit=<size>[:mode]

  // MemTag-specific form:
  // MallocLimit=<mem-tag>:<size>[:mode][,<mem-tag>:<size>[:mode]...]

  reset();

  ParserHelper sst(v);

  BAIL_UNLESS(!sst.eof(), "Empty string");

  // Global form?
  if (sst.match_size(&_glob.sz)) {
    // Match optional mode  (e.g. 1g:oom)
    if (!sst.eof()) {
      BAIL_UNLESS(sst.match_char(':'), "Expected colon");
      BAIL_UNLESS(sst.match_mode(&_glob.mode), "Expected mode");
    }
  }
  // MemTag-specific form?
  else {
    while (!sst.eof()) {
      MemTag mem_tag;

      // Match MemTag, followed by :
      BAIL_UNLESS(sst.match_mem_tag(&mem_tag), "Expected category name");
      BAIL_UNLESS(sst.match_char(':'), "Expected colon following category");

      malloclimit* const modified_limit = &_memtag.at_grow(NMTUtil::tag_to_index(mem_tag));

      // Match size
      BAIL_UNLESS(sst.match_size(&modified_limit->sz), "Expected size");

      // Match optional mode
      if (!sst.eof() && sst.match_char(':')) {
        BAIL_UNLESS(sst.match_mode(&modified_limit->mode), "Expected mode");
      }

      // More to come?
      if (!sst.eof()) {
        BAIL_UNLESS(sst.match_char(','), "Expected comma");
      }
    }
  }
  return true;
}

void MallocLimitHandler::initialize(const char* options) {
  _have_limit = false;
  _limits.initialize();
  if (options != nullptr && options[0] != '\0') {
    const char* err = nullptr;
    if (!_limits->parse_malloclimit_option(options, &err)) {
      vm_exit_during_initialization("Failed to parse MallocLimit", err);
    }
    _have_limit = true;
  }
}

void MallocLimitHandler::print_on(outputStream* st) {
  if (have_limit()) {
    _limits->print_on(st);
  } else {
    st->print_cr("MallocLimit: unset");
  }
}
