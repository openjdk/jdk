/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

package stream.XMLStreamReaderTest;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.DTD;
import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.events.EntityReference;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLStreamReaderTest.SupportDTDTest
 * @summary Test SUPPORT_DTD and IS_REPLACING_ENTITY_REFERENCES.
 */

/**
*
* SUPPORT_DTD behavior:
* Regardless of supportDTD, always report a DTD event () and throw an
* exception if an entity reference is found when supportDTD is false
*
* The behavior is related to property IS_REPLACING_ENTITY_REFERENCES.
*
* SUPPORT_DTD      Replace Entity   DTD                    ENTITY_REFERENCE
* true (default)   true (default)   yes, has entities      no, return Characters
* true (default)   false            yes, has entities      yes, can print entity name
* false            true (default)   yes, but no entity     Exception: Undeclared general entity
* false            false            yes, but no entity     yes, can print entity name
*
* Two patches related:
* sjsxp issue 9: XMLDocumentScannerImpl.java rev 1.6
* If the supportDTD property is set to FALSE, external and internal subsets
* are now ignored, rather than an error being reported. In particular, with
* this property set to FALSE, no error is reported if an external subset cannot
* be found. Note that the internal subset is still parsed (and errors could be
* reported here) but no events are returned by the parser. This fixes SJSXP
* issue 9 from Java.net.
* Note: SAX and DOM report fatal errors:
*       If either SAX or DOM is used, turning on http://apache.org/xml/features/disallow-doctype-decl [1] effectively disables DTD,
*       according to the spec: A fatal error is thrown if the incoming document contains a DOCTYPE declaration.
*       The current jaxp implementation actually throws a nullpointexception. A better error message could be used.
*
*/
public class SupportDTDTest {
    final boolean DEBUG = false;
    final String _file = "ExternalDTD.xml";
    final String XML = "<?xml version='1.0' ?>" + "<!DOCTYPE root [\n" + "<!ENTITY intEnt 'internal entity'>\n" + "<!ENTITY extParsedEnt SYSTEM 'url:dummy'>\n"
            + "<!NOTATION notation PUBLIC 'notation-public-id'>\n" + "<!NOTATION notation2 SYSTEM 'url:dummy'>\n"
            + "<!ENTITY extUnparsedEnt SYSTEM 'url:dummy2' NDATA notation>\n" + "]>" + "<root>&intEnt;</root>";

    final String XML1 = "<?xml version='1.0' encoding ='utf-8'?>" + "<!DOCTYPE document SYSTEM \"" + this.getClass().getResource("ExternalDTD.dtd").getFile()
            + "\">" + "<document>" + "<name>&mkm;</name>" + "</document>";

   // final String XML1 = "<?xml version='1.0' encoding ='utf-8'?>" + "<!DOCTYPE document SYSTEM \"/home/oracle/repo/xmlwork/dev/jdk/test/javax/xml/jaxp/unittest/javax/xml/stream/XMLStreamReaderTest/ExternalDTD.dtd\">" + "<document>"
   //         + "<name>&mkm;</name>" + "</document>";

    final int ENTITY_INTERNAL_ONLY = 1;
    final int ENTITY_EXTERNAL_ONLY = 2;
    final int ENTITY_BOTH = 3;

    private boolean _DTDReturned = false;
    private boolean _EntityEventReturned = false;
    private boolean _hasEntityDelaration = false;
    private boolean _exceptionThrown = false;

    // tests 1-4 test internal entities only
    @Test
    public void test1() throws Exception {
        supportDTD(true, true, ENTITY_INTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertTrue(_hasEntityDelaration);
        assertFalse(_EntityEventReturned);
    }

    @Test
    public void test2() throws Exception {
        supportDTD(true, false, ENTITY_INTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertTrue(_hasEntityDelaration);
        assertTrue(_EntityEventReturned);
    }

    @Test
    public void test3() throws Exception {
        supportDTD(false, true, ENTITY_INTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertFalse(_hasEntityDelaration);
        assertTrue(_exceptionThrown);
    }

    @Test
    public void test4() throws Exception {
        supportDTD(false, false, ENTITY_INTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertFalse(_hasEntityDelaration);
        assertTrue(_EntityEventReturned);
    }

    // tests 5-8 test external entities only
    @Test
    public void test5() throws Exception {
        supportDTD(true, true, ENTITY_EXTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertTrue(_hasEntityDelaration);
        assertFalse(_EntityEventReturned);
    }

    @Test
    public void test6() throws Exception {
        supportDTD(true, false, ENTITY_EXTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertTrue(_hasEntityDelaration);
        assertTrue(_EntityEventReturned);
    }

    @Test
    public void test7() throws Exception {
        supportDTD(false, true, ENTITY_EXTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertFalse(_hasEntityDelaration);
        assertTrue(_exceptionThrown);
    }

    @Test
    public void test8() throws Exception {
        supportDTD(false, false, ENTITY_EXTERNAL_ONLY);
        assertTrue(_DTDReturned);
        assertFalse(_hasEntityDelaration);
        assertTrue(_EntityEventReturned);
    }

    // tests 9-12 test both internal and external entities
    @Test
    public void test9() throws Exception {
        supportDTD(true, true, ENTITY_BOTH);
        assertTrue(_DTDReturned);
        assertTrue(_hasEntityDelaration);
        assertFalse(_EntityEventReturned);
    }

    @Test
    public void test10() throws Exception {
        supportDTD(true, false, ENTITY_BOTH);
        assertTrue(_DTDReturned);
        assertTrue(_hasEntityDelaration);
        assertTrue(_EntityEventReturned);
    }

    @Test
    public void test11() throws Exception {
        supportDTD(false, true, ENTITY_BOTH);
        assertTrue(_DTDReturned);
        assertFalse(_hasEntityDelaration);
        assertTrue(_exceptionThrown);
    }

    @Test
    public void test12() throws Exception {
        supportDTD(false, false, ENTITY_BOTH);
        assertTrue(_DTDReturned);
        assertFalse(_hasEntityDelaration);
        assertTrue(_EntityEventReturned);
    }

    public void supportDTD(boolean supportDTD, boolean replaceEntity, int inputType) throws Exception {
        print("\n");
        print((supportDTD ? "SupportDTD=true" : "SupportDTD=false") + ", " + (replaceEntity ? "replaceEntity=true" : "replaceEntity=false"));
        try {
            XMLInputFactory xif = getFactory(supportDTD, replaceEntity);
            XMLEventReader r = getEventReader(xif, inputType);
            int eventType = 0;
            int count = 0;
            while (r.hasNext()) {
                XMLEvent event = r.nextEvent();
                eventType = event.getEventType();
                print("Event " + ++count + ": " + eventType);
                switch (eventType) {
                    case XMLStreamConstants.DTD:
                        DisplayEntities((DTD) event);
                        _DTDReturned = true;
                        break;
                    case XMLStreamConstants.ENTITY_REFERENCE:
                        print("Entity Name: " + ((EntityReference) event).getName());
                        _EntityEventReturned = true;
                        break;
                    case XMLStreamConstants.CHARACTERS:
                        print("Text: " + ((Characters) event).getData());
                }
            }

        } catch (Exception e) {
            _exceptionThrown = true;
            if (DEBUG)
                e.printStackTrace();
        }
    }

    XMLInputFactory getFactory(boolean supportDTD, boolean replaceEntity) {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, (supportDTD) ? Boolean.TRUE : Boolean.FALSE);
        xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, (replaceEntity) ? Boolean.TRUE : Boolean.FALSE);
        // xif.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.TRUE);
        return xif;
    }

    private XMLEventReader getEventReader(XMLInputFactory inputFactory, int input) throws Exception {
        XMLEventReader er = null;
        if (input == ENTITY_INTERNAL_ONLY) {
            er = inputFactory.createXMLEventReader(new StringReader(XML));
        } else if (input == ENTITY_EXTERNAL_ONLY) {
            er = inputFactory.createXMLEventReader(new StringReader(XML1));
        } else {
            File file = new File(this.getClass().getResource(_file).getFile());
            FileInputStream inputStream = new FileInputStream(file);
            // XMLStreamReader r = xif.createXMLStreamReader(inputStream);
            er = inputFactory.createXMLEventReader(inputStream);
        }
        return er;
    }

    void DisplayEntities(DTD event) {
        List<EntityDeclaration> entities = event.getEntities();
        if (entities == null) {
            _hasEntityDelaration = false;
            print("No entity found.");
        } else {
            _hasEntityDelaration = true;
            for (int i = 0; i < entities.size(); i++) {
                EntityDeclaration entity = entities.get(i);
                print(entity.getName());
            }
        }

    }

    void print(String s) {
        if (DEBUG)
            System.out.println(s);
    }

}
