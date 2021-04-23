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
package common;

import com.sun.org.apache.xerces.internal.utils.XMLSecurityManager.Limit;
import java.util.EnumSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;
import jdk.xml.internal.JdkXmlUtils.ImplPropMap;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.XMLReader;

/*
 * @test
 * @bug 8265248
 * @modules java.xml/com.sun.org.apache.xerces.internal.utils
 * @modules java.xml/jdk.xml.internal
 * @run testng common.ImplPropertyTest
 * @summary Verifies Implementation-specific Features and Properties as specified
 * in the java.xml module summary.
 */
public class ImplPropertyTest {
    private final SAXParserFactory spf = SAXParserFactory.newDefaultInstance();
    private SAXParser sp;
    private XMLReader reader;
    private final DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
    private final XMLInputFactory xif = XMLInputFactory.newDefaultFactory();
    private final SchemaFactory sf = SchemaFactory.newDefaultInstance();
    private final TransformerFactory tf = TransformerFactory.newDefaultInstance();
    private Transformer transformer;
    private LSSerializer serializer;
    private DOMConfiguration domConfig;
    private final XPathFactory xf = XPathFactory.newDefaultInstance();

    // as in the Processors table in java.xml module summary
    private enum Processor {
        DOM,
        SAX,
        XMLREADER,
        StAX,
        VALIDATION,
        TRANSFORM,
        XSLTC,
        DOMLS,
        XPATH
    };

    @BeforeClass
    public void setUpClass() throws Exception {
        sp = spf.newSAXParser();
        reader = sp.getXMLReader();

        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
        serializer = impl.createLSSerializer();
        domConfig = serializer.getDomConfig();

        transformer = TransformerFactory.newInstance().newTransformer();
    }

    @Test
    public void testLimits() throws Exception {
        // Supported processors for Limits
        Set<Processor> pLimit = EnumSet.of(Processor.DOM, Processor.SAX, Processor.XMLREADER,
                Processor.StAX, Processor.VALIDATION, Processor.TRANSFORM);

        for (Limit limit : Limit.values()) {
            for (Processor p : pLimit) {
                testProperties(p, limit.apiProperty(), limit.systemProperty(), 100);
                testProperties(p, limit.systemProperty(), limit.apiProperty(), 200);
            }
        }

    }

    // Supported processor for isStandalone: DOMLS
    @Test
    public void testIsStandalone() throws Exception {
        testProperties(Processor.DOMLS, ImplPropMap.ISSTANDALONE.qName(),
                ImplPropMap.ISSTANDALONE.systemProperty(), true);
        testProperties(Processor.DOMLS, ImplPropMap.ISSTANDALONE.systemProperty(),
                ImplPropMap.ISSTANDALONE.qName(), false);
    }

    // Supported processor for xsltcIsStandalone: XSLTC Serializer
    @Test
    public void testXSLTCIsStandalone() throws Exception {
        testProperties(Processor.XSLTC, ImplPropMap.XSLTCISSTANDALONE.qName(),
                ImplPropMap.XSLTCISSTANDALONE.systemProperty(), "yes");
        testProperties(Processor.XSLTC, ImplPropMap.XSLTCISSTANDALONE.systemProperty(),
                ImplPropMap.XSLTCISSTANDALONE.qName(), "no");
    }

    // Supported processor for cdataChunkSize: SAX and StAX
    @Test
    public void testCData() throws Exception {
        // Supported processors for CDATA
        Set<Processor> pCData = EnumSet.of(Processor.SAX, Processor.XMLREADER,
                Processor.StAX);
        ImplPropMap CDATA = ImplPropMap.CDATACHUNKSIZE;
        for (Processor p : pCData) {
            testProperties(p, CDATA.qName(), CDATA.systemProperty(), 100);
            testProperties(p, CDATA.systemProperty(), CDATA.qName(), 200);
        }
    }

    // Supported processor for extensionClassLoader: Transform
    @Test
    public void testExtensionClassLoader() throws Exception {
        ImplPropMap ECL = ImplPropMap.EXTCLSLOADER;
        testProperties(Processor.TRANSFORM, ECL.qName(), ECL.qNameOld(), new TestCL());
        testProperties(Processor.TRANSFORM, ECL.qNameOld(), ECL.qName(), new TestCL());
    }

    // Supported processor for feature enableExtensionFunctions: Transform, XPath
    @Test
    public void testEnableExtensionFunctions() throws Exception {
        Set<Processor> pEEF = EnumSet.of(Processor.TRANSFORM, Processor.XPATH);
        ImplPropMap EEF = ImplPropMap.ENABLEEXTFUNC;
        for (Processor p : pEEF) {
            testFeatures(p, EEF.qName(), EEF.systemProperty(), true);
            testFeatures(p, EEF.systemProperty(), EEF.qName(), true);
        }
    }

    // Supported processor for feature overrideDefaultParser: Transform, Validation, XPath
    @Test
    public void testOverrideDefaultParser() throws Exception {
        Set<Processor> pEEF = EnumSet.of(Processor.TRANSFORM, Processor.VALIDATION, Processor.XPATH);
        ImplPropMap ODP = ImplPropMap.OVERRIDEPARSER;
        for (Processor p : pEEF) {
            testFeatures(p, ODP.qName(), ODP.systemProperty(), true);
            testFeatures(p, ODP.systemProperty(), ODP.qName(), true);
        }
    }

    // Supported processor for feature resetSymbolTable: SAX
    @Test
    public void testResetSymbolTable() throws Exception {
        ImplPropMap RST = ImplPropMap.RESETSYMBOLTABLE;
        testFeatures(Processor.SAX, RST.qName(), RST.systemProperty(), true);
        testFeatures(Processor.SAX, RST.systemProperty(), RST.qName(), true);
    }

    private void testProperties(Processor processor, String name1, String name2, Object value)
            throws Exception {
        Object ret = null;
        switch (processor) {
            case DOM:
                dbf.setAttribute(name1, value);
                ret = dbf.getAttribute(name2);
                break;
            case SAX:
                sp.setProperty(name1, value);
                ret = sp.getProperty(name2);
                break;
            case XMLREADER:
                reader.setProperty(name1, value);
                ret = reader.getProperty(name2);
                break;
            case StAX:
                xif.setProperty(name1, value);
                ret = xif.getProperty(name2);
                break;
            case VALIDATION:
                sf.setProperty(name1, value);
                ret = sf.getProperty(name2);
                break;
            case TRANSFORM:
                tf.setAttribute(name1, value);
                ret = tf.getAttribute(name2);
                break;
            case XSLTC:
                transformer.setOutputProperty(name1, (String)value);
                ret = transformer.getOutputProperty(name2);
                break;
            case DOMLS:
                domConfig.setParameter(name1, value);
                ret = domConfig.getParameter(name2);
                break;
            case XPATH:
                break;
        }
        if ((value instanceof Integer) && ret instanceof String) {
            ret = Integer.parseInt((String)ret);
        }
        Assert.assertEquals(ret, value);
    }

    private void testFeatures(Processor processor, String name1, String name2, boolean value)
            throws Exception {
        switch (processor) {
            case DOM:
                dbf.setFeature(name1, value);
                Assert.assertEquals(dbf.getFeature(name2), value);
                return;
            case SAX:
                spf.setFeature(name1, value);
                Assert.assertEquals(spf.getFeature(name2), value);
                return;
            case VALIDATION:
                sf.setFeature(name1, value);
                Assert.assertEquals(sf.getFeature(name2), value);
                return;
            case TRANSFORM:
                tf.setFeature(name1, value);
                Assert.assertEquals(tf.getFeature(name2), value);
                return;
            case XPATH:
                xf.setFeature(name1, value);
                Assert.assertEquals(xf.getFeature(name2), value);
                return;
        }

        Assert.fail("Failed setting features for : " + processor);
    }


    class TestCL extends ClassLoader {

        public TestCL() {
        }

        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException( name );
        }
    }
}
