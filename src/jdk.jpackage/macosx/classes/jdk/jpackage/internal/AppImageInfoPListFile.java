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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.XmlUtils.initDocumentBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import jdk.jpackage.internal.model.DottedVersion;

/**
 * Mandatory elements of Info.plist file of app image.
 */
record AppImageInfoPListFile(String bundleIdentifier, String bundleName, String copyright, 
        DottedVersion shortVersion, DottedVersion bundleVersion, String category) {

    static final class InvalidPlistFileException extends Exception {
        InvalidPlistFileException(Throwable cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }

    static AppImageInfoPListFile loadFromInfoPList(Path infoPListFile) 
            throws IOException, InvalidPlistFileException, SAXException {
        final var doc = initDocumentBuilder().parse(Files.newInputStream(infoPListFile));

        XPath xPath = XPathFactory.newInstance().newXPath();

        try {
            return new AppImageInfoPListFile(
                    getStringValue(doc, xPath, "CFBundleIdentifier"),
                    getStringValue(doc, xPath, "CFBundleName"),
                    getStringValue(doc, xPath, "NSHumanReadableCopyright"),
                    DottedVersion.greedy(getStringValue(doc, xPath, "CFBundleShortVersionString")),
                    DottedVersion.greedy(getStringValue(doc, xPath, "CFBundleVersion")),
                    getStringValue(doc, xPath, "LSApplicationCategoryType"));
        } catch (XPathExpressionException ex) {
            // This should never happen as XPath expressions should be correct
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            throw new InvalidPlistFileException(ex);
        }
    }

    private static String getStringValue(Document doc, XPath xPath, String elementName) throws XPathExpressionException {
        // Query for the value of <string> element preceding <key>
        // element with value equal to the value of `elementName`
        return (String) xPath.evaluate(String.format("//string[preceding-sibling::key = \"%s\"][1]", elementName), 
                doc, XPathConstants.STRING);
    }
}
