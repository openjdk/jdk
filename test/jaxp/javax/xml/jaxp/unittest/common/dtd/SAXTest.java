/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package common.dtd;

import common.util.TestBase;

/**
 * @test @bug 8306632
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.dtd.SAXTest 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.SAXTest 1 // verifies overriding with config file
 * @run driver common.dtd.SAXTest 2 // verifies overriding with system property
 * @run driver common.dtd.SAXTest 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.SAXTest 4 // verifies DTD=ignore
 * @run driver common.dtd.SAXTest 5 // verifies disallow-doctype-decl=false
 * @run driver common.dtd.SAXTest 6 // verifies disallow-doctype-decl=true
 * @summary verifies SAX's support of the property jdk.xml.dtd.support.
 */
public class SAXTest extends DTDTestBase {
    public static void main(String args[]) throws Exception {
        new SAXTest().run(args[0]);
    }

    public void run(String index) throws Exception {
        paramMap(TestBase.Processor.SAX, null, index);
        super.testSAX(filename, fsp, state, config, sysProp, apiProp, expectError, error);
    }
}
