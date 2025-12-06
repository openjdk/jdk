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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

public class NMTXmlUtils {

    private static final String RESERVED_CURRENT_OF_TEST_SEARCH =
        // get reservedCurrent of Test tag
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
    private static final String GENERAL_STATISTICS_PREINIT_STATE_SEARCH =
        "/nativeMemoryTracking/state/preinitState";
    private static final String GENERAL_STATISTICS_MALLOC_LIMIT_SEARCH =
        "/nativeMemoryTracking/state/mallocLimit";
    private static final String DETAIL_STATISTICS_TABLE_SIZE_SEARCH =
        "/nativeMemoryTracking/state/tableSize";
    private static final String DETAIL_STATISTICS_STACK_DEPTH_SEARCH =
        "/nativeMemoryTracking/state/stackDepth";
    private static final String DETAIL_STATISTICS_SITE_TABLE_SEARCH =
        "/nativeMemoryTracking/state/siteTable";

    private Document doc;

    private static void dropNonXMLParts(File inputXmlFile) throws IOException {
        String fileName = inputXmlFile.getAbsolutePath();
        Path originalPath = Paths.get(fileName);
        Path tempPath = Paths.get(fileName + ".tmp");


        // This flag is used to skip the first line we read
        boolean firstLineSkipped = false;

        BufferedReader reader = new BufferedReader(new FileReader(inputXmlFile));
        PrintWriter writer = new PrintWriter(new FileWriter(tempPath.toFile()));

        String currentLine;

        // Read the file line by line
        while ((currentLine = reader.readLine()) != null) {
            if (!firstLineSkipped) {
                // Skip the first line and set the flag
                firstLineSkipped = true;
                continue; // Skip writing the line
            }

            // Write all subsequent lines to the temporary file
            writer.println(currentLine);
        }
        reader.close();
        writer.close();
        Files.move(tempPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("✅ Successfully removed the first line from " + fileName);
    }

    public NMTXmlUtils(File xmlFile) throws Exception {
        NMTXmlUtils.dropNonXMLParts(xmlFile);
        doc =  DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
    }

    public NMTXmlUtils shouldHaveValue(String xpathSearch, String expectedValue) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();

        String value = (String)xpath.compile(xpathSearch).evaluate(doc, XPathConstants.STRING);
        if (!value.equals(expectedValue)) {
          throw new RuntimeException("Mismatch for " + xpathSearch + " expected " + expectedValue + " got " + value);
        }
        return this;
    }

    public NMTXmlUtils shouldExist(String xpathSearch) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();

        String value = (String)xpath.compile(xpathSearch).evaluate(doc, XPathConstants.STRING);
        if (value.isEmpty()) {
          throw new RuntimeException("No value for " + xpathSearch);
        }
        return this;
    }

    public NMTXmlUtils shouldNotExist(String xpathSearch) throws Exception {
        shouldHaveValue(xpathSearch, "");
        return this;
    }

    public NMTXmlUtils shouldBeReservedCurrentOfTest(String value) throws Exception {
        shouldHaveValue(RESERVED_CURRENT_OF_TEST_SEARCH, value);
        return this;
    }

    public NMTXmlUtils shouldBeReservedDiffOfTest(String value) throws Exception {
        shouldHaveValue(RESERVED_DIFF_OF_TEST_SEARCH, value);
        return this;
    }

    public NMTXmlUtils shouldBeCommittedCurrentOfTest(String value) throws Exception {
        shouldHaveValue(COMMITTED_CURRENT_OF_TEST_SEARCH, value);
        return this;
    }

    public NMTXmlUtils shouldBeCommittedDiffOfTest(String value) throws Exception {
        shouldHaveValue(COMMITTED_DIFF_OF_TEST_SEARCH, value);
        return this;
    }

    public NMTXmlUtils shouldNotExistTestTag() throws Exception {
        shouldNotExist(TEST_TAG_SEARCH);
        return this;
    }

    public NMTXmlUtils shouldBeReportType(String reportType) throws Exception {
        shouldHaveValue(REPORT_TYPE_SEARCH, reportType);
        return this;
    }

    public NMTXmlUtils shouldBeScale(String scale) throws Exception {
        shouldHaveValue(SCALE_ATTR_SEARCH, scale);
        return this;
    }

    public NMTXmlUtils shouldExistClasses() throws Exception {
        shouldExist(CLASSES_COUNT_SEARCH);
        return this;
    }

    public NMTXmlUtils shouldExistInstanceClasses() throws Exception {
        shouldExist(INSTANCE_CLASSES_COUNT_SEARCH);
        return this;
    }

    public NMTXmlUtils shouldExistArrayClasses() throws Exception {
        shouldExist(ARRAY_CLASSES_COUNT_SEARCH);
        return this;
    }

    public NMTXmlUtils shouldExistGeneralStatistics() throws Exception {
        shouldExist(GENERAL_STATISTICS_PREINIT_STATE_SEARCH);
        shouldExist(GENERAL_STATISTICS_MALLOC_LIMIT_SEARCH);
        return this;
    }

    public NMTXmlUtils shouldExistDetailStatistics() throws Exception {
        shouldExist(DETAIL_STATISTICS_TABLE_SIZE_SEARCH);
        shouldExist(DETAIL_STATISTICS_STACK_DEPTH_SEARCH);
        shouldExist(DETAIL_STATISTICS_SITE_TABLE_SEARCH);
        return this;
    }
}
