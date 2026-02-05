/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class RuntimeVersionReader {

    // jdk.tools.jlink.internal.plugins.ReleaseInfoPlugin uses java.util.Properties
    // to read/write "release" file and puts quotes around version string.
    // Implementation of this function is based on behavior of ReleaseInfoPlugin.
    public static Optional<Runtime.Version> readVersion(Path releaseFilePath) {
        try (Reader reader = Files.newBufferedReader(releaseFilePath)) {
            Properties props = new Properties();
            props.load(reader);
            String version = props.getProperty("JAVA_VERSION");
            if (version != null) {
                // "JAVA_VERSION" value is set to quoted string in "release"
                // file. getProperty() will include leading and trailing quote
                // when returning value. We should remove them, since they are
                // not part of version.
                version = version.replaceAll("^\"|\"$", "");
            }
            return Optional.of(Runtime.Version.parse(version));
        } catch (IllegalArgumentException | NullPointerException ex) {
            // In case of Runtime.Version.parse() fails return empty optional.
            // It will fail for "" or "foo" strings. ReleaseInfoPlugin class
            // uses Runtime.Version class to create version for "release" file, so
            // if Runtime.Version.parse() fails something is wrong and we should
            // just ignore such version from "release" file.
            return Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
