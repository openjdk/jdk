/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.security;

import java.io.File;

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.artifacts.Artifact;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.artifacts.ArtifactResolverException;

public class OpensslArtifactFetcher {

    /**
     * Gets the openssl binary path of version 1.1.*
     *
     * Openssl selection flow:
        1. Check whether property test.openssl.path is set and it's the
           preferred version(1.1.*) of openssl, then return that path.
        2. Else look for already installed openssl (version 1.1.*) in system
           path /usr/bin/openssl or /usr/local/bin/openssl, then return that
           path.
        3. Else try to download openssl (version 1.1.*) from the artifactory
           and return that path, if download fails then return null.
     *
     * @return openssl binary path of version 1.1.*
     */
    public static String getOpenssl1dot1dotStar() {
        String version = "1.1.";
        String path = getOpensslFromSystemProp(version);
        if (path != null) {
            return path;
        } else {
            path = getDefaultSystemOpensslPath(version);
            if (path != null) {
                return path;
            } else if (Platform.is64bit()) {
                if (Platform.isLinux()) {
                    path = fetchOpenssl(LINUX_X64.class);
                } else if (Platform.isOSX()) {
                    path = fetchOpenssl(MACOSX_X64.class);
                } else if (Platform.isWindows()) {
                    path = fetchOpenssl(WINDOWS_X64.class);
                }
                if (verifyOpensslVersion(path, version)) {
                    return path;
                }
            }
        }
        return null;
    }

    private static String getOpensslFromSystemProp(String version) {
        String path = System.getProperty("test.openssl.path");
        System.out.println("System Property - test.openssl.path: " + path);
        if (!verifyOpensslVersion(path, version)) {
            path = null;
        }
        return path;
    }

    private static String getDefaultSystemOpensslPath(String version) {
        if (verifyOpensslVersion("/usr/bin/openssl", version)) {
            return "/usr/bin/openssl";
        } else if (verifyOpensslVersion("/usr/local/bin/openssl", version)) {
            return "/usr/local/bin/openssl";
        }
        return null;
    }

    private static boolean verifyOpensslVersion(String path, String version) {
        if (path != null) {
            try {
                ProcessTools.executeCommand(path, "version")
                        .shouldHaveExitValue(0)
                        .shouldContain(version);
                return true;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return false;
    }

    private static String fetchOpenssl(Class<?> clazz) {
        String path = null;
        try {
            path = ArtifactResolver.resolve(clazz).entrySet().stream()
                    .findAny().get().getValue() + File.separator + "openssl"
                    + File.separator + "bin" + File.separator + "openssl";
            System.out.println("path: " + path);
        } catch (ArtifactResolverException e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                System.out.println("Cannot resolve artifact, "
                        + "please check if JIB jar is present in classpath.");
            } else {
                throw new RuntimeException("Fetch artifact failed: " + clazz
                        + "\nPlease make sure the artifact is available.", e);
            }
        }
        return path;
    }

    @Artifact(
            organization = "jpg.tests.jdk.openssl",
            name = "openssl-linux_x64",
            revision = "1.1.1g",
            extension = "zip")
    private static class LINUX_X64 { }

    @Artifact(
            organization = "jpg.tests.jdk.openssl",
            name = "openssl-macosx_x64",
            revision = "1.1.1g",
            extension = "zip")
    private static class MACOSX_X64 { }

    @Artifact(
            organization = "jpg.tests.jdk.openssl",
            name = "openssl-windows_x64",
            revision = "1.1.1g",
            extension = "zip")
    private static class WINDOWS_X64 { }
}
