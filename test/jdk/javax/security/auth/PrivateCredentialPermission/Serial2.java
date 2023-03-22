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
 * @bug 8291974
 * @summary     PrivateCredentialPermission should not use local variable to enable debugging
 *              implementation-dependent class
 */

import javax.security.auth.*;
import java.io.*;
import java.util.*;

public class Serial2 {

    /*
     * Base64 encoding of Serialized PrivateCredentialPermission object
     * before bug fix for JDK-8291974.
     */
    static String before = """
            rO0ABXNyAC9qYXZheC5zZWN1cml0eS5hdXRoLlByaXZhdGVDcmVkZW50aWFsUGVybW\
            lzc2lvbklV3Hd7UH9MAgADWgAHdGVzdGluZ0wAD2NyZWRlbnRpYWxDbGFzc3QAEkxq\
            YXZhL2xhbmcvU3RyaW5nO0wACnByaW5jaXBhbHN0AA9MamF2YS91dGlsL1NldDt4cg\
            AYamF2YS5zZWN1cml0eS5QZXJtaXNzaW9uscbhPyhXUX4CAAFMAARuYW1lcQB+AAF4\
            cHQAGWNyZWQxIHBjMSAicG4xIiBwYzIgInBuMiIAdAAFY3JlZDFw\
            """;

    public static void main(String[] args) {

        byte[] decoded = Base64.getDecoder().decode(before);

        try (
            // Decode Base64 string and turn it into an input stream.
            InputStream is = new ByteArrayInputStream(decoded);
            ObjectInputStream ois = new ObjectInputStream(is)
        ) {
            // Deserialize input stream and create a new object.
            PrivateCredentialPermission pcp2 =
                    (PrivateCredentialPermission)ois.readObject();
            PrivateCredentialPermission pcp =
                    new PrivateCredentialPermission
                    ("cred1 pc1 \"pn1\" pc2 \"pn2\"", "read");
            /*
             * Compare deserialized object with current object.
             * This should always succeed. What is important is
             * that we get here without a deserialization exception.
             */
            if (!pcp.equals(pcp2) || !pcp2.equals(pcp)) {
                throw new SecurityException("Serial2 test failed: " +
                                    "EQUALS TEST FAILED");
            }

            System.out.println("Serial2 test succeeded");
        } catch (Exception e) {
            throw new SecurityException("Serial test failed", e);
        }
    }
}
