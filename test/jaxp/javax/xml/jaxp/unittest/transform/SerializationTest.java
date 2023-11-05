/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

/*
 * @test
 * @bug 8273370
 * @run testng transform.SerializationTest
 * @summary Verifies that the characters are written correctly during serialization.
 */
public class SerializationTest {

    private static final String PREFS_DTD_URI
            = "http://java.sun.com/dtd/preferences.dtd";
    private static String CLS_DIR = System.getProperty("test.classes", ".");
    private static String SRC_DIR = System.getProperty("test.src");

    /**
     * Verifies that the XMLSupport for exportSubtree handles control characters
     * correctly by reporting en error.
     *
     * Note: exportSubtree currently throws AssertionError. It would be more
     * appropriate to throw InvalidPreferencesFormatException as the import
     * method does. Since this is an edge case however, we'll keep it as is to
     * avoid signature change.
     *
     * The following was the original test:
            Preferences p = Preferences.userRoot().node("test");
            p.put("key", "[\u0018\u0019]");
            p.exportSubtree(new ByteArrayOutputStream());
     *
     * The code however, hanged when running in JTReg. This test therefore replaced
     * the above code with the process extracted from the exportSubtree routine.
     *
     * @throws Exception if the test fails
     */
    @Test
    public void testTrasformer() throws Exception {
        Assert.assertThrows(AssertionError.class,
                () -> export(new ByteArrayOutputStream()));
    }

    private void export(OutputStream os) throws IOException {
        Document doc = createPrefsDoc("preferences");
        Element preferences = doc.getDocumentElement();
        preferences.setAttribute("EXTERNAL_XML_VERSION", "1.0");
        Element xmlRoot = (Element) preferences.appendChild(doc.createElement("root"));
        xmlRoot.setAttribute("type", "user");

        Element e = xmlRoot;

        e.appendChild(doc.createElement("map"));
        e = (Element) e.appendChild(doc.createElement("node"));
        e.setAttribute("name", "test");

        putPreferencesInXml(e, doc);

        writeDoc(doc, os);
    }

    private static Document createPrefsDoc(String qname) {
        try {
            DOMImplementation di = DocumentBuilderFactory.newInstance().
                    newDocumentBuilder().getDOMImplementation();
            DocumentType dt = di.createDocumentType(qname, null, PREFS_DTD_URI);
            return di.createDocument(null, qname, dt);
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e);
        }
    }

    private static void putPreferencesInXml(Element elt, Document doc) {
        Element map = (Element) elt.appendChild(doc.createElement("map"));
        Element entry = (Element) map.appendChild(doc.createElement("entry"));
        entry.setAttribute("key", "key");
        entry.setAttribute("value", "[\u0018\u0019]");
    }

    private void writeDoc(Document doc, OutputStream out)
            throws IOException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute("indent-number", 2);
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doc.getDoctype().getSystemId());
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            //Transformer resets the "indent" info if the "result" is a StreamResult with
            //an OutputStream object embedded, creating a Writer object on top of that
            //OutputStream object however works.
            t.transform(new DOMSource(doc),
                    new StreamResult(new BufferedWriter(new OutputStreamWriter(out, "UTF-8"))));
        } catch (TransformerException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Verifies that the XMLSupport for importPreferences handles control
     * characters correctly by reporting en error.
     *
     * Note: this is the existing behavior. This test is here to match with the
     * export method.
     *
     * "preferences.xml" was generated by calling the exportSubtree method
     * before the patch.
     *
     * @throws Exception if the test fails
     */
    @Test
    public void testParser() throws Exception {
        Assert.assertThrows(InvalidPreferencesFormatException.class, () -> {
            Preferences.importPreferences(
                    new FileInputStream(new File(SRC_DIR + "/preferences.xml")));
        });
    }
}
