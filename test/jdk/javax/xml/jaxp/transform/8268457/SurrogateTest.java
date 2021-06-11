/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * @test
 * @bug 8268457
 * @summary XML Transformer outputs Unicode supplementary character incorrectly to HTML
 */
public class SurrogateTest {
    
    final static String TEST_SRC = System.getProperty("test.src", ".");
    static File baseDir = new File(TEST_SRC + File.separator + "testdata");

    public static void main(String[] args) throws Exception {
        case01();
        case02();
    }
    
    private static void case01() throws Exception {
        File out = new File("case01out.html");
        File expected = new File(baseDir, "case01ok.html");
        FileInputStream tFis = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;
        
        try {
            tFis = new FileInputStream(new File(baseDir, "case01.xslt"));
            Source tSrc = new StreamSource(tFis);
            
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer(tSrc);
            t.setOutputProperty("method", "html");
            
            fis = new FileInputStream(new File(baseDir, "case01.xml"));
            fos = new FileOutputStream(out);
        
            Source src = new StreamSource(fis);
            Result res = new StreamResult(fos);
        
            t.transform(src, res);
            
        } finally {
            try {
                if (tFis != null) {
                    tFis.close();
                }
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } finally {
                    if (fos != null) {
                        fos.flush();
                        fos.close();
                    }
                }
            }
        }
        verify(out, expected);
    }
    
    private static void case02() throws Exception {
        File xmlFile = new File(baseDir, "case02.xml");
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser sp = spf.newSAXParser();
        
        TestHandler th = new TestHandler();
        sp.parse(xmlFile, th);
        
        File out = new File("case02out.txt");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
            
            osw.write(th.sb.toString());
            osw.flush();
            
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
        verify(out, new File(baseDir, "case02ok.txt"));
    }
    
    private static class TestHandler extends DefaultHandler {
        private StringBuilder sb = new StringBuilder();
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            sb.append( localName + "@attr:" + attributes.getValue("attr") + '\n');
        }
    }
    
    /*
     * Compare output of test run to expected output.
     * Throw an Error if they don't match.
     */
    public static void verify(File outputFile, File expectedOutputFile) throws IOException {
        BufferedReader thisRun =
            new BufferedReader(new FileReader(outputFile));
        BufferedReader expected =
            new BufferedReader(new FileReader(expectedOutputFile));

        for (int lineNum = 1; true; lineNum++) {
            String line1 = thisRun.readLine();
            String line2 = expected.readLine();
            if (line1 == null && line2 == null) {
                return;         // EOF with all lines matching
            }
            if (line1 == null || !line1.trim().equals(line2.trim())) {
                throw new Error(outputFile + ":" + lineNum +
                                ": output doesn't match");
            }
        }
    }
}
