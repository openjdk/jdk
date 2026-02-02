/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public record AppImageFile(String mainLauncherName, Optional<String> mainLauncherClassName,
        String version, boolean macAppStore, Map<String, Map<String, String>> launchers) {

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

    public AppImageFile(String mainLauncherName, Optional<String> mainLauncherClassName) {
        this(mainLauncherName, mainLauncherClassName, "1.0", false, Map.of(mainLauncherName, Map.of()));
    }

    public AppImageFile(String mainLauncherName, String mainLauncherClassName) {
        this(mainLauncherName, Optional.of(mainLauncherClassName));
    }

    public Map<String, Map<String, String>> addLaunchers() {
        var map = launchers.entrySet().stream().filter(e -> {
            return !e.getKey().equals(mainLauncherName);
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (v, _) -> {
            throw new IllegalStateException(String.format("Duplicate value [%s]", v));
        }, LinkedHashMap::new));
        return Collections.unmodifiableMap(map);
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
            xml.writeAttribute("name", mainLauncherName);
            writeLauncherDescription(xml, mainLauncherName);
            xml.writeEndElement();

            mainLauncherClassName.ifPresent(toConsumer(v -> {
                xml.writeStartElement("main-class");
                xml.writeCharacters(v);
                xml.writeEndElement();
            }));

            xml.writeStartElement("app-store");
            xml.writeCharacters(Boolean.toString(macAppStore));
            xml.writeEndElement();

            for (var al : addLaunchers().keySet().stream().sorted().toList()) {
                xml.writeStartElement("add-launcher");
                xml.writeAttribute("name", al);
                var props = launchers.get(al);
                if (!props.containsKey("description")) {
                    writeLauncherDescription(xml, al);
                }
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
                    getPathInAppImage(appImageDir).toFile());

            XPath xPath = XPathFactory.newInstance().newXPath();

            var version = xPath.evaluate("/jpackage-state/app-version/text()", doc);

            var mainLauncherClassName = Optional.ofNullable(xPath.evaluate(
                    "/jpackage-state/main-class/text()", doc));

            var macAppStore = Optional.ofNullable(xPath.evaluate(
                    "/jpackage-state/app-store/text()", doc)).map(
                            Boolean::parseBoolean).orElse(false);

            var addLaunchers = XmlUtils.queryNodes(doc, xPath, "/jpackage-state/add-launcher").map(Element.class::cast).map(toFunction(launcher -> {
                return readLauncherProperties(xPath, launcher);
            }));

            var mainLauncher = XmlUtils.queryNodes(doc, xPath, "/jpackage-state/main-launcher[last()]").map(Element.class::cast).map(toFunction(launcher -> {
                return readLauncherProperties(xPath, launcher);
            })).findFirst().orElseThrow();

            var mainLauncherName = mainLauncher.get("name");

            var launchers = Stream.concat(Stream.of(mainLauncher), addLaunchers).collect(toMap(launcherProps -> {
                return Objects.requireNonNull(launcherProps.get("name"));
            }, launcherProps -> {
                launcherProps.remove("name");
                return Collections.unmodifiableMap(launcherProps);
            }, (v, _) -> {
                throw new IllegalStateException(String.format("Duplicate value [%s]", v));
            }, LinkedHashMap::new));

            return new AppImageFile(
                    mainLauncherName,
                    mainLauncherClassName,
                    version,
                    macAppStore,
                    Collections.unmodifiableMap(launchers));

        }).get();
    }

    private static void writeLauncherDescription(XMLStreamWriter xml, String description) throws XMLStreamException {
        xml.writeStartElement("description");
        xml.writeCharacters(Objects.requireNonNull(description));
        xml.writeEndElement();
    }

    private static HashMap<String, String> readLauncherProperties(XPath xPath, Element launcherElement) throws XPathExpressionException {
        HashMap<String, String> launcherProps = new HashMap<>();

        var name = Objects.requireNonNull(xPath.evaluate("@name", launcherElement));

        launcherProps.put("name", name);

        // Extra properties.
        XmlUtils.queryNodes(launcherElement, xPath, "*[count(*) = 0]").map(Element.class::cast).forEach(e -> {
            launcherProps.put(e.getNodeName(), e.getTextContent());
        });

        return launcherProps;
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
