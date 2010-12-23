/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/oop.inline.hpp"
#include "oops/symbolOop.hpp"


// ------------------------------------------------------------------
// symbolOopDesc::equals
//
// Compares the symbol with a string of the given length.
bool symbolOopDesc::equals(const char* str, int len) const {
  int l = utf8_length();
  if (l != len) return false;
  while (l-- > 0) {
    if (str[l] != (char) byte_at(l))
      return false;
  }
  assert(l == -1, "we should be at the beginning");
  return true;
}


// ------------------------------------------------------------------
// symbolOopDesc::starts_with
//
// Tests if the symbol starts with the specified prefix of the given
// length.
bool symbolOopDesc::starts_with(const char* prefix, int len) const {
  if (len > utf8_length()) return false;
  while (len-- > 0) {
    if (prefix[len] != (char) byte_at(len))
      return false;
  }
  assert(len == -1, "we should be at the beginning");
  return true;
}


// ------------------------------------------------------------------
// symbolOopDesc::index_of
//
// Finds if the given string is a substring of this symbol's utf8 bytes.
// Return -1 on failure.  Otherwise return the first index where str occurs.
int symbolOopDesc::index_of_at(int i, const char* str, int len) const {
  assert(i >= 0 && i <= utf8_length(), "oob");
  if (len <= 0)  return 0;
  char first_char = str[0];
  address bytes = (address) ((symbolOopDesc*)this)->base();
  address limit = bytes + utf8_length() - len;  // inclusive limit
  address scan = bytes + i;
  if (scan > limit)
    return -1;
  for (;;) {
    scan = (address) memchr(scan, first_char, (limit + 1 - scan));
    if (scan == NULL)
      return -1;  // not found
    assert(scan >= bytes+i && scan <= limit, "scan oob");
    if (memcmp(scan, str, len) == 0)
      return (int)(scan - bytes);
  }
}


char* symbolOopDesc::as_C_string(char* buf, int size) const {
  if (size > 0) {
    int len = MIN2(size - 1, utf8_length());
    for (int i = 0; i < len; i++) {
      buf[i] = byte_at(i);
    }
    buf[len] = '\0';
  }
  return buf;
}

char* symbolOopDesc::as_C_string() const {
  int len = utf8_length();
  char* str = NEW_RESOURCE_ARRAY(char, len + 1);
  return as_C_string(str, len + 1);
}

char* symbolOopDesc::as_C_string_flexible_buffer(Thread* t,
                                                 char* buf, int size) const {
  char* str;
  int len = utf8_length();
  int buf_len = len + 1;
  if (size < buf_len) {
    str = NEW_RESOURCE_ARRAY(char, buf_len);
  } else {
    str = buf;
  }
  return as_C_string(str, buf_len);
}

void symbolOopDesc::print_symbol_on(outputStream* st) {
  st = st ? st : tty;
  int length = UTF8::unicode_length((const char*)bytes(), utf8_length());
  const char *ptr = (const char *)bytes();
  jchar value;
  for (int index = 0; index < length; index++) {
    ptr = UTF8::next(ptr, &value);
    if (value >= 32 && value < 127 || value == '\'' || value == '\\') {
      st->put(value);
    } else {
      st->print("\\u%04x", value);
    }
  }
}

jchar* symbolOopDesc::as_unicode(int& length) const {
  symbolOopDesc* this_ptr = (symbolOopDesc*)this;
  length = UTF8::unicode_length((char*)this_ptr->bytes(), utf8_length());
  jchar* result = NEW_RESOURCE_ARRAY(jchar, length);
  if (length > 0) {
    UTF8::convert_to_unicode((char*)this_ptr->bytes(), result, length);
  }
  return result;
}

const char* symbolOopDesc::as_klass_external_name(char* buf, int size) const {
  if (size > 0) {
    char* str    = as_C_string(buf, size);
    int   length = (int)strlen(str);
    // Turn all '/'s into '.'s (also for array klasses)
    for (int index = 0; index < length; index++) {
      if (str[index] == '/') {
        str[index] = '.';
      }
    }
    return str;
  } else {
    return buf;
  }
}

const char* symbolOopDesc::as_klass_external_name() const {
  char* str    = as_C_string();
  int   length = (int)strlen(str);
  // Turn all '/'s into '.'s (also for array klasses)
  for (int index = 0; index < length; index++) {
    if (str[index] == '/') {
      str[index] = '.';
    }
  }
  return str;
}
