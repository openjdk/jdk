/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4249111
 * @summary 'new' of inner class should not be allowed unless outer is constructed
 *
 * @compile/fail NewBeforeOuterConstructed.java
 */

import java.io.*;

public class NewBeforeOuterConstructed extends PrintStream {
      private class NullOutputStream extends OutputStream {
              public NullOutputStream() {
                      super();
              }
              public void write(int b) { }
              public void write(byte b[]) { }
              public void write(byte b[], int off, int len) { }
              public void flush() { }
              public void close() { }
      }
       public NewBeforeOuterConstructed() {
                // The 'new' below is illegal, as the outer
                // constructor has not been called when the
                // implicit reference to 'this' is evaluated
                // during the new instance expression.
              super(new NullOutputStream());
      }
}
