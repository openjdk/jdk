/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.jpackage.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.incubator.jpackage.internal.StandardBundlerParam.ADD_LAUNCHERS;
import static jdk.incubator.jpackage.internal.StandardBundlerParam.APP_NAME;

public class AppImageFile {

    // These values will be loaded from AppImage xml file.
    private final String creatorVersion;
    private final String creatorPlatform;
    private final String launcherName;
    private final List<String> addLauncherNames;

    private final static String FILENAME = ".jpackage.xml";

    private final static Map<Platform, String> PLATFORM_LABELS = Map.of(
            Platform.LINUX, "linux", Platform.WINDOWS, "windows", Platform.MAC,
            "macOS");


    private AppImageFile() {
        this(null, null, null, null);
    }

    private AppImageFile(String launcherName, List<String> addLauncherNames,
            String creatorVersion, String creatorPlatform) {
        this.launcherName = launcherName;
        this.addLauncherNames = addLauncherNames;
        this.creatorVersion = creatorVersion;
        this.creatorPlatform = creatorPlatform;
    }

    /**
     * Returns list of additional launchers configured for the application.
     * Each item in the list is not null or empty string.
     * Returns empty list for application without additional launchers.
     */
    List<String> getAddLauncherNames() {
        return addLauncherNames;
    }

    /**
     * Returns main application launcher name. Never returns null or empty value.
     */
    String getLauncherName() {
        return launcherName;
    }

    void verifyCompatible() throws ConfigException {
        // Just do nothing for now.
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
     * Saves file with application image info in application image.
     * @param appImageDir - path to application image
     * @throws IOException
     */
    static void save(Path appImageDir, Map<String, Object> params)
            throws IOException {
        IOUtils.createXml(getPathInAppImage(appImageDir), xml -> {
            xml.writeStartElement("jpackage-state");
            xml.writeAttribute("version", getVersion());
            xml.writeAttribute("platform", getPlatform());

            xml.writeStartElement("app-version");
            xml.writeCharacters(VERSION.fetchFrom(params));
            xml.writeEndElement();

            xml.writeStartElement("main-launcher");
            xml.writeCharacters(APP_NAME.fetchFrom(params));
            xml.writeEndElement();

            List<Map<String, ? super Object>> addLaunchers =
                ADD_LAUNCHERS.fetchFrom(params);

            for (int i = 0; i < addLaunchers.size(); i++) {
                Map<String, ? super Object> sl = addLaunchers.get(i);
                xml.writeStartElement("add-launcher");
                xml.writeCharacters(APP_NAME.fetchFrom(sl));
                xml.writeEndElement();
            }
        });
    }

    /**
     * Loads application image info from application image.
     * @param appImageDir - path to application image
     * @return valid info about application image or null
     * @throws IOException
     */
    static AppImageFile load(Path appImageDir) throws IOException {
        try {
            Document doc = readXml(appImageDir);

            XPath xPath = XPathFactory.newInstance().newXPath();

            String mainLauncher = xpathQueryNullable(xPath,
                    "/jpackage-state/main-launcher/text()", doc);
            if (mainLauncher == null) {
                // No main launcher, this is fatal.
                return new AppImageFile();
            }

            List<String> addLaunchers = new ArrayList<>();

            String platform = xpathQueryNullable(xPath,
                    "/jpackage-state/@platform", doc);

            String version = xpathQueryNullable(xPath,
                    "/jpackage-state/@version", doc);

            NodeList launcherNameNodes = (NodeList) xPath.evaluate(
                    "/jpackage-state/add-launcher/text()", doc,
                    XPathConstants.NODESET);

            for (int i = 0; i != launcherNameNodes.getLength(); i++) {
                addLaunchers.add(launcherNameNodes.item(i).getNodeValue());
            }

            AppImageFile file = new AppImageFile(
                    mainLauncher, addLaunchers, version, platform);
            if (!file.isValid()) {
                file = new AppImageFile();
            }
            return file;
        } catch (XPathExpressionException ex) {
            // This should never happen as XPath expressions should be correct
            throw new RuntimeException(ex);
        }
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
     * Returns list of launcher names configured for the application.
     * The first item in the returned list is main launcher name.
     * Following items in the list are names of additional launchers.
     */
    static List<String> getLauncherNames(Path appImageDir,
            Map<String, ? super Object> params) {
        List<String> launchers = new ArrayList<>();
        try {
            AppImageFile appImageInfo = AppImageFile.load(appImageDir);
            if (appImageInfo != null) {
                launchers.add(appImageInfo.getLauncherName());
                launchers.addAll(appImageInfo.getAddLauncherNames());
                return launchers;
            }
        } catch (IOException ioe) {
            Log.verbose(ioe);
        }

        launchers.add(APP_NAME.fetchFrom(params));
        ADD_LAUNCHERS.fetchFrom(params).stream().map(APP_NAME::fetchFrom).forEach(
                launchers::add);
        return launchers;
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

    private static String getVersion() {
        return System.getProperty("java.version");
    }

    private static String getPlatform() {
        return PLATFORM_LABELS.get(Platform.getPlatform());
    }

    private boolean isValid() {
        if (launcherName == null || launcherName.length() == 0 ||
            addLauncherNames.indexOf("") != -1) {
            // Some launchers have empty names. This is invalid.
            return false;
        }

        // Add more validation.

        return true;
    }

}
