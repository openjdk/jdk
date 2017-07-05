/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 */

package jdk.test.lib;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * @deprecated This class is deprecated. Use the one from
 *             {@code <root>/test/lib/share/classes/jdk/test/lib/process}
 */
@Deprecated
public final class StreamPumper implements Runnable {

  private static final int BUF_SIZE = 256;

  private final OutputStream out;
  private final InputStream in;

  /**
   * Create a StreamPumper that reads from in and writes to out.
   *
   * @param in The stream to read from.
   * @param out The stream to write to.
   */
  public StreamPumper(InputStream in, OutputStream out) {
    this.in = in;
    this.out = out;
  }

  /**
   * Implements Thread.run(). Continuously read from <code>in</code> and write
   * to <code>out</code> until <code>in</code> has reached end of stream. Abort
   * on interruption. Abort on IOExceptions.
   */
  @Override
  public void run() {
    int length;
    InputStream localIn = in;
    OutputStream localOut = out;
    byte[] buffer = new byte[BUF_SIZE];

    try {
      while (!Thread.interrupted() && (length = localIn.read(buffer)) > 0) {
        localOut.write(buffer, 0, length);
      }
    } catch (IOException e) {
      // Just abort if something like this happens.
      e.printStackTrace();
    } finally {
      try {
        localOut.flush();
        in.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
