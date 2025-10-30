/*
 * Copyright (C) 2022, Tencent. All rights reserved.
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
 * @bug 8282723
 * @summary Add constructors taking a cause to JSSE exceptions
 */
import javax.net.ssl.SSLProtocolException;
import java.util.Objects;

public class CheckSSLProtocolException {
    private static String exceptionMessage = "message";
    private static Throwable exceptionCause = new RuntimeException();

    public static void main(String[] args) throws Exception {
        testException(
                new SSLProtocolException(exceptionMessage, exceptionCause));
    }

    private static void testException(Exception ex) {
        if (!Objects.equals(ex.getMessage(), exceptionMessage)) {
            throw new RuntimeException("Unexpected exception message");
        }

        if (ex.getCause() != exceptionCause) {
            throw new RuntimeException("Unexpected exception cause");
        }
    }
}
