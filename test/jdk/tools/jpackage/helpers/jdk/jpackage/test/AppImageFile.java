/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public record AppImageFile(String mainLauncherName, String mainLauncherClassName,
        String version, boolean macSigned, boolean macAppStore, Map<String, Map<String, String>> launchers) {

    public static Path getPathInAppImage(Path appImageDir) {
        return ApplicationLayout.platformAppImage()
                .resolveAt(appImageDir)
                .appDirectory()
                .resolve(FILENAME);
    }

    public AppImageFile {
        Objects.requireNonNull(mainLauncherName);
        Objects.requireNonNull(mainLauncherClassName);
        Objects.requireNonNull(version);
        if (!launchers.containsKey(mainLauncherName)) {
            throw new IllegalArgumentException();
        }
    }

    public AppImageFile(String mainLauncherName, String mainLauncherClassName) {
        this(mainLauncherName, mainLauncherClassName, "1.0", false, false, Map.of(mainLauncherName, Map.of()));
    }

    public Map<String, Map<String, String>> addLaunchers() {
        return launchers.entrySet().stream().filter(e -> {
            return !e.getKey().equals(mainLauncherName);
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void save(Path appImageDir) throws IOException {
        XmlUtils.createXml(getPathInAppImage(appImageDir), xml -> {
            xml.writeStartElement("jpackage-state");
            xml.writeAttribute("version", getVersion());
            xml.writeAttribute("platform", getPlatform());

            xml.writeStartElement("app-version");
            xml.writeCharacters(version);
            xml.writeEndElement();

            xml.writeStartElement("main-launcher");
            xml.writeCharacters(mainLauncherName);
            xml.writeEndElement();

            xml.writeStartElement("main-class");
            xml.writeCharacters(mainLauncherClassName);
            xml.writeEndElement();

            xml.writeStartElement("signed");
            xml.writeCharacters(Boolean.toString(macSigned));
            xml.writeEndElement();

            xml.writeStartElement("app-store");
            xml.writeCharacters(Boolean.toString(macAppStore));
            xml.writeEndElement();

            for (var al : addLaunchers().keySet().stream().sorted().toList()) {
                xml.writeStartElement("add-launcher");
                xml.writeAttribute("name", al);
                var props = launchers.get(al);
                for (var prop : props.keySet().stream().sorted().toList()) {
                    xml.writeStartElement(prop);
                    xml.writeCharacters(props.get(prop));
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }
        });
    }

    public static AppImageFile load(Path appImageDir) {
        return toSupplier(() -> {
            Document doc = XmlUtils.initDocumentBuilder().parse(
                    Files.newInputStream(getPathInAppImage(appImageDir)));

            XPath xPath = XPathFactory.newInstance().newXPath();

            var version = xPath.evaluate("/jpackage-state/app-version/text()", doc);

            var mainLauncherName = xPath.evaluate(
                    "/jpackage-state/main-launcher/text()", doc);

            var mainLauncherClassName = xPath.evaluate(
                    "/jpackage-state/main-class/text()", doc);

            var macSigned = Optional.ofNullable(xPath.evaluate(
                    "/jpackage-state/signed/text()", doc)).map(
                            Boolean::parseBoolean).orElse(false);

            var macAppStore = Optional.ofNullable(xPath.evaluate(
                    "/jpackage-state/app-store/text()", doc)).map(
                            Boolean::parseBoolean).orElse(false);

            var addLaunchers = XmlUtils.queryNodes(doc, xPath, "/jpackage-state/add-launcher").map(Element.class::cast).map(toFunction(addLauncher -> {
                Map<String, String> launcherProps = new HashMap<>();

                // @name and @service attributes.
                XmlUtils.toStream(addLauncher.getAttributes()).forEach(attr -> {
                    launcherProps.put(attr.getNodeName(), attr.getNodeValue());
                });

                // Extra properties.
                XmlUtils.queryNodes(addLauncher, xPath, "*[count(*) = 0]").map(Element.class::cast).forEach(e -> {
                    launcherProps.put(e.getNodeName(), e.getTextContent());
                });

                return launcherProps;
            }));

            var mainLauncherProperties = Map.of("name", mainLauncherName);

            var launchers = Stream.concat(Stream.of(mainLauncherProperties), addLaunchers).collect(toMap(attrs -> {
                return Objects.requireNonNull(attrs.get("name"));
            }, attrs -> {
                Map<String, String> copy = new HashMap<>(attrs);
                copy.remove("name");
                return Map.copyOf(copy);
            }));

            return new AppImageFile(mainLauncherName, mainLauncherClassName,
                    version, macSigned, macAppStore, launchers);

        }).get();
    }

    private static String getVersion() {
        return System.getProperty("java.version");
    }

    private static String getPlatform() {
        return PLATFORM_LABELS.get(OperatingSystem.current());
    }

    private static final String FILENAME = ".jpackage.xml";

    private static final Map<OperatingSystem, String> PLATFORM_LABELS = Map.of(
            OperatingSystem.LINUX, "linux",
            OperatingSystem.WINDOWS, "windows",
            OperatingSystem.MACOS, "macOS");
}
