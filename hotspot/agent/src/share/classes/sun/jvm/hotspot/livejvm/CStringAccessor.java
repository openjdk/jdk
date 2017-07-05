/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.livejvm;

import java.io.UnsupportedEncodingException;
import sun.jvm.hotspot.debugger.*;

class CStringAccessor {
  private Address addr;
  private int bufLen;

  CStringAccessor(Address addr, int bufLen) {
    this.addr = addr;
    this.bufLen = bufLen;
  }

  String getValue() throws DebuggerException {
    int len = 0;
    while ((addr.getCIntegerAt(len, 1, true) != 0) && (len < bufLen)) {
      ++len;
    }
    byte[] res = new byte[len];
    for (int i = 0; i < len; i++) {
      res[i] = (byte) addr.getCIntegerAt(i, 1, true);
    }
    try {
      return new String(res, "US-ASCII");
    } catch (UnsupportedEncodingException e) {
      throw new DebuggerException("Unable to use US-ASCII encoding");
    }
  }

  void setValue(String value) throws DebuggerException {
    try {
      byte[] data = value.getBytes("US-ASCII");
      if (data.length >= bufLen) {
        throw new DebuggerException("String too long");
      }
      for (int i = 0; i < data.length; i++) {
        addr.setCIntegerAt(i, 1, data[i]);
      }
      addr.setCIntegerAt(data.length, 1, 0);
    } catch (UnsupportedEncodingException e) {
      throw new DebuggerException("Unable to use US-ASCII encoding");
    }
  }
}
