/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


final class AppImageFile implements ExternalApplication {

    AppImageFile(Application app) {
        this(new ApplicationData(app));
    }

    private AppImageFile(ApplicationData app) {

        appVersion = app.version();
        launcherName = app.mainLauncherName();
        mainClass = app.mainLauncherMainClassName();
        extra = app.extra;
        creatorVersion = getVersion();
        creatorPlatform = getPlatform();
        addLauncherInfos = app.additionalLaunchers;
    }

    @Override
    public List<LauncherInfo> getAddLaunchers() {
        return addLauncherInfos;
    }

    @Override
    public String getAppVersion() {
        return appVersion;
    }

    @Override
    public String getAppName() {
        return launcherName;
    }

    @Override
    public String getLauncherName() {
        return launcherName;
    }

    @Override
    public String getMainClass() {
        return mainClass;
    }

    @Override
    public Map<String, String> getExtra() {
        return extra;
    }

    /**
     * Saves file with application image info in application image using values
     * from this instance.
     */
    void save(ApplicationLayout appLayout) throws IOException {
        XmlUtils.createXml(getPathInAppImage(appLayout), xml -> {
            xml.writeStartElement("jpackage-state");
            xml.writeAttribute("version", creatorVersion);
            xml.writeAttribute("platform", creatorPlatform);

            xml.writeStartElement("app-version");
            xml.writeCharacters(appVersion);
            xml.writeEndElement();

            xml.writeStartElement("main-launcher");
            xml.writeCharacters(launcherName);
            xml.writeEndElement();

            xml.writeStartElement("main-class");
            xml.writeCharacters(mainClass);
            xml.writeEndElement();

            for (var extraKey : extra.keySet().stream().sorted().toList()) {
                xml.writeStartElement(extraKey);
                xml.writeCharacters(extra.get(extraKey));
                xml.writeEndElement();
            }

            for (var li : addLauncherInfos) {
                xml.writeStartElement("add-launcher");
                xml.writeAttribute("name", li.name());
                xml.writeAttribute("service", Boolean.toString(li.service()));
                for (var extraKey : li.extra().keySet().stream().sorted().toList()) {
                    xml.writeStartElement(extraKey);
                    xml.writeCharacters(li.extra().get(extraKey));
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }
        });
    }

    /**
     * Returns path to application image info file.
     * @param appLayout - application layout
     */
    static Path getPathInAppImage(ApplicationLayout appLayout) {
        return appLayout.appDirectory().resolve(FILENAME);
    }

    /**
     * Loads application image info from application image.
     * @param appImageDir - path at which to resolve the given application layout
     * @param appLayout - application layout
     */
    static AppImageFile load(Path appImageDir, ApplicationLayout appLayout) throws ConfigException, IOException {
        var srcFilePath = getPathInAppImage(appLayout.resolveAt(appImageDir));
        try {
            final Document doc = XmlUtils.initDocumentBuilder().parse(Files.newInputStream(srcFilePath));

            final XPath xPath = XPathFactory.newInstance().newXPath();

            final var isPlatformValid = XmlUtils.queryNodes(doc, xPath, "/jpackage-state/@platform").findFirst().map(
                    Node::getNodeValue).map(getPlatform()::equals).orElse(false);
            if (!isPlatformValid) {
                throw new InvalidAppImageFileException();
            }

            final var isVersionValid = XmlUtils.queryNodes(doc, xPath, "/jpackage-state/@version").findFirst().map(
                    Node::getNodeValue).map(getVersion()::equals).orElse(false);
            if (!isVersionValid) {
                throw new InvalidAppImageFileException();
            }

            final AppImageProperties props;
            try {
                props = AppImageProperties.main(doc, xPath);
            } catch (IllegalArgumentException ex) {
                throw new InvalidAppImageFileException(ex);
            }

            final var additionalLaunchers = AppImageProperties.launchers(doc, xPath).stream().map(launcherProps -> {
                try {
                    return new LauncherInfo(launcherProps.get("name"),
                            launcherProps.find("service").map(Boolean::parseBoolean).orElse(false), launcherProps.getExtra());
                } catch (IllegalArgumentException ex) {
                    throw new InvalidAppImageFileException(ex);
                }
            }).toList();

            return new AppImageFile(new ApplicationData(props.get("app-version"), props.get("main-launcher"),
                    props.get("main-class"), props.getExtra(), additionalLaunchers));

        } catch (XPathExpressionException ex) {
            // This should never happen as XPath expressions should be correct
            throw new RuntimeException(ex);
        } catch (SAXException ex) {
            // Exception reading input XML (probably malformed XML)
            throw new IOException(ex);
        } catch (NoSuchFileException ex) {
            throw I18N.buildConfigException("error.foreign-app-image", appImageDir).create();
        } catch (InvalidAppImageFileException ex) {
            // Invalid input XML
            throw I18N.buildConfigException("error.invalid-app-image", appImageDir, srcFilePath).create();
        }
    }

    static boolean getBooleanExtraFieldValue(String fieldId, ExternalApplication appImageFile) {
        Objects.requireNonNull(fieldId);
        Objects.requireNonNull(appImageFile);
        return Optional.ofNullable(appImageFile.getExtra().get(fieldId)).map(Boolean::parseBoolean).orElse(false);
    }

    static String getVersion() {
        return System.getProperty("java.version");
    }

    static String getPlatform() {
        return PLATFORM_LABELS.get(OperatingSystem.current());
    }

    private static final class AppImageProperties {
        private AppImageProperties(Map<String, String> data, Set<String> stdKeys) {
            this.data = data;
            this.stdKeys = stdKeys;
        }

        static AppImageProperties main(Document xml, XPath xPath) throws XPathExpressionException {
            final var data = queryProperties(xml.getDocumentElement(), xPath, MAIN_PROPERTIES_XPATH_QUERY);
            return new AppImageProperties(data, MAIN_ELEMENT_NAMES);
        }

        static AppImageProperties launcher(Element addLauncherNode, XPath xPath) throws XPathExpressionException {
            final var attrData = XmlUtils.toStream(addLauncherNode.getAttributes())
                    .collect(toMap(Node::getNodeName, Node::getNodeValue));

            final var extraData = queryProperties(addLauncherNode, xPath, LAUNCHER_PROPERTIES_XPATH_QUERY);

            final Map<String, String> data = new HashMap<>(attrData);
            data.putAll(extraData);

            return new AppImageProperties(data, LAUNCHER_ATTR_NAMES);
        }

        static List<AppImageProperties> launchers(Document xml, XPath xPath) throws XPathExpressionException {
            return XmlUtils.queryNodes(xml, xPath, "/jpackage-state/add-launcher")
                    .map(Element.class::cast).map(toFunction(e -> {
                        return launcher(e, xPath);
                    })).toList();
        }

        String get(String name) {
            return find(name).orElseThrow(InvalidAppImageFileException::new);
        }

        Optional<String> find(String name) {
            return Optional.ofNullable(data.get(name));
        }

        Map<String, String> getExtra() {
            Map<String, String> extra = new HashMap<>(data);
            stdKeys.forEach(extra::remove);
            return extra;
        }

        private static  Map<String, String> queryProperties(Element e, XPath xPath, String xpathExpr)
                throws XPathExpressionException {
            return XmlUtils.queryNodes(e, xPath, xpathExpr)
                    .map(Element.class::cast)
                    .collect(toMap(Node::getNodeName, selectedElement -> {
                        return selectedElement.getTextContent();
                    }, (a, b) -> b));
        }

        private static String xpathQueryForExtraProperties(Set<String> excludeNames) {
            final String otherElementNames = excludeNames.stream().map(name -> {
                return String.format("name() != '%s'", name);
            }).collect(joining(" and "));

            return String.format("*[(%s) and not(*)]", otherElementNames);
        }

        private final Map<String, String> data;
        private final Set<String> stdKeys;

        private static final Set<String> LAUNCHER_ATTR_NAMES = Set.of("name", "service");
        private static final String LAUNCHER_PROPERTIES_XPATH_QUERY = xpathQueryForExtraProperties(LAUNCHER_ATTR_NAMES);

        private static final Set<String> MAIN_ELEMENT_NAMES = Set.of("app-version", "main-launcher", "main-class");
        private static final String MAIN_PROPERTIES_XPATH_QUERY;

        static {
            final String nonEmptyMainElements = MAIN_ELEMENT_NAMES.stream().map(name -> {
                return String.format("/jpackage-state/%s[text()]", name);
            }).collect(joining("|"));

            MAIN_PROPERTIES_XPATH_QUERY = String.format("%s|/jpackage-state/%s", nonEmptyMainElements,
                    xpathQueryForExtraProperties(Stream.concat(MAIN_ELEMENT_NAMES.stream(),
                            Stream.of("add-launcher")).collect(toSet())));
        }
    }

    private record ApplicationData(String version, String mainLauncherName, String mainLauncherMainClassName,
            Map<String, String> extra, List<LauncherInfo> additionalLaunchers) {

        ApplicationData {
            Objects.requireNonNull(version);
            Objects.requireNonNull(mainLauncherName);
            Objects.requireNonNull(mainLauncherMainClassName);
            Objects.requireNonNull(extra);
            Objects.requireNonNull(additionalLaunchers);

            for (final var property : List.of(version, mainLauncherName, mainLauncherMainClassName)) {
                if (property.isBlank()) {
                    throw new IllegalArgumentException();
                }
            }
        }

        ApplicationData(Application app) {
            this(app, app.mainLauncher().orElseThrow());
        }

        private ApplicationData(Application app, Launcher mainLauncher) {
            this(app.version(), mainLauncher.name(), mainLauncher.startupInfo().orElseThrow().qualifiedClassName(),
                    app.extraAppImageFileData(), app.additionalLaunchers().stream().map(launcher -> {
                        return new LauncherInfo(launcher.name(), launcher.isService(),
                                launcher.extraAppImageFileData());
                    }).toList());
        }
    }

    private static class InvalidAppImageFileException extends RuntimeException {

        InvalidAppImageFileException() {
        }

        InvalidAppImageFileException(Throwable t) {
            super(t);
        }

        private static final long serialVersionUID = 1L;
    }

    private final String appVersion;
    private final String launcherName;
    private final String mainClass;
    private final Map<String, String> extra;
    private final List<LauncherInfo> addLauncherInfos;
    private final String creatorVersion;
    private final String creatorPlatform;

    private static final String FILENAME = ".jpackage.xml";

    private static final Map<OperatingSystem, String> PLATFORM_LABELS = Map.of(
            OperatingSystem.LINUX, "linux",
            OperatingSystem.WINDOWS, "windows",
            OperatingSystem.MACOS, "macOS");
}
