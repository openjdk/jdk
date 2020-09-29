/*
 * Copyright (c) 2008, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.nio.ch;

import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Platform specific helper functions
 */
class UnixDomainHelper {
    static Charset getCharset() {
        return Charset.defaultCharset();
    }

    /**
     * Return the temp directory for storing automatically bound
     * server sockets.
     *
     * On UNIX we search the following directories in sequence:
     *
     * 1. ${jdk.nio.unixdomain.tmpdir} if set, Use that unconditionally
     * 2. /tmp
     * 3. /var/tmp
     * 4. ${java.io.tmpdir}
     *
     */
    static Path getTempDir() {
        PrivilegedAction<Path> action = () -> {
            try {
                String s = System.getProperty("jdk.nio.unixdomain.tmpdir");
                if (s != null) {
                    return Path.of(s);
                }
                Path p = Path.of("/tmp");
                if (Files.exists(p)) {
                    return p;
                }
                p = Path.of("/var/tmp");
                if (Files.exists(p)) {
                    return p;
                }
                return Path.of(System.getProperty("java.io.tmpdir"));
            } catch (InvalidPathException ipe) {
                return null;
            }
        };
        return AccessController.doPrivileged(action);
    }
}
