/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4052976
 * @summary Test URL.equals involving anchors
 *
 */

import java.net.*;

public class Equals {

    public static void main(String[] args) throws Exception {
        URL url1, url2;

        url1 = new URL(null, "http://JavaSoft/Test#bar");
        url2 = new URL(null, "http://JavaSoft/Test");

        if (url1.equals(url2))
            throw new RuntimeException("URL.equals fails with anchors");
        if (url2.equals(url1))
            throw new RuntimeException("URL.equals fails with anchors");
        if (url1.equals(null))
            throw new RuntimeException("URL.equals fails given null");
    }
}
