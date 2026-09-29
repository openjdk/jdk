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
package jaxp.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import jdk.test.lib.Platform;

public class JUnitTestUtil {
    public static final String CLS_DIR = System.getProperty("test.classes");
    public static final String SRC_DIR = System.getProperty("test.src");

    // as in the Processors table in java.xml module summary
    public enum Processor {
        DOM,
        SAX,
        XMLREADER,
        StAX,
        VALIDATION,
        TRANSFORM,
        XSLTC,
        DOMLS,
        XPATH
    };

    /**
     * Returns the System identifier (URI) of the source.
     * @param path the path to the source
     * @return the System identifier
     */
    public static String getSystemId(String path) {
        if (path == null) return null;
        String xmlSysId = "file://" + path;
        if (Platform.isWindows()) {
            path = path.replace('\\', '/');
            xmlSysId = "file:///" + path;
        }
        return xmlSysId;
    }

    /**
     * Copies a file.
     * @param src the path of the source file
     * @param target the path of the target file
     * @throws Exception if the process fails
     */
    public static void copyFile(String src, String target) throws Exception {
        try {
            Files.copy(Path.of(src), Path.of(target), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException x) {
            throw new Exception(x.getMessage());
        }
    }
}
