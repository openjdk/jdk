/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.xml.sax.ptests;

import java.io.File;
import java.nio.file.Path;

/**
 * This is the Base test class provide basic support for JAXP SAX functional
 * test. These are JAXP SAX functional related properties that every test suite
 * has their own TestBase class.
 */
public class SAXTestConst {
    private static final Path TEST_SRC = Path.of(System.getProperty("test.src")).toAbsolutePath();

    private static String forwardSlashDir(Path p) {
        // Convention in these tests is to include trailing '/' in directory strings.
        return p.toString().replace(File.separatorChar, '/') + '/';
    }

    /**
     * XML source file directory.
     */
    public static final String XML_DIR = forwardSlashDir(TEST_SRC.resolveSibling("xmlfiles"));

    /**
     * Golden validation files directory.
     */
    public static final String GOLDEN_DIR = XML_DIR + "out/";
}
