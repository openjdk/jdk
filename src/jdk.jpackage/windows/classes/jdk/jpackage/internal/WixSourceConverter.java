/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stream.StreamSource;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.internal.resources.ResourceLocator;

/**
 * Converts Wix v3 source file into Wix v4 format.
 */
final class WixSourceConverter {

    WixSourceConverter(String xsltResourceName) throws IOException {
        var xslt = new StreamSource(ResourceLocator.class.getResourceAsStream(xsltResourceName));

        TransformerFactory factory = TransformerFactory.newInstance();
        try {
            this.transformer = factory.newTransformer(xslt);
        } catch (TransformerException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }

        this.outputFactory = XMLOutputFactory.newInstance();
    }

    void appyTo(OverridableResource resource, Path resourceSaveAsFile) throws IOException {
        if (resource.saveToStream(null) != OverridableResource.Source.DefaultResource) {
            // Don't convert external resources
            resource.saveToFile(resourceSaveAsFile);
            return;
        }

        var buf = new ByteArrayOutputStream();
        resource.saveToStream(buf);

        Source input = new StreamSource(new ByteArrayInputStream(buf.toByteArray()));

        try (var outXml = Files.newOutputStream(resourceSaveAsFile)) {
            XMLStreamWriter xmlWriter = (XMLStreamWriter) Proxy.newProxyInstance(
                    XMLStreamWriter.class.getClassLoader(), new Class<?>[]{XMLStreamWriter.class},
                    new NamespaceCleaner(outputFactory.createXMLStreamWriter(outXml)));

            transformer.transform(input, new StAXResult(xmlWriter));

        } catch (TransformerException | XMLStreamException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }
    }

    final static class ResourceGroup {

        ResourceGroup(WixToolsetType wixToolsetType, String xsltResourceName) throws IOException {
            if (wixToolsetType == WixToolsetType.Wix4) {
                // Need to convert internal WiX sources
                conv = new WixSourceConverter(xsltResourceName);
            } else {
                conv = null;
            }
        }

        void addResource(OverridableResource resource, Path resourceSaveAsFile) {
            resources.put(resourceSaveAsFile, resource);
        }

        void saveResources() throws IOException {
            if (conv != null) {
                for (var e : resources.entrySet()) {
                    conv.appyTo(e.getValue(), e.getKey());
                }
            } else {
                for (var e : resources.entrySet()) {
                    e.getValue().saveToFile(e.getKey());
                }
            }
        }

        private final Map<Path, OverridableResource> resources = new HashMap<>();
        private final WixSourceConverter conv;
    }

    //
    //    Default JDK XSLT v1.0 processor is not handling well default namespace mappings.
    //    Running generic template:
    //
    //    <xsl:template match="wix3loc:*">
    //      <xsl:element name="{local-name()}" namespace="http://wixtoolset.org/schemas/v4/wxl">
    //        <xsl:apply-templates select="@*|node()"/>
    //      </xsl:element>
    //    </xsl:template>
    //
    //    produces:
    //
    //    <ns0:WixLocalization xmlns:ns0="http://wixtoolset.org/schemas/v4/wxl" Culture="en-us" Codepage="1252">
    //      <ns1:String xmlns:ns1="http://wixtoolset.org/schemas/v4/wxl" Value="The folder [INSTALLDIR] already exist. Would you like to install to that folder anyway?" Id="message.install.dir.exist"/>
    //      <ns2:String xmlns:ns2="http://wixtoolset.org/schemas/v4/wxl" Value="Main Feature" Id="MainFeatureTitle"/>
    //      ...
    //      <ns12:String xmlns:ns12="http://wixtoolset.org/schemas/v4/wxl" Value="Open with [ProductName]" Id="ContextMenuCommandLabel"/>
    //    </ns0:WixLocalization>
    //
    //    which is conformant XML but WiX4 doesn't like it:
    //
    //    wix.exe : error WIX0202: The {http://wixtoolset.org/schemas/v4/wxl}String element contains an unsupported extension attribute '{http://www.w3.org/2000/xmlns/}ns1'. The {http://wixtoolset.org/schemas/v4/wxl}String element does not currently support extension attributes. Is the {http://www.w3.org/2000/xmlns/}ns1 attribute using the correct XML namespace?
    //    wix.exe : error WIX0202: The {http://wixtoolset.org/schemas/v4/wxl}String element contains an unsupported extension attribute '{http://www.w3.org/2000/xmlns/}ns2'. The {http://wixtoolset.org/schemas/v4/wxl}String element does not currently support extension attributes. Is the {http://www.w3.org/2000/xmlns/}ns2 attribute using the correct XML namespace?
    //    wix.exe : error WIX0202: The {http://wixtoolset.org/schemas/v4/wxl}String element contains an unsupported extension attribute '{http://www.w3.org/2000/xmlns/}ns3'. The {http://wixtoolset.org/schemas/v4/wxl}String element does not currently support extension attributes. Is the {http://www.w3.org/2000/xmlns/}ns3 attribute using the correct XML namespace?
    //
    //    Someone hit this issue long ago - https://stackoverflow.com/questions/26904623/replace-default-namespace-using-xsl and they suggested to use different XSLT processor.
    //    Two online XSLT processors used in testing produce clean XML with this template indeed:
    //
    //    <WixLocalization xmlns="http://wixtoolset.org/schemas/v4/wxl" Codepage="1252" Culture="en-us">
    //      <String Value="The folder [INSTALLDIR] already exist. Would you like to install to that folder anyway?" Id="message.install.dir.exist"/>
    //      <String Value="Main Feature" Id="MainFeatureTitle"/>
    //      ...
    //      <String Value="Open with [ProductName]" Id="ContextMenuCommandLabel"/>
    //    </WixLocalization>
    //
    //    To workaround default JDK's XSLT processor limitations we do additionl postprocessing of output XML with NamespaceCleaner class.
    //
    private static class NamespaceCleaner implements InvocationHandler {

        NamespaceCleaner(XMLStreamWriter target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "setPrefix" -> {
                    if (defaultNamespace == null) {
                        defaultNamespace = (String)args[1];
                        target.setDefaultNamespace(defaultNamespace);
                    }
                }
                case "writeNamespace" -> {
                    if (!rootElementProcessed) {
                        rootElementProcessed = true;
                        target.writeDefaultNamespace(defaultNamespace);
                    }
                }
                case "writeStartElement", "writeEmptyElement" -> {
                    final String name;
                    switch (args.length) {
                        case 1 -> name = (String)args[0];
                        case 2, 3 -> name = (String)args[1];
                        default -> throw new IllegalArgumentException();
                    }

                    final String localName;
                    final String[] tokens = name.split(":", 2);
                    if (tokens.length == 2) {
                        // The name has a prefix
                        localName = tokens[1];
                    } else {
                        localName = name;
                    }

                    if (method.getName().equals("writeStartElement")) {
                        target.writeStartElement(localName);
                    } else {
                        target.writeEmptyElement(localName);
                    }
                }
                default -> method.invoke(target, args);
            }
            return null;
        }

        private boolean rootElementProcessed;
        private String defaultNamespace;
        private final XMLStreamWriter target;
    }

    private final Transformer transformer;
    private final XMLOutputFactory outputFactory;
}
