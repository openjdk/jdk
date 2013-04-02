/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_UTF8_HPP
#define SHARE_VM_UTILITIES_UTF8_HPP

#include "memory/allocation.hpp"
#include "utilities/top.hpp"

// Low-level interface for UTF8 strings

class UTF8 : AllStatic {
 public:
  // returns the unicode length of a 0-terminated utf8 string
  static int unicode_length(const char* utf8_str);

  // returns the unicode length of a non-0-terminated utf8 string
  static int unicode_length(const char* utf8_str, int len);

  // converts a utf8 string to a unicode string
  static void convert_to_unicode(const char* utf8_str, jchar* unicode_buffer, int unicode_length);

  // returns the quoted ascii length of a utf8 string
  static int quoted_ascii_length(const char* utf8_str, int utf8_length);

  // converts a utf8 string to quoted ascii
  static void as_quoted_ascii(const char* utf8_str, int utf8_length, char* buf, int buflen);

  // converts a quoted ascii string to utf8 string.  returns the original
  // string unchanged if nothing needs to be done.
  static const char* from_quoted_ascii(const char* quoted_ascii_string);

  // decodes the current utf8 character, stores the result in value,
  // and returns the end of the current utf8 chararacter.
  static char* next(const char* str, jchar* value);

  // decodes the current utf8 character, gets the supplementary character instead of
  // the surrogate pair when seeing a supplementary character in string,
  // stores the result in value, and returns the end of the current utf8 chararacter.
  static char* next_character(const char* str, jint* value);

  // Utility methods
  static const jbyte* strrchr(const jbyte* base, int length, jbyte c);
  static bool   equal(const jbyte* base1, int length1, const jbyte* base2,int length2);
  static bool   is_supplementary_character(const unsigned char* str);
  static jint   get_supplementary_character(const unsigned char* str);
};


// Low-level interface for UNICODE strings

// A unicode string represents a string in the UTF-16 format in which supplementary
// characters are represented by surrogate pairs. Index values refer to char code
// units, so a supplementary character uses two positions in a unicode string.

class UNICODE : AllStatic {
 public:
  // returns the utf8 size of a unicode character
  static int utf8_size(jchar c);

  // returns the utf8 length of a unicode string
  static int utf8_length(jchar* base, int length);

  // converts a unicode string to utf8 string
  static void convert_to_utf8(const jchar* base, int length, char* utf8_buffer);

  // converts a unicode string to a utf8 string; result is allocated
  // in resource area unless a buffer is provided.
  static char* as_utf8(jchar* base, int length);
  static char* as_utf8(jchar* base, int length, char* buf, int buflen);

  // returns the quoted ascii length of a unicode string
  static int quoted_ascii_length(jchar* base, int length);

  // converts a utf8 string to quoted ascii
  static void as_quoted_ascii(const jchar* base, int length, char* buf, int buflen);
};

#endif // SHARE_VM_UTILITIES_UTF8_HPP
