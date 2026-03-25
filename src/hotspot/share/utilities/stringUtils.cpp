/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm_io.h"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/stringUtils.hpp"

#include <ctype.h>
#include <string.h>

int StringUtils::replace_no_expand(char* string, const char* from, const char* to) {
  int replace_count = 0;
  size_t from_len = strlen(from);
  size_t to_len = strlen(to);
  assert(from_len >= to_len, "must not expand input");

  for (char* dst = string; *dst && (dst = strstr(dst, from)) != nullptr;) {
    char* left_over = dst + from_len;
    memmove(dst, to, to_len);                       // does not copy trailing 0 of <to>
    dst += to_len;                                  // skip over the replacement.
    memmove(dst, left_over, strlen(left_over) + 1); // copies the trailing 0 of <left_over>
    ++ replace_count;
  }

  return replace_count;
}

double StringUtils::similarity(const char* str1, size_t len1, const char* str2, size_t len2) {
  assert(str1 != nullptr && str2 != nullptr, "sanity");

  // filter out zero-length strings else we will underflow on len-1 below
  if (len1 == 0 || len2 == 0) {
    return 0.0;
  }

  size_t total = len1 + len2;
  size_t hit = 0;

  for (size_t i = 0; i < len1 - 1; i++) {
    for (size_t j = 0; j < len2 - 1; j++) {
      if ((str1[i] == str2[j]) && (str1[i+1] == str2[j+1])) {
        ++hit;
        break;
      }
    }
  }

  return 2.0 * (double) hit / (double) total;
}

class StringMatcher {
 public:
  typedef int getc_function_t(const char* &source, const char* limit);

 private:
  // These do not get properly inlined.
  // For full performance, this should be a template class
  // parameterized by two function arguments.
  getc_function_t* _pattern_getc;
  getc_function_t* _string_getc;

 public:
  StringMatcher(getc_function_t pattern_getc,
                getc_function_t string_getc)
    : _pattern_getc(pattern_getc),
      _string_getc(string_getc)
  { }

  enum {  // special results from _pattern_getc
    string_match_comma  = -0x100 + ',',
    string_match_star   = -0x100 + '*',
    string_match_eos    = -0x100 + '\0'
  };

 private:
  const char*
  skip_anchor_word(const char* match,
                   const char* match_end,
                   int anchor_length,
                   const char* pattern,
                   const char* pattern_end) {
    assert(pattern < pattern_end && anchor_length > 0, "");
    const char* begp = pattern;
    int ch1 = _pattern_getc(begp, pattern_end);
    // note that begp is now advanced over ch1
    assert(ch1 > 0, "regular char only");
    const char* matchp = match;
    const char* limitp = match_end - anchor_length;
    while (matchp <= limitp) {
      int mch = _string_getc(matchp, match_end);
      if (mch == ch1) {
        const char* patp = begp;
        const char* anchorp = matchp;
        while (patp < pattern_end) {
          char ch = _pattern_getc(patp, pattern_end);
          char mch = _string_getc(anchorp, match_end);
          if (mch != ch) {
            anchorp = nullptr;
            break;
          }
        }
        if (anchorp != nullptr) {
          return anchorp;  // Found a full copy of the anchor.
        }
        // That did not work, so restart the search for ch1.
      }
    }
    return nullptr;
  }

 public:
  bool string_match(const char* pattern,
                    const char* string) {
    return string_match(pattern, pattern + strlen(pattern),
                        string, string + strlen(string));
  }
  bool string_match(const char* pattern, const char* pattern_end,
                    const char* string, const char* string_end) {
    const char* patp = pattern;
    switch (_pattern_getc(patp, pattern_end)) {
    case string_match_eos:
      return false;  // Empty pattern is always false.
    case string_match_star:
      if (patp == pattern_end) {
        return true;   // Lone star pattern is always true.
      }
      break;
    }
    patp = pattern;  // Reset after lookahead.
    const char* matchp = string;  // nullptr if failing
    for (;;) {
      int ch = _pattern_getc(patp, pattern_end);
      switch (ch) {
      case string_match_eos:
      case string_match_comma:
        // End of a list item; see if it's a match.
        if (matchp == string_end) {
          return true;
        }
        if (ch == string_match_comma) {
          // Get ready to match the next item.
          matchp = string;
          continue;
        }
        return false;  // End of all items.

      case string_match_star:
        if (matchp != nullptr) {
          // Wildcard:  Parse out following anchor word and look for it.
          const char* begp = patp;
          const char* endp = patp;
          int anchor_len = 0;
          for (;;) {
            // get as many following regular characters as possible
            endp = patp;
            ch = _pattern_getc(patp, pattern_end);
            if (ch <= 0) {
              break;
            }
            anchor_len += 1;
          }
          // Anchor word [begp..endp) does not contain ch, so back up.
          // Now do an eager match to the anchor word, and commit to it.
          patp = endp;
          if (ch == string_match_eos ||
              ch == string_match_comma) {
            // Anchor word is at end of pattern, so treat it as a fixed pattern.
            const char* limitp = string_end - anchor_len;
            matchp = limitp;
            patp = begp;
            // Resume normal scanning at the only possible match position.
            continue;
          }
          // Find a floating occurrence of the anchor and continue matching.
          // Note:  This is greedy; there is no backtrack here.  Good enough.
          matchp = skip_anchor_word(matchp, string_end, anchor_len, begp, endp);
        }
        continue;
      }
      // Normal character.
      if (matchp != nullptr) {
        int mch = _string_getc(matchp, string_end);
        if (mch != ch) {
          matchp = nullptr;
        }
      }
    }
  }
};

// Match a wildcarded class list to a proposed class name (in internal form).
// Commas or newlines separate multiple possible matches; stars are shell-style wildcards.
class ClassListMatcher : public StringMatcher {
 public:
  ClassListMatcher()
    : StringMatcher(pattern_list_getc, class_name_getc)
  { }

 private:
  static int pattern_list_getc(const char* &pattern_ptr,
                               const char* pattern_end) {
    if (pattern_ptr == pattern_end) {
      return string_match_eos;
    }
    int ch = (unsigned char) *pattern_ptr++;
    switch (ch) {
    case ' ': case '\t': case '\n': case '\r':
    case ',':
      // End of list item.
      for (;;) {
        switch (*pattern_ptr) {
        case ' ': case '\t': case '\n': case '\r':
        case ',':
          pattern_ptr += 1;  // Collapse multiple commas or spaces.
          continue;
        }
        break;
      }
      return string_match_comma;

    case '*':
      // Wildcard, matching any number of chars.
      while (*pattern_ptr == '*') {
        pattern_ptr += 1;  // Collapse multiple stars.
      }
      return string_match_star;

    case '.':
      ch = '/';   // Look for internal form of package separator
      break;

    case '\\':
      // Superquote in pattern escapes * , whitespace, and itself.
      if (pattern_ptr < pattern_end) {
        ch = (unsigned char) *pattern_ptr++;
      }
      break;
    }

    assert(ch > 0, "regular char only");
    return ch;
  }

  static int class_name_getc(const char* &name_ptr,
                             const char* name_end) {
    if (name_ptr == name_end) {
      return string_match_eos;
    }
    int ch = (unsigned char) *name_ptr++;
    if (ch == '.') {
      ch = '/';   // Normalize to internal form of package separator
    }
    return ch;  // plain character
  }
};

bool StringUtils::class_list_match(const char* class_pattern_list,
                                   const char* class_name) {
  if (class_pattern_list == nullptr || class_name == nullptr || class_name[0] == '\0')
    return false;
  ClassListMatcher clm;
  return clm.string_match(class_pattern_list, class_name);
}


const char* StringUtils::strstr_nocase(const char* haystack, const char* needle) {
  if (needle[0] == '\0') {
    return haystack; // empty needle matches with anything
  }
  for (size_t i = 0; haystack[i] != '\0'; i++) {
    bool matches = true;
    for (size_t j = 0; needle[j] != '\0'; j++) {
      if (haystack[i + j] == '\0') {
        return nullptr; // hit end of haystack, abort
      }
      if (tolower(haystack[i + j]) != tolower(needle[j])) {
        matches = false;
        break; // abort, try next i
      }
    }
    if (matches) {
      return &haystack[i]; // all j were ok for this i
    }
  }
  return nullptr; // no i was a match
}

bool StringUtils::is_star_match(const char* star_pattern, const char* str) {
  const int N = 1000;
  char pattern[N]; // copy pattern into this to ensure null termination
  jio_snprintf(pattern, N, "%s", star_pattern);// ensures null termination
  char buf[N]; // copy parts of pattern into this
  const char* str_idx = str;
  const char* pattern_idx = pattern;
  while (strlen(pattern_idx) > 0) {
    // find next section in pattern
    const char* pattern_part_end = strstr(pattern_idx, "*");
    const char* pattern_part = pattern_idx;
    if (pattern_part_end != nullptr) { // copy part into buffer
      size_t pattern_part_len = pattern_part_end-pattern_part;
      strncpy(buf, pattern_part, pattern_part_len);
      buf[pattern_part_len] = '\0'; // end of string
      pattern_part = buf;
    }
    // find this section in s, case insensitive
    const char* str_match = strstr_nocase(str_idx, pattern_part);
    if (str_match == nullptr) {
      return false; // r_part did not match - abort
    }
    size_t match_len = strlen(pattern_part);
    // advance to match position plus part length
    str_idx = str_match + match_len;
    // advance by part length and "*"
    pattern_idx += match_len + (pattern_part_end == nullptr ? 0 : 1);
  }
  return true; // all parts of pattern matched
}

StringUtils::CommaSeparatedStringIterator::~CommaSeparatedStringIterator() {
  FREE_C_HEAP_ARRAY(char, _list);
}

ccstrlist StringUtils::CommaSeparatedStringIterator::canonicalize(ccstrlist option_value) {
  char* canonicalized_list = NEW_C_HEAP_ARRAY(char, strlen(option_value) + 1, mtCompiler);
  int i = 0;
  char current;
  while ((current = option_value[i]) != '\0') {
    if (current == '\n' || current == ' ') {
      canonicalized_list[i] = ',';
    } else {
      canonicalized_list[i] = current;
    }
    i++;
  }
  canonicalized_list[i] = '\0';
  return canonicalized_list;
}
