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
package common.dtd;

import java.net.ProxySelector;

import common.util.TestBase;

/*
 * @test
 * @bug 8306632 8359337
 * @summary verifies SAX's support of the property jdk.xml.dtd.support.
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.dtd.SAXTest 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.SAXTest 1 // verifies overriding with config file
 * @run driver common.dtd.SAXTest 2 // verifies overriding with system property
 * @run driver common.dtd.SAXTest 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.SAXTest 4 // verifies DTD=ignore
 * @run driver common.dtd.SAXTest 5 // verifies disallow-doctype-decl=false
 * @run driver common.dtd.SAXTest 6 // verifies disallow-doctype-decl=true
 */
public class SAXTest extends DTDTestBase {
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
        paramMap(TestBase.Processor.SAX, null, index);
        super.testSAX(filename, fsp, state, config, sysProp, apiProp, expectError, error);
    }
}
