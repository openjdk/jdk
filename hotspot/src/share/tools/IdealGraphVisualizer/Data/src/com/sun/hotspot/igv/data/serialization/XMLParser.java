/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.hotspot.igv.data.serialization;

import com.sun.hotspot.igv.data.Property;
import com.sun.hotspot.igv.data.Properties;
import java.util.HashMap;
import java.util.Stack;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 *
 * @author Thomas Wuerthinger
 */
public class XMLParser implements ContentHandler {

    public static interface ParseMonitor {

        public void setProgress(double d);

        public void setState(String state);
    }

    public static class MissingAttributeException extends SAXException {

        private String name;

        public MissingAttributeException(String name) {
            super("Missing attribute \"" + name + "\"");
            this.name = name;
        }

        public String getAttributeName() {
            return this.getMessage();
        }
    }

    public static class HandoverElementHandler<P> extends ElementHandler<P, P> {

        @Override
        protected P start() throws SAXException {
            return getParentObject();
        }

        public HandoverElementHandler(String name) {
            super(name);
        }

        public HandoverElementHandler(String name, boolean needsText) {
            super(name, needsText);
        }
    }

    public static class TopElementHandler<P> extends ElementHandler<P, Object> {

        public TopElementHandler() {
            super(null);
        }
    }

    public static class ElementHandler<T, P> {

        private String name;
        private T object;
        private Attributes attr;
        private StringBuilder currentText;
        private ParseMonitor monitor;
        private HashMap<String, ElementHandler<?, ? super T>> hashtable;
        private boolean needsText;
        private ElementHandler<P, ?> parentElement;

        public ElementHandler(String name) {
            this(name, false);
        }

        public ElementHandler<P, ?> getParentElement() {
            return parentElement;
        }

        public P getParentObject() {
            return getParentElement().getObject();
        }

        protected boolean needsText() {
            return needsText;
        }

        public ElementHandler(String name, boolean needsText) {
            this.hashtable = new HashMap<String, ElementHandler<?, ? super T>>();
            this.name = name;
            this.needsText = needsText;
        }

        public ParseMonitor getMonitor() {
            return monitor;
        }

        public ElementHandler<?, ? super T> getChild(String name) {
            return hashtable.get(name);
        }

        public void addChild(ElementHandler<?, ? super T> handler) {
            assert handler != null;
            hashtable.put(handler.getName(), handler);
        }

        public String getName() {
            return name;
        }

        public T getObject() {
            return object;
        }

        public String readAttribute(String name) {
            return attr.getValue(name);
        }

        public String readRequiredAttribute(String name) throws SAXException {
            String s = readAttribute(name);
            if (s == null) {
                throw new MissingAttributeException(name);
            }
            return s;
        }

        public void processAttributesAsProperties(Properties p) {
            int length = attr.getLength();
            for (int i = 0; i < length; i++) {
                String val = attr.getValue(i).intern();
                String localName = attr.getLocalName(i).intern();
                p.setProperty(val, localName);
            }
        }

        public void startElement(ElementHandler<P, ?> parentElement, Attributes attr, ParseMonitor monitor) throws SAXException {
            this.currentText = new StringBuilder();
            this.attr = attr;
            this.monitor = monitor;
            this.parentElement = parentElement;
            object = start();
        }

        protected T start() throws SAXException {
            return null;
        }

        protected void end(String text) throws SAXException {

        }

        public void endElement() throws SAXException {
            end(currentText.toString());
        }

        protected void text(char[] c, int start, int length) {
            assert currentText != null;
            currentText.append(c, start, length);
        }
    }
    private Stack<ElementHandler> stack;
    private ParseMonitor monitor;

    public XMLParser(TopElementHandler rootHandler, ParseMonitor monitor) {
        this.stack = new Stack<ElementHandler>();
        this.monitor = monitor;
        this.stack.push(rootHandler);
    }

    public void setDocumentLocator(Locator locator) {
        if (monitor != null) {
            monitor.setState("Starting parsing");
        }
    }

    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

        assert !stack.isEmpty();
        ElementHandler parent = stack.peek();
        if (parent != null) {
            ElementHandler child = parent.getChild(qName);
            if (child != null) {
                child.startElement(parent, atts, monitor);
                stack.push(child);
                return;
            }
        }

        stack.push(null);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        ElementHandler handler = stack.pop();
        if (handler != null) {
            handler.endElement();
        }
    }

    public void characters(char[] ch, int start, int length) throws SAXException {

        assert !stack.isEmpty();


        ElementHandler top = stack.peek();
        if (top != null && top.needsText()) {
            top.text(ch, start, length);
        }
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    }

    public void processingInstruction(String target, String data) throws SAXException {
    }

    public void skippedEntity(String name) throws SAXException {
    }
}
