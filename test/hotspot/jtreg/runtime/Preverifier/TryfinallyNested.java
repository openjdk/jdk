/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

// Test nested try-finally blocks. This should be compiled in Java 1.3 to ensure JSRs and RETs appear in the bytecode
class TryfinallyNested {
    public static void main(String[] args) {
        try {
          System.out.println("Try!");
          try {
             System.out.println("Nested Try!");  // hm need two things to jsr to the finally
          } catch (NullPointerException e) {
          } finally {
             System.out.println("Finally!");
          }
        } catch (RuntimeException ex) {
          System.out.println("Catch!");
        }
    }
}
