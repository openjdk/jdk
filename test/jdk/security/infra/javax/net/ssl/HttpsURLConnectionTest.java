/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320362
 * @summary Verifies successful connection to external server with
 *          KEYCHAINSTORE-ROOT trust store
 * @library /test/lib
 * @requires os.family == "mac"
 * @run main/othervm/manual HttpsURLConnectionTest https://github.com KeychainStore-Root
 */
import java.io.*;
import java.net.*;
import javax.net.ssl.*;

public class HttpsURLConnectionTest {
    public static void main(String[] args) {
        System.setProperty( "javax.net.ssl.trustStoreType", args[1]);
        try {
            HttpsURLConnection httpsCon = (HttpsURLConnection) new URL(args[0]).openConnection();
            if(httpsCon.getResponseCode() != 200) {
                throw new RuntimeException("Test failed : bad http response code : "+ httpsCon.getResponseCode());
            }
        } catch(IOException ioe) {
            throw new RuntimeException("Test failed: " + ioe.getMessage());
        }
    }
}
