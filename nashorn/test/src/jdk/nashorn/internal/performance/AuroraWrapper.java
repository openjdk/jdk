/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.performance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class AuroraWrapper {

    public static String fileName = "report.xml";

    public static void deleteReportDocument() {
        final File f = new File(fileName);
        if (f.exists()) {
            f.delete();
        }
    }

    public static Document createOrOpenDocument() throws ParserConfigurationException, SAXException, IOException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder documentBuilder = factory.newDocumentBuilder();

        Document document;
        final File f = new File(fileName);
        if (!f.exists()) {
            document = documentBuilder.newDocument();
            final Element root = document.createElement("entity");
            document.appendChild(root);
            root.setAttribute("type", "REFWORKLOADRUN");
            root.setAttribute("name", "default");
        } else {
            document = documentBuilder.parse(f);
        }

        return document;
    }

    public static void addBenchmarkResults(final Document doc, final Element root, final String name, final String score, final String higherBetter) {
        final Element results = addEntity(doc, root, name, "BENCHMARK_RESULTS");
        addAttribute(doc, results, "benchmark", name);
        addAttribute(doc, results, "is_higher_better", higherBetter);

        final Element iteration = addEntity(doc, results, "1", "ITERATION");
        addAttribute(doc, iteration, "score", score);
        addAttribute(doc, iteration, "successful", "true");

        addConfig(doc, results, name);
    }

    public static Element getRootEntity(final org.w3c.dom.Document doc) {
        final Element rootEntity = doc.getDocumentElement();
        Element resultsEntity = null;

        final NodeList entities = rootEntity.getChildNodes();
        for (int i = 0; i < entities.getLength(); i++) {
            if (entities.item(i).getNodeName().equals("entity")) {
                resultsEntity = (Element)entities.item(i);
                break;
            }
        }

        if (resultsEntity == null) {
            resultsEntity = addResults(doc);
        }
        //System.out.println(resultsEntity);
        return resultsEntity;
    }

    public static void addAttribute(final Document doc, final Element entity, final String attributeName, final String attributeValue) {
        final Element attr = doc.createElement("attribute");
        entity.appendChild(attr);
        attr.setAttribute("name", attributeName);
        attr.setTextContent(attributeValue);
    }

    public static Element addEntity(final Document doc, final Element entity, final String entityName, final String entityType) {
        final Element newEntity = doc.createElement("entity");
        entity.appendChild(newEntity);

        if (entityType != null) {
            newEntity.setAttribute("type", entityType);
        }
        if (entityName != null) {
            newEntity.setAttribute("name", entityName);
        }
        return newEntity;
    }

    public static Element addResults(final Document doc) {

        String _benchmark = "nashorn-octaneperf";

        final String vmName = java.lang.System.getProperties().getProperty("java.vm.name");
        try {
            String vmType;
            if (vmName.toLowerCase().contains("client")) {
                vmType = "client";
            } else {
                vmType = "server";
            }
            _benchmark += "-" + vmType;
        } catch (final Exception e) {
            // In case VM name has different format
        }

        final Element root = doc.getDocumentElement();

        final Element result = doc.createElement("entity");

        root.appendChild(result);
        result.setAttribute("name", _benchmark);
        result.setAttribute("type", "BENCHMARK_RESULTS");

        addAttribute(doc, result, "benchmark", _benchmark);
        addAttribute(doc, result, "score", "0");
        addAttribute(doc, result, "mean", "0");
        addAttribute(doc, result, "stdev", "0");
        addAttribute(doc, result, "var", "0");
        addAttribute(doc, result, "attempts", "1");
        addAttribute(doc, result, "successes", "1");
        addAttribute(doc, result, "failures", "0");
        addAttribute(doc, result, "jvmOptions", "");
        addAttribute(doc, result, "is_workload", "0");
        addAttribute(doc, result, "is_higher_better", "1");

        addConfig(doc, result, _benchmark);

        final Element iteration = addEntity(doc, result, "1", "ITERATION");
        addAttribute(doc, iteration, "score", "0");
        addAttribute(doc, iteration, "successful", "true");

        return result;
    }

    public static void addConfig(final Document doc, final Element result, final String _benchmark) {
        final Element config = addEntity(doc, result, "default", "BENCHMARK_CONFIG");
        addAttribute(doc, config, "settings", "benchmarks=" + _benchmark + "\ncomponent=j2se\niterations=1\n");
        addAttribute(doc, config, "info", "");
    }

    public static void addResults(final Document doc, final String _benchmark, final String _score) throws TransformerConfigurationException, TransformerException, IOException {
        final Element result = getRootEntity(doc);

        addBenchmarkResults(doc, result, _benchmark, _score, "1");

        final TransformerFactory tranformerFactory = TransformerFactory.newInstance();
        final Transformer tr = tranformerFactory.newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            tr.transform(new DOMSource(doc), new StreamResult(fos));
        }
    }

    /**
     * Test
     */
    @SuppressWarnings("UseSpecificCatch")
    public static void main(final String... args) {
        try {
            deleteReportDocument();
            Document document = createOrOpenDocument();
            addResults(document, "benchmark1", "0.01");
            document = createOrOpenDocument();
            addResults(document, "benchmark2", "0.02");
            document = createOrOpenDocument();
            addResults(document, "benchmark3", "0.03");

            final TransformerFactory tranformerFactory = TransformerFactory.newInstance();
            final Transformer tr = tranformerFactory.newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.transform(new DOMSource(document), new StreamResult(System.out));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
