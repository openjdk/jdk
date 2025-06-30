/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package common.catalog;

import java.net.ProxySelector;

/*
 * @test
 * @bug 8306055 8359337
 * @summary verifies DOM's support of the JDK Catalog.
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.catalog.SAXTest 0 // verifies default setting catalog.resolve=allow
 * @run driver common.catalog.SAXTest 1 // verifies overriding with catalog.resolve=strict in a config file
 * @run driver common.catalog.SAXTest 2 // verifies overriding with system property
 * @run driver common.catalog.SAXTest 3 // verifies overriding with factory setting (catalog.resolve=strict)
 * @run driver common.catalog.SAXTest 4 // verifies external DTD resolution with the JDK Catalog while resolve=strict in config file
 * @run driver common.catalog.SAXTest 5 // verifies external DTD resolution with the JDK Catalog while resolve=strict in API setting
 * @run driver common.catalog.SAXTest 6 // verifies external DTD resolution with a custom Catalog while resolve=strict in config file
 * @run driver common.catalog.SAXTest 7 // verifies external DTD resolution with a custom Catalog while resolve=strict in API setting
 * @run driver common.catalog.SAXTest 8 // verifies external parameter are resolved with a custom Catalog though resolve=strict in API setting
 * @run driver common.catalog.SAXTest 9 // verifies XInclude are resolved with a custom Catalog though resolve=strict in API setting
 */
public class SAXTest extends CatalogTestBase {
    public static void main(String[] args) throws Exception {
        final ProxySelector previous = ProxySelector.getDefault();
        // disable proxy
        ProxySelector.setDefault(ProxySelector.of(null));
        try {
            new SAXTest().run(args[0]);
        } finally {
            // reset to the previous proxy selector
            ProxySelector.setDefault(previous);
        }
    }

    public void run(String index) throws Exception {
        paramMap(Processor.SAX, null, index);
        super.testSAX(filename, fsp, state, config, sysProp, apiProp, cc, expectError, error);
    }
}
