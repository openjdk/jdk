/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @library /test/lib
 * */

import jdk.test.lib.Platform;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RmiTestBase {
    static final String SEP = System.getProperty("file.separator");
    static final String SRC = System.getProperty("test.src");
    static final String DEST = System.getProperty("test.classes");
    static final String SRC_SSL = SRC + SEP + "ssl";
    static final String DEST_SSL = DEST + SEP + "ssl";
    static final String TEST_SRC = "@TEST-SRC@";

    static final String defaultFileNamePrefix =
            System.getProperty("java" + ".home") + SEP + "conf" + SEP + "management" + SEP;

    static final String defaultStoreNamePrefix = SRC + SEP + "ssl" + SEP;

    /**
     * A filter to find all filenames who match <prefix>*<suffix>.
     * Note that <prefix> and <suffix> can overlap.
     **/
    static class FilenameFilterFactory {
        static FilenameFilter prefixSuffix(final String p, final String s) {
            return (dir, name) -> name.startsWith(p) && name.endsWith(s);
        }
    }

    enum AccessControl {
        OWNER,
        EVERYONE,
    }

    /**
     * Default values for RMI configuration properties.
     **/
    public interface DefaultValues {
        String PORT = "0";
        String CONFIG_FILE_NAME = "management.properties";
        String USE_SSL = "true";
        String USE_AUTHENTICATION = "true";
        String PASSWORD_FILE_NAME = "jmxremote.password";
        String ACCESS_FILE_NAME = "jmxremote.access";
        String KEYSTORE = "keystore";
        String KEYSTORE_PASSWD = "password";
        String TRUSTSTORE = "truststore";
        String TRUSTSTORE_PASSWD = "trustword";
        String SSL_NEED_CLIENT_AUTH = "false";
    }

    /**
     * Names of RMI configuration properties.
     **/
    public interface PropertyNames {
        String PORT = "com.sun.management.jmxremote.port";
        String CONFIG_FILE_NAME = "com.sun.management.config.file";
        String USE_SSL = "com.sun.management.jmxremote.ssl";
        String USE_AUTHENTICATION = "com.sun.management.jmxremote.authenticate";
        String PASSWORD_FILE_NAME = "com.sun.management.jmxremote.password.file";
        String ACCESS_FILE_NAME = "com.sun.management.jmxremote.access.file";
        String INSTRUMENT_ALL = "com.sun.management.instrumentall";
        String CREDENTIALS = "jmx.remote.credentials";
        String KEYSTORE = "javax.net.ssl.keyStore";
        String KEYSTORE_PASSWD = "javax.net.ssl.keyStorePassword";
        String KEYSTORE_TYPE = "javax.net.ssl.keyStoreType";
        String TRUSTSTORE = "javax.net.ssl.trustStore";
        String TRUSTSTORE_PASSWD = "javax.net.ssl.trustStorePassword";
        String SSL_ENABLED_CIPHER_SUITES = "com.sun.management.jmxremote.ssl.enabled.cipher.suites";
        String SSL_ENABLED_PROTOCOLS = "com.sun.management.jmxremote.ssl.enabled.protocols";
        String SSL_NEED_CLIENT_AUTH = "com.sun.management.jmxremote.ssl.need.client.auth";
        String SSL_CLIENT_ENABLED_CIPHER_SUITES = "javax.rmi.ssl.client.enabledCipherSuites";
    }

    /**
     * Copy test artifacts to test folder.
     *
     * @param filenamePattern the filename pattern to look for
     * @return files who match the filename pattern
     * @throws IOException if error occurs
     */
    static List<Path> prepareTestFiles(String filenamePattern) throws IOException {
        copySsl();
        List<Path> files = Utils.findFiles(Paths.get(SRC), (dir, name) -> name.matches(filenamePattern));

        final Function<String, String> removeSuffix = (s) -> s.substring(0, s.lastIndexOf("."));

        List<Path> propertyFiles =
                Utils.copyFiles(files, Paths.get(DEST), removeSuffix, StandardCopyOption.REPLACE_EXISTING);

        // replace @TEST-SRC@ with the path of the current test folder
        if (Platform.isWindows()) {
            // On Windows, also replace forward slash or single backslash to double backslashes
            Utils.replaceFilesString(propertyFiles,
                    (s) -> s.replace(TEST_SRC, DEST).replaceAll("[/\\\\]", "\\\\\\\\"));
        } else {
            Utils.replaceFilesString(propertyFiles, (s) -> s.replace(TEST_SRC, DEST));
        }

        grantFilesAccess(propertyFiles, AccessControl.OWNER);

        return Collections.unmodifiableList(files);
    }

    /**
     * Grant file access.
     *
     * @param file   file to grant access
     * @param access user access or full access
     * @throws IOException if error occurs
     */
    static void grantAccess(Path file, AccessControl access) throws IOException {
        Set<String> attr = file.getFileSystem().supportedFileAttributeViews();
        if (attr.contains("posix")) {
            String perms = access == AccessControl.OWNER ? "rw-------" : "rwxrwxrwx";
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(perms));
        } else if (attr.contains("acl")) {
            AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
            List<AclEntry> acl = new ArrayList<>();
            for (AclEntry thisEntry : view.getAcl()) {
                if (access == AccessControl.OWNER) {
                    if (thisEntry.principal().getName().equals(view.getOwner().getName())) {
                        acl.add(Utils.allowAccess(thisEntry));
                    } else if (thisEntry.type() == AclEntryType.ALLOW) {
                        acl.add(Utils.revokeAccess(thisEntry));
                    } else {
                        acl.add(thisEntry);
                    }
                } else {
                    if (!thisEntry.principal().getName().contains("NULL SID")
                            && thisEntry.type() != AclEntryType.ALLOW) {
                        acl.add(Utils.allowAccess(thisEntry));
                    } else {
                        acl.add(thisEntry);
                    }
                }
            }
            view.setAcl(acl);
        } else {
            throw new RuntimeException("Unsupported file attributes: " + attr);
        }
    }

    /**
     * Grant files' access.
     *
     * @param files  files to grant access
     * @param access user access or full access
     * @throws IOException if error occurs
     */
    static void grantFilesAccess(List<Path> files, AccessControl access) throws IOException {
        for (Path thisFile : files) {
            grantAccess(thisFile, access);
        }
    }

    /**
     * Copy SSL files to test folder.
     *
     * @throws IOException
     */
    static void copySsl() throws IOException {
        Path sslSource = Paths.get(SRC_SSL);
        Path sslTarget = Paths.get(DEST_SSL);

        List<Path> files = Arrays.stream(sslSource.toFile().listFiles()).map(File::toPath).collect(Collectors.toList());
        Utils.copyFiles(files, sslTarget, StandardCopyOption.REPLACE_EXISTING);

        for (Path file : files) {
            grantAccess(sslTarget.resolve(file.getFileName()), AccessControl.EVERYONE);
        }
    }

    /**
     * Get all "management*ok.properties" files in the directory
     * indicated by the "test.src" management property.
     *
     * @param useSsl boolean that indicates if test uses SSL
     * @return configuration files
     **/
    static File[] findConfigurationFilesOk(boolean useSsl) {
        String prefix = useSsl ? "management_ssltest" : "management_test";
        return findAllConfigurationFiles(prefix, "ok.properties");
    }

    /**
     * Get all "management*ko.properties" files in the directory
     * indicated by the "test.src" management property.
     *
     * @param useSsl boolean that indicates if test uses SSL
     * @return configuration files
     **/
    static File[] findConfigurationFilesKo(boolean useSsl) {
        String prefix = useSsl ? "management_ssltest" : "management_test";
        return findAllConfigurationFiles(prefix, "ko.properties");
    }

    /**
     * Get all "management*.properties" files in the directory
     * indicated by the "test.src" management property.
     *
     * @param useSsl boolean that indicates if test uses SSL
     * @return configuration files
     **/
    static File[] findAllConfigurationFiles(boolean useSsl) {
        String prefix = useSsl ? "management_ssltest" : "management_test";
        return findAllConfigurationFiles(prefix, "properties");
    }

    /**
     * Get all "management*.properties" files in the directory
     * indicated by the "test.src" management property.
     *
     * @param prefix filename prefix
     * @param suffix filename suffix
     * @return configuration files
     **/
    static File[] findAllConfigurationFiles(String prefix, String suffix) {
        final File dir = new File(DEST);
        final FilenameFilter filter = FilenameFilterFactory.prefixSuffix(prefix, suffix);
        return dir.listFiles(filter);
    }
}
