/*
 * Copyright (c) 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4827312
 * @summary verify that argument validity check is not fooled by overflow
 */
public class BidiBug {
  public static void main(String[] args) {
    try {
        byte buff[] = new byte[3000];
        java.text.Bidi bidi = new java.text.Bidi(new char[20],10,buff,Integer.MAX_VALUE-3,4,1);
    }
    catch (IllegalArgumentException e) {
        System.out.println(e);
        return; // success
    }
    throw new RuntimeException("didn't throw error, though we didn't crash either");
  }
}
