/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

#include <ctype.h>
#include <string.h>
#include "ioUtils.hpp"
#include "IOBuf.hpp"

bool
scanInt(char** data, int* num) {
  *num = 0;

  // Skip whitespace
  while ((**data != 0) && (isspace(**data))) {
    ++*data;
  }

  if (**data == 0) {
    return false;
  }

  while ((**data != 0) && (!isspace(**data))) {
    char cur = **data;
    if ((cur < '0') || (cur > '9')) {
      return false;
    }
    *num *= 10;
    *num += cur - '0';
    ++*data;
  }

  return true;
}

bool
scanUnsignedLong(char** data, unsigned long* num) {
  *num = 0;

  // Skip whitespace
  while ((**data != 0) && (isspace(**data))) {
    ++*data;
  }

  if (**data == 0) {
    return false;
  }

  while ((**data != 0) && (!isspace(**data))) {
    char cur = **data;
    if ((cur < '0') || (cur > '9')) {
      return false;
    }
    *num *= 10;
    *num += cur - '0';
    ++*data;
  }

  return true;
}

bool
charToNibble(char ascii, int* value) {
  if (ascii >= '0' && ascii <= '9') {
    *value = ascii - '0';
    return true;
  } else if (ascii >= 'A' && ascii <= 'F') {
    *value = 10 + ascii - 'A';
    return true;
  } else if (ascii >= 'a' && ascii <= 'f') {
    *value = 10 + ascii - 'a';
    return true;
  }

  return false;
}

bool
scanAddress(char** data, unsigned long* addr) {
  *addr = 0;

  // Skip whitespace
  while ((**data != 0) && (isspace(**data))) {
    ++*data;
  }

  if (**data == 0) {
    return false;
  }

  if (strncmp(*data, "0x", 2) != 0) {
    return false;
  }

  *data += 2;

  while ((**data != 0) && (!isspace(**data))) {
    int val;
    bool res = charToNibble(**data, &val);
    if (!res) {
      return false;
    }
    *addr <<= 4;
    *addr |= val;
    ++*data;
  }

  return true;
}

bool
scanAndSkipBinEscapeChar(char** data) {
  // Skip whitespace
  while ((**data != 0) && (isspace(**data))) {
    ++*data;
  }

  if (!IOBuf::isBinEscapeChar(**data)) {
    return false;
  }

  ++*data;

  return true;
}

bool
scanBinUnsignedLong(char** data, unsigned long* num) {
  *num = 0;
  for (int i = 0; i < 4; i++) {
    unsigned char val = (unsigned char) **data;
    *num = (*num << 8) | val;
    ++*data;
  }
  return true;
}
