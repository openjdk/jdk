/*
 * Copyright 2008 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 *
 * The Java Kernel Bundle security check.
 *
 * This class is responsible for detail of creating, storing, dispensing, and
 * updating bundle security checks and security checks for all the files
 * extracted from a bundle. Security checks are cryptographic
 * hashcodes that make it impractical to counterfeit a file. The security
 * check algorithm is defined by peer class StandaloneMessageDigest. The
 * cryptographic
 * hashcodes are held in instances of this class as byte arrays and externally
 * as hexidecimal string values for Bundle name Property keys. The properties
 * are a resource in the Java Kernel core JRE rt.jar and accessed after a
 * real or simulated bundle download by peer classes DownloadManager and
 * Bundle. Build-time deployment class SplitJRE uses this class to create file
 * security checks directly and via a special execution of DownloadManager.
 * The main method of this class can be used to create a
 * new set of security codes and updated properties for a given JRE path
 * and set of bundle names (CWD assume to contain bundle files as <name>.zip).
 *
 * This is a Sun internal class defined by the Sun implementation and
 * intended for JRE/JDK release deployment.
 *
 * @see sun.jkernel.DownloadManager
 * @see sun.jkernel.Bundle
 * @see sun.jkernel.StandaloneSHA
 * @see sun.jkernel.ByteArrayToFromHexDigits
 * See also deploy/src/kernel/share/classes/sun/kernel/SplitJRE.java
 */

package sun.jkernel;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;


public class BundleCheck {

    /* File buffer size */

    private static final int DIGEST_STREAM_BUFFER_SIZE = 2048;

    /* The bundle filename suffix */

    private static final String BUNDLE_SUFFIX = ".zip";

    /* Mutable static state. */

    /* Properties (Bundle name/check hex String pairs) for a set of Bundles.
       Guarded by this class' object. */

    private static volatile Properties properties;

    /* Mutable instance state. */

    /**
     * The bytes of the check value. Guarded by the bundle Mutex (in
     * sun.jkernel.DownloadManager) or the fact that sun.kernel.SplitJRE
     * and/or DownloadManager with "-download all" runs a single thread.
     */

    private byte[] checkBytes;

    /* Prevent instantiation by default constructor */

    private BundleCheck(){}

    /**
     * Store the bundle check values as properties to the path specified.
     * Only invoked by SplitJRE.
     */

    public static void storeProperties(String fullPath)  {

        try {
            File f = new File(fullPath);
            f.getParentFile().mkdirs();
            OutputStream out = new FileOutputStream(f);
            properties.store(out, null);
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(
                "BundleCheck: storing properties threw: " + e);
        }
    }

    /**
     * Fetch the check value properties as a DownloadManager resource.
     */

    private static void loadProperties()  {
        properties = new Properties();
        try {
            InputStream in = new BufferedInputStream(
                DownloadManager.class.getResourceAsStream(
                DownloadManager.CHECK_VALUES_FILE));
            if (in == null)
                throw new RuntimeException("BundleCheck: unable to locate " +
                    DownloadManager.CHECK_VALUES_FILE + " as resource");
            properties.load(in);
            in.close();
        } catch (Exception e) {
            throw new RuntimeException("BundleCheck: loadProperties threw " +
                e);
        }
    }

    /* Get the check value Properties object */

    private synchronized static Properties getProperties() {
        if (properties == null) {
            // If this fails it means addProperty has been used inappropriately
            loadProperties();
        }
        return properties;
    }

    /* Reset the properties with an empty Properties object */

    public static void resetProperties() {
        properties = null;
    }

    /* The BundleCheck expressed as a String */

    public String toString() {
        return ByteArrayToFromHexDigits.bytesToHexString(checkBytes);
    }

    /* Add the given BundleCheck as a property to bundleCheckvalueProperties */

    private void addProperty(String name) {
        // When first called by SplitJRE just start with empty object
        // rather than allowing a load to happen, as it does at install time.
        if (properties == null) {
           properties = new Properties();
        }
        getProperties().put(name, toString());
    }

    /* private ctor for creating/initializing a BundleCheck */

    private BundleCheck(byte[] checkBytes) {
        this.checkBytes = checkBytes;
    }

    /* private ctor for creating a BundleCheck with a given name and known
       Property value. */

    private BundleCheck(String name) {
        String hexString = getProperties().getProperty(name);
        if  (hexString == null) {
            throw new RuntimeException(
                "BundleCheck: no check property for bundle: " + name);
        }
        this.checkBytes = ByteArrayToFromHexDigits.hexStringToBytes(hexString);
    }

    /* Make a BundleCheck from the contents of the given file or a Bundle
       name. Save the new object's value as a property if saveProperty is
       true. Behavior is only defined for name or file being null, but not
       both, and for saveProperty to be true only when both name and file
       are not null.
       Any IO or other exception implies an unexpected and fatal internal
       error and results in a RuntimeException.  */

    private static BundleCheck getInstance(String name,
        File file, boolean saveProperty) {
        if (file == null ) {
            return new BundleCheck(name);

        } else {
            StandaloneMessageDigest checkDigest = null;
            try {
                FileInputStream checkFileStream = new FileInputStream(file);
                checkDigest = StandaloneMessageDigest.getInstance("SHA-1");

                // Compute a check code across all of the file bytes.
                // NOTE that every time a bundle is created, even from
                // the "same bits", it may be different wrt to the security
                // code because of slight variations build to build. For
                // example, the JVM build normally contains an
                // auto-incrementing build number, built archives might have
                // timestamps, etc.

                int readCount;
                byte[] messageStreamBuff =
                    new byte[DIGEST_STREAM_BUFFER_SIZE];
                do {
                    readCount = checkFileStream.read(messageStreamBuff);
                    if (readCount > 0) {
                        checkDigest.update(messageStreamBuff,0,readCount);
                    }
                } while (readCount != -1);
                checkFileStream.close();

            } catch (Exception e) {
                throw new RuntimeException(
                    "BundleCheck.addProperty() caught: " + e);
            }
            BundleCheck bc = new BundleCheck(checkDigest.digest());
            if (saveProperty) {
                bc.addProperty(name);
            }
            return bc;
        }
    }

    /* Create a BundleCheck from the given file */

    public static BundleCheck getInstance(File file) {
        return getInstance(null, file, false);
    }

    /* Create a BundleCheck from the given bundle name */

    static BundleCheck getInstance(String name) {
        return getInstance(name, null, false);
    }

    /* Create a BundleCheck from the given bundle name and file and
       use it to make and save a security check Property value. */

    public static void addProperty(String name,  File file) {
        getInstance(name, file, true);
    }

    /* Create a bundlecheck from the given bundle name and file and
       add a Property value for it. */

    static void add(String name, File file) {
        getInstance(name, file, true).addProperty(name);
    }

    /* Compare two BundkCheck instances for equal check values */

    boolean equals(BundleCheck b) {
        if ((checkBytes == null) || (b.checkBytes == null)) {
            return false;
        }
        if (checkBytes.length != b.checkBytes.length) {
            return false;
        }
        for (int i = 0; i < checkBytes.length; i++) {
            if (checkBytes[i] != b.checkBytes[i]) {
                if (DownloadManager.debug) {
                    System.out.println(
                        "BundleCheck.equals mismatch between this: " +
                        toString() + " and param: " + b.toString());
                }
                return false;
            }
         }
         return true;
    }

    /* After SplitJRE is used to restructure the JRE into a "core JRE" and
       a set of Java Kernel "bundles", if extra compression is available
       the bundles are extracted and rearchived with zero compression by
       deploy build make steps. The newly compressed bundle names are then
       passed to this main with the path of the kernel core JRE to have new
       bundle security check values computed and the corresponding properties
       updated in rt.jar. If extra compression isn't available then this main is
       never used and the default jar/zip bundle compression and security
       codes created by SplitJRE are left in place and ready to use. */

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java BundleCheck <jre path> " +
                "<bundle 1 name> ... <bundle N name>");
            return;
        }

        // Make a security check code for each bundle file
        for (int arg = 1; arg < args.length; arg++) {
            BundleCheck.addProperty(args[arg],
                new File(args[arg] + BUNDLE_SUFFIX));
        }

        // Store the new check code properties below the current directory
        BundleCheck.storeProperties(DownloadManager.CHECK_VALUES_DIR);

        // Now swap the new properties file into the core rt.jar
        try {
            int status = Runtime.getRuntime().exec(
                "jar uf " + args[0] + "\\lib\\rt.jar " +
                DownloadManager.CHECK_VALUES_DIR).waitFor();
            if (status != 0) {
                System.err.println(
                    "BundleCheck: exec of jar uf gave nonzero status");
                return;
            }
        } catch (Exception e) {
            System.err.println("BundleCheck: exec of jar uf threw: " + e);
            return;
        }
    } // main
}
