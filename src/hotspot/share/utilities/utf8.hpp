/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_UTF8_HPP
#define SHARE_UTILITIES_UTF8_HPP

#include "jni.h"
#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"

/**

String handling within Java and the VM requires a bit of explanation.

Logically a java.lang.String is a sequence of 16-bit Unicode characters
encoded in UTF-16. In the past a String contained a Java char[] and so
could theoretically contain INT_MAX 16-bit characters. Then came JEP 254:
Compact Strings.

With Compact Strings the Java char[] becomes a Java byte[], and that byte[]
contains either latin-1 characters all of which fit in 8-bits, or else each
pair of bytes represents a UTF-16 character. Consequently the maximum length
in characters of a latin-1 string is INT_MAX, whilst for non-latin-1 it is INT_MAX/2.

In the code below if we have latin-1 content then we treat the String's data
array as a jbyte[], else a jchar[]. The lengths of these arrays are specified
as an int value, with a nominal maximum of INT_MAX.

The modified UTF-8 encoding specified for the VM, nominally encodes characters
in 1, 2, 3 or 6 bytes. The 6-byte representation is actually two 3-byte representations
for two UTF-16 characters forming a surrogate pair. If we are dealing with
a latin-1 string then each character will be encoded as either 1 or 2 bytes and so the
maximum UTF8 length is 2*INT_MAX. This can't be stored in an int so utf8 buffers must
use a size_t length. For non-latin-1 strings each UTF-16 character will encode as either
2 or 3 bytes, so the maximum UTF8 length in that case is 3 * INT_MAX/2 i.e. 1.5*INT_MAX.

The "quoted ascii" form of a unicode string is at worst 6 times longer than its
regular form, and so these lengths must always be size_t - though if we know we only
ever do this to symbols (or small symbol combinations) then we could use int.

There is an additional assumption/expectation that our UTF8 API's are never dealing with
invalid UTF8, and more generally that all UTF8 sequences could form valid Strings.
Consequently the Unicode length of a UTF8 sequence is assumed to always be representable
by an int. However, there are API's, such as JNI NewStringUTF, that do deal with such input
and could potentially have an unrepresentable string. The long standing position with JNI
is that the user must supply valid input so we do not try to account for these cases.

*/

// Low-level interface for UTF8 strings

class UTF8 : AllStatic {
 public:
  // returns the unicode length of a 0-terminated utf8 string
  static int unicode_length(const char* utf8_str) {
    bool is_latin1, has_multibyte;
    return unicode_length(utf8_str, is_latin1, has_multibyte);
  }
  static int unicode_length(const char* utf8_str, bool& is_latin1, bool& has_multibyte);

  // returns the unicode length of a non-0-terminated utf8 string
  static int unicode_length(const char* utf8_str, size_t len) {
    bool is_latin1, has_multibyte;
    return unicode_length(utf8_str, len, is_latin1, has_multibyte);
  }
  static int unicode_length(const char* utf8_str, size_t len, bool& is_latin1, bool& has_multibyte);

  // converts a utf8 string to a unicode string
  template<typename T> static void convert_to_unicode(const char* utf8_str, T* unicode_str, int unicode_length);

  // returns the quoted ascii length of a utf8 string
  static size_t quoted_ascii_length(const char* utf8_str, size_t utf8_length);

  // converts a utf8 string to quoted ascii
  static void as_quoted_ascii(const char* utf8_str, size_t utf8_length, char* buf, size_t buflen);

#ifndef PRODUCT
  // converts a quoted ascii string to utf8 string.  returns the original
  // string unchanged if nothing needs to be done.
  static const char* from_quoted_ascii(const char* quoted_ascii_string);
#endif

  // decodes the current utf8 character, stores the result in value,
  // and returns the end of the current utf8 character.
  template<typename T> static char* next(const char* str, T* value);

  // decodes the current utf8 character, gets the supplementary character instead of
  // the surrogate pair when seeing a supplementary character in string,
  // stores the result in value, and returns the end of the current utf8 character.
  static char* next_character(const char* str, jint* value);

  // Utility methods

  // Returns null if 'c' it not found. This only works as long
  // as 'c' is an ASCII character
  static const jbyte* strrchr(const jbyte* base, int length, jbyte c) {
    assert(length >= 0, "sanity check");
    assert(c >= 0, "does not work for non-ASCII characters");
    // Skip backwards in string until 'c' is found or end is reached
    while(--length >= 0 && base[length] != c);
    return (length < 0) ? nullptr : &base[length];
  }
  static bool   equal(const jbyte* base1, int length1, const jbyte* base2, int length2);
  static bool   is_supplementary_character(const unsigned char* str);
  static jint   get_supplementary_character(const unsigned char* str);

  static bool   is_legal_utf8(const unsigned char* buffer, size_t length,
                              bool version_leq_47);
  static void   truncate_to_legal_utf8(unsigned char* buffer, size_t length);
};


// Low-level interface for UNICODE strings

// A unicode string represents a string in the UTF-16 format in which supplementary
// characters are represented by surrogate pairs. Index values refer to char code
// units, so a supplementary character uses two positions in a unicode string.

class UNICODE : AllStatic {

  // returns the utf8 size of a unicode character
  // uses size_t for convenience in overflow checks
  static size_t utf8_size(jchar c);
  static size_t utf8_size(jbyte c);

 public:
  // checks if the given unicode character can be encoded as latin1
  static bool is_latin1(jchar c);

  // checks if the given string can be encoded as latin1
  static bool is_latin1(const jchar* base, int length);

  // returns the utf8 length of a unicode string
  template<typename T> static size_t utf8_length(const T* base, int length);

  // returns the utf8 length of a unicode string as an int - truncated if needed
  template<typename T> static int utf8_length_as_int(const T* base, int length);

  // converts a unicode string to utf8 string
  static void convert_to_utf8(const jchar* base, int length, char* utf8_buffer);

  // converts a unicode string to a utf8 string; result is allocated
  // in resource area unless a buffer is provided. The unicode 'length'
  // parameter is set to the length of the resulting utf8 string.
  template<typename T> static char* as_utf8(const T* base, size_t& length);
  static char* as_utf8(const jchar* base, int length, char* buf, size_t buflen);
  static char* as_utf8(const jbyte* base, int length, char* buf, size_t buflen);

  // returns the quoted ascii length of a unicode string
  template<typename T> static size_t quoted_ascii_length(const T* base, int length);

  // converts a unicode string to quoted ascii
  template<typename T> static void as_quoted_ascii(const T* base, int length, char* buf, size_t buflen);
};

#endif // SHARE_UTILITIES_UTF8_HPP
