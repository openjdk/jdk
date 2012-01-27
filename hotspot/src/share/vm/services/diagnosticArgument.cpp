/*
 * Copyright (c) 2011, 2012 Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "runtime/thread.hpp"
#include "services/diagnosticArgument.hpp"

void GenDCmdArgument::read_value(const char* str, size_t len, TRAPS) {
  if (is_set()) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
            "Duplicates in diagnostic command arguments");
  }
  parse_value(str, len, CHECK);
  set_is_set(true);
}

template <> void DCmdArgument<jlong>::parse_value(const char* str,
                                                  size_t len, TRAPS) {
  if (sscanf(str, INT64_FORMAT, &_value) != 1) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
      "Integer parsing error in diagnostic command arguments");
  }
}

template <> void DCmdArgument<jlong>::init_value(TRAPS) {
  if (has_default()) {
    this->parse_value(_default_string, strlen(_default_string), THREAD);
    if (HAS_PENDING_EXCEPTION) {
      fatal("Default string must be parsable");
    }
  } else {
    set_value(0);
  }
}

template <> void DCmdArgument<jlong>::destroy_value() { }

template <> void DCmdArgument<bool>::parse_value(const char* str,
                                                 size_t len, TRAPS) {
  // len is the length of the current token starting at str
  if (len == 0) {
    set_value(true);
  } else {
    if (len == strlen("true") && strncasecmp(str, "true", len) == 0) {
       set_value(true);
    } else if (len == strlen("false") && strncasecmp(str, "false", len) == 0) {
       set_value(false);
    } else {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
        "Boolean parsing error in diagnostic command arguments");
    }
  }
}

template <> void DCmdArgument<bool>::init_value(TRAPS) {
  if (has_default()) {
    this->parse_value(_default_string, strlen(_default_string), THREAD);
    if (HAS_PENDING_EXCEPTION) {
      fatal("Default string must be parsable");
    }
  } else {
    set_value(false);
  }
}

template <> void DCmdArgument<bool>::destroy_value() { }

template <> void DCmdArgument<char*>::parse_value(const char* str,
                                                  size_t len, TRAPS) {
  _value = NEW_C_HEAP_ARRAY(char, len+1);
  strncpy(_value, str, len);
  _value[len] = 0;
}

template <> void DCmdArgument<char*>::init_value(TRAPS) {
  if (has_default()) {
    this->parse_value(_default_string, strlen(_default_string), THREAD);
    if (HAS_PENDING_EXCEPTION) {
      fatal("Default string must be parsable");
    }
  } else {
    set_value(NULL);
  }
}

template <> void DCmdArgument<char*>::destroy_value() {
  if (_value != NULL) {
    FREE_C_HEAP_ARRAY(char, _value);
    set_value(NULL);
  }
}
