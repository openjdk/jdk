/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.servicetag;

import java.io.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import static com.sun.servicetag.Util.*;

/**
 * Service Tag Installer for Java SE.
 */
public class Installer {
    // System properties for testing
    private static String SVCTAG_DIR_PATH =
        "servicetag.dir.path";
    private static String SVCTAG_ENABLE_REGISTRATION =
        "servicetag.registration.enabled";
    private final static String SUN_VENDOR = "Sun Microsystems";
    private final static String REGISTRATION_XML = "registration.xml";
    private final static String SERVICE_TAG_FILE = "servicetag";
    private final static String REGISTRATION_HTML_NAME = "register";

    private final static Locale[] knownSupportedLocales =
        new Locale[] { Locale.ENGLISH,
                       Locale.JAPANESE,
                       Locale.SIMPLIFIED_CHINESE};

    private final static String javaHome = System.getProperty("java.home");
    private static File svcTagDir;
    private static File serviceTagFile;
    private static File regXmlFile;
    private static RegistrationData registration;
    private static boolean supportRegistration;
    private static String registerHtmlParent;
    private static Set<Locale> supportedLocales = new HashSet<Locale>();
    private static Properties swordfishProps = null;
    private static String[] jreArchs = null;
    static {
        String dir = System.getProperty(SVCTAG_DIR_PATH);
        if (dir == null) {
            svcTagDir = new File(getJrePath(), "lib" + File.separator + SERVICE_TAG_FILE);
        } else {
            svcTagDir = new File(dir);
        }
        serviceTagFile = new File(svcTagDir, SERVICE_TAG_FILE);
        regXmlFile = new File(svcTagDir, REGISTRATION_XML);
        if (System.getProperty(SVCTAG_ENABLE_REGISTRATION) == null) {
            supportRegistration = isJdk();
        } else {
            supportRegistration = true;
        }
    }

    private Installer() {
    }

    // Implementation of ServiceTag.getJavaServiceTag(String) method
    static ServiceTag getJavaServiceTag(String source) throws IOException {
        if (!System.getProperty("java.vendor").startsWith(SUN_VENDOR)) {
            // Products bundling this implementation may run on
            // Mac OS which is not a Sun JDK
            return null;
        }
        boolean cleanup = false;
        try {
            // Check if we have the swordfish entries for this JRE version
            if (loadSwordfishEntries() == null) {
                return null;
            }

            ServiceTag st = getJavaServiceTag();
            // Check if the service tag created by this bundle owner
            if (st != null && st.getSource().equals(source)) {
                // Install the system service tag if supported
                // stclient may be installed after the service tag creation
                if (Registry.isSupported()) {
                    installSystemServiceTag();
                }
                return st;
            }

            // in case any exception thrown during the cleanup
            cleanup = true;

            // re-create a new one for this bundle owner
            // first delete the registration data
            deleteRegistrationData();
            cleanup = false;

            // create service tag and generate new register.html pages
            return createServiceTag(source);
        } finally {
            if (cleanup) {
                if (regXmlFile.exists()) {
                    regXmlFile.delete();
                }
                if (serviceTagFile.exists()) {
                    serviceTagFile.delete();
                }
            }
        }
    }

    /**
     * Returns the Java SE registration data located in
     * the <JRE>/lib/servicetag/registration.xml by default.
     *
     * @throws IllegalArgumentException if the registration data
     *         is of invalid format.
     */
    private static synchronized RegistrationData getRegistrationData()
            throws IOException {
        if (registration != null) {
            return registration;
        }
        if (regXmlFile.exists()) {
            BufferedInputStream in = null;
            try {
                in = new BufferedInputStream(new FileInputStream(regXmlFile));
                registration = RegistrationData.loadFromXML(in);
            } catch (IllegalArgumentException ex) {
                System.err.println("Error: Bad registration data \"" +
                                    regXmlFile + "\":" + ex.getMessage());
                throw ex;
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } else {
            registration = new RegistrationData();
        }
        return registration;
    }

    /**
     * Write the registration data to the registration.xml file.
     *
     * The offline registration page has to be regenerated with
     * the new registration data.
     *
     * @throws java.io.IOException
     */
    private static synchronized void writeRegistrationXml()
            throws IOException {
        if (!svcTagDir.exists()) {
            // This check is for NetBeans or other products that
            // bundles this com.sun.servicetag implementation for
            // pre-6u5 release.
            if (!svcTagDir.mkdir()) {
                throw new IOException("Failed to create directory: " + svcTagDir);
            }
        }

        // regenerate the new offline registration page
        deleteRegistrationHtmlPage();
        getRegistrationHtmlPage();

        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(regXmlFile));
            getRegistrationData().storeToXML(out);
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: Bad registration data \"" +
                                regXmlFile + "\":" + ex.getMessage());
            throw ex;
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Returns the instance urn(s) stored in the servicetag file
     * or empty set if file not exists.
     */
    private static Set<String> getInstalledURNs() throws IOException {
        Set<String> urnSet = new HashSet<String>();
        if (serviceTagFile.exists()) {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(serviceTagFile));
                String urn;
                while ((urn = in.readLine()) != null) {
                    urn = urn.trim();
                    if (urn.length() > 0) {
                        urnSet.add(urn);
                    }
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }
        return urnSet;
    }

    /**
     * Return the Java SE service tag(s) if it exists.
     * Typically only one Java SE service tag but it could have two for
     * Solaris 32-bit and 64-bit on the same install directory.
     *
     * @return the service tag(s) for Java SE
     */
    private static ServiceTag[] getJavaServiceTagArray() throws IOException {
        RegistrationData regData = getRegistrationData();
        Set<ServiceTag> svcTags = regData.getServiceTags();
        Set<ServiceTag> result = new HashSet<ServiceTag>();

        Properties props = loadSwordfishEntries();
        String jdkUrn = props.getProperty("servicetag.jdk.urn");
        String jreUrn = props.getProperty("servicetag.jre.urn");
        for (ServiceTag st : svcTags) {
            if (st.getProductURN().equals(jdkUrn) ||
                st.getProductURN().equals(jreUrn)) {
                result.add(st);
            }
        }
        return result.toArray(new ServiceTag[0]);
    }

    /**
     * Returns the Java SE service tag for this running platform;
     * or null if not exist.
     * This method will return the 64-bit service tag if the JDK
     * supports both 32-bit and 64-bit if already created.
     */
    private static ServiceTag getJavaServiceTag() throws IOException {
        String definedId = getProductDefinedId();
        for (ServiceTag st : getJavaServiceTagArray()) {
            if (st.getProductDefinedInstanceID().equals(definedId)) {
                return st;
            }
        }
        return null;
    }

    /**
     * Create a service tag for Java SE and install in the system
     * service tag registry if supported.
     *
     * A registration data <JRE>/lib/servicetag/registration.xml
     * will be created to storeToXML the XML entry for Java SE service tag.
     * If the system supports service tags, this method will install
     * the Java SE service tag in the system service tag registry and
     * its <tt>instance_urn</tt> will be stored to <JRE>/lib/servicetag/servicetag.
     *
     * If <JRE>/lib/servicetag/registration.xml exists but is not installed
     * in the system service tag registry (i.e. servicetag doesn't exist),
     * this method will install it as described above.
     *
     * If the system supports service tag, stclient will be used
     * to create the Java SE service tag.
     *
     * A Solaris 32-bit and 64-bit JDK will be installed in the same
     * directory but the registration.xml will have 2 service tags.
     * The servicetag file will also contain 2 instance_urns for that case.
     */
    private static ServiceTag createServiceTag(String svcTagSource)
            throws IOException {
        // determine if a new service tag is needed to be created
        ServiceTag newSvcTag = null;
        if (getJavaServiceTag() == null) {
            newSvcTag = newServiceTag(svcTagSource);
        }

        // Add the new service tag in the registration data
        if (newSvcTag != null) {
            RegistrationData regData = getRegistrationData();

            // Add the service tag to the registration data in JDK/JRE
            newSvcTag = regData.addServiceTag(newSvcTag);

            // add if there is a service tag for the OS
            ServiceTag osTag = SolarisServiceTag.getServiceTag();
            if (osTag != null && regData.getServiceTag(osTag.getInstanceURN()) == null) {
                regData.addServiceTag(osTag);
            }
            // write to the registration.xml
            writeRegistrationXml();
        }

        // Install the system service tag if supported
        if (Registry.isSupported()) {
            installSystemServiceTag();
        }
        return newSvcTag;
    }

    private static void installSystemServiceTag() throws IOException {
        // only install the service tag in the registry if
        // it has permission to write the servicetag file.
        if ((!serviceTagFile.exists() && !svcTagDir.canWrite()) ||
                (serviceTagFile.exists() && !serviceTagFile.canWrite())) {
            return;
        }

        Set<String> urns = getInstalledURNs();
        ServiceTag[] javaSvcTags = getJavaServiceTagArray();
        if (urns.size() < javaSvcTags.length) {
            for (ServiceTag st : javaSvcTags) {
                // Add the service tag in the system service tag registry
                // if not installed
                String instanceURN = st.getInstanceURN();
                if (!urns.contains(instanceURN)) {
                    Registry.getSystemRegistry().addServiceTag(st);
                }
            }
        }
        writeInstalledUrns();
    }

    private static ServiceTag newServiceTag(String svcTagSource) throws IOException {
        // Load the swoRDFish information for the service tag creation
        Properties props = loadSwordfishEntries();

        // Determine the product URN and name
        String productURN;
        String productName;

        if (isJdk()) {
            // <HOME>/jre exists which implies it's a JDK
            productURN = props.getProperty("servicetag.jdk.urn");
            productName = props.getProperty("servicetag.jdk.name");
        } else {
            // Otherwise, it's a JRE
            productURN = props.getProperty("servicetag.jre.urn");
            productName = props.getProperty("servicetag.jre.name");
        }

        return ServiceTag.newInstance(ServiceTag.generateInstanceURN(),
                                      productName,
                                      System.getProperty("java.version"),
                                      productURN,
                                      props.getProperty("servicetag.parent.name"),
                                      props.getProperty("servicetag.parent.urn"),
                                      getProductDefinedId(),
                                      SUN_VENDOR,
                                      System.getProperty("os.arch"),
                                      getZoneName(),
                                      svcTagSource);
    }

    /**
     * Delete the registration data, the offline registration pages and
     * the service tags in the system service tag registry if installed.
     *
     * The registration.xml and servicetag file will be removed.
     */
    private static synchronized void deleteRegistrationData()
            throws IOException {
        try {
            // delete the offline registration page
            deleteRegistrationHtmlPage();

            // Remove the service tag from the system ST registry if exists
            Set<String> urns = getInstalledURNs();
            if (urns.size() > 0 && Registry.isSupported()) {
                for (String u : urns) {
                    Registry.getSystemRegistry().removeServiceTag(u);
                }
            }
            registration = null;
        } finally {
            // Delete the registration.xml and servicetag files if exists
            if (regXmlFile.exists()) {
                if (!regXmlFile.delete()) {
                    throw new IOException("Failed to delete " + regXmlFile);
                }
            }
            if (serviceTagFile.exists()) {
                if (!serviceTagFile.delete()) {
                    throw new IOException("Failed to delete " + serviceTagFile);
                }
            }
        }
    }

    /**
     * Updates the registration data to contain one single service tag
     * for the running Java runtime.
     */
    private static synchronized void updateRegistrationData(String svcTagSource)
            throws IOException {
        RegistrationData regData = getRegistrationData();
        ServiceTag curSvcTag = newServiceTag(svcTagSource);

        ServiceTag[] javaSvcTags = getJavaServiceTagArray();
        Set<String> urns = getInstalledURNs();
        for (ServiceTag st : javaSvcTags) {
            if (!st.getProductDefinedInstanceID().equals(curSvcTag.getProductDefinedInstanceID())) {
                String instanceURN = st.getInstanceURN();
                regData.removeServiceTag(instanceURN);

                // remove it from the system service tag registry if exists
                if (urns.contains(instanceURN) && Registry.isSupported()) {
                    Registry.getSystemRegistry().removeServiceTag(instanceURN);
                }
            }
        }
        writeRegistrationXml();
        writeInstalledUrns();
    }

    private static void writeInstalledUrns() throws IOException {
        // if the Registry is not supported,
        // remove the servicetag file
        if (!Registry.isSupported() && serviceTagFile.exists()) {
            serviceTagFile.delete();
            return;
        }

        PrintWriter out = null;
        try {
            out = new PrintWriter(serviceTagFile);

            ServiceTag[] javaSvcTags = getJavaServiceTagArray();
            for (ServiceTag st : javaSvcTags) {
                // Write the instance_run to the servicetag file
                String instanceURN = st.getInstanceURN();
                out.println(instanceURN);
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Load the values associated with the swoRDFish metadata entries
     * for Java SE. The swoRDFish metadata entries are different for
     * different release.
     *
     * @param version Version of Java SE
     */
    private static synchronized Properties loadSwordfishEntries() throws IOException {
        if (swordfishProps != null) {
            return swordfishProps;
        }

        // The version string for Java SE 6 is 1.6.0
        // We just need the minor number in the version string
        int version = Util.getJdkVersion();

        String filename = "/com/sun/servicetag/resources/javase_" +
                version + "_swordfish.properties";
        InputStream in = Installer.class.getResourceAsStream(filename);
        if (in == null) {
            return null;
        }
        swordfishProps = new Properties();
        try {
            swordfishProps.load(in);
        } finally {
            in.close();
        }
        return swordfishProps;
    }

    /**
     * Returns the product defined instance ID for Java SE.
     * It is a list of comma-separated name/value pairs:
     *    "id=<full-version>  <arch> [<arch>]*"
     *    "dir=<java.home system property value>"
     *
     * where <full-version> is the full version string of the JRE,
     *       <arch> is the architecture that the runtime supports
     *       (i.e. "sparc", "sparcv9", "i386", "amd64" (ISA list))
     *
     * For Solaris, it can be dual mode that can support both
     * 32-bit and 64-bit. the "id" will be set to
     *     "1.6.0_03-b02 sparc sparcv9"
     *
     * The "dir" property is included in the service tag to enable
     * the Service Tag software to determine if a service tag for
     * Java SE is invalid and perform appropriate service tag
     * cleanup if necessary.  See RFE# 6574781 Service Tags Enhancement.
     *
     */
    private static String getProductDefinedId() {
        StringBuilder definedId = new StringBuilder();
        definedId.append("id=");
        definedId.append(System.getProperty("java.runtime.version"));

        String[] archs = getJreArchs();
        for (String name : archs) {
            definedId.append(" " + name);
        }

        String location = ",dir=" + javaHome;
        if ((definedId.length() + location.length()) < 256) {
            definedId.append(",dir=");
            definedId.append(javaHome);
        } else {
            // if it exceeds the limit, we will not include the location
            if (isVerbose()) {
                System.err.println("Warning: Product defined instance ID exceeds the field limit:");
            }
        }

        return definedId.toString();
    }

    /**
     * Returns the architectures that the runtime supports
     *  (i.e. "sparc", "sparcv9", "i386", "amd64" (ISA list))
     * The directory name where libjava.so is located.
     *
     * On Windows, returns the "os.arch" system property value.
     */
    private synchronized static String[] getJreArchs() {
        if (jreArchs != null) {
            return jreArchs;
        }

        Set<String> archs = new HashSet<String>();

        String os = System.getProperty("os.name");
        if (os.equals("SunOS") || os.equals("Linux")) {
            // Traverse the directories under <JRE>/lib.
            // If <JRE>/lib/<arch>/libjava.so exists, add <arch>
            // to the product defined ID
            File dir = new File(getJrePath() + File.separator + "lib");
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (String name : children) {
                    File f = new File(dir, name + File.separator + "libjava.so");
                    if (f.exists()) {
                        archs.add(name);
                    }
                }
            }
        } else {
            // Windows - append the os.arch
            archs.add(System.getProperty("os.arch"));
        }
        jreArchs = archs.toArray(new String[0]);
        return jreArchs;
    }

    /**
     * Return the zonename if zone is supported; otherwise, return
     * "global".
     */
    private static String getZoneName() throws IOException {
        String zonename = "global";

        String command = "/usr/bin/zonename";
        File f = new File(command);
        // com.sun.servicetag package has to be compiled with JDK 5 as well
        // JDK 5 doesn't support the File.canExecute() method.
        // Risk not checking isExecute() for the zonename command is very low.
        if (f.exists()) {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            String output = commandOutput(p);
            if (p.exitValue() == 0) {
                zonename = output.trim();
            }

        }
        return zonename;
    }

    private synchronized static String getRegisterHtmlParent() throws IOException {
        if (registerHtmlParent == null) {
            File htmlDir;    // register.html is put under the JDK directory
            if (getJrePath().endsWith(File.separator + "jre")) {
                htmlDir = new File(getJrePath(), "..");
            } else {
                // j2se non-image build
                htmlDir = new File(getJrePath());
            }

            // initialize the supported locales
            initSupportedLocales(htmlDir);

            // Determine the location of the offline registration page
            String path = System.getProperty(SVCTAG_DIR_PATH);
            if (path == null) {
                // Default is <JDK>/register.html
                registerHtmlParent = htmlDir.getCanonicalPath();
            } else {
                File f = new File(path);
                registerHtmlParent = f.getCanonicalPath();
                if (!f.isDirectory()) {
                    throw new InternalError("Path " + path + " set in \"" +
                            SVCTAG_DIR_PATH + "\" property is not a directory");
                }
            }
        }
        return registerHtmlParent;
    }

    /**
     * Returns the File object of the offline registration page localized
     * for the default locale in the JDK directory.
     */
    static synchronized File getRegistrationHtmlPage() throws IOException {
        if (!supportRegistration) {
            // No register.html page generated if JRE
            return null;
        }

        String parent = getRegisterHtmlParent();

        // check if the offline registration page is already generated
        File f = new File(parent, REGISTRATION_HTML_NAME + ".html");
        if (!f.exists()) {
            // Generate the localized version of the offline registration Page
            generateRegisterHtml(parent);
        }

        String name = REGISTRATION_HTML_NAME;
        Locale locale = getDefaultLocale();
        if (!locale.equals(Locale.ENGLISH) && supportedLocales.contains(locale)) {
            // if the locale is not English and is supported by JDK
            // set to the appropriate offline registration page;
            // otherwise,set to register.html.
            name = REGISTRATION_HTML_NAME + "_" + locale.toString();
        }
        File htmlFile = new File(parent, name + ".html");
        if (isVerbose()) {
            System.out.print("Offline registration page: " + htmlFile);
            System.out.println((htmlFile.exists() ?
                               "" : " not exist. Use register.html"));
        }
        if (htmlFile.exists()) {
            return htmlFile;
        } else {
            return new File(parent,
                            REGISTRATION_HTML_NAME + ".html");
        }
    }

    private static Locale getDefaultLocale() {
        List<Locale> candidateLocales = getCandidateLocales(Locale.getDefault());
        for (Locale l : candidateLocales) {
            if (supportedLocales.contains(l)) {
                return l;
            }
        }
        return Locale.getDefault();
    }

    private static List<Locale> getCandidateLocales(Locale locale) {
        String language = locale.getLanguage();
        String country = locale.getCountry();
        String variant = locale.getVariant();

        List<Locale> locales = new ArrayList<Locale>(3);
        if (variant.length() > 0) {
            locales.add(locale);
        }
        if (country.length() > 0) {
            locales.add((locales.size() == 0) ?
                        locale : new Locale(language, country, ""));
        }
        if (language.length() > 0) {
            locales.add((locales.size() == 0) ?
                        locale : new Locale(language, "", ""));
        }
        return locales;
    }

    // Remove the offline registration pages
    private static void deleteRegistrationHtmlPage() throws IOException {
        String parent = getRegisterHtmlParent();
        if (parent == null) {
            return;
        }

        for (Locale locale : supportedLocales) {
            String name = REGISTRATION_HTML_NAME;
            if (!locale.equals(Locale.ENGLISH)) {
                name += "_" + locale.toString();
            }
            File f = new File(parent, name + ".html");
            if (f.exists()) {
                if (!f.delete()) {
                    throw new IOException("Failed to delete " + f);
                }
            }
        }
    }

    private static void initSupportedLocales(File jdkDir) {
        if (supportedLocales.isEmpty()) {
            // initialize with the known supported locales
            for (Locale l : knownSupportedLocales) {
                supportedLocales.add(l);
            }
        }

        // Determine unknown supported locales if any
        // by finding the localized version of README.html
        // This prepares if a new locale in JDK is supported in
        // e.g. in the OpenSource world
        FilenameFilter ff = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                String fname = name.toLowerCase();
                if (fname.startsWith("readme") && fname.endsWith(".html")) {
                    return true;
                }
                return false;
            }
        };

        String[] readmes = jdkDir.list(ff);
        for (String name : readmes) {
            String basename = name.substring(0, name.length() - ".html".length());
            String[] ss = basename.split("_");
            switch (ss.length) {
                case 1:
                    // English version
                    break;
                case 2:
                    supportedLocales.add(new Locale(ss[1]));
                    break;
                case 3:
                    supportedLocales.add(new Locale(ss[1], ss[2]));
                    break;
                default:
                    // ignore
                    break;
            }
        }
        if (isVerbose()) {
            System.out.println("Supported locales: ");
            for (Locale l : supportedLocales) {
                System.out.println(l);
            }
        }
    }

    private static final String JDK_HEADER_PNG_KEY = "@@JDK_HEADER_PNG@@";
    private static final String JDK_VERSION_KEY = "@@JDK_VERSION@@";
    private static final String REGISTRATION_URL_KEY = "@@REGISTRATION_URL@@";
    private static final String REGISTRATION_PAYLOAD_KEY = "@@REGISTRATION_PAYLOAD@@";

    @SuppressWarnings("unchecked")
    private static void generateRegisterHtml(String parent) throws IOException {
        int version = Util.getJdkVersion();
        int update = Util.getUpdateVersion();
        String jdkVersion = "Version " + version;
        if (update > 0) {
            // product name is not translated
            jdkVersion += " Update " + update;
        }
        RegistrationData regData = getRegistrationData();
        // Make sure it uses the canonical path before getting the URI.
        File img = new File(svcTagDir.getCanonicalPath(), "jdk_header.png");
        String headerImageSrc = img.toURI().toString();

        // Format the registration data in one single line
        StringBuilder payload = new StringBuilder();
        String xml = regData.toString().replaceAll("\"", "%22");
        BufferedReader reader = new BufferedReader(new StringReader(xml));
        try {
            String line = null;
            while ((line = reader.readLine()) != null) {
                payload.append(line.trim());
            }
        } finally {
            reader.close();
        }

        String resourceFilename = "/com/sun/servicetag/resources/register";
        for (Locale locale : supportedLocales) {
            String name = REGISTRATION_HTML_NAME;
            String resource = resourceFilename;
            if (!locale.equals(Locale.ENGLISH)) {
                name += "_" + locale.toString();
                resource += "_" + locale.toString();
            }
            File f = new File(parent, name + ".html");
            InputStream in = null;
            BufferedReader br = null;
            PrintWriter pw = null;
            String registerURL = SunConnection.
                getRegistrationURL(regData.getRegistrationURN(),
                                   locale,
                                   String.valueOf(version)).toString();
            try {
                in = Installer.class.getResourceAsStream(resource + ".html");
                if (in == null) {
                    // if the resource file is missing
                    if (isVerbose()) {
                        System.out.println("Missing resouce file: " + resource + ".html");
                    }
                    continue;
                }
                if (isVerbose()) {
                    System.out.println("Generating " + f + " from " + resource + ".html");
                }

                try {
                    br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    pw = new PrintWriter(f, "UTF-8");
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        String output = line;
                        if (line.contains(JDK_VERSION_KEY)) {
                            output = line.replace(JDK_VERSION_KEY, jdkVersion);
                        } else if (line.contains(JDK_HEADER_PNG_KEY)) {
                            output = line.replace(JDK_HEADER_PNG_KEY, headerImageSrc);
                        } else if (line.contains(REGISTRATION_URL_KEY)) {
                            output = line.replace(REGISTRATION_URL_KEY, registerURL);
                        } else if (line.contains(REGISTRATION_PAYLOAD_KEY)) {
                            output = line.replace(REGISTRATION_PAYLOAD_KEY, payload.toString());
                        }
                        pw.println(output);
                    }
                    f.setReadOnly();
                    pw.flush();
                } finally {
                    // It's safe for this finally block to have two close statements
                    // consecutively as PrintWriter.close doesn't throw IOException.
                    if (pw != null) {
                        pw.close();
                    }
                    if (br!= null) {
                        br.close();
                    }
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }
    }

    private static final int MAX_SOURCE_LEN = 63;

    /**
     * A utility class to create a service tag for Java SE.
     * <p>
     * <b>Usage:</b><br>
     * <blockquote><tt>
     * &lt;JAVA_HOME&gt;/bin/java com.sun.servicetag.Installer
     * </tt></blockquote>
     * <p>
     */
    public static void main(String[] args) {
        String source = "Manual ";
        String runtimeName = System.getProperty("java.runtime.name");
        if (runtimeName.startsWith("OpenJDK")) {
            source = "OpenJDK ";
        }
        source += System.getProperty("java.runtime.version");
        if (source.length() > MAX_SOURCE_LEN) {
            source = source.substring(0, MAX_SOURCE_LEN);
        }

        // Parse the options (arguments starting with "-" )
        boolean delete = false;
        boolean update = false;
        boolean register = false;
        int count = 0;
        while (count < args.length) {
            String arg = args[count];
            if (arg.trim().length() == 0) {
                // skip empty arguments
                count++;
                continue;
            }

            if (arg.equals("-source")) {
                source = args[++count];
            } else if (arg.equals("-delete")) {
                delete = true;
            } else if (arg.equals("-register")) {
                register = true;
            } else {
                usage();
                return;
            }
            count++;
        }
        try {
            if (delete) {
                deleteRegistrationData();
            } else {
                ServiceTag[] javaSvcTags = getJavaServiceTagArray();
                String[] archs = getJreArchs();
                if (javaSvcTags.length > archs.length) {
                    // 64-bit has been uninstalled
                    // so remove the service tag
                    updateRegistrationData(source);
                } else {
                    // create the service tag
                    createServiceTag(source);
                }
            }

            if (register) {
                // Registration is only supported by JDK
                // For testing purpose, override with a "servicetag.enable.registration" property

                RegistrationData regData = getRegistrationData();
                if (supportRegistration && !regData.getServiceTags().isEmpty()) {
                    SunConnection.register(regData,
                                           getDefaultLocale(),
                                           String.valueOf(Util.getJdkVersion()));
                }
            }
            System.exit(0);
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            if (isVerbose()) {
                e.printStackTrace();
            }
        } catch (IllegalArgumentException ex) {
            if (isVerbose()) {
                ex.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (isVerbose()) {
                e.printStackTrace();
            }
        }
        System.exit(1);
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.print("    " + Installer.class.getName());
        System.out.println(" [-delete|-source <source>|-register]");
        System.out.println("       to create a service tag for the Java platform");
        System.out.println("");
        System.out.println("Internal Options:");
        System.out.println("    -source: to specify the source of the service tag to be created");
        System.out.println("    -delete: to delete the service tag ");
        System.out.println("    -register: to register the JDK");
        System.out.println("    -help:   to print this help message");
    }
}
