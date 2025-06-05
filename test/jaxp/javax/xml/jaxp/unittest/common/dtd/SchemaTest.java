/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package common.dtd;

/**
 * @test @bug 8306632
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.dtd.SchemaTest SchemaTest1 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.SchemaTest SchemaTest1 1 // verifies overriding with config file
 * @run driver common.dtd.SchemaTest SchemaTest1 2 // verifies overriding with system property
 * @run driver common.dtd.SchemaTest SchemaTest1 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.SchemaTest SchemaTest1 4 // verifies DTD=ignore
 * @run driver common.dtd.SchemaTest SchemaTest2 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.SchemaTest SchemaTest2 1 // verifies overriding with config file
 * @run driver common.dtd.SchemaTest SchemaTest2 2 // verifies overriding with system property
 * @run driver common.dtd.SchemaTest SchemaTest2 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.SchemaTest SchemaTest2 4 // verifies DTD=ignore
 * @run driver common.dtd.SchemaTest Validation 0 // verifies default setting dtd.support=allow
 * @run driver common.dtd.SchemaTest Validation 1 // verifies overriding with config file
 * @run driver common.dtd.SchemaTest Validation 2 // verifies overriding with system property
 * @run driver common.dtd.SchemaTest Validation 3 // verifies overriding with factory setting (DTD=deny)
 * @run driver common.dtd.SchemaTest Validation 4 // verifies DTD=ignore
 * @summary verifies Schema and Validation's support of the property jdk.xml.dtd.support.
 */
public class SchemaTest extends DTDTestBase {

    public static void main(String args[]) throws Exception {
        new SchemaTest().run(args[0], args[1]);
    }

    public void run(String method, String index) throws Exception {
        paramMap(Processor.VALIDATOR, method, index);
        switch (method) {
            case "SchemaTest1":
                super.testSchema1(filename, xsd, fsp, state, config, sysProp, apiProp, expectError, error);
                break;
            case "SchemaTest2":
                super.testSchema2(filename, xsd, fsp, state, config, sysProp, apiProp, expectError, error);
                break;
            case "Validation":
                super.testValidation(filename, xsd, fsp, state, config, sysProp, apiProp, expectError, error);
                break;
        }
    }
}
