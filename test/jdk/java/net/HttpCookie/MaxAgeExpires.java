/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351983
 * @summary HttpCookie Parser Incorrectly Handles Cookies with Expires Attribute
 */

import java.net.*;
import java.util.*;

public class MaxAgeExpires {

    static record Test(long maxAge, String expires, long expectedAge, boolean hasExpired) {
        public void run() throws RuntimeException {
            StringBuilder sb = new StringBuilder();
            sb.append("Set-Cookie: name=value");
            if (expires() != null)
                sb.append("; expires=" + expires());
            if (maxAge() != -1)
                sb.append("; max-age=" + Long.toString(maxAge));

            String s = sb.toString();
            System.out.println(s);
            var cookie = HttpCookie.parse(s).get(0);
 
            if (expectedAge != -1 && cookie.getMaxAge() != expectedAge()) {
                System.out.println("getMaxAge() returned " + cookie.getMaxAge());
                System.out.println("expectedAge() was " + expectedAge());
                throw new RuntimeException("Test failed: wrong age");
            }
            
            if (cookie.hasExpired() != hasExpired()) {
                System.out.println("cookie.hasExpired() returned " + cookie.hasExpired());
                System.out.println("hasExpired() was " + hasExpired());
                System.out.println("getMaxAge() returned " + cookie.getMaxAge());
                throw new RuntimeException("Test failed: wrong hasExpired");
            }
        }
    }

    static Test[] tests = new Test[] {
        new Test(-1, "Thu, 01 Jan 2024 00:00:00 GMT", 0, true),
        new Test(1000, "Thu, 01 Jan 2024 00:00:00 GMT", 1000, false),
        new Test(0, "Thu, 01 Jan 2024 00:00:00 GMT", 0, true),
        // Far in the future. Just check hasExpired() not the exact maxAge
        new Test(-1, "Thu, 01 Jan 3024 00:00:00 GMT", -1, false)
    };

    public static void main(String[] args) throws Exception {
        for (Test test : tests) {
            test.run();
        }
    }
}
