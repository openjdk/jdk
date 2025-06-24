/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import jdk.jpackage.internal.model.WinApplication;
import jdk.jpackage.internal.model.WinLauncher;
import jdk.jpackage.internal.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class WixLauncherAsService extends LauncherAsService {

    WixLauncherAsService(WinApplication app, WinLauncher launcher, Function<String, OverridableResource> createResource) {
        super(app, launcher,
                createResource.apply("service-install.wxi").setCategory(
                        I18N.getString("resource.launcher-as-service-wix-file")));

        serviceConfigResource = createResource.apply("service-config.wxi").setCategory(
                I18N.getString("resource.launcher-as-service-wix-file"));

        addSubstitutionDataEntry("SERVICE_NAME", getName());

        setPublicName(getResource());
        setPublicName(serviceConfigResource);
    }

    WixLauncherAsService setLauncherInstallPath(String v) {
        return addSubstitutionDataEntry("APPLICATION_LAUNCHER", v);
    }

    WixLauncherAsService setLauncherInstallPathId(String v) {
        return addSubstitutionDataEntry("APPLICATION_LAUNCHER_ID", v);
    }

    void writeServiceConfig(XMLStreamWriter xml) throws XMLStreamException,
            IOException {
        writeResource(serviceConfigResource, xml);
    }

    void writeServiceInstall(XMLStreamWriter xml) throws XMLStreamException,
            IOException {
        writeResource(getResource(), xml);
    }

    private WixLauncherAsService addSubstitutionDataEntry(String name,
            String value) {
        getResource().addSubstitutionDataEntry(name, value);
        serviceConfigResource.addSubstitutionDataEntry(name, value);
        return this;
    }

    private OverridableResource setPublicName(OverridableResource r) {
        return r.setPublicName(getName() + "-" + r.getDefaultName());
    }

    private void writeResource(OverridableResource resource, XMLStreamWriter xml)
            throws XMLStreamException, IOException {
        var buffer = new ByteArrayOutputStream();
        resource.saveToStream(buffer);

        try {
            Document doc = XmlUtils.initDocumentBuilder().parse(
                    new ByteArrayInputStream(buffer.toByteArray()));

            XPath xPath = XPathFactory.newInstance().newXPath();

            NodeList nodes = (NodeList) xPath.evaluate("/Include/*", doc,
                    XPathConstants.NODESET);

            List<Source> sources = new ArrayList<>();
            for (int i = 0; i != nodes.getLength(); i++) {
                Node n = nodes.item(i);
                sources.add(new DOMSource(n));
            }

            XmlUtils.mergeXmls(xml, sources);

        } catch (SAXException ex) {
            throw new IOException(ex);
        } catch (XPathExpressionException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }
    }

    private final OverridableResource serviceConfigResource;
}
