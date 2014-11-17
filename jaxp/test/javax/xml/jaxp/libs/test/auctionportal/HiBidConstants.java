/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package test.auctionportal;

import static jaxp.library.JAXPTestUtilities.FILE_SEP;
import static jaxp.library.JAXPTestUtilities.USER_DIR;

/**
 * This is the Base test class provide basic support for Auction portal test.
 */
public class HiBidConstants {
    /**
     * Current test directory.
     */
    public static final String CLASS_DIR
            = System.getProperty("test.classes", ".") + FILE_SEP;

    /**
     * Package name that separates by slash.
     */
    public static final String PACKAGE_NAME = FILE_SEP +
            HiBidConstants.class.getPackage().getName().replaceAll("[.]", FILE_SEP);


    /**
     * Java source directory.
     */
    public static final String SRC_DIR = System.getProperty("test.src", USER_DIR)
            .replaceAll("\\" + System.getProperty("file.separator"), "/")
                + PACKAGE_NAME + FILE_SEP;

    /**
     * Source XML file directory.
     */
    public static final String XML_DIR = SRC_DIR + "content" + FILE_SEP;

    /**
     * Golden output file directory.
     * We pre-define all expected output in golden output file.  Test verifies
     * whether the standard output is same as content of golden file.
     */
    public static final String GOLDEN_DIR = SRC_DIR + "golden" + FILE_SEP;

    /**
     * Name space for account operation.
     */
    public static final String PORTAL_ACCOUNT_NS = "http://www.auctionportal.org/Accounts";
}
