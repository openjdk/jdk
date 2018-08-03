/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.cmd;

import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.process.OutputAnalyzer;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @test
 * @key jfr
 * @summary Tests print --xml
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @modules java.scripting
 *          java.xml
 *          jdk.jfr
 *
 * @run main/othervm jdk.jfr.cmd.TestPrintXML
 */
public class TestPrintXML {

    public static void main(String... args) throws Exception {

        Path recordingFile = ExecuteHelper.createProfilingRecording().toAbsolutePath();

        OutputAnalyzer output = ExecuteHelper.run("print", "--xml", recordingFile.toString());
        String xml = output.getStdout();
        System.out.println(xml);
        // Parse XML string
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser sp = factory.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        RecordingHandler handler = new RecordingHandler();
        xr.setContentHandler(handler);
        xr.parse(new InputSource(new StringReader(xml)));

        // Verify that all data was written correctly
        Iterator<RecordedEvent> it = RecordingFile.readAllEvents(recordingFile).iterator();
        for (XMLEvent xmlEvent : handler.events) {
            RecordedEvent re = it.next();
            if (!compare(re, xmlEvent.values)) {
                System.out.println(re);
                System.out.println(xmlEvent.values.toString());
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
                return false;
            }
            for (ValueDescriptor v : fields) {
                String name = v.getName();
                if (!compare(re.getValue(name), xmlMap.get(name))) {
                    return false;
                }
            }
            return true;
        }
        if (eventObject.getClass().isArray()) {
            Object[] array = (Object[]) eventObject;
            Object[] xmlArray = (Object[]) xmlObject;
            if (array.length != xmlArray.length) {
                return false;
            }
            for (int i = 0; i < array.length; i++) {
                if (!compare(array[i], xmlArray[i])) {
                    return false;
                }
            }
            return true;
        }
        String s1 = String.valueOf(eventObject);
        String s2 = (String) xmlObject;
        return s1.equals(s2);
    }

    static class XMLEvent {
        String name;
        Instant startTime;
        Duration duration;
        Map<String, Object> values = new HashMap<>();

        XMLEvent(String name, Instant startTime, Duration duration) {
            this.name = name;
            this.startTime = startTime;
            this.duration = duration;
        }
    }

    public static final class RecordingHandler extends DefaultHandler {

        private Stack<Object> objects = new Stack<>();
        private Stack<SimpleEntry<String, String>> elements = new Stack<>();
        private List<XMLEvent> events = new ArrayList<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
            elements.push(new SimpleEntry<>(attrs.getValue("name"), attrs.getValue("index")));
            switch (qName) {
            case "null":
                objects.pop();
                objects.push(null);
                break;
            case "event":
                Instant startTime = Instant.parse(attrs.getValue("startTime"));
                Duration duration = Duration.parse(attrs.getValue("duration"));
                objects.push(new XMLEvent(attrs.getValue("name"), startTime, duration));
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
                    events.add((XMLEvent) value);
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
    }
}
