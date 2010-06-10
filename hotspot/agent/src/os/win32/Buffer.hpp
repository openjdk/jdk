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

#ifndef _BUFFER_
#define _BUFFER_

// A Buffer is the backing store for the IOBuf abstraction and
// supports producer-consumer filling and draining.

class Buffer {
public:
  Buffer(int bufSize);
  ~Buffer();

  char* fillPos();   // Position of the place where buffer should be filled
  int   remaining(); // Number of bytes that can be placed starting at fillPos
  int   size();      // Size of the buffer
  // Move up fill position by amount (decreases remaining()); returns
  // false if not enough space
  bool  incrFillPos(int amt);

  // Read single byte (0..255); returns -1 if no data available.
  int   readByte();
  // Read multiple bytes, non-blocking (this buffer does not define a
  // fill mechanism), into provided buffer. Returns number of bytes read.
  int   readBytes(char* buf, int len);

  // Access to drain position. Be very careful using this.
  char* drainPos();
  int   drainRemaining();
  bool  incrDrainPos(int amt);

  // Compact buffer, removing already-consumed input. This must be
  // called periodically to yield the illusion of an infinite buffer.
  void  compact();

private:
  Buffer(const Buffer&);
  Buffer& operator=(const Buffer&);

  char* buf;
  int   sz;
  int   fill;
  int   drain;
};

#endif // #defined _BUFFER_
