/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package common.access;


import common.util.TestBase;

import java.net.ProxySelector;

/**
 * @test @bug 8357394
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.access.StAXTest 0 // by default, both RESOURCE_ACCESS and EAP allow access
 * @run driver common.access.StAXTest 3 // RESOURCE_ACCESS denies access though EAP allows it
 * @run driver common.access.StAXTest 4 // ACCESS_EXTERNAL_DTD denies access though RESOURCE_ACCESS allows it
 * @run driver common.access.StAXTest 5 // system properties override FSP secure values
 * @run driver common.access.StAXTest 6 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.StAXTest 7 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.StAXTest 8 // the built-in catalog's Resolve property is strict
 * @run driver common.access.StAXTest 9 // the built-in catalog resolves the reference before direct fetch
 * @run driver common.access.StAXTest 10 // the custom catalog resolves the reference before direct fetch
 * @summary Tests the interaction between Resource Access (jdk.xml.resource.access),
 * External Access Properties (EAPs), and the built-in catalog's Resolve setting,
 * ensuring correct precedence and behavior across different combinations of these
 * configuration mechanisms
 */
public class StAXTest extends AccessTestBase {
    public static void main(String[] args) throws Exception {
        new StAXTest().run(args[0]);
    }

    public void run(String index) throws Exception {
        paramMap(TestBase.Processor.STAX, null, index);
        super.testStAX(filename, fsp, state, config, sysProp, apiProp, cc, expectError, error);

    }
}
