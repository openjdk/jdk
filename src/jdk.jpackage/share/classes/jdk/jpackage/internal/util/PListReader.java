/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class PListReader {

    public String queryValue(String keyName) {
        final var node = getNode(keyName);
        switch (node.getNodeName()) {
            case "string" -> {
                return node.getTextContent();
            }
            default -> {
                throw new NoSuchElementException();
            }
        }
    }

    public boolean queryBoolValue(String keyName) {
        final var node = getNode(keyName);
        switch (node.getNodeName()) {
            case "true" -> {
                return true;
            }
            case "false" -> {
                return false;
            }
            default -> {
                throw new NoSuchElementException();
            }
        }
    }

    public List<String> queryArrayValue(String keyName) {
        final var node = getNode(keyName);
        switch (node.getNodeName()) {
            case "array" -> {
                return XmlUtils.toStream(node.getChildNodes()).filter(n -> {
                    return n.getNodeName().equals("string");
                }).map(Node::getTextContent).toList();
            }
            default -> {
                throw new NoSuchElementException();
            }
        }
    }

    public PListReader(Node doc) {
        this.root = Objects.requireNonNull(doc);
    }

    public PListReader(byte[] xmlData) throws ParserConfigurationException, SAXException, IOException {
        this(XmlUtils.initDocumentBuilder().parse(new ByteArrayInputStream(xmlData)));
    }

    private Node getNode(String keyName) {
        final var xPath = XPathFactory.newInstance().newXPath();
        final var query = String.format("//*[preceding-sibling::key = \"%s\"][1]", keyName);
        return Optional.ofNullable(ThrowingSupplier.toSupplier(() -> {
            return (Node) xPath.evaluate(query, root, XPathConstants.NODE);
        }).get()).orElseThrow(NoSuchElementException::new);
    }

    private final Node root;
}
