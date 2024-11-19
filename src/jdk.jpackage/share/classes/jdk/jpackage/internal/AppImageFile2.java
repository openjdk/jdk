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

import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static java.util.stream.Collectors.toMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jdk.internal.util.OperatingSystem;
import static jdk.jpackage.internal.I18N.buildConfigException;
import jdk.jpackage.internal.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


final class AppImageFile2 {

    AppImageFile2(Application app) {
        appVersion = app.version();
        launcherName = app.mainLauncher().name();
        mainClass = app.mainLauncher().startupInfo().qualifiedClassName();
        extra = app.extraAppImageFileData();
        creatorVersion = getVersion();
        creatorPlatform = getPlatform();
        addLauncherInfos = app.additionalLaunchers().stream().map(launcher -> {
            return new LauncherInfo(launcher.name(), launcher.isService(),
                    launcher.extraAppImageFileData());
        }).toList();

        for (var str : List.of(appVersion, launcherName, mainClass)) {
            if (str == null || str.isBlank()) {
                throw new InavlidAppImageFileException();
            }
        }
    }

    /**
     * Returns list of additional launchers configured for the application.
     *
     * Returns empty list for application without additional launchers.
     */
    List<LauncherInfo> getAddLaunchers() {
        return addLauncherInfos;
    }

    /**
     * Returns application version. Never returns null or empty value.
     */
    String getAppVersion() {
        return appVersion;
    }

    /**
     * Returns application name. Never returns null or empty value.
     */
    String getAppName() {
        return launcherName;
    }

    /**
     * Returns main application launcher name. Never returns null or empty value.
     */
    String getLauncherName() {
        return launcherName;
    }

    /**
     * Returns main class name. Never returns null or empty value.
     */
    String getMainClass() {
        return mainClass;
    }

    /**
     * Returns additional properties. Never returns null.
     */
    Map<String, String> getExtra() {
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
            xml.writeCharacters(launcherName);
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
    static AppImageFile2 load(Path appImageDir, ApplicationLayout appLayout) throws ConfigException, IOException {
        var srcFilePath = getPathInAppImage(appLayout.resolveAt(appImageDir));
        try {
            Document doc = XmlUtils.initDocumentBuilder().parse(Files.newInputStream(srcFilePath));

            XPath xPath = XPathFactory.newInstance().newXPath();

            String platform = queryNodes(doc, xPath, "/jpackage-state/@platform").findFirst().map(
                    Node::getNodeValue).orElse(null);

            String version = queryNodes(doc, xPath, "/jpackage-state/@version").findFirst().map(
                    Node::getNodeValue).orElse(null);

            if (!platform.equals(getPlatform()) || !version.equals(getVersion())) {
                throw new InavlidAppImageFileException();
            }

            var props = AppImageProperties.main(doc, xPath);

            Launcher mainLauncher = new Launcher.Unsupported() {
                @Override
                public String name() {
                    return props.get("main-launcher");
                }

                @Override
                public LauncherStartupInfo startupInfo() {
                    return startupInfo;
                }

                private final LauncherStartupInfo startupInfo = new LauncherStartupInfo.Unsupported() {
                    @Override
                    public String qualifiedClassName() {
                        return props.get("main-class");
                    }
                };
            };

            List<Launcher> additionalLaunchers = AppImageProperties.launchers(doc, xPath).stream().map(launcherProps -> {
                Launcher launcher = new Launcher.Unsupported() {
                    @Override
                    public String name() {
                        return launcherProps.get("name");
                    }

                    @Override
                    public boolean isService() {
                        return Boolean.parseBoolean(launcherProps.get("service"));
                    }

                    @Override
                    public Map<String, String> extraAppImageFileData() {
                        return launcherProps.getExtra();
                    }
                };
                return launcher;
            }).toList();

            return new AppImageFile2(new Application.Unsupported() {
                @Override
                public String version() {
                   return props.get("app-version");
                }

                @Override
                public List<Launcher> launchers() {
                   return Stream.concat(Stream.of(mainLauncher), additionalLaunchers.stream()).toList();
                }

                @Override
                public Map<String, String> extraAppImageFileData() {
                    return props.getExtra();
                }
            });
        } catch (XPathExpressionException ex) {
            // This should never happen as XPath expressions should be correct
            throw new RuntimeException(ex);
        } catch (SAXException ex) {
            // Exception reading input XML (probably malformed XML)
            throw new IOException(ex);
        } catch (NoSuchFileException ex) {
            throw buildConfigException("error.foreign-app-image", appImageDir).create();
        } catch (InavlidAppImageFileException ex) {
            // Invalid input XML
            throw buildConfigException("error.invalid-app-image", appImageDir,
                    FILENAME).create();
        }
    }

    static Stream<Node> queryNodes(Node xml, XPath xPath, String xpathExpr) throws XPathExpressionException {
        NodeList nodes = (NodeList) xPath.evaluate(xpathExpr, xml, XPathConstants.NODESET);
        return Optional.ofNullable(nodes).map(AppImageFile2::toStream).orElseGet(Stream::of);
    }

    static Stream<Node> toStream(NodeList nodes) {
        return Optional.ofNullable(nodes).map(v -> {
            return IntStream.range(0, v.getLength()).mapToObj(v::item);
        }).orElseGet(Stream::of);
    }

    static Stream<Node> toStream(NamedNodeMap nodes) {
        return Optional.ofNullable(nodes).map(v -> {
            return IntStream.range(0, v.getLength()).mapToObj(v::item);
        }).orElseGet(Stream::of);
    }

    private static String getVersion() {
        return System.getProperty("java.version");
    }

    private static String getPlatform() {
        return PLATFORM_LABELS.get(OperatingSystem.current());
    }

    private final static class AppImageProperties {
        private AppImageProperties(Map<String, String> data, Set<String> stdKeys) {
            this.data = data;
            this.stdKeys = stdKeys;
        }

        static AppImageProperties main(Document xml, XPath xPath) throws XPathExpressionException {
            var data = queryNodes(xml, xPath, "/jpackage-state/*/text()").map(node -> {
                return Map.entry(node.getParentNode().getNodeName(), node.getNodeValue());
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
            return new AppImageProperties(data, Set.of("app-version", "main-launcher", "main-class"));
        }

        static AppImageProperties launcher(Node node) {
            var data = toStream(node.getAttributes()).map(attrNode -> {
                return Map.entry(attrNode.getNodeName(), attrNode.getNodeValue());
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            return new AppImageProperties(data, Set.of("name", "service"));
        }

        static List<AppImageProperties> launchers(Document xml, XPath xPath)
                throws XPathExpressionException {
            return queryNodes(xml, xPath, "/jpackage-state/add-launcher").map(
                    AppImageProperties::launcher).toList();
        }

        String get(String name) {
            return Optional.ofNullable(data.get(name)).orElseThrow(
                    InavlidAppImageFileException::new);
        }

        Map<String, String> getExtra() {
            Map<String, String> extra = new HashMap<>(data);
            stdKeys.forEach(extra::remove);
            return extra;
        }

        private final Map<String, String> data;
        private final Set<String> stdKeys;
    }

    record LauncherInfo(String name, boolean service, Map<String, String> extra) {
        LauncherInfo {
            if (name == null || name.isBlank()) {
                throw new InavlidAppImageFileException();
            }
        }

        Launcher asLauncher() {
            return new Launcher.Stub(name, null, null, service, null, null);
        }
    }

    private static class InavlidAppImageFileException extends RuntimeException {
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
