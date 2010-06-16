/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

#include "Buffer.hpp"

#include <string.h>

Buffer::Buffer(int bufSize) {
  buf = new char[bufSize];
  sz = bufSize;
  fill = 0;
  drain = 0;
}

Buffer::~Buffer() {
  delete[] buf;
}

char*
Buffer::fillPos() {
  return buf + fill;
}

int
Buffer::remaining() {
  return sz - fill;
}

int
Buffer::size() {
  return sz;
}

bool
Buffer::incrFillPos(int amt) {
  if (fill + amt >= sz) {
    return false;
  }
  fill += amt;
  return true;
}

int
Buffer::readByte() {
  if (drain < fill) {
    return buf[drain++] & 0xFF;
  } else {
    return -1;
  }
}

int
Buffer::readBytes(char* data, int len) {
  int numRead = 0;
  while (numRead < len) {
    int c = readByte();
    if (c < 0) break;
    data[numRead++] = (char) c;
  }
  return numRead;
}

char*
Buffer::drainPos() {
  return buf + drain;
}

int
Buffer::drainRemaining() {
  return fill - drain;
}

bool
Buffer::incrDrainPos(int amt) {
  if (drainRemaining() < amt) {
    return false;
  }
  drain += amt;
  return true;
}

void
Buffer::compact() {
  // Copy down data
  memmove(buf, buf + drain, fill - drain);
  // Adjust positions
  fill -= drain;
  drain = 0;
}
