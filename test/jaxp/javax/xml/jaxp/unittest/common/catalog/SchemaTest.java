/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test @bug 8306632
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.catalog.SchemaTest SchemaTest1 0 // verifies default setting dtd.support=allow
 * @run driver common.catalog.SchemaTest SchemaTest1 1 // verifies overriding with config file
 * @run driver common.catalog.SchemaTest SchemaTest1 2 // verifies overriding with system property
 * @run driver common.catalog.SchemaTest SchemaTest1 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.catalog.SchemaTest SchemaTest1 4 // verifies DTD=ignore
 * @run driver common.catalog.SchemaTest SchemaTest2 0 // verifies default setting dtd.support=allow
 * @run driver common.catalog.SchemaTest SchemaTest2 1 // verifies overriding with config file
 * @run driver common.catalog.SchemaTest SchemaTest2 2 // verifies overriding with system property
 * @run driver common.catalog.SchemaTest SchemaTest2 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.catalog.SchemaTest SchemaTest2 4 // verifies DTD=ignore
 * @run driver common.catalog.SchemaTest Validation 0 // verifies default setting dtd.support=allow
 * @run driver common.catalog.SchemaTest Validation 1 // verifies overriding with config file
 * @run driver common.catalog.SchemaTest Validation 2 // verifies overriding with system property
 * @run driver common.catalog.SchemaTest Validation 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.catalog.SchemaTest Validation 4 // verifies DTD=ignore
 * @summary verifies Schema and Validation's support of the property jdk.xml.dtd.support.
 */
public class SchemaTest extends CatalogTestBase {

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
