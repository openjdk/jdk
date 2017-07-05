/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.xml.sax.ptests;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import static jaxp.library.JAXPTestUtilities.compareWithGold;
import static jaxp.library.JAXPTestUtilities.failCleanup;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import static org.xml.sax.ptests.SAXTestConst.CLASS_DIR;
import static org.xml.sax.ptests.SAXTestConst.GOLDEN_DIR;
import static org.xml.sax.ptests.SAXTestConst.XML_DIR;

/**
 * ErrorHandler unit test. Set a ErrorHandle to XMLReader. Capture fatal error
 * events in ErrorHandler.
 */
public class EHFatalTest {
    /**
     * Error Handler to capture all error events to output file. Verifies the
     * output file is same as golden file.
     */
    @Test
    public void testEHFatal() {
        String outputFile = CLASS_DIR + "EHFatal.out";
        String goldFile = GOLDEN_DIR + "EHFatalGF.out";
        String xmlFile = XML_DIR + "invalid.xml";

        try(MyErrorHandler eHandler = new MyErrorHandler(outputFile);
                FileInputStream instream = new FileInputStream(xmlFile)) {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setErrorHandler(eHandler);
            InputSource is = new InputSource(instream);
            xmlReader.parse(is);
        } catch (IOException | ParserConfigurationException ex) {
            failUnexpected(ex);
        } catch (SAXException ex) {
            System.out.println("This is expected:" + ex);
        }
        // Need close the output file before we compare it with golden file.
        try {
            assertTrue(compareWithGold(goldFile, outputFile));
        } catch (IOException ex) {
            failUnexpected(ex);
        } finally {
            try {
                Path outputPath = Paths.get(outputFile);
                if(Files.exists(outputPath))
                    Files.delete(outputPath);
            } catch (IOException ex) {
                failCleanup(ex, outputFile);
            }
        }
    }
}

/**
 * A fatal error event handler only capture fatal error event and write event to
 * output file.
 */
class MyErrorHandler extends XMLFilterImpl implements AutoCloseable {
    /**
     * FileWriter to write string to output file.
     */
    private final BufferedWriter bWriter;

    /**
     * Initiate FileWriter when construct a MyContentHandler.
     * @param outputFileName output file name.
     * @throws SAXException creation of FileWriter failed.
     */
    MyErrorHandler(String outputFileName) throws SAXException {
        super();
        try {
            bWriter = new BufferedWriter(new FileWriter(outputFileName));
        } catch (IOException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Write fatalError tag along with exception to the file when meet
     * unrecoverable error event.
     * @throws IOException error happen when writing file.
     */
    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        String str = "In fatalError..\nSAXParseException: " + e.getMessage();
        try {
            bWriter.write( str, 0,str.length());
            bWriter.newLine();
        } catch (IOException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Flush the content and close the file.
     * @throws IOException error happen when writing file or closing file.
     */
    @Override
    public void close() throws IOException {
        bWriter.flush();
        bWriter.close();
    }
}
