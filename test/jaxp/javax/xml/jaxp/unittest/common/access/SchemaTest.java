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

import java.net.ProxySelector;

/**
 * @test @bug 8306632
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.access.SchemaTest SchemaTest1 0 // default RESOURCE_ACCESS and ACCESS_EXTERNAL_SCHEMA allow access
 * @run driver common.access.SchemaTest SchemaTest1 1 // FSP with explicit RESOURCE_ACCESS and ACCESS_EXTERNAL_SCHEMA allow
 * @run driver common.access.SchemaTest SchemaTest1 2 // FSP leaves RESOURCE_ACCESS unchanged, ACCESS_EXTERNAL_SCHEMA denies
 * @run driver common.access.SchemaTest SchemaTest1 3 // RESOURCE_ACCESS denies access though ACCESS_EXTERNAL_SCHEMA allows it
 * @run driver common.access.SchemaTest SchemaTest1 4 // ACCESS_EXTERNAL_SCHEMA denies access though RESOURCE_ACCESS allows it
 * @run driver common.access.SchemaTest SchemaTest1 5 // system properties override FSP secure values
 * @run driver common.access.SchemaTest SchemaTest1 6 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.SchemaTest SchemaTest1 7 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.SchemaTest SchemaTest1 8 // built-in catalog resolve=strict denies unresolved reference
 * @run driver common.access.SchemaTest SchemaTest1 9 // custom catalog resolves before direct fetch
 * @run driver common.access.SchemaTest SchemaTest2 0 // default RESOURCE_ACCESS and ACCESS_EXTERNAL_SCHEMA allow access
 * @run driver common.access.SchemaTest SchemaTest2 1 // FSP with explicit RESOURCE_ACCESS and ACCESS_EXTERNAL_SCHEMA allow
 * @run driver common.access.SchemaTest SchemaTest2 2 // FSP leaves RESOURCE_ACCESS unchanged, ACCESS_EXTERNAL_SCHEMA denies
 * @run driver common.access.SchemaTest SchemaTest2 3 // RESOURCE_ACCESS denies access though ACCESS_EXTERNAL_SCHEMA allows it
 * @run driver common.access.SchemaTest SchemaTest2 4 // ACCESS_EXTERNAL_SCHEMA denies access though RESOURCE_ACCESS allows it
 * @run driver common.access.SchemaTest SchemaTest2 5 // system properties override FSP secure values
 * @run driver common.access.SchemaTest SchemaTest2 6 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.SchemaTest SchemaTest2 7 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.SchemaTest SchemaTest2 8 // built-in catalog resolve=strict denies unresolved reference
 * @run driver common.access.SchemaTest SchemaTest2 9 // custom catalog resolves before direct fetch
 * @run driver common.access.SchemaTest Validation 0 // default RESOURCE_ACCESS and ACCESS_EXTERNAL_SCHEMA allow access
 * @run driver common.access.SchemaTest Validation 1 // FSP with explicit RESOURCE_ACCESS and ACCESS_EXTERNAL_SCHEMA allow
 * @run driver common.access.SchemaTest Validation 2 // FSP leaves RESOURCE_ACCESS unchanged, ACCESS_EXTERNAL_SCHEMA denies
 * @run driver common.access.SchemaTest Validation 3 // RESOURCE_ACCESS denies access though ACCESS_EXTERNAL_SCHEMA allows it
 * @run driver common.access.SchemaTest Validation 4 // ACCESS_EXTERNAL_SCHEMA denies access though RESOURCE_ACCESS allows it
 * @run driver common.access.SchemaTest Validation 5 // system properties override FSP secure values
 * @run driver common.access.SchemaTest Validation 6 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.SchemaTest Validation 7 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.SchemaTest Validation 8 // built-in catalog resolve=strict denies unresolved reference
 * @run driver common.access.SchemaTest Validation 9 // custom catalog resolves before direct fetch
 * @summary verifies Schema and Validation support for Resource Access, ACCESS_EXTERNAL_SCHEMA, and catalog precedence.
 */
public class SchemaTest extends AccessTestBase {

    public static void main(String args[]) throws Exception {
        new SchemaTest().run(args[0], args[1]);
    }

    public void run(String method, String index) throws Exception {
        paramMap(Processor.VALIDATOR, method, index);
        switch (method) {
            case "SchemaTest1":
                super.testSchema1(filename, xsd, fsp, state, config, sysProp, apiProp, cc, expectError, error);
                break;
            case "SchemaTest2":
                super.testSchema2(filename, xsd, fsp, state, config, sysProp, apiProp, cc, expectError, error);
                break;
            case "Validation":
                super.testValidation(filename, xsd, fsp, state, config, sysProp, apiProp, cc, expectError, error);
                break;
        }
    }
}
