/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.parsers.*;
import java.io.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;

/**
 * @test
 * @bug 8022548
 * @modules java.xml/com.sun.org.apache.xerces.internal.impl
 *          java.xml/com.sun.org.apache.xerces.internal.parsers
 *          java.xml/com.sun.org.apache.xerces.internal.util
 *          java.xml/com.sun.org.apache.xerces.internal.xni.parser
 * @summary tests that a parser can use DTDConfiguration; XOM is supported after jaxp 1.5.
 * @run testng XOMParserTest
 */
public class XOMParserTest {
    /**
     * Verifies that a parser can use DTDConfiguration.
     * @throws Exception if the test fails
     */
    @Test
    public final void testTransform() throws Exception {
        String filePath = System.getProperty("test.src");
        String inFilename = filePath + "/JDK8022548.xml";
        String xslFilename = filePath + "/JDK8022548.xsl";
        String outFilename = "JDK8022548.out";

        try (InputStream xslInput = new FileInputStream(xslFilename);
             InputStream xmlInput = new FileInputStream(inFilename);
             OutputStream out = new FileOutputStream(outFilename);
        ) {


            StringWriter sw = new StringWriter();
            // Create transformer factory
            TransformerFactory factory = TransformerFactory.newInstance();

            // Use the factory to create a template containing the xsl file
            Templates template = factory.newTemplates(new StreamSource(xslInput));
            // Use the template to create a transformer
            Transformer xformer = template.newTransformer();
            // Prepare the input and output files
            Source source = new StreamSource(xmlInput);
            Result result = new StreamResult(outFilename);
            //Result result = new StreamResult(sw);
            // Apply the xsl file to the source file and write the result to the output file
            xformer.transform(source, result);

            /**
             * String out = sw.toString(); if (out.indexOf("<p>") < 0 ) {
             * fail(out); }
             */
            String canonicalizedFileName = outFilename + ".canonicalized";
            canonicalize(outFilename, canonicalizedFileName);
        }
    }

    public void canonicalize(String inName, String outName) throws Exception {
        try (//FileOutputStream outStream = new FileOutputStream(outName);
                FileInputStream inputStream = new FileInputStream(inName);) {
            JDK15XML1_0Parser parser = new JDK15XML1_0Parser();
            parser.parse(new InputSource(inputStream));
        }
    }

    class JDK15XML1_0Parser extends SAXParser {

        JDK15XML1_0Parser() throws org.xml.sax.SAXException {

            super(new DTDConfiguration());
            // workaround for Java 1.5 beta 2 bugs
            com.sun.org.apache.xerces.internal.util.SecurityManager manager =
                    new com.sun.org.apache.xerces.internal.util.SecurityManager();
            setProperty(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, manager);

        }
    }
}
