/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

public class NMTXmlUtils {

    private static final String RESERVED_CURRENT_OF_TEST_SEARCH =
        "/nativeMemoryTracking/memoryTag[name[text() = 'Test']]/vmDiff/reservedCurrent/text()";
    private static final String RESERVED_DIFF_OF_TEST_SEARCH =
        "/nativeMemoryTracking/memoryTag[name[text() = 'Test']]/vmDiff/reservedDiff/text()";
    private static final String COMMITTED_CURRENT_OF_TEST_SEARCH =
        "/nativeMemoryTracking/memoryTag[name[text() = 'Test']]/vmDiff/committedCurrent/text()";
    private static final String COMMITTED_DIFF_OF_TEST_SEARCH =
        "/nativeMemoryTracking/memoryTag[name[text() = 'Test']]/vmDiff/committedDiff/text()";
    private static final String TEST_TAG_SEARCH =
        "/nativeMemoryTracking/memoryTag[name[text() = 'Test']]";
    private static final String SCALE_ATTR_SEARCH =
        "/nativeMemoryTracking/@scale";
    private static final String REPORT_TYPE_SEARCH =
        "/nativeMemoryTracking/report/text()";
    private static final String CLASSES_COUNT_SEARCH =
        "/nativeMemoryTracking/memoryTags/memoryTag/classes/text()";
    private static final String INSTANCE_CLASSES_COUNT_SEARCH =
        "/nativeMemoryTracking/memoryTags/memoryTag/instanceClasses/text()";
    private static final String ARRAY_CLASSES_COUNT_SEARCH =
        "/nativeMemoryTracking/memoryTags/memoryTag/arrayClasses/text()";

    private Document doc;

    public NMTXmlUtils(File xmlFile) throws Exception {
        doc =  DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
    }

    public void shouldHaveValue(String xpathSearch, String expectedValue) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();

        String value = (String)xpath.compile(xpathSearch).evaluate(doc, XPathConstants.STRING);
        if (!value.equals(expectedValue)) {
          throw new RuntimeException("Mismatch for " + xpathSearch + " expected " + expectedValue + " got " + value);
        }
    }

    public void shouldExist(String xpathSearch) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();

        String value = (String)xpath.compile(xpathSearch).evaluate(doc, XPathConstants.STRING);
        if (value.isEmpty()) {
          throw new RuntimeException("No value for " + xpathSearch);
        }
    }

    public void shouldNotExist(String xpathSearch) throws Exception {
        shouldHaveValue(xpathSearch, "");
    }

    public void shouldBeReservedCurrentOfTest(String value) throws Exception {
        shouldHaveValue(RESERVED_CURRENT_OF_TEST_SEARCH, value);
    }

    public void shouldBeReservedDiffOfTest(String value) throws Exception {
        shouldHaveValue(RESERVED_DIFF_OF_TEST_SEARCH, value);
    }

    public void shouldBeCommittedCurrentOfTest(String value) throws Exception {
        shouldHaveValue(COMMITTED_CURRENT_OF_TEST_SEARCH, value);
    }

    public void shouldBeCommittedDiffOfTest(String value) throws Exception {
        shouldHaveValue(COMMITTED_DIFF_OF_TEST_SEARCH, value);
    }

    public void shouldNotExistTestTag() throws Exception {
        shouldNotExist(TEST_TAG_SEARCH);
    }

    public void shouldBeReportType(String reportType) throws Exception {
        shouldHaveValue(REPORT_TYPE_SEARCH, reportType);
    }

    public void shouldBeScale(String scale) throws Exception {
        shouldHaveValue(SCALE_ATTR_SEARCH, scale);
    }

    public void shouldExistClasses() throws Exception {
        shouldExist(CLASSES_COUNT_SEARCH);
    }

    public void shouldExistInstanceClasses() throws Exception {
        shouldExist(INSTANCE_CLASSES_COUNT_SEARCH);
    }

    public void shouldExistArrayClasses() throws Exception {
      shouldExist(ARRAY_CLASSES_COUNT_SEARCH);
    }
}
