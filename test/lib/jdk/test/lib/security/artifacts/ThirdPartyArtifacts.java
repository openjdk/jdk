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

public class ThirdPartyArtifacts {

    public static final String ARTIFACT_BASE = "jpg.tests.jdk";
    public static final String ACVP_BUNDLE_NAME = "ACVP-Server";
    public static final String ACVP_BUNDLE_VERSION = "1.1.0.38";

    // Version of the NSS artifact. This coincides with the version of
    // the NSS version
    public static final String NSS_BUNDLE_VERSION = "3.111";
    public static final String NSSLIB =  ARTIFACT_BASE + ".nsslib";

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-windows_x64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class NSS_WINDOWS_X64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-macosx_x64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class NSS_MACOSX_X64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-macosx_aarch64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class NSS_MACOSX_AARCH64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-linux_x64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip")
    public static class NSS_LINUX_X64 {
    }

    @Artifact(
            organization = NSSLIB,
            name = "nsslib-linux_aarch64",
            revision = NSS_BUNDLE_VERSION,
            extension = "zip"
    )
    public static class NSS_LINUX_AARCH64 {
    }

    @Artifact(
            organization = ARTIFACT_BASE,
            name = ACVP_BUNDLE_NAME,
            revision = ACVP_BUNDLE_VERSION,
            extension = "zip",
            unpack = false)
    public static class ACVP_SERVER_TESTS {
    }
}
