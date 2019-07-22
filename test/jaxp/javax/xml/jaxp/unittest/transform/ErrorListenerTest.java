/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package transform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;

/*
 * @test
 * @bug 8157830
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm transform.ErrorListenerTest
 * @summary Verifies that ErrorListeners are handled properly
 */
public class ErrorListenerTest {

    static final private String INVALID_STYLESHEET = "xxx";
    static final private String SYSTEM_ID = "http://openjdk_java_net/xsl/dummy.xsl";

    PrintStream original;

    @BeforeClass
    public void setUpClass() throws Exception {
        // save the PrintStream
        original = System.err;
    }

    @AfterClass
    protected void tearDown() throws Exception {
        // set back to the original
        System.setErr(original);
    }

    /**
     * Verifies that when an ErrorListener is registered, parser errors are passed
     * onto the listener without other output.
     *
     * @throws Exception
     */
    @Test
    public void test() throws Exception {
        InputStream is = new ByteArrayInputStream(INVALID_STYLESHEET.getBytes());
        InputSource source = new InputSource(is);
        source.setSystemId(SYSTEM_ID);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        System.setErr(ps);

        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setErrorListener(new ErrListener());

        try {
            factory.newTransformer(new SAXSource(source));
        } catch (TransformerConfigurationException e) {
            // nothing
        }

        // all errors are handled by the ErrorListener, no other output
        Assert.assertEquals(baos.toString(), "");

    }

    class ErrListener implements ErrorListener {

        @Override
        public void error(TransformerException exception)
                throws TransformerException {
            System.out.println("Correctly handled error: " + exception.getMessage());
        }

        @Override
        public void fatalError(TransformerException exception)
                throws TransformerException {
            System.out.println("Correctly handled fatal: " + exception.getMessage());
        }

        @Override
        public void warning(TransformerException exception)
                throws TransformerException {
            System.out.println("Correctly handled warning: " + exception.getMessage());
        }
    }
}
