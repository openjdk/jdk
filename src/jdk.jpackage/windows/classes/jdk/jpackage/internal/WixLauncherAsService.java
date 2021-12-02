/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import static jdk.jpackage.internal.OverridableResource.createResource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class WixLauncherAsService extends LauncherAsService {

    WixLauncherAsService(String name, Map<String, ? super Object> mainParams) {
        super(name, mainParams,
                createResource("service.wxi", mainParams).setCategory(
                        I18N.getString("resource.launcher-as-service-wix-file")));

        String publicName = getName() + "-" + "service.wxi";

        getResource()
                .setPublicName(publicName)
                .addSubstitutionDataEntry("SERVICE_NAME", getName());
    }

    void apply(String launcherPathId, XMLStreamWriter xml) throws
            XMLStreamException, IOException {
        var buffer = new ByteArrayOutputStream();
        getResource()
                .addSubstitutionDataEntry("SERVICE_INSTALL_ID", "svi_"
                        + launcherPathId)
                .addSubstitutionDataEntry("SERVICE_CONTROL_ID", "svc_"
                        + launcherPathId)
                .saveToStream(buffer);

        xml = (XMLStreamWriter) Proxy.newProxyInstance(
                XMLStreamWriter.class.getClassLoader(), new Class<?>[]{
            XMLStreamWriter.class}, new SkipDocumentHandler(xml));

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
            dbf.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            DocumentBuilder b = dbf.newDocumentBuilder();
            Document doc = b.parse(
                    new ByteArrayInputStream(buffer.toByteArray()));

            TransformerFactory tf = TransformerFactory.newInstance();
            Result result = new StAXResult(xml);

            XPath xPath = XPathFactory.newInstance().newXPath();

            NodeList nodes = (NodeList) xPath.evaluate("/Include/*", doc,
                    XPathConstants.NODESET);
            for (int i = 0; i != nodes.getLength(); i++) {
                Node n = nodes.item(i);
                Source src = new DOMSource(n);
                tf.newTransformer().transform(src, result);
            }
        } catch (SAXException ex) {
            throw new IOException(ex);
        } catch (XPathExpressionException | ParserConfigurationException
                | TransformerException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }
    }

    private static class SkipDocumentHandler implements InvocationHandler {

        SkipDocumentHandler(XMLStreamWriter target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws
                Throwable {
            switch (method.getName()) {
                case "writeStartDocument", "writeEndDocument" -> {
                }
                default -> method.invoke(target, args);
            }
            return null;
        }

        private final XMLStreamWriter target;
    }
}
