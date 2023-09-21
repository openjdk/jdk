/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package common.dtd;

/**
 * @test @bug 8306632
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.dtd.DOMTest 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.DOMTest 1 // verifies overriding with config file
 * @run driver common.dtd.DOMTest 2 // verifies overriding with system property
 * @run driver common.dtd.DOMTest 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.DOMTest 4 // verifies DTD=ignore
 * @run driver common.dtd.DOMTest 5 // verifies disallow-doctype-decl=false
 * @run driver common.dtd.DOMTest 6 // verifies disallow-doctype-decl=true
 * @summary verifies DOM's support of the property jdk.xml.dtd.support.
 */
public class DOMTest extends DTDTestBase {
    public static void main(String args[]) throws Exception {
        new DOMTest().run(args[0]);
    }

    public void run(String index) throws Exception {
        paramMap(Processor.DOM, null, index);
        super.testDOM(filename, fsp, state, config, sysProp, apiProp, expectError, error);
    }
}
