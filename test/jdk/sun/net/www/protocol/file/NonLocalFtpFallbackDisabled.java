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

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.Assert.assertThrows;

/**
 * @test
 * @bug 8353440
 * @summary Verify that non-local file URLs are rejected by default
 * @run junit/othervm NonLocalFtpFallbackDisabled
 * @run junit/othervm -Djdk.net.file.ftpfallback=false NonLocalFtpFallbackDisabled
 * @run junit/othervm -Djdk.net.file.ftpfallback NonLocalFtpFallbackDisabled
 */
public class NonLocalFtpFallbackDisabled {

    // The file requested in this test
    private Path file = Path.of("ftp-file.txt");

    /**
     * Verifies that the long-standing and unspecified FTP fallback feature
     * where the file URL scheme handler attempts an FTP connection for non-local
     * files is disabled by default and that opening connections for such URLs
     * is rejected with a MalformedURLException.
     *
     * @throws MalformedURLException if an unexpected URL exception occurs
     * @throws URISyntaxException if an unexpected URI exception occurs
     */
    @Test
    public void verifyNonLocalFileURLRejected() throws MalformedURLException, URISyntaxException {
        // We can use a fake host name here, no actual FTP request will be made
        String hostname = "remotehost";

        URL local = file.toUri().toURL();

        URL nonLocal = new URI("file", hostname, local.getFile(), "").toURL();
        assertThrows(MalformedURLException.class, () -> {
            nonLocal.openConnection();
        });

        URL nonLocalEmptyPath = new URI("file", hostname, "", "").toURL();
        assertThrows(MalformedURLException.class, () -> {
            nonLocalEmptyPath.openConnection();
        });
    }
}
