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

package jdk.test.lib.security.artifacts;

import jdk.test.lib.artifacts.Artifact;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.artifacts.ArtifactResolverException;

import java.io.IOException;
import java.nio.file.Path;

public class ThirdPartyArtifacts {

    /**
     * Retrieve an artifact/library/file from a repository or local file system.
     * <p>
     * Artifacts are defined with the {@link jdk.test.lib.artifacts.Artifact}
     * annotation. The file name should have the format ARTIFACT_NAME-VERSION.EXT
     * <p>
     * If you have a local version of a dependency that you want to use, you can
     * specify that by setting the System property:
     * <code>jdk.test.lib.artifacts.ARTIFACT_NAME</code>
     * <p>
     * Generally, tests that use this method should be run with <code>make test</code>.
     * However, tests can also be run with <code>jtreg</code> but you must have a
     * local copy of the artifact and the system property must be set as specified
     * above.
     *
     * @param clazz a class annotated with {@link jdk.test.lib.artifacts.Artifact}
     * @return the local path to the artifact. If the artifact is a compressed
     * file that gets unpacked, this path will point to the root
     * directory of the uncompressed file.
     * @throws IOException thrown if the artifact cannot be found
     */
    public static Path fetch(Class<?> clazz) throws IOException {
        try {
            var entry = ArtifactResolver
                    .resolve(clazz)
                    .entrySet()
                    .stream()
                    .findAny()
                    .orElseThrow();
            return entry.getValue();
        } catch (ArtifactResolverException exc) {
            Artifact artifact = clazz.getAnnotation(Artifact.class);
            String message;
            if (artifact != null) {
                message = "Cannot find the artifact " + artifact.name();
            } else {
                message = "Class " + clazz.getName() + " missing @Artifact annotation.";
            }
            throw new IOException(message, exc);
        }
    }

    public static final String ACVP_BUNDLE_LOC = "jpg.tests.jdk";
    public static final String ACVP_BUNDLE_NAME = "ACVP-Server";
    public static final String ACVP_BUNDLE_VERSION = "1.1.0.38";

    // Version of the NSS artifact. This coincides with the version of
    // the NSS version
    public static final String NSS_BUNDLE_VERSION = "3.107";
    public static final String NSSLIB = "jpg.tests.jdk.nsslib";

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-windows_x64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class WINDOWS_X64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-macosx_x64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class MACOSX_X64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-macosx_aarch64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class MACOSX_AARCH64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-linux_x64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class LINUX_X64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-linux_aarch64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip"
    )
    public static class LINUX_AARCH64 {
    }

    @Artifact(
            organization = ACVP_BUNDLE_LOC,
            name = ACVP_BUNDLE_NAME,
            revision = ACVP_BUNDLE_VERSION,
            extension = "zip",
            unpack = false)
    public static class ACVP_SERVER_TESTS {
    }
}