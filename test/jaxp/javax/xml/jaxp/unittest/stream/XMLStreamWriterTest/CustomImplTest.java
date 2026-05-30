/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

package stream.XMLStreamWriterTest;

import org.junit.jupiter.api.Test;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stax.StAXResult;

/*
 * @test
 * @bug 8230094
 * @run junit stream.XMLStreamWriterTest.CustomImplTest
 * @summary Verifies custom implementation of the XMLStreamWriter.
 */
public class CustomImplTest {

    @Test
    public void testEventReader() throws Exception {
        XMLOutputFactory.newFactory()
                .createXMLEventWriter(new StAXResult(new StreamWriterFilter()));
    }

    static class StreamWriterFilter implements XMLStreamWriter
    {
        @Override
        public void writeStartElement(String localName) {
        }

        @Override
        public void writeStartElement(String namespaceURI, String localName) {
        }

        @Override
        public void writeStartElement(
                String prefix, String localName, String namespaceURI) {
        }

        @Override
        public void writeEmptyElement(String namespaceURI, String localName) {
        }

        @Override
        public void writeEmptyElement(
                String prefix, String localName, String namespaceURI) {
        }

        @Override
        public void writeEmptyElement(String localName) {
        }

        @Override
        public void writeEndElement() {
        }

        @Override
        public void writeEndDocument() {
        }

        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }

        @Override
        public void writeAttribute(String localName, String value) {
        }

        @Override
        public void writeAttribute(
                String prefix, String namespaceURI, String localName, String value) {
        }

        @Override
        public void writeAttribute(
                String namespaceURI, String localName, String value) {
        }

        @Override
        public void writeNamespace(String prefix, String namespaceURI) {
        }

        @Override
        public void writeDefaultNamespace(String namespaceURI) {
        }

        @Override
        public void writeComment(String data) {
        }

        @Override
        public void writeProcessingInstruction(String target) {
        }

        @Override
        public void writeProcessingInstruction(String target, String data) {
        }

        @Override
        public void writeCData(String data) {
        }

        @Override
        public void writeDTD(String dtd) {
        }

        @Override
        public void writeEntityRef(String name) {
        }

        @Override
        public void writeStartDocument() {
        }

        @Override
        public void writeStartDocument(String version) {
        }

        @Override
        public void writeStartDocument(String encoding, String version) {
        }

        @Override
        public void writeCharacters(String text) {
        }

        @Override
        public void writeCharacters(char[] text, int start, int len) {
        }

        @Override
        public String getPrefix(String uri) {
            return null;
        }

        @Override
        public void setPrefix(String prefix, String uri) {
        }

        @Override
        public void setDefaultNamespace(String uri) {
        }

        @Override
        public void setNamespaceContext(NamespaceContext context) {
        }

        @Override
        public NamespaceContext getNamespaceContext()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getProperty(String name) throws IllegalArgumentException
        {
            throw new UnsupportedOperationException();
        }
    }

}
