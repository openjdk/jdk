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

/// Fetches data for a repo.
///
/// By default, data is extracted from ZIP file on an artifact server.
///
/// Users can specify the "jdk.tests.repos.pattern" system property
/// to fetch the file in an alternative location. The value of this
/// system property represents the URL for the entry with "%o" mapping to
/// the last part of [organization name][Artifact#organization()], "%n"
/// to [name][Artifact#name()], "%r" to[version][Artifact#revision()],
/// and "%e" to the entry name. For example:
///
/// With the [CMS_ML_DSA] class inside this test, the pattern
/// `file:///Users/tester/repos/external/%o/%n/%e` will be resolved to
/// a local file like `/Users/tester/repos/external/lamps-wg/cms-ml-dsa/entry`;
/// and a pattern `https://raw.repos.com/%o/%n/%r/%e` will be resolved to
/// a URL like `https://raw.repos.com/lamps-wg/cms-ml-dsa/c8f0cf7/entry`.
///
public interface DataFetcher extends AutoCloseable {

    byte[] fetch(String entry) throws IOException;

    /// @param klass the `Artifact` class
    /// @param zipPrefix the common prefix for each entry in the ZIP file
    static DataFetcher of(Class<?> klass, String zipPrefix) {
        Artifact artifact = klass.getAnnotation(Artifact.class);
        var org = artifact.organization();
        var prop = System.getProperty("jdk.tests.repos.pattern");
        if (prop != null && org.startsWith("jpg.tests.jdk.repos.")) {
            prop = prop.replace("%o", org.substring(org.lastIndexOf('.') + 1));
            prop = prop.replace("%n", artifact.name());
            prop = prop.replace("%r", artifact.revision());
            System.out.println("Creating FileFetcher on " + prop);
            return new FileFetcher(prop);
        } else {
            try {
                Path p = ArtifactResolver.resolve(klass).entrySet().stream()
                        .findAny().get().getValue();
                System.out.println("Creating ZipFetcher on " + p);
                return new ZipFetcher(new ZipFile(p.toFile()), zipPrefix);
            } catch (ArtifactResolverException | IOException e) {
                throw new SkippedException("Cannot find " + artifact.name(), e);
            }
        }
    }

    /// Fetches data from a URL.
    /// @param base the base URL string, contains "%e" mapping to entry name
    record FileFetcher(String base) implements DataFetcher {
        @Override
        public void close() throws Exception {
            // nothing to do
        }

        @Override
        public byte[] fetch(String entry) throws IOException {
            System.out.println("Reading " + entry + "...");
            try (var is = new URI(base.replace("%e", entry)).toURL().openStream()) {
                return is.readAllBytes();
            } catch (URISyntaxException e) {
                throw new IOException("Cannot create URI", e);
            }
        }
    }

    /// Fetches data from a ZIP file.
    /// @param zf the `ZipFile`
    /// @param zipPrefix optional prefix string inside the ZIP file. For
    ///     example, if an entry "sample/data" is "archive/sample/data"
    ///     inside the ZIP, "archive/" should be provided as `zipPrefix`.
    record ZipFetcher(ZipFile zf, String zipPrefix) implements DataFetcher {
        @Override
        public void close() throws Exception {
            zf.close();
        }

        @Override
        public byte[] fetch(String entry) throws IOException {
            System.out.println("Reading " + entry + "...");
            ZipEntry ze = zf.getEntry(zipPrefix + entry);
            if (ze != null) {
                return zf.getInputStream(ze).readAllBytes();
            } else {
                throw new RuntimeException("Entry not found: " + entry);
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
