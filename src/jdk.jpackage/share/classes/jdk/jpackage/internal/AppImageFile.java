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
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.ADD_LAUNCHER;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.MAIN_CLASS;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.MAIN_LAUNCHER;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.parseAddLauncherProperties;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.parseAppProperties;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.OptionValue;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.internal.util.function.ExceptionBox;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


final class AppImageFile {

    AppImageFile(Application app) {
        var mainLauncher = Objects.requireNonNull(app.mainLauncher().orElseThrow());

        appVersion = Objects.requireNonNull(app.version());
        launcherName = Objects.requireNonNull(mainLauncher.name());
        mainClass = mainLauncher.startupInfo().orElseThrow().qualifiedClassName();
        extra = Objects.requireNonNull(app.extraAppImageFileData());
        addLauncherInfos = app.additionalLaunchers().stream().map(LauncherInfo::new).toList();
    }

    /**
     * Writes the values captured in this instance into the application image info
     * file in the given application layout.
     *
     * @param appLayout the application layout
     * @throws IOException if an I/O error occurs when writing
     */
    void save(ApplicationLayout appLayout) throws IOException {
        XmlUtils.createXml(getPathInAppImage(appLayout), xml -> {
            xml.writeStartElement("jpackage-state");
            xml.writeAttribute("version", getVersion());
            xml.writeAttribute("platform", getPlatform());

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
     * Returns the path to the application image info file in the given application layout.
     *
     * @param appLayout the application layout
     */
    static Path getPathInAppImage(ApplicationLayout appLayout) {
        return appLayout.appDirectory().resolve(FILENAME);
    }

    /**
     * Loads application image info from the specified application layout.
     * <p>
     * It is an equivalent to calling
     * {@link #load(ApplicationLayout, OperatingSystem)} method with
     * {@code OperatingSystem.current())} for the second parameter.
     *
     * @param appLayout the application layout
     */
    static ExternalApplication load(ApplicationLayout appLayout) {
        return load(appLayout, OperatingSystem.current());
    }

    /**
     * Loads application image info from the specified application layout and OS.
     *
     * @param appLayout the application layout
     * @param os        the OS defining extra properties of the application and
     *                  additional launchers
     */
    static ExternalApplication load(ApplicationLayout appLayout, OperatingSystem os) {
        Objects.requireNonNull(appLayout);
        Objects.requireNonNull(os);

        final var appImageDir = appLayout.rootDirectory();
        final var appImageFilePath = getPathInAppImage(appLayout);
        final var relativeAppImageFilePath = appImageDir.relativize(appImageFilePath);

        try {
            final Document doc = XmlUtils.initDocumentBuilder().parse(Files.newInputStream(appImageFilePath));

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

            final var appOptions = parseAppProperties(appImageFilePath, AppImageProperties.main(doc, xPath), os);
            final var addLauncherOptions = AppImageProperties.launchers(doc, xPath).stream().map(launcherProps -> {
                return parseAddLauncherProperties(appImageFilePath, launcherProps, os);
            }).toList();

            try {
                return ExternalApplication.create(appOptions, addLauncherOptions);
            } catch (NoSuchElementException ex) {
                throw new InvalidAppImageFileException(ex);
            }

        } catch (XPathExpressionException ex) {
            // This should never happen as XPath expressions should be correct
            throw ExceptionBox.rethrowUnchecked(ex);
        } catch (SAXException ex) {
            // Malformed input XML
            throw new JPackageException(I18N.format("error.malformed-app-image-file", relativeAppImageFilePath, appImageDir), ex);
        } catch (NoSuchFileException ex) {
            // Don't save the original exception as its error message is redundant.
            throw new JPackageException(I18N.format("error.missing-app-image-file", relativeAppImageFilePath, appImageDir));
        } catch (InvalidAppImageFileException ex) {
            // Invalid input XML
            throw new JPackageException(I18N.format("error.invalid-app-image-file", relativeAppImageFilePath, appImageDir));
        } catch (IOException ex) {
            throw new JPackageException(I18N.format("error.reading-app-image-file", relativeAppImageFilePath, appImageDir), ex);
        }
    }

    static String getVersion() {
        return System.getProperty("java.version");
    }

    static String getPlatform() {
        return PLATFORM_LABELS.get(OperatingSystem.current());
    }


    private static final class AppImageProperties {

        static Map<String, String> main(Document xml, XPath xPath) throws XPathExpressionException {
            return queryProperties(xml.getDocumentElement(), xPath, MAIN_PROPERTIES_XPATH_QUERY);
        }

        static Map<String, String> launcher(Element addLauncherNode, XPath xPath) throws XPathExpressionException {
            final var attrData = XmlUtils.toStream(addLauncherNode.getAttributes())
                    .collect(toMap(Node::getNodeName, Node::getNodeValue));

            final var extraData = queryProperties(addLauncherNode, xPath, LAUNCHER_PROPERTIES_XPATH_QUERY);

            final Map<String, String> data = new HashMap<>(attrData);
            data.putAll(extraData);

            return data;
        }

        static List<Map<String, String>> launchers(Document xml, XPath xPath) throws XPathExpressionException {
            return XmlUtils.queryNodes(xml, xPath, "/jpackage-state/add-launcher")
                    .map(Element.class::cast).map(toFunction(e -> {
                        return launcher(e, xPath);
                    })).toList();
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

        private static final Set<String> LAUNCHER_ATTR_NAMES = Stream.of(
                LAUNCHER_AS_SERVICE,
                ADD_LAUNCHER
        ).map(OptionValue::getName).collect(toSet());
        private static final String LAUNCHER_PROPERTIES_XPATH_QUERY = xpathQueryForExtraProperties(LAUNCHER_ATTR_NAMES);

        private static final Set<String> MAIN_ELEMENT_NAMES = Stream.of(
                APP_VERSION,
                MAIN_LAUNCHER,
                MAIN_CLASS
        ).map(OptionValue::getName).collect(toSet());
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


    private record LauncherInfo(String name, boolean service, Map<String, String> extra) {
        LauncherInfo {
            Objects.requireNonNull(name);
            Objects.requireNonNull(extra);
        }

        LauncherInfo(Launcher launcher) {
            this(launcher.name(), launcher.isService(), launcher.extraAppImageFileData());
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

    private static final String FILENAME = ".jpackage.xml";

    private static final Map<OperatingSystem, String> PLATFORM_LABELS = Map.of(
            OperatingSystem.LINUX, "linux",
            OperatingSystem.WINDOWS, "windows",
            OperatingSystem.MACOS, "macOS");
}
