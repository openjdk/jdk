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

/**
 * @test @bug 8306632
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.access.TransformTest Stylesheet 0 // default RESOURCE_ACCESS and ACCESS_EXTERNAL_DTD allow access
 * @run driver common.access.TransformTest Stylesheet 1 // FSP with explicit RESOURCE_ACCESS and ACCESS_EXTERNAL_DTD allow
 * @run driver common.access.TransformTest Stylesheet 2 // FSP leaves RESOURCE_ACCESS unchanged, ACCESS_EXTERNAL_DTD denies
 * @run driver common.access.TransformTest Stylesheet 3 // RESOURCE_ACCESS denies access though ACCESS_EXTERNAL_DTD allows it
 * @run driver common.access.TransformTest Stylesheet 4 // ACCESS_EXTERNAL_DTD denies access though RESOURCE_ACCESS allows it
 * @run driver common.access.TransformTest Stylesheet 5 // system properties override FSP secure values
 * @run driver common.access.TransformTest Stylesheet 6 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.TransformTest Stylesheet 7 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.TransformTest Stylesheet 8 // built-in catalog resolve=strict denies unresolved reference
 * @run driver common.access.TransformTest Stylesheet 9 // custom catalog resolves before direct fetch
 * @run driver common.access.TransformTest Transform 0 // default RESOURCE_ACCESS and ACCESS_EXTERNAL_STYLESHEET allow access
 * @run driver common.access.TransformTest Transform 1 // FSP with explicit RESOURCE_ACCESS and ACCESS_EXTERNAL_STYLESHEET allow
 * @run driver common.access.TransformTest Transform 2 // FSP leaves RESOURCE_ACCESS unchanged, ACCESS_EXTERNAL_STYLESHEET denies
 * @run driver common.access.TransformTest Transform 3 // RESOURCE_ACCESS denies access though ACCESS_EXTERNAL_STYLESHEET allows it
 * @run driver common.access.TransformTest Transform 4 // ACCESS_EXTERNAL_STYLESHEET denies access though RESOURCE_ACCESS allows it
 * @run driver common.access.TransformTest Transform 5 // system properties override FSP secure values
 * @run driver common.access.TransformTest Transform 6 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.TransformTest Transform 7 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.TransformTest Transform 8 // built-in catalog resolve=strict denies unresolved reference
 * @run driver common.access.TransformTest Transform 9 // custom catalog resolves before direct fetch
 * @summary verifies Transform support for Resource Access, external access properties, and catalog precedence.
 */
public class TransformTest extends AccessTestBase {

    public static void main(String args[]) throws Exception {
        new TransformTest().run(args[0], args[1]);
    }

    public void run(String method, String index) throws Exception {
        paramMap(Processor.TRANSFORMER, method, index);
        switch (method) {
            case "Stylesheet":
                super.testStylesheet(filename, xsl, fsp, state, config, sysProp, apiProp, cc, expectError, error);
                break;
            case "Transform":
                super.testTransform(filename, xsl, fsp, state, config, sysProp, apiProp, cc, expectError, error);
                break;
        }
    }
}
