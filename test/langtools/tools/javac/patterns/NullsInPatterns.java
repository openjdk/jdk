/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8231827
 * @summary Testing pattern matching against the null constant
 * @compile --enable-preview -source ${jdk.version} NullsInPatterns.java
 * @run main/othervm --enable-preview NullsInPatterns
 */
import java.util.List;

public class NullsInPatterns {

    public static void main(String[] args) {
        if (null instanceof List t) {
            throw new AssertionError("broken");
        } else {
            System.out.println("null does not match List type pattern");
        }
        //reifiable types not allowed in type test patterns in instanceof:
//        if (null instanceof List<Integer> l) {
//            throw new AssertionError("broken");
//        } else {
//            System.out.println("null does not match List<Integer> type pattern");
//        }
        if (null instanceof List<?> l) {
            throw new AssertionError("broken");
        } else {
            System.out.println("null does not match List<?> type pattern");
        }
    }
}
