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
 * @run driver common.dtd.TransformTest Stylesheet 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.TransformTest Stylesheet 1 // verifies overriding with config file
 * @run driver common.dtd.TransformTest Stylesheet 2 // verifies overriding with system property
 * @run driver common.dtd.TransformTest Stylesheet 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.TransformTest Stylesheet 4 // verifies DTD=ignore
 * @run driver common.dtd.TransformTest Transform 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.TransformTest Transform 1 // verifies overriding with config file
 * @run driver common.dtd.TransformTest Transform 2 // verifies overriding with system property
 * @run driver common.dtd.TransformTest Transform 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.TransformTest Transform 4 // verifies DTD=ignore
 * @summary verifies Transform's support of the property jdk.xml.dtd.support.
 */
public class TransformTest extends DTDTestBase {

    public static void main(String args[]) throws Exception {
        new TransformTest().run(args[0], args[1]);
    }

    public void run(String method, String index) throws Exception {
        paramMap(TestBase.Processor.TRANSFORMER, method, index);
        switch (method) {
            case "Stylesheet":
                super.testStylesheet(filename, xsl, fsp, state, config, sysProp, apiProp, expectError, error);
                break;
            case "Transform":
                super.testTransform(filename, xsl, fsp, state, config, sysProp, apiProp, expectError, error);
                break;
        }
    }
}
