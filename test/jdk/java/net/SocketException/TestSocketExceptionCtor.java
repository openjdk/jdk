/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282686
 * @summary Verify cause and message handling of SocketException
 */
import java.net.SocketException;
import java.util.Objects;

public class TestSocketExceptionCtor {
    public static void main(String... args) {
        String message = "message";
        Throwable cause = new RuntimeException();

        testException(new SocketException(cause), cause.toString(), cause);
        testException(new SocketException(message, cause), message, cause);
    }

    private static void testException(SocketException se,
                                      String expectedMessage,
                                      Throwable expectedCause) {
        var message = se.getMessage();
        if (!Objects.equals(message, expectedMessage)) {
            throw new RuntimeException("Unexpected message " + message);
        }

        var cause = se.getCause();
        if (cause != expectedCause) {
            throw new RuntimeException("Unexpected cause");
        }
    }
}
