/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "runtime/java.hpp"
#include "runtime/globals.hpp"
#include "services/mallocLimit.hpp"
#include "services/nmtCommon.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/parseInteger.hpp"
#include "utilities/ostream.hpp"

MallocLimitSet MallocLimitHandler::_limits;
bool MallocLimitHandler::_have_limit = false;

class ParserHelper {
  const char* const _s;
  const char* const _end;
  const char* _p;

public:
  ParserHelper(const char* s) : _s(s), _end(s + strlen(s)), _p(s) {}

  bool eof() const { return _p >= _end; }

  // Check if string at position matches a malloclimit_mode_t.
  // Advance position on match.
  bool match_mode_flag(malloclimit_mode_t* out) {
    if (!eof()) {
      if (strncasecmp(_p, "oom", 3) == 0) {
        *out = malloclimit_mode_t::trigger_oom;
        _p += 3;
        return true;
      } else if (strncasecmp(_p, "fatal", 5) == 0) {
        *out = malloclimit_mode_t::trigger_fatal;
        _p += 5;
        return true;
      }
    }
    return false;
  }

  // Check if string at position matches a category name.
  // Advances position on match.
  bool match_category(MEMFLAGS* out) {
    if (!eof()) {
      const char* end = strchr(_p, ':');
      if (end == nullptr) {
        end = _end;
      }
      stringStream ss;
      ss.print("%.*s", (int)(end - _p), _p);
      MEMFLAGS f = NMTUtil::string_to_flag(ss.base());
      if (f != mtNone) {
        *out = f;
        _p = end;
        return true;
      }
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

void MallocLimitSet::set_global_limit(size_t s, malloclimit_mode_t flag) {
  _glob.v = s; _glob.f = flag;
}

void MallocLimitSet::set_category_limit(MEMFLAGS f, size_t s, malloclimit_mode_t flag) {
  const int i = NMTUtil::flag_to_index(f);
  _cat[i].v = s; _cat[i].f = flag;
}

void MallocLimitSet::reset() {
  set_global_limit(0, malloclimit_mode_t::trigger_fatal);
  _glob.v = 0; _glob.f = malloclimit_mode_t::trigger_fatal;
  for (int i = 0; i < mt_number_of_types; i++) {
    set_category_limit(NMTUtil::index_to_flag(i), 0, malloclimit_mode_t::trigger_fatal);
  }
}

void MallocLimitSet::print_on(outputStream* st) const {
  static const char* flagnames[] = { "fatal", "oom" };
  if (_glob.v > 0) {
    st->print_cr("MallocLimit: total limit: " PROPERFMT " (%s)", PROPERFMTARGS(_glob.v),
              flagnames[(int)_glob.f]);
  } else {
    for (int i = 0; i < mt_number_of_types; i++) {
      if (_cat[i].v > 0) {
        st->print_cr("MallocLimit: category \"%s\" limit: " PROPERFMT " (%s)",
          NMTUtil::flag_to_enum_name(NMTUtil::index_to_flag(i)),
          PROPERFMTARGS(_cat[i].v), flagnames[(int)_cat[i].f]);
      }
    }
  }
}

bool MallocLimitSet::parse_malloclimit_option(const char* v, const char** err) {

#define BAIL_UNLESS(condition, errormessage) if (!(condition)) { *err = errormessage; return false; }

  // Global form:
  // MallocLimit=<size>[:flag]

  // Category-specific form:
  // MallocLimit=<category>:<size>[:flag][,<category>:<size>[:flag]...]

  reset();

  ParserHelper sst(v);

  BAIL_UNLESS(!sst.eof(), "Empty string");

  // Global form?
  if (sst.match_size(&_glob.v)) {
    // Match optional mode flag (e.g. 1g:oom)
    if (!sst.eof()) {
      BAIL_UNLESS(sst.match_char(':'), "Expected colon");
      BAIL_UNLESS(sst.match_mode_flag(&_glob.f), "Expected flag");
    }
  }
  // Category-specific form?
  else {
    while (!sst.eof()) {
      MEMFLAGS f;

      // Match category, followed by :
      BAIL_UNLESS(sst.match_category(&f), "Expected category name");
      BAIL_UNLESS(sst.match_char(':'), "Expected colon following category");

      malloclimit_t* const modified_limit = &_cat[NMTUtil::flag_to_index(f)];

      // Match size
      BAIL_UNLESS(sst.match_size(&modified_limit->v), "Expected size");

      // Match optional flag
      if (!sst.eof() && sst.match_char(':')) {
        BAIL_UNLESS(sst.match_mode_flag(&modified_limit->f), "Expected flag");
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
  if (options != nullptr && options[0] != '\0') {
    const char* err = nullptr;
    if (!_limits.parse_malloclimit_option(options, &err)) {
      vm_exit_during_initialization("Failed to parse MallocLimit", err);
    }
    _have_limit = true;
  }
}

void MallocLimitHandler::print_on(outputStream* st) {
  if (have_limit()) {
    _limits.print_on(st);
  } else {
    st->print_cr("MallocLimit: unset");
  }
}

