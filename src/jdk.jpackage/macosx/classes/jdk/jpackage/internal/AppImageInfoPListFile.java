/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.XmlUtils.initDocumentBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.util.PListReader;
import org.xml.sax.SAXException;

/**
 * Mandatory elements of Info.plist file of app image.
 */
record AppImageInfoPListFile(String bundleIdentifier, String bundleName, String copyright,
        DottedVersion shortVersion, DottedVersion bundleVersion, String category) {

    static final class InvalidPlistFileException extends Exception {
        InvalidPlistFileException(Throwable cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }

    static AppImageInfoPListFile loadFromInfoPList(Path infoPListFile)
            throws IOException, InvalidPlistFileException, SAXException {

        final var plistReader = new PListReader(initDocumentBuilder().parse(Files.newInputStream(infoPListFile)));

        try {
            return new AppImageInfoPListFile(
                    plistReader.queryValue("CFBundleIdentifier"),
                    plistReader.queryValue("CFBundleName"),
                    plistReader.queryValue("NSHumanReadableCopyright"),
                    DottedVersion.greedy(plistReader.queryValue("CFBundleShortVersionString")),
                    DottedVersion.greedy(plistReader.queryValue("CFBundleVersion")),
                    plistReader.queryValue("LSApplicationCategoryType"));
        } catch (Exception ex) {
            throw new InvalidPlistFileException(ex);
        }
    }
}
