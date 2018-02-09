/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4221448
 * @summary Use explicit check for integer arithmetic exception on win32.
 */

public class ExplicitArithmeticCheck {
    public static void main(String argv[]) throws Exception {
        for (int i = 0; i < 64; i++) {
          boolean result = false;
          int n;
          try {
              n = 0 / 0;
          } catch (ArithmeticException e) {
              result = true;
          }
          if (result == false) {
            throw new Error("Failed to throw correct exception!");
          }
          result = false;
          try {
              n = 0 % 0;
          } catch (ArithmeticException e) {
              result = true;
          }
          if (result == false) {
            throw new Error("Failed to throw correct exception!");
          }
          try {
              n = 0x80000000 / -1;
          } catch (Throwable t) {
            throw new Error("Failed to throw correct exception!");
          }
          if (n != 0x80000000) {
            throw new Error("Incorrect integer arithmetic ! ");
          }
          try {
              n = 0x80000000 % -1;
          } catch (Throwable t) {
            throw new Error("Failed to throw correct exception!");
          }
          if (n != 0) {
            throw new Error("Incorrect integer arithmetic!");
          }
        }
    }
}
