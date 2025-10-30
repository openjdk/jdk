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
package jdk.test.lib.security;

import jdk.test.lib.artifacts.Artifact;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.artifacts.ArtifactResolverException;
import jtreg.SkippedException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DataFetcher {
    /// Fetches data.
    ///
    /// By default, this method fetches a ZIP from an artifact server,
    /// extracts an entry inside, and returns its content.
    ///
    /// Users can also specify the "jdk.tests.repos.pattern" system property
    /// to fetch the extracted file in an alternative location. The value of
    /// this system property is a [format][java.util.Formatter] string that
    /// takes the last part of [organization name][Artifact#organization()],
    /// the [name][Artifact#name()], the [version][Artifact#revision()],
    /// and `entry` as arguments. For example:
    ///
    /// With the [CMS_ML_DSA] class inside this test, the pattern
    /// `file:///Users/tester/repos/external/%s/%s/%4$s` will be resolved to
    /// a local file like `/Users/tester/repos/external/lamps-wg/cms-ml-dsa/entry`;
    /// and a pattern `https://raw.repos.com/%s/%s/%s/%s` will be resolved to
    /// a URL like `https://raw.repos.com/lamps-wg/cms-ml-dsa/c8f0cf7/entry`.
    ///
    /// @param klass the `Artifact` class
    /// @param zipPrefix the common prefix for each entry in the ZIP file
    /// @param entry the entry name without `zipPrefix`
    public static byte[] fetchData(Class<?> klass, String zipPrefix, String entry)
            throws IOException {
        Artifact artifact = klass.getAnnotation(Artifact.class);
        var org = artifact.organization();
        var prop = System.getProperty("jdk.tests.repos.pattern");
        if (prop != null && org.startsWith("jpg.tests.jdk.repos.")) {
            var url = String.format(prop,
                    org.substring(org.lastIndexOf('.') + 1),
                    artifact.name(),
                    artifact.revision(),
                    entry);
            System.out.println("fetching " + url + "...");
            try (var is = new URI(url).toURL().openStream()) {
                return is.readAllBytes();
            } catch (URISyntaxException e) {
                throw new IOException("Invalid URL: " + url);
            }
        } else {
            try {
                Path p = ArtifactResolver.resolve(klass).entrySet().stream()
                        .findAny().get().getValue();
                System.out.println("fetching " + zipPrefix + entry + " in " + p + "...");
                try (ZipFile zf = new ZipFile(p.toFile())) {
                    ZipEntry ze = zf.getEntry(zipPrefix + entry);
                    if (ze != null) {
                        return zf.getInputStream(ze).readAllBytes();
                    } else {
                        throw new RuntimeException("Entry not found: " + entry);
                    }
                }
            } catch (ArtifactResolverException e) {
                throw new SkippedException("Cannot find the artifact " + artifact.name(), e);
            }
        }
    }

    @Artifact(
            organization = "jpg.tests.jdk.repos.lamps-wg",
            name = "dilithium-certificates",
            revision = "785a549",
            extension = "zip",
            unpack = false)
    public static class DILITHIUM_CERTIFICATES {
    }

    @Artifact(
            organization = "jpg.tests.jdk.repos.lamps-wg",
            name = "cms-ml-dsa",
            revision = "c8f0cf7",
            extension = "zip",
            unpack = false)
    public static class CMS_ML_DSA {
    }
}
