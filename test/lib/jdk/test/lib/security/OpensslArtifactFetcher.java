/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.artifacts.Artifact;
import jdk.test.lib.artifacts.ArtifactResolver;
import jtreg.SkippedException;

public class OpensslArtifactFetcher {

    private static final String OPENSSL_BUNDLE_VERSION = "3.5.4";
    private static final String OPENSSL_ORG = "jpg.tests.jdk.openssl";

    /**
     * Gets the openssl binary path of OPENSSL_BUNDLE_VERSION
     *
     * Openssl selection flow:
        1. Check whether property test.openssl.path is set and it's the
           current version of openssl, then return that path.
        2. Else look for already installed openssl in system
           path /usr/bin/openssl or /usr/local/bin/openssl, then return that
           path.
        3. Else try to download the current version of openssl from the artifactory
           and return that path, if download fails then return null.
     *
     * @return openssl binary path of the current version
     * @throws SkippedException if a valid version of OpenSSL cannot be found
     *         or if OpenSSL is not available on the target platform
     */
    public static String getOpensslPath() {
        String path = getOpensslFromSystemProp(OPENSSL_BUNDLE_VERSION);
        if (path != null) {
            System.out.println("Using OpenSSL from system property.");
            return path;
        }

        path = getDefaultSystemOpensslPath(OPENSSL_BUNDLE_VERSION);
        if (path != null) {
            System.out.println("Using OpenSSL from system.");
            return path;
        }

        if (Platform.isX64()) {
            if (Platform.isLinux()) {
                return fetchOpenssl(LINUX_X64.class);
            } else if (Platform.isOSX()) {
                return fetchOpenssl(MACOSX_X64.class);
            } else if (Platform.isWindows()) {
                return fetchOpenssl(WINDOWS_X64.class);
            }
        } else if (Platform.isAArch64()) {
            if (Platform.isLinux()) {
                return fetchOpenssl(LINUX_AARCH64.class);
            }
            if (Platform.isOSX()) {
                return fetchOpenssl(MACOSX_AARCH64.class);
            }
        }

        throw new SkippedException(String.format("No OpenSSL %s found for %s/%s",
                OPENSSL_BUNDLE_VERSION, Platform.getOsName(), Platform.getOsArch()));
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
        if (verifyOpensslVersion("openssl", version)) {
            return "openssl";
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
        try {
            return ArtifactResolver.fetchOne(clazz)
                    .resolve("openssl", "bin", "openssl")
                    .toString();
        } catch (IOException exc) {
            throw new SkippedException("Could not find openssl", exc);
        }
    }

    // retrieve the provider directory path from <OPENSSL_HOME>/bin/openssl
    public static Path getProviderPath(String opensslPath) {
        Path openSslRootPath = Path.of(opensslPath).getParent().getParent();
        String libDir = "lib";
        if (Platform.isX64() && (Platform.isLinux() || Platform.isWindows())) {
            libDir = "lib64";
        }
        return openSslRootPath.resolve(libDir, "ossl-modules");
    }

    public static String getTestOpensslBundleVersion() {
        return OPENSSL_BUNDLE_VERSION;
    }

    @Artifact(
            organization = OPENSSL_ORG,
            name = "openssl-linux_x64",
            revision = OPENSSL_BUNDLE_VERSION,
            extension = "zip")
    private static class LINUX_X64 { }

    @Artifact(
            organization = OPENSSL_ORG,
            name = "openssl-linux_aarch64",
            revision = OPENSSL_BUNDLE_VERSION,
            extension = "zip")
    private static class LINUX_AARCH64{ }

    @Artifact(
            organization = OPENSSL_ORG,
            name = "openssl-macosx_x64",
            revision = OPENSSL_BUNDLE_VERSION,
            extension = "zip")
    private static class MACOSX_X64 { }

    @Artifact(
            organization = OPENSSL_ORG,
            name = "openssl-macosx_aarch64",
            revision = OPENSSL_BUNDLE_VERSION,
            extension = "zip")
    private static class MACOSX_AARCH64 { }

    @Artifact(
            organization = OPENSSL_ORG,
            name = "openssl-windows_x64",
            revision = OPENSSL_BUNDLE_VERSION,
            extension = "zip")
    private static class WINDOWS_X64 { }
}
