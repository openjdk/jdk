/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FilePermission;
import static jaxp.library.JAXPBaseTest.setPermissions;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;

/**
 * This is a base class that every test class that need to reading local XML
 * files must extend if it needs to be run with security mode.
 */
public class JAXPFileReadOnlyBaseTest extends JAXPBaseTest {
    /**
     * Source files/XML files directory.
     */
    private final String SRC_DIR = getSystemProperty("test.src");

    /**
     * Allowing access local file system for this group.
     */
    @BeforeGroups (groups = {"readLocalFiles"})
    public void setFilePermissions() {
        setPermissions(new FilePermission(SRC_DIR + "/-", "read"));
    }

    /**
     * Restore the system property.
     */
    @AfterGroups (groups = {"readLocalFiles"})
    public void restoreFilePermissions() {
        setPermissions();
    }
}
