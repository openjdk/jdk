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
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stream.StreamSource;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Converts WiX v3 source file into WiX v4 format.
 */
final class WixSourceConverter {

    enum Status {
        SavedAsIs,
        SavedAsIsMalfromedXml,
        Transformed,
    }

    WixSourceConverter(Path resourceDir) throws IOException {
        var buf = new ByteArrayOutputStream();

        new OverridableResource("wix3-to-wix4-conv.xsl")
                .setPublicName("wix-conv.xsl")
                .setResourceDir(resourceDir)
                .setCategory(I18N.getString("resource.wix-src-conv"))
                .saveToStream(buf);

        var xslt = new StreamSource(new ByteArrayInputStream(buf.toByteArray()));

        var tf = TransformerFactory.newInstance();
        try {
            this.transformer = tf.newTransformer(xslt);
        } catch (TransformerException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }

        this.outputFactory = XMLOutputFactory.newInstance();
    }

    Status appyTo(OverridableResource resource, Path resourceSaveAsFile) throws IOException {
        // Save the resource into DOM tree and read xml namespaces from it.
        // If some namespaces are not recognized by this converter, save the resource as is.
        // If all detected namespaces are recognized, run transformation of the DOM tree and save
        // output into destination file.

        var buf = saveResourceInMemory(resource);

        Document inputXmlDom;
        try {
            inputXmlDom = IOUtils.initDocumentBuilder().parse(new ByteArrayInputStream(buf));
        } catch (SAXException ex) {
            // Malformed XML, don't run converter, save as is.
            resource.saveToFile(resourceSaveAsFile);
            return Status.SavedAsIsMalfromedXml;
        }

        try {
            var nc = new NamespaceCollector();
            TransformerFactory.newInstance().newTransformer().
                    transform(new DOMSource(inputXmlDom), new StAXResult((XMLStreamWriter) Proxy.
                            newProxyInstance(XMLStreamWriter.class.getClassLoader(),
                                    new Class<?>[]{XMLStreamWriter.class}, nc)));
            if (!nc.isOnlyKnownNamespacesUsed()) {
                // Unsupported namespaces detected in input XML, don't run converter, save as is.
                resource.saveToFile(resourceSaveAsFile);
                return Status.SavedAsIs;
            }
        } catch (TransformerException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }

        Supplier<Source> inputXml = () -> {
            // Should be "new DOMSource(inputXmlDom)", but no transfromation is applied in this case!
            return new StreamSource(new ByteArrayInputStream(buf));
        };

        var nc = new NamespaceCollector();
        try {
            // Run transfomation to collect namespaces from the output XML.
            transformer.transform(inputXml.get(), new StAXResult((XMLStreamWriter) Proxy.
                    newProxyInstance(XMLStreamWriter.class.getClassLoader(),
                            new Class<?>[]{XMLStreamWriter.class}, nc)));
        } catch (TransformerException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }

        try (var outXml = new ByteArrayOutputStream()) {
            transformer.transform(inputXml.get(), new StAXResult((XMLStreamWriter) Proxy.
                    newProxyInstance(XMLStreamWriter.class.getClassLoader(),
                            new Class<?>[]{XMLStreamWriter.class}, new NamespaceCleaner(nc.
                                    getPrefixToUri(), outputFactory.createXMLStreamWriter(outXml)))));
            Files.createDirectories(IOUtils.getParent(resourceSaveAsFile));
            Files.copy(new ByteArrayInputStream(outXml.toByteArray()), resourceSaveAsFile,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (TransformerException | XMLStreamException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }

        return Status.Transformed;
    }

    private static byte[] saveResourceInMemory(OverridableResource resource) throws IOException {
        var buf = new ByteArrayOutputStream();
        resource.saveToStream(buf);
        return buf.toByteArray();
    }

    final static class ResourceGroup {

        ResourceGroup(WixToolsetType wixToolsetType) {
            this.wixToolsetType = wixToolsetType;
        }

        void addResource(OverridableResource resource, Path resourceSaveAsFile) {
            resources.put(resourceSaveAsFile, resource);
        }

        void saveResources() throws IOException {
            switch (wixToolsetType) {
                case Wix3 -> {
                    for (var e : resources.entrySet()) {
                        e.getValue().saveToFile(e.getKey());
                    }
                }
                case Wix4 -> {
                    var resourceDir = resources.values().stream().filter(res -> {
                        return null != res.getResourceDir();
                    }).findAny().map(OverridableResource::getResourceDir).orElse(null);
                    var conv = new WixSourceConverter(resourceDir);
                    for (var e : resources.entrySet()) {
                        conv.appyTo(e.getValue(), e.getKey());
                    }
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        }

        private final Map<Path, OverridableResource> resources = new HashMap<>();
        private final WixToolsetType wixToolsetType;
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

        NamespaceCleaner(Map<String, String> prefixToUri, XMLStreamWriter target) {
            this.uriToPrefix = prefixToUri.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getValue, e -> {
                        return new Prefix(e.getKey());
                    }, (x, y) -> x));
            this.prefixToUri = prefixToUri;
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "writeNamespace" -> {
                    final String uri = (String) args[1];
                    var prefixObj = uriToPrefix.get(uri);
                    if (!prefixObj.written) {
                        prefixObj.written = true;
                        target.writeNamespace(prefixObj.name, uri);
                    }
                    return null;
                }
                case "writeStartElement", "writeEmptyElement" -> {
                    final String name;
                    switch (args.length) {
                        case 1 ->
                            name = (String) args[0];
                        case 2, 3 ->
                            name = (String) args[1];
                        default ->
                            throw new IllegalArgumentException();
                    }

                    final String prefix;
                    final String localName;
                    final String[] tokens = name.split(":", 2);
                    if (tokens.length == 2) {
                        prefix = tokens[0];
                        localName = tokens[1];
                    } else {
                        localName = name;
                        switch (args.length) {
                            case 3 ->
                                prefix = (String) args[0];
                            case 2 ->
                                prefix = uriToPrefix.get((String) args[0]).name;
                            default ->
                                prefix = null;
                        }
                    }

                    if (prefix != null && !XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
                        final String uri = prefixToUri.get(prefix);
                        var prefixObj = uriToPrefix.get(uri);
                        if (prefixObj.written) {
                            var writeName = String.join(":", prefixObj.name, localName);
                            if ("writeStartElement".equals(method.getName())) {
                                target.writeStartElement(writeName);
                            } else {
                                target.writeEmptyElement(writeName);
                            }
                            return null;
                        } else {
                            prefixObj.written = (args.length > 1);
                            args = Arrays.copyOf(args, args.length, Object[].class);
                            if (localName.equals(name)) {
                                // No prefix in the name
                                if (args.length == 3) {
                                    args[0] = prefixObj.name;
                                }
                            } else {
                                var writeName = String.join(":", prefixObj.name, localName);
                                switch (args.length) {
                                    case 1 ->
                                        args[0] = writeName;
                                    case 2 -> {
                                        args[0] = uri;
                                        args[1] = writeName;
                                    }
                                    case 3 -> {
                                        args[0] = prefixObj.name;
                                        args[1] = writeName;
                                        args[2] = uri;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return method.invoke(target, args);
        }

        static class Prefix {

            Prefix(String name) {
                this.name = name;
            }

            private final String name;
            private boolean written;
        }

        private final Map<String, Prefix> uriToPrefix;
        private final Map<String, String> prefixToUri;
        private final XMLStreamWriter target;
    }

    private static class NamespaceCollector implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "setPrefix", "writeNamespace" -> {
                    var prefix = (String) args[0];
                    var namespace = prefixToUri.computeIfAbsent(prefix, k -> createValue(args[1]));
                    if (XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)) {
                        namespace.setValue(true);
                    }
                }
                case "writeStartElement", "writeEmptyElement" -> {
                    switch (args.length) {
                        case 3 ->
                            prefixToUri.computeIfAbsent((String) args[0], k -> createValue(
                                    (String) args[2])).setValue(true);
                        case 2 ->
                            initFromElementName((String) args[1], (String) args[0]);
                        case 1 ->
                            initFromElementName((String) args[0], null);
                    }
                }
            }
            return null;
        }

        boolean isOnlyKnownNamespacesUsed() {
            return prefixToUri.values().stream().filter(namespace -> {
                return namespace.getValue();
            }).allMatch(namespace -> {
                if (!namespace.getValue()) {
                    return true;
                } else {
                    return KNOWN_NAMESPACES.contains(namespace.getKey());
                }
            });
        }

        Map<String, String> getPrefixToUri() {
            return prefixToUri.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> {
                        return e.getValue().getKey();
                    }));
        }

        private void initFromElementName(String name, String namespace) {
            final String[] tokens = name.split(":", 2);
            if (tokens.length == 2) {
                if (namespace != null) {
                    prefixToUri.computeIfAbsent(tokens[0], k -> createValue(namespace)).setValue(
                            true);
                } else {
                    prefixToUri.computeIfPresent(tokens[0], (k, v) -> {
                        v.setValue(true);
                        return v;
                    });
                }
            }
        }

        private Map.Entry<String, Boolean> createValue(Object prefix) {
            return new AbstractMap.SimpleEntry<String, Boolean>((String) prefix, false);
        }

        private final Map<String, Map.Entry<String, Boolean>> prefixToUri = new HashMap<>();
    }

    private final Transformer transformer;
    private final XMLOutputFactory outputFactory;

    // The list of WiX v3 namespaces this converter can handle
    private final static Set<String> KNOWN_NAMESPACES = Set.of(
            "http://schemas.microsoft.com/wix/2006/localization",
            "http://schemas.microsoft.com/wix/2006/wi");
}
