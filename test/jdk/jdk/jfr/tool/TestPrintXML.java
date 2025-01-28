/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.tool;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.Unsigned;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @requires vm.flagless
 * @summary Tests print --xml
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @modules java.scripting java.xml jdk.jfr
 *
 * @run main/othervm jdk.jfr.tool.TestPrintXML
 */
public class TestPrintXML {

    public static void main(String... args) throws Throwable {

        Path recordingFile = ExecuteHelper.createProfilingRecording().toAbsolutePath();

        OutputAnalyzer output = ExecuteHelper.jfr("print", "--xml", "--stack-depth", "9999", recordingFile.toString());
        System.out.println(recordingFile);
        String xml = output.getStdout();

        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(new File(System.getProperty("test.src"), "jfr.xsd"));

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setSchema(schema);
        factory.setNamespaceAware(true);

        SAXParser sp = factory.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        RecordingHandler handler = new RecordingHandler();
        xr.setContentHandler(handler);
        xr.setErrorHandler(handler);
        xr.parse(new InputSource(new StringReader(xml)));

        // Verify that all data was written correctly
        List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
        Collections.sort(events, new EndTicksComparator());
        Iterator<RecordedEvent> it = events.iterator();
        for (XMLEvent xmlEvent : handler.events) {
            RecordedEvent re = it.next();
            if (!compare(re, xmlEvent.values)) {
                System.out.println("Expected:");
                System.out.println("----------------------");
                System.out.println(re);
                System.out.println();
                System.out.println("Was (XML)");
                System.out.println("----------------------");
                if (xmlEvent.begin > 0 && xmlEvent.end > 0) {
                    String lines[] = xml.split("\\r?\\n");
                    for (int i = xmlEvent.begin - 1; i < xmlEvent.end; i++) {
                        System.out.println(i + " " + lines[i]);
                    }
                } else {
                    System.out.println("Could not locate XML position");
                }
                System.out.println();
                throw new Exception("Event doesn't match");
            }
        }

    }

    @SuppressWarnings("unchecked")
    static boolean compare(Object eventObject, Object xmlObject) {
        if (eventObject == null) {
            return xmlObject == null;
        }
        if (eventObject instanceof RecordedObject) {
            RecordedObject re = (RecordedObject) eventObject;
            Map<String, Object> xmlMap = (Map<String, Object>) xmlObject;
            List<ValueDescriptor> fields = re.getFields();
            if (fields.size() != xmlMap.size()) {
                System.err.println("Size of fields of recorded object (" + fields.size() +
                                   ") and reference (" + xmlMap.size() + ") differ");
                return false;
            }
            for (ValueDescriptor v : fields) {
                String name = v.getName();
                Object xmlValue = xmlMap.get(name);
                Object expectedValue = re.getValue(name);
                if (v.getAnnotation(Timestamp.class) != null) {
                    if (expectedValue.equals(Long.MIN_VALUE)) { // Missing
                        expectedValue = OffsetDateTime.MIN;
                    } else {
                        // Make instant of OffsetDateTime
                        xmlValue = OffsetDateTime.parse("" + xmlValue).toInstant().toString();
                        expectedValue = re.getInstant(name);
                    }
                }
                if (v.getAnnotation(Timespan.class) != null) {
                    expectedValue = re.getDuration(name);
                }
                if (expectedValue instanceof Number && v.getAnnotation(Unsigned.class) != null) {
                    expectedValue = Long.toUnsignedString(re.getLong(name));
                }
                if (!compare(expectedValue, xmlValue)) {
                    System.err.println("Expcted value " + expectedValue + " differs from " + xmlValue);
                    return false;
                }
            }
            return true;
        }
        if (eventObject.getClass().isArray()) {
            Object[] array = (Object[]) eventObject;
            Object[] xmlArray = (Object[]) xmlObject;
            if (array.length != xmlArray.length) {
                System.err.println("Array length " + array.length + " differs from length " +
                                   xmlArray.length);
                return false;
            }
            for (int i = 0; i < array.length; i++) {
                if (!compare(array[i], xmlArray[i])) {
                    System.err.println("Array element " + i + "(" + array[i] +
                                       ") differs from element " + xmlArray[i]);
                    return false;
                }
            }
            return true;
        }
        String s1 = String.valueOf(eventObject);
        String s2 = (String) xmlObject;
        boolean res = s1.equals(s2);
        if (! res) {
            System.err.println("Event object string " + s1 + " differs from " + s2);
        }
        return res;
    }

    static class XMLEvent {
        String name;
        private Map<String, Object> values = new HashMap<>();
        private int begin = -1;
        private int end = -1;

        XMLEvent(String name) {
            this.name = name;
        }
    }

    public static final class RecordingHandler extends DefaultHandler {

        private Locator locator;
        private Stack<Object> objects = new Stack<>();
        private Stack<SimpleEntry<String, String>> elements = new Stack<>();
        private List<XMLEvent> events = new ArrayList<>();

        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
            elements.push(new SimpleEntry<>(attrs.getValue("name"), attrs.getValue("index")));
            String nil = attrs.getValue("xsi:nil");
            if ("true".equals(nil)) {
                objects.push(null);
                return;
            }

            switch (qName) {
            case "event":
                XMLEvent event = new XMLEvent(attrs.getValue("type"));
                event.begin = locator.getLineNumber();
                objects.push(event);
                break;
            case "struct":
                objects.push(new HashMap<String, Object>());
                break;
            case "array":
                objects.push(new Object[Integer.parseInt(attrs.getValue("size"))]);
                break;
            case "value":
                objects.push(new StringBuilder());
                break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (!objects.isEmpty()) {
                Object o = objects.peek();
                if (o instanceof StringBuilder) {
                    ((StringBuilder) o).append(ch, start, length);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void endElement(String uri, String localName, String qName) {
            SimpleEntry<String, String> element = elements.pop();
            switch (qName) {
            case "event":
            case "struct":
            case "array":
            case "value":
                String name = element.getKey();
                Object value = objects.pop();
                if (objects.isEmpty()) {
                    XMLEvent event = (XMLEvent) value;
                    event.end = locator.getLineNumber();
                    events.add(event);
                    return;
                }
                if (value instanceof StringBuilder) {
                    value = ((StringBuilder) value).toString();
                }
                Object parent = objects.peek();
                if (parent instanceof XMLEvent) {
                    ((XMLEvent) parent).values.put(name, value);
                }
                if (parent instanceof Map) {
                    ((Map<String, Object>) parent).put(name, value);
                }
                if (parent != null && parent.getClass().isArray()) {
                    int index = Integer.parseInt(element.getValue());
                    ((Object[]) parent)[index] = value;
                }
            }
        }

        public void warning(SAXParseException spe) throws SAXException {
            throw new SAXException(spe);
        }

        public void error(SAXParseException spe) throws SAXException {
            throw new SAXException(spe);
        }

        public void fatalError(SAXParseException spe) throws SAXException {
            throw new SAXException(spe);
        }
    }
}
