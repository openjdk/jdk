/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import sun.net.www.MimeTable;
import java.util.Properties;

/*
 * @test
 * @summary Basic tests for MIME types for Windows operating system
 * @requires os.family == "windows"
 * @modules jdk.httpserver java.base/sun.net.www:+open
 * @run testng/othervm WindowsMimeTypesTest
 */
public class WindowsMimeTypesTest extends AbstractMimeTypesTest {

    protected Properties getActualOperatingSystemSpecificMimeTypes(Properties properties) throws Exception {
        properties.load(MimeTable.class.getResourceAsStream("content-types.properties"));
        return properties;
    }

    protected Properties getExpectedOperatingSystemSpecificMimeTypes(Properties properties) throws Exception {
        Properties content = load("expected-windows-content-types.properties");
        for (Object key : content.keySet()) {
            properties.put(key, content.get(key));
        }
        return properties;
    }

}

