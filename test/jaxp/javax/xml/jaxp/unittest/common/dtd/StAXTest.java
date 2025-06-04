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
 * @run driver common.dtd.StAXTest 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.StAXTest 1 // verifies overriding with config file
 * @run driver common.dtd.StAXTest 2 // verifies overriding with system property
 * @run driver common.dtd.StAXTest 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.StAXTest 4 // verifies DTD=ignore
 * @run driver common.dtd.StAXTest 5 // verifies disallow-doctype-decl=false
 * @run driver common.dtd.StAXTest 6 // verifies disallow-doctype-decl=true
 * @run driver common.dtd.StAXTest 7 // verifies supportDTD=true
 * @run driver common.dtd.StAXTest 8 // verifies supportDTD=false
 * @summary verifies StAX's support of the property jdk.xml.dtd.support.
 */
public class StAXTest extends DTDTestBase {

    public static void main(String args[]) throws Exception {
        new StAXTest().run(args[0]);
    }

    public void run(String index) throws Exception {
        paramMap(TestBase.Processor.STAX, null, index);
        super.testStAX(filename, fsp, state, config, sysProp, apiProp, expectError, error);
    }
}
