/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jdk.internal.util.OperatingSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.jpackage.internal.StandardBundlerParam.ADD_LAUNCHERS;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.MAIN_CLASS;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.MENU_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.SIGN_BUNDLE;
import static jdk.jpackage.internal.StandardBundlerParam.APP_STORE;
import jdk.jpackage.internal.util.XmlUtils;

final class AppImageFile {

    // These values will be loaded from AppImage xml file.
    private final String appVersion;
    private final String launcherName;
    private final String mainClass;
    private final List<LauncherInfo> addLauncherInfos;
    private final String creatorVersion;
    private final String creatorPlatform;
    private final boolean signed;
    private final boolean appStore;

    private static final String FILENAME = ".jpackage.xml";

    private static final Map<OperatingSystem, String> PLATFORM_LABELS = Map.of(
            OperatingSystem.LINUX, "linux",
            OperatingSystem.WINDOWS, "windows",
            OperatingSystem.MACOS, "macOS");

    private AppImageFile(Path appImageDir, String appVersion, String launcherName,
            String mainClass, List<LauncherInfo> launcherInfos,
            String creatorVersion, String creatorPlatform, String signedStr,
            String appStoreStr) {
        boolean isValid = true;

        if (appVersion == null || appVersion.length() == 0) {
            isValid = false;
        }

        if (launcherName == null || launcherName.length() == 0) {
            isValid = false;
        }

        if (mainClass == null || mainClass.length() == 0) {
            isValid = false;
        }

        for (var launcher : launcherInfos) {
            if ("".equals(launcher.getName())) {
                isValid = false;
            }
        }

        if (!Objects.equals(getVersion(), creatorVersion)) {
            isValid = false;
        }

        if (!Objects.equals(getPlatform(), creatorPlatform)) {
            isValid = false;
        }

        if (signedStr == null ||
                !("true".equals(signedStr) || "false".equals(signedStr))) {
            isValid = false;
        }

        if (appStoreStr == null ||
                !("true".equals(appStoreStr) || "false".equals(appStoreStr))) {
            isValid = false;
        }

        if (!isValid) {
            throw new RuntimeException(MessageFormat.format(I18N.getString(
                "error.invalid-app-image"), appImageDir,
                AppImageFile.getPathInAppImage(appImageDir)));
        }

        this.appVersion = appVersion;
        this.launcherName = launcherName;
        this.mainClass = mainClass;
        this.addLauncherInfos = launcherInfos;
        this.creatorVersion = creatorVersion;
        this.creatorPlatform = creatorPlatform;
        this.signed = "true".equals(signedStr);
        this.appStore = "true".equals(appStoreStr);
    }

    /**
     * Returns list of additional launchers configured for the application.
     * Each item in the list is not null or empty string.
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

    public boolean isSigned() {
        return signed;
    }

    public boolean isAppStore() {
        return appStore;
    }

    /**
     * Returns path to application image info file.
     * @param appImageDir - path to application image
     */
    public static Path getPathInAppImage(Path appImageDir) {
        return ApplicationLayout.platformAppImage()
                .resolveAt(appImageDir)
                .appDirectory()
                .resolve(FILENAME);
    }

    /**
     * Saves file with application image info in application image using values
     * from current instance.
     * @param appImageDir - path to application image
     * @throws IOException
     */
    void save(Path appImageDir) throws IOException {
        AppImageFile.save(appImageDir, null, this);
    }

    /**
     * Saves file with application image info in application image.
     * @param appImageDir - path to application image
     * @param params - parameters used to generate application image
     * @throws IOException
     */
    static void save(Path appImageDir, Map<String, Object> params)
            throws IOException {
        AppImageFile.save(appImageDir, params, null);
    }

    /**
     * Saves file with application image info in application image using params
     * or appImage. Both params or appImage cannot be valid.
     * @param appImageDir - path to application image
     * @param params - parameters used to generate application image
     * @param appImage - instance of already existing application image file
     * @throws IOException
     * @throws IllegalArgumentException - If both params and appImage are null or
     *                                    If both params and appImage are not null
     */
    private static void save(Path appImageDir,
            Map<String, Object> params,
            AppImageFile appImage) throws IOException {
        if ((params == null && appImage == null) ||
            (params != null && appImage != null)) {
                throw new IllegalArgumentException();
        }

        final String appVersionSave;
        final String mainLauncherSave;
        final String mainClassSave;
        final String signedSave;
        final String appStoreSave;
        final List<LauncherInfo> addLauncherInfoSave;
        if (params != null) {
            appVersionSave = VERSION.fetchFrom(params);
            mainLauncherSave = APP_NAME.fetchFrom(params);
            mainClassSave = MAIN_CLASS.fetchFrom(params);
            signedSave = SIGN_BUNDLE.fetchFrom(params).toString();
            appStoreSave = APP_STORE.fetchFrom(params).toString();
            addLauncherInfoSave = null;
        } else {
            appVersionSave = appImage.getAppVersion();
            mainLauncherSave = appImage.getLauncherName();
            mainClassSave = appImage.getMainClass();
            signedSave = String.valueOf(appImage.isSigned());
            appStoreSave = String.valueOf(appImage.isAppStore());
            addLauncherInfoSave = appImage.getAddLaunchers();
        }

        XmlUtils.createXml(getPathInAppImage(appImageDir), xml -> {
            xml.writeStartElement("jpackage-state");
            xml.writeAttribute("version", getVersion());
            xml.writeAttribute("platform", getPlatform());

            xml.writeStartElement("app-version");
            xml.writeCharacters(appVersionSave);
            xml.writeEndElement();

            xml.writeStartElement("main-launcher");
            xml.writeCharacters(mainLauncherSave);
            xml.writeEndElement();

            xml.writeStartElement("main-class");
            xml.writeCharacters(mainClassSave);
            xml.writeEndElement();

            xml.writeStartElement("signed");
            xml.writeCharacters(signedSave);
            xml.writeEndElement();

            xml.writeStartElement("app-store");
            xml.writeCharacters(appStoreSave);
            xml.writeEndElement();

            if (addLauncherInfoSave != null) {
                for (var li : addLauncherInfoSave) {
                    addLauncherInfo(xml, li);
                }
            } else {
                List<Map<String, ? super Object>> addLaunchers =
                    ADD_LAUNCHERS.fetchFrom(params);

                for (var launcherParams : addLaunchers) {
                    var li = new LauncherInfo(launcherParams);
                    addLauncherInfo(xml, li);
                }
            }
        });
    }

    static void addLauncherInfo(XMLStreamWriter xml, LauncherInfo li)
            throws XMLStreamException {
        xml.writeStartElement("add-launcher");
        xml.writeAttribute("name", li.getName());
        xml.writeAttribute("shortcut", Boolean.toString(li.isShortcut()));
        xml.writeAttribute("menu", Boolean.toString(li.isMenu()));
        xml.writeAttribute("service", Boolean.toString(li.isService()));
        xml.writeEndElement();
    }

    /**
     * Loads application image info from application image.
     * @param appImageDir - path to application image
     * @return valid info about application image or null
     * @throws IOException
     */
    public static AppImageFile load(Path appImageDir) {
        try {
            Document doc = readXml(appImageDir);

            XPath xPath = XPathFactory.newInstance().newXPath();

            String appVersion = xpathQueryNullable(xPath,
                    "/jpackage-state/app-version/text()", doc);

            String mainLauncher = xpathQueryNullable(xPath,
                    "/jpackage-state/main-launcher/text()", doc);

            String mainClass = xpathQueryNullable(xPath,
                    "/jpackage-state/main-class/text()", doc);

            List<LauncherInfo> launcherInfos = new ArrayList<>();

            String platform = xpathQueryNullable(xPath,
                    "/jpackage-state/@platform", doc);

            String version = xpathQueryNullable(xPath,
                    "/jpackage-state/@version", doc);

            String signedStr = xpathQueryNullable(xPath,
                    "/jpackage-state/signed/text()", doc);

            String appStoreStr = xpathQueryNullable(xPath,
                    "/jpackage-state/app-store/text()", doc);

            NodeList launcherNodes = (NodeList) xPath.evaluate(
                    "/jpackage-state/add-launcher", doc,
                    XPathConstants.NODESET);

            for (int i = 0; i != launcherNodes.getLength(); i++) {
                 launcherInfos.add(new LauncherInfo(launcherNodes.item(i)));
            }

            return new AppImageFile(appImageDir, appVersion, mainLauncher,
                    mainClass, launcherInfos, version, platform, signedStr,
                    appStoreStr);
        } catch (XPathExpressionException ex) {
            // This should never happen as XPath expressions should be correct
            throw new RuntimeException(ex);
        } catch (NoSuchFileException nsfe) {
            // non jpackage generated app-image (no app/.jpackage.xml)
            throw new RuntimeException(MessageFormat.format(I18N.getString(
                    "error.foreign-app-image"), appImageDir));
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Returns copy of AppImageFile, but with signed set to true if AppImageFile
     * is not marked as signed. If AppImageFile already signed it will return
     * instance to itself.
     */
    public AppImageFile copyAsSigned() {
        if (isSigned()) {
            return this;
        }

        // Pass null for appImageDir, it is used only to show location of
        // .jpackage.xml in case of error. copyAsSigned() should not produce
        // invalid app image file.
        return new AppImageFile(null, getAppVersion(),
                getLauncherName(), getMainClass(), getAddLaunchers(),
                getVersion(), getPlatform(), "true", String.valueOf(isAppStore()));
    }

    public static Document readXml(Path appImageDir) throws IOException {
        try {
            Path path = getPathInAppImage(appImageDir);

            DocumentBuilderFactory dbf =
                    DocumentBuilderFactory.newDefaultInstance();
            dbf.setFeature(
                   "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            DocumentBuilder b = dbf.newDocumentBuilder();
            return b.parse(Files.newInputStream(path));
        } catch (ParserConfigurationException | SAXException ex) {
            // Let caller sort this out
            throw new IOException(ex);
        }
    }

    /**
     * Returns list of LauncherInfo objects configured for the application.
     * The first item in the returned list is main launcher.
     * Following items in the list are names of additional launchers.
     */
    static List<LauncherInfo> getLaunchers(Path appImageDir,
            Map<String, Object> params) {
        List<LauncherInfo> launchers = new ArrayList<>();
        if (appImageDir != null) {
            AppImageFile appImageInfo = AppImageFile.load(appImageDir);
            launchers.add(new LauncherInfo(
                    appImageInfo.getLauncherName(), params));
                    launchers.addAll(appImageInfo.getAddLaunchers());
            return launchers;
        }

        launchers.add(new LauncherInfo(params));
        ADD_LAUNCHERS.fetchFrom(params).stream()
                .map(launcherParams -> new LauncherInfo(launcherParams))
                .forEach(launchers::add);
        return launchers;
    }

    public static String extractAppName(Path appImageDir) {
        return AppImageFile.load(appImageDir).getLauncherName();
    }

    public static String extractMainClass(Path appImageDir) {
        return AppImageFile.load(appImageDir).getMainClass();
    }

    private static String xpathQueryNullable(XPath xPath, String xpathExpr,
            Document xml) throws XPathExpressionException {
        NodeList nodes = (NodeList) xPath.evaluate(xpathExpr, xml,
                XPathConstants.NODESET);
        if (nodes != null && nodes.getLength() > 0) {
            return nodes.item(0).getNodeValue();
        }
        return null;
    }

    public static String getVersion() {
        return System.getProperty("java.version");
    }

    public static String getPlatform() {
        return PLATFORM_LABELS.get(OperatingSystem.current());
    }

    static class LauncherInfo {
        private final String name;
        private final boolean shortcut;
        private final boolean menu;
        private final boolean service;

        private LauncherInfo(Map<String, Object> params) {
            this(APP_NAME.fetchFrom(params), params);
        }

        private LauncherInfo(String name, Map<String, Object> params) {
            this.name = name;
            this.shortcut = SHORTCUT_HINT.fetchFrom(params);
            this.menu = MENU_HINT.fetchFrom(params);
            this.service = LAUNCHER_AS_SERVICE.fetchFrom(params);
        }

        private LauncherInfo(Node node) {
            this.name = getAttribute(node, "name");
            this.shortcut = !"false".equals(getAttribute(node, "shortcut"));
            this.menu = !"false".equals(getAttribute(node, "menu"));
            this.service = !"false".equals(getAttribute(node, "service"));
        }

        private String getAttribute(Node item, String attr) {
            NamedNodeMap attrs = item.getAttributes();
            Node attrNode = attrs.getNamedItem(attr);
            return ((attrNode == null) ? null : attrNode.getNodeValue());
        }

        public String getName() {
            return name;
        }

        public boolean isShortcut() {
            return shortcut;
        }

        public boolean isMenu() {
            return menu;
        }

        public boolean isService() {
            return service;
        }
    }

}
