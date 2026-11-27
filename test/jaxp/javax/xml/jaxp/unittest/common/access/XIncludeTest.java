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
 * @run driver common.access.XIncludeTest DOM 0 // default RESOURCE_ACCESS allows direct XInclude access
 * @run driver common.access.XIncludeTest DOM 1 // FSP with explicit RESOURCE_ACCESS allow
 * @run driver common.access.XIncludeTest DOM 2 // RESOURCE_ACCESS denies direct XInclude access
 * @run driver common.access.XIncludeTest DOM 3 // system properties override FSP
 * @run driver common.access.XIncludeTest DOM 4 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.XIncludeTest DOM 5 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.XIncludeTest DOM 6 // custom catalog resolves before direct fetch
 * @run driver common.access.XIncludeTest SAX 0 // default RESOURCE_ACCESS allows direct XInclude access
 * @run driver common.access.XIncludeTest SAX 1 // FSP with explicit RESOURCE_ACCESS allow
 * @run driver common.access.XIncludeTest SAX 2 // RESOURCE_ACCESS denies direct XInclude access
 * @run driver common.access.XIncludeTest SAX 3 // system properties override FSP
 * @run driver common.access.XIncludeTest SAX 4 // API RESOURCE_ACCESS allow overrides system RESOURCE_ACCESS deny
 * @run driver common.access.XIncludeTest SAX 5 // API RESOURCE_ACCESS deny overrides system RESOURCE_ACCESS allow
 * @run driver common.access.XIncludeTest SAX 6 // custom catalog resolves before direct fetch
 * @summary verifies XInclude support for Resource Access and catalog precedence.
 */
public class XIncludeTest extends AccessTestBase {

    public static void main(String args[]) throws Exception {
        new XIncludeTest().run(args[0], args[1]);
    }

    public void run(String processor, String index) throws Exception {
        Processor p = Processor.valueOf(processor);
        paramMapXInclude(p, index);
        switch (p) {
            case DOM:
                super.testDOM(filename, fsp, state, config, sysProp, apiProp, cc, expectError, error);
                break;
            case SAX:
                super.testSAX(filename, fsp, state, config, sysProp, apiProp, cc, expectError, error);
                break;
            default:
                throw new IllegalArgumentException("Unsupported processor: " + processor);
        }
    }
}
