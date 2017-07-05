/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.fastinfoset.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class SAXEventSerializer extends DefaultHandler
        implements LexicalHandler {

    private Writer _writer;
    private boolean _charactersAreCDATA;
    private StringBuffer _characters;

    private Stack _namespaceStack = new Stack();
    protected List _namespaceAttributes;

    public SAXEventSerializer(OutputStream s) throws IOException {
        _writer = new OutputStreamWriter(s);
        _charactersAreCDATA = false;
    }

    // -- ContentHandler interface ---------------------------------------

    public void startDocument() throws SAXException {
        try {
            _writer.write("<sax xmlns=\"http://www.sun.com/xml/sax-events\">\n");
            _writer.write("<startDocument/>\n");
            _writer.flush();
        }
        catch (IOException e) {
            throw new SAXException(e);
        }
    }

    public void endDocument() throws SAXException {
        try {
            _writer.write("<endDocument/>\n");
            _writer.write("</sax>");
            _writer.flush();
            _writer.close();
        }
        catch (IOException e) {
            throw new SAXException(e);
        }
    }


    public void startPrefixMapping(String prefix, String uri)
        throws SAXException
    {
        if (_namespaceAttributes == null) {
            _namespaceAttributes = new ArrayList();
        }

        String qName = (prefix.length() == 0) ? "xmlns" : "xmlns" + prefix;
        AttributeValueHolder attribute = new AttributeValueHolder(
                qName,
                prefix,
                uri,
                null,
                null);
        _namespaceAttributes.add(attribute);
    }

    public void endPrefixMapping(String prefix)
        throws SAXException
    {
        /*
        try {
            outputCharacters();

            _writer.write("<endPrefixMapping prefix=\"" +
                prefix + "\"/>\n");
            _writer.flush();
        }
        catch (IOException e) {
            throw new SAXException(e);
        }
         */
    }

    public void startElement(String uri, String localName,
            String qName, Attributes attributes)
        throws SAXException
    {
        try {
            outputCharacters();

            if (_namespaceAttributes != null) {

                AttributeValueHolder[] attrsHolder = new AttributeValueHolder[0];
                attrsHolder = (AttributeValueHolder[])_namespaceAttributes.toArray(attrsHolder);

                // Sort attributes
                quicksort(attrsHolder, 0, attrsHolder.length - 1);

                for (int i = 0; i < attrsHolder.length; i++) {
                    _writer.write("<startPrefixMapping prefix=\"" +
                        attrsHolder[i].localName + "\" uri=\"" + attrsHolder[i].uri + "\"/>\n");
                    _writer.flush();
                }

                _namespaceStack.push(attrsHolder);
                _namespaceAttributes = null;
            } else {
                _namespaceStack.push(null);
            }

            AttributeValueHolder[] attrsHolder =
                new AttributeValueHolder[attributes.getLength()];
            for (int i = 0; i < attributes.getLength(); i++) {
                attrsHolder[i] = new AttributeValueHolder(
                    attributes.getQName(i),
                    attributes.getLocalName(i),
                    attributes.getURI(i),
                    attributes.getType(i),
                    attributes.getValue(i));
            }

            // Sort attributes
            quicksort(attrsHolder, 0, attrsHolder.length - 1);

            int attributeCount = 0;
            for (int i = 0; i < attrsHolder.length; i++) {
                if (attrsHolder[i].uri.equals("http://www.w3.org/2000/xmlns/")) {
                    // Ignore XMLNS attributes
                    continue;
                }
                attributeCount++;
            }

            if (attributeCount == 0) {
                _writer.write("<startElement uri=\"" + uri
                    + "\" localName=\"" + localName + "\" qName=\""
                    + qName + "\"/>\n");
                return;
            }

            _writer.write("<startElement uri=\"" + uri
                + "\" localName=\"" + localName + "\" qName=\""
                + qName + "\">\n");

            // Serialize attributes as children
            for (int i = 0; i < attrsHolder.length; i++) {
                if (attrsHolder[i].uri.equals("http://www.w3.org/2000/xmlns/")) {
                    // Ignore XMLNS attributes
                    continue;
                }
                _writer.write(
                    "  <attribute qName=\"" + attrsHolder[i].qName +
                    "\" localName=\"" + attrsHolder[i].localName +
                    "\" uri=\"" + attrsHolder[i].uri +
                    // "\" type=\"" + attrsHolder[i].type +
                    "\" value=\"" + attrsHolder[i].value +
                    "\"/>\n");
            }

            _writer.write("</startElement>\n");
            _writer.flush();
        }
        catch (IOException e) {
            throw new SAXException(e);
        }
    }

    public void endElement(String uri, String localName, String qName)
            throws SAXException
    {
        try {
            outputCharacters();

            _writer.write("<endElement uri=\"" + uri
                + "\" localName=\"" + localName + "\" qName=\""
                + qName + "\"/>\n");
            _writer.flush();

            // Write out the end prefix here rather than waiting
            // for the explicit events
            AttributeValueHolder[] attrsHolder = (AttributeValueHolder[])_namespaceStack.pop();
            if (attrsHolder != null) {
                for (int i = 0; i < attrsHolder.length; i++) {
                    _writer.write("<endPrefixMapping prefix=\"" +
                        attrsHolder[i].localName  + "\"/>\n");
                    _writer.flush();
                }
            }

        }
        catch (IOException e) {
            throw new SAXException(e);
        }
    }

    public void characters(char[] ch, int start, int length)
            throws SAXException
    {
        if (length == 0) {
            return;
        }

        if (_characters == null) {
            _characters = new StringBuffer();
        }

        // Coalesce multiple character events
        _characters.append(ch, start, length);

        /*
        try {
            _writer.write("<characters>" +
                (_charactersAreCDATA ? "<![CDATA[" : "") +
                new String(ch, start, length) +
                (_charactersAreCDATA ? "]]>" : "") +
                "</characters>\n");
            _writer.flush();
        }
        catch (IOException e) {
            throw new SAXException(e);
        }
         */
    }

    private void outputCharacters() throws SAXException {
        if (_characters == null) {
            return;
        }

        try {
            _writer.write("<characters>" +
                (_charactersAreCDATA ? "<![CDATA[" : "") +
                _characters +
                (_charactersAreCDATA ? "]]>" : "") +
                "</characters>\n");
            _writer.flush();

            _characters = null;
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException
    {
        // Report ignorable ws as characters (assumes validation off)
        characters(ch, start, length);
    }

    public void processingInstruction(String target, String data)
            throws SAXException
    {
        try {
            outputCharacters();

            _writer.write("<processingInstruction target=\"" + target
                + "\" data=\"" + data + "\"/>\n");
            _writer.flush();
        }
        catch (IOException e) {
            throw new SAXException(e);
        }
    }

    // -- LexicalHandler interface ---------------------------------------

    public void startDTD(String name, String publicId, String systemId)
            throws SAXException {
        // Not implemented
    }

    public void endDTD()
            throws SAXException {
        // Not implemented
    }

    public void startEntity(String name)
            throws SAXException {
        // Not implemented
    }

    public void endEntity(String name)
            throws SAXException {
        // Not implemented
    }

    public void startCDATA()
            throws SAXException {
        _charactersAreCDATA = true;
    }

    public void endCDATA()
            throws SAXException {
        _charactersAreCDATA = false;
    }

    public void comment(char[] ch, int start, int length)
            throws SAXException
    {
        try {
            outputCharacters();

            _writer.write("<comment>" +
                new String(ch, start, length) +
                "</comment>\n");
            _writer.flush();
        }
        catch (IOException e) {
            throw new SAXException(e);
        }
    }

    // -- Utility methods ------------------------------------------------

    private void quicksort(AttributeValueHolder[] attrs, int p, int r) {
        while (p < r) {
            final int q = partition(attrs, p, r);
            quicksort(attrs, p, q);
            p = q + 1;
        }
    }

    private int partition(AttributeValueHolder[] attrs, int p, int r) {
        AttributeValueHolder x = attrs[(p + r) >>> 1];
        int i = p - 1;
        int j = r + 1;
        while (true) {
            while (x.compareTo(attrs[--j]) < 0);
            while (x.compareTo(attrs[++i]) > 0);
            if (i < j) {
                final AttributeValueHolder t = attrs[i];
                attrs[i] = attrs[j];
                attrs[j] = t;
            }
            else {
                return j;
            }
        }
    }

    public static class AttributeValueHolder implements Comparable {
        public final String qName;
        public final String localName;
        public final String uri;
        public final String type;
        public final String value;

        public AttributeValueHolder(String qName,
            String localName,
            String uri,
            String type,
            String value)
        {
            this.qName = qName;
            this.localName = localName;
            this.uri = uri;
            this.type = type;
            this.value = value;
        }

        public int compareTo(Object o) {
            try {
                return qName.compareTo(((AttributeValueHolder) o).qName);
            }
            catch (Exception e) {
                throw new RuntimeException(CommonResourceBundle.getInstance().getString("message.AttributeValueHolderExpected"));
            }
        }
    }

}
