/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6337925
 * @summary Ensure that callers cannot modify the internal JarEntry cert and
 *          codesigner arrays.
 * @run junit GetMethodsReturnClones
 */
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GetMethodsReturnClones {

    private static final String BASE = System.getProperty("test.src", ".") +
        System.getProperty("file.separator");
    private static List<JarEntry> jarEntries;

    @BeforeAll()
    static void setupEntries() throws IOException {
        List<JarEntry> entries = new ArrayList<>();
        try (JarFile jf = new JarFile(BASE + "test.jar", true)) {
            byte[] buffer = new byte[8192];
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                entries.add(je);
                try (InputStream is = jf.getInputStream(je)) {
                    while (is.read(buffer, 0, buffer.length) != -1) {
                        // we just read. this will throw a SecurityException
                        // if  a signature/digest check fails.
                    }
                }
            }
        }
        jarEntries = entries;
    }

    @Test
    void certsTest() {
        for (JarEntry je : jarEntries) {
            Certificate[] certs = je.getCertificates();
            if (certs != null) {
                certs[0] = null;
                certs = je.getCertificates();
                assertNotNull(certs[0], "Modified internal certs array");
            }
        }
    }

    @Test
    void signersTest() {
        for (JarEntry je : jarEntries) {
            CodeSigner[] signers = je.getCodeSigners();
            if (signers != null) {
                signers[0] = null;
                signers = je.getCodeSigners();
                assertNotNull(signers[0], "Modified internal codesigners array");
            }
        }
    }
}
