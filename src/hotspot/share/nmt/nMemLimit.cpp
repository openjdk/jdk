/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Amazon.com Inc and/or its affiliates. All rights reserved.
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

#include "nmt/nMemLimit.hpp"
#include "nmt/memTag.hpp"
#include "nmt/nmtCommon.hpp"
#include "runtime/java.hpp"
#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/parseInteger.hpp"
#include "utilities/ostream.hpp"
#include "logging/log.hpp"

NMemLimitSet NMemLimitHandler::_malloc_limits;
NMemLimitSet NMemLimitHandler::_mmap_limits;
bool NMemLimitHandler::_have_limit_map[2] = {false, false};

static const char* const MODE_OOM = "oom";
static const char* const MODE_FATAL = "fatal";

static const char* mode_to_name(NMemLimitMode m) {
  switch (m) {
  case NMemLimitMode::trigger_fatal: return MODE_FATAL;
  case NMemLimitMode::trigger_oom: return MODE_OOM;
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
  bool match_mode_flag(NMemLimitMode* out) {
    if (eof()) {
      return false;
    }
    if (strncasecmp(_p, MODE_OOM, strlen(MODE_OOM)) == 0) {
      *out = NMemLimitMode::trigger_oom;
      _p += 3;
      return true;
    } else if (strncasecmp(_p, MODE_FATAL, strlen(MODE_FATAL)) == 0) {
      *out = NMemLimitMode::trigger_fatal;
      _p += 5;
      return true;
    }
    return false;
  }

  // Check if string at position matches a category name.
  // Advances position on match.
  bool match_category(MemTag* out) {
    if (eof()) {
      return false;
    }
    const char* end = strchr(_p, ':');
    if (end == nullptr) {
      end = _end;
    }
    stringStream ss;
    ss.print("%.*s", (int)(end - _p), _p);
    MemTag mem_tag = NMTUtil::string_to_mem_tag(ss.base());
    if (mem_tag != mtNone) {
      *out = mem_tag;
      _p = end;
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

NMemLimitSet::NMemLimitSet() {
  reset();
}

void NMemLimitSet::set_global_limit(size_t s, NMemLimitMode flag) {
  _glob.sz = s; _glob.mode = flag;
}

void NMemLimitSet::set_category_limit(MemTag mem_tag, size_t s, NMemLimitMode flag) {
  const int i = NMTUtil::tag_to_index(mem_tag);
  _cat[i].sz = s; _cat[i].mode = flag;
}

void NMemLimitSet::reset() {
  set_global_limit(0, NMemLimitMode::trigger_fatal);
  _glob.sz = 0; _glob.mode = NMemLimitMode::trigger_fatal;
  for (int i = 0; i < mt_number_of_tags; i++) {
    set_category_limit(NMTUtil::index_to_tag(i), 0, NMemLimitMode::trigger_fatal);
  }
}

void NMemLimitSet::print_on(outputStream* st, const char* type_str) const {
  static const char* flagnames[] = { MODE_FATAL, MODE_OOM };
  if (_glob.sz > 0) {
    st->print_cr("%sLimit: total limit: " PROPERFMT " (%s)", type_str, PROPERFMTARGS(_glob.sz),
                 mode_to_name(_glob.mode));
  } else {
    for (int i = 0; i < mt_number_of_tags; i++) {
      if (_cat[i].sz > 0) {
        st->print_cr("%sLimit: category \"%s\" limit: " PROPERFMT " (%s)",
                     type_str, NMTUtil::tag_to_enum_name(NMTUtil::index_to_tag(i)),
                     PROPERFMTARGS(_cat[i].sz), mode_to_name(_cat[i].mode));
      }
    }
  }
}

bool NMemLimitSet::parse_n_mem_limit_option(const char* v, const char** err) {

#define BAIL_UNLESS(condition, errormessage) if (!(condition)) { *err = errormessage; return false; }

  // Global form:
  // MallocLimit=<size>[:flag] or MmapLimit=<size>[:flag]

  // Category-specific form:
  // MallocLimit=<category>:<size>[:flag][,<category>:<size>[:flag]...]
  // or, MmapLimit=<category>:<size>[:flag][,<category>:<size>[:flag]...]

  reset();

  ParserHelper sst(v);

  BAIL_UNLESS(!sst.eof(), "Empty string");

  // Global form?
  if (sst.match_size(&_glob.sz)) {
    // Match optional mode flag (e.g. 1g:oom)
    if (!sst.eof()) {
      BAIL_UNLESS(sst.match_char(':'), "Expected colon");
      BAIL_UNLESS(sst.match_mode_flag(&_glob.mode), "Expected flag");
    }
  }
  // Category-specific form?
  else {
    while (!sst.eof()) {
      MemTag mem_tag;

      // Match category, followed by :
      BAIL_UNLESS(sst.match_category(&mem_tag), "Expected category name");
      BAIL_UNLESS(sst.match_char(':'), "Expected colon following category");

      nMemlimit* const modified_limit = &_cat[NMTUtil::tag_to_index(mem_tag)];

      // Match size
      BAIL_UNLESS(sst.match_size(&modified_limit->sz), "Expected size");

      // Match optional flag
      if (!sst.eof() && sst.match_char(':')) {
        BAIL_UNLESS(sst.match_mode_flag(&modified_limit->mode), "Expected flag");
      }

      // More to come?
      if (!sst.eof()) {
        BAIL_UNLESS(sst.match_char(','), "Expected comma");
      }
    }
  }
  return true;
}

void NMemLimitHandler::initialize(const char* options, NMemType type) {
  log_info(nmt)("in NMemLimitHandler initialize. type: %s", NMemLimitHandler::nmem_type_to_str(type));
  _have_limit_map[nmemtype_to_int(type)] = false;
  NMemLimitSet* limits = get_mem_limit_set(type);

  if (options != nullptr && options[0] != '\0') {
    const char* err = nullptr;
    if (!limits->parse_n_mem_limit_option(options, &err)) {
      vm_exit_during_initialization("Failed to parse MallocLimit", err);
    }
    _have_limit_map[nmemtype_to_int(type)] = true;
  }
}

void NMemLimitHandler::print_on(outputStream* st) {
  NMemLimitHandler::print_on_by_type(st, NMemType::Malloc);
  NMemLimitHandler::print_on_by_type(st, NMemType::Mmap);
}

void NMemLimitHandler::print_on_by_type(outputStream* st, NMemType type) {
  NMemLimitSet* limits = get_mem_limit_set(type);

  if (have_limit(type)) {
    limits->print_on(st, NMemLimitHandler::nmem_type_to_str(type));
  } else {
    st->print_cr("%sLimit: unset", NMemLimitHandler::nmem_type_to_str(type));
  }
}

int NMemLimitHandler::nmemtype_to_int(NMemType type) {
  if (NMemType::Malloc == type) {
    return 0;
  } else if (NMemType::Mmap == type) {
    return 1;
  } else {
    ShouldNotReachHere();
  }
}

const char* NMemLimitHandler::nmem_type_to_str(NMemType type) { // TODO: use snake name
  switch (type) {
    case NMemType::Malloc: return "Malloc";
    case NMemType::Mmap: return "Mmap";
    default: ShouldNotReachHere();
  }
  ShouldNotReachHere();
}

