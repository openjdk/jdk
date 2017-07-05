/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.jmx.examples.scandir.config;

import junit.framework.*;
import java.io.File;

/**
 * Unit tests for {@code XmlConfigUtils}
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class XmlConfigUtilsTest extends TestCase {

    public XmlConfigUtilsTest(String testName) {
        super(testName);
    }

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(XmlConfigUtilsTest.class);

        return suite;
    }


    /**
     * Test of writeToFile method, of class XmlConfigUtils.
     */
    public void testWriteToFile() throws Exception {
        System.out.println("writeToFile");

        final File file = File.createTempFile("test",".xml");
        file.deleteOnExit();

        final String tmp = System.getProperty("java.io.tmpdir");

        DirectoryScannerConfig dir1 =
                new DirectoryScannerConfig("scan2");
        dir1.setRootDirectory(tmp);
        ScanManagerConfig bean = new ScanManagerConfig("session2");
        bean.putScan(dir1);
        XmlConfigUtils instance = new XmlConfigUtils(file.getPath());

        instance.writeToFile(bean);
    }

    /**
     * Test of readFromFile method, of class com.sun.jmx.examples.scandir.config.XmlConfigUtils.
     */
    public void testReadFromFile() throws Exception {
        System.out.println("readFromFile");

        final String tmp = System.getProperty("java.io.tmpdir");
        final File file = File.createTempFile("test",".xml");
        file.deleteOnExit();

        DirectoryScannerConfig dir1 =
                new DirectoryScannerConfig("scan1");
        dir1.setRootDirectory(tmp);
        ScanManagerConfig bean = new ScanManagerConfig("session1");
        bean.putScan(dir1);
        XmlConfigUtils instance = new XmlConfigUtils(file.getPath());

        instance.writeToFile(bean);

        ScanManagerConfig expResult = bean;
        ScanManagerConfig result = instance.readFromFile();
        System.out.println(result);
        assertEquals(expResult, result);


    }

}
