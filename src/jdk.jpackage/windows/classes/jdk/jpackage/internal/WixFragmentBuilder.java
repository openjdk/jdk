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

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.IOUtils.XmlConsumer;
import jdk.jpackage.internal.OverridableResource.Source;
import static jdk.jpackage.internal.StandardBundlerParam.CONFIG_ROOT;
import jdk.internal.util.Architecture;
import static jdk.jpackage.internal.OverridableResource.createResource;
import jdk.jpackage.internal.WixSourceConverter.ResourceGroup;
import jdk.jpackage.internal.WixToolset.WixToolsetType;

/**
 * Creates WiX fragment.
 */
abstract class WixFragmentBuilder {

    final void setWixVersion(DottedVersion version, WixToolsetType type) {
        Objects.requireNonNull(version);
        Objects.requireNonNull(type);
        wixVersion = version;
        wixType = type;
    }

    final void setOutputFileName(String v) {
        outputFileName = v;
    }

    void initFromParams(Map<String, ? super Object> params) {
        wixVariables = null;
        additionalResources = null;
        configRoot = CONFIG_ROOT.fetchFrom(params);
        fragmentResource = createResource(outputFileName, params).setSourceOrder(
                Source.ResourceDir);
    }

    List<String> getLoggableWixFeatures() {
        return List.of();
    }

    void configureWixPipeline(WixPipeline wixPipeline) {
        wixPipeline.addSource(configRoot.resolve(outputFileName),
                Optional.ofNullable(wixVariables).map(WixVariables::getValues).orElse(
                        null));
    }

    void addFilesToConfigRoot() throws IOException {
        Path fragmentPath = configRoot.resolve(outputFileName);
        if (fragmentResource.saveToFile(fragmentPath) == null) {
            createWixSource(fragmentPath, xml -> {
                for (var fragmentWriter : getFragmentWriters()) {
                    xml.writeStartElement("Fragment");
                    fragmentWriter.accept(xml);
                    xml.writeEndElement();  // <Fragment>
                }
            });
        }

        if (additionalResources != null) {
            additionalResources.saveResources();
        }
    }

    final WixToolsetType getWixType() {
        return wixType;
    }

    final DottedVersion getWixVersion() {
        return wixVersion;
    }

    protected static enum WixNamespace {
        Default,
        Util;
    }

    final protected Map<WixNamespace, String> getWixNamespaces() {
        switch (wixType) {
            case Wix3 -> {
                return Map.of(WixNamespace.Default,
                        "http://schemas.microsoft.com/wix/2006/wi",
                        WixNamespace.Util,
                        "http://schemas.microsoft.com/wix/UtilExtension");
            }
            case Wix4 -> {
                return Map.of(WixNamespace.Default,
                        "http://wixtoolset.org/schemas/v4/wxs",
                        WixNamespace.Util,
                        "http://wixtoolset.org/schemas/v4/wxs/util");
            }
            default -> {
                throw new IllegalArgumentException();
            }

        }
    }

    static boolean is64Bit() {
        return Architecture.is64bit();
    }

    final protected Path getConfigRoot() {
        return configRoot;
    }

    protected abstract Collection<XmlConsumer> getFragmentWriters();

    final protected void defineWixVariable(String variableName) {
        setWixVariable(variableName, "yes");
    }

    final protected void setWixVariable(String variableName, String variableValue) {
        if (wixVariables == null) {
            wixVariables = new WixVariables();
        }
        wixVariables.setWixVariable(variableName, variableValue);
    }

    final protected void addResource(OverridableResource resource, String saveAsName) {
        if (additionalResources == null) {
            additionalResources = new ResourceGroup(getWixType());
        }
        additionalResources.addResource(resource, configRoot.resolve(saveAsName));
    }

    private void createWixSource(Path file, XmlConsumer xmlConsumer) throws IOException {
        IOUtils.createXml(file, xml -> {
            xml.writeStartElement("Wix");
            for (var ns : getWixNamespaces().entrySet()) {
                switch (ns.getKey()) {
                    case Default ->
                        xml.writeDefaultNamespace(ns.getValue());
                    default ->
                        xml.writeNamespace(ns.getKey().name().toLowerCase(), ns.
                                getValue());
                }
            }

            xmlConsumer.accept((XMLStreamWriter) Proxy.newProxyInstance(
                    XMLStreamWriter.class.getClassLoader(), new Class<?>[]{
                XMLStreamWriter.class}, new WixPreprocessorEscaper(xml)));

            xml.writeEndElement(); // <Wix>
        });
    }

    private static class WixPreprocessorEscaper implements InvocationHandler {

        WixPreprocessorEscaper(XMLStreamWriter target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws
                Throwable {
            switch (method.getName()) {
                case "writeAttribute" -> {
                    Object newArgs[] = new Object[args.length];
                    for (int i = 0; i < args.length - 1; ++i) {
                        newArgs[i] = args[i];
                    }
                    newArgs[args.length - 1] = escape(
                            (CharSequence) args[args.length - 1]);
                    return method.invoke(target, newArgs);
                }
                case "writeCData" -> {
                    target.writeCData(escape((CharSequence) args[0]));
                    return null;
                }
                case "writeCharacters" -> {
                    if (args.length == 3) {
                        // writeCharacters(char[] text, int start, int len)
                        target.writeCharacters(escape(String.copyValueOf(
                                (char[]) args[0], (int) args[1], (int) args[2])));
                    } else {
                        target.writeCharacters(escape((CharSequence) args[0]));
                    }
                    return null;
                }
            }
            return method.invoke(target, args);
        }

        private String escape(CharSequence str) {
            Matcher m = dollarPattern.matcher(str);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, "\\$\\$");
            }
            m.appendTail(sb);
            return sb.toString();
        }

        // Match '$', but don't match $(var.foo)
        private final Pattern dollarPattern = Pattern.compile("\\$(?!\\([^)]*\\))");
        private final XMLStreamWriter target;
    }

    private WixToolsetType wixType;
    private DottedVersion wixVersion;
    private WixVariables wixVariables;
    private ResourceGroup additionalResources;
    private OverridableResource fragmentResource;
    private String outputFileName;
    private Path configRoot;
}
