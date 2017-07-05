/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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


// common infrastructure for SunPKCS11 tests

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

import java.security.*;

public abstract class PKCS11Test {

    // directory of the test source
    static final String BASE = System.getProperty("test.src", ".");

    static final char SEP = File.separatorChar;

    private final static String REL_CLOSED = "../../../../closed/sun/security/pkcs11".replace('/', SEP);

    // directory corresponding to BASE in the /closed hierarchy
    static final String CLOSED_BASE;

    static {
        // hack
        String absBase = new File(BASE).getAbsolutePath();
        int k = absBase.indexOf(SEP + "test" + SEP + "sun" + SEP);
        if (k < 0) k = 0;
        String p1 = absBase.substring(0, k + 6);
        String p2 = absBase.substring(k + 5);
        CLOSED_BASE = p1 + "closed" + p2;
    }

    static String NSPR_PREFIX = "";

    // NSS version info
    public static enum ECCState { None, Basic, Extended };
    static double nss_version = -1;
    static ECCState nss_ecc_status = ECCState.Extended;

    // The NSS library we need to search for in getNSSLibDir()
    // Default is "libsoftokn3.so", listed as "softokn3"
    // The other is "libnss3.so", listed as "nss3".
    static String nss_library = "softokn3";

    static Provider getSunPKCS11(String config) throws Exception {
        Class clazz = Class.forName("sun.security.pkcs11.SunPKCS11");
        Constructor cons = clazz.getConstructor(new Class[] {String.class});
        Object obj = cons.newInstance(new Object[] {config});
        return (Provider)obj;
    }

    public abstract void main(Provider p) throws Exception;

    private void premain(Provider p) throws Exception {
        long start = System.currentTimeMillis();
        System.out.println("Running test with provider " + p.getName() + "...");
        main(p);
        long stop = System.currentTimeMillis();
        System.out.println("Completed test with provider " + p.getName() + " (" + (stop - start) + " ms).");
    }

    public static void main(PKCS11Test test) throws Exception {
        Provider[] oldProviders = Security.getProviders();
        try {
            System.out.println("Beginning test run " + test.getClass().getName() + "...");
            testDefault(test);
            testNSS(test);
            testDeimos(test);
        } finally {
            // NOTE: Do not place a 'return' in any finally block
            // as it will suppress exceptions and hide test failures.
            Provider[] newProviders = Security.getProviders();
            boolean found = true;
            // Do not restore providers if nothing changed. This is especailly
            // useful for ./Provider/Login.sh, where a SecurityManager exists.
            if (oldProviders.length == newProviders.length) {
                found = false;
                for (int i = 0; i<oldProviders.length; i++) {
                    if (oldProviders[i] != newProviders[i]) {
                        found = true;
                        break;
                    }
                }
            }
            if (found) {
                for (Provider p: newProviders) {
                    Security.removeProvider(p.getName());
                }
                for (Provider p: oldProviders) {
                    Security.addProvider(p);
                }
            }
        }
    }

    public static void testDeimos(PKCS11Test test) throws Exception {
        if (new File("/opt/SUNWconn/lib/libpkcs11.so").isFile() == false ||
            "true".equals(System.getProperty("NO_DEIMOS"))) {
            return;
        }
        String base = getBase();
        String p11config = base + SEP + "nss" + SEP + "p11-deimos.txt";
        Provider p = getSunPKCS11(p11config);
        test.premain(p);
    }

    public static void testDefault(PKCS11Test test) throws Exception {
        // run test for default configured PKCS11 providers (if any)

        if ("true".equals(System.getProperty("NO_DEFAULT"))) {
            return;
        }

        Provider[] providers = Security.getProviders();
        for (int i = 0; i < providers.length; i++) {
            Provider p = providers[i];
            if (p.getName().startsWith("SunPKCS11-")) {
                test.premain(p);
            }
        }
    }

    private static String PKCS11_BASE;
    static {
        try {
            PKCS11_BASE = getBase();
        } catch (Exception e) {
            // ignore
        }
    }

    private final static String PKCS11_REL_PATH = "sun/security/pkcs11";

    public static String getBase() throws Exception {
        if (PKCS11_BASE != null) {
            return PKCS11_BASE;
        }
        File cwd = new File(System.getProperty("test.src", ".")).getCanonicalFile();
        while (true) {
            File file = new File(cwd, "TEST.ROOT");
            if (file.isFile()) {
                break;
            }
            cwd = cwd.getParentFile();
            if (cwd == null) {
                throw new Exception("Test root directory not found");
            }
        }
        PKCS11_BASE = new File(cwd, PKCS11_REL_PATH.replace('/', SEP)).getAbsolutePath();
        return PKCS11_BASE;
    }

    public static String getNSSLibDir() throws Exception {
        Properties props = System.getProperties();
        String osName = props.getProperty("os.name");
        if (osName.startsWith("Win")) {
            osName = "Windows";
            NSPR_PREFIX = "lib";
        }
        String osid = osName + "-"
                + props.getProperty("os.arch") + "-" + props.getProperty("sun.arch.data.model");
        String[] nssLibDirs = osMap.get(osid);
        if (nssLibDirs == null) {
            System.out.println("Unsupported OS, skipping: " + osid);
            return null;
        }
        if (nssLibDirs.length == 0) {
            System.out.println("NSS not supported on this platform, skipping test");
            return null;
        }
        String nssLibDir = null;
        for (String dir : nssLibDirs) {
            if (new File(dir).exists() &&
                new File(dir + System.mapLibraryName(nss_library)).exists()) {
                nssLibDir = dir;
                System.setProperty("pkcs11test.nss.libdir", nssLibDir);
                break;
            }
        }
        return nssLibDir;
    }

    protected static void safeReload(String lib) throws Exception {
        try {
            System.load(lib);
        } catch (UnsatisfiedLinkError e) {
            if (e.getMessage().contains("already loaded")) {
                return;
            }
        }
    }

    static boolean loadNSPR(String libdir) throws Exception {
        // load NSS softoken dependencies in advance to avoid resolver issues
        safeReload(libdir + System.mapLibraryName(NSPR_PREFIX + "nspr4"));
        safeReload(libdir + System.mapLibraryName(NSPR_PREFIX + "plc4"));
        safeReload(libdir + System.mapLibraryName(NSPR_PREFIX + "plds4"));
        safeReload(libdir + System.mapLibraryName("sqlite3"));
        safeReload(libdir + System.mapLibraryName("nssutil3"));
        return true;
    }

    // Check the provider being used is NSS
    public static boolean isNSS(Provider p) {
        return p.getName().toUpperCase().equals("SUNPKCS11-NSS");
    }

    static double getNSSVersion() {
        if (nss_version == -1)
            getNSSInfo();
        return nss_version;
    }

    static ECCState getNSSECC() {
        if (nss_version == -1)
            getNSSInfo();
        return nss_ecc_status;
    }

    /* Read the library to find out the verison */
    static void getNSSInfo() {
        String nssHeader = "$Header: NSS";
        boolean found = false;
        String s = null;
        int i = 0;
        String libfile = "";

        try {
            libfile = getNSSLibDir() + System.mapLibraryName(nss_library);
            FileInputStream is = new FileInputStream(libfile);
            byte[] data = new byte[1000];
            int read = 0;

            while (is.available() > 0) {
                if (read == 0) {
                    read = is.read(data, 0, 1000);
                } else {
                    // Prepend last 100 bytes in case the header was split
                    // between the reads.
                    System.arraycopy(data, 900, data, 0, 100);
                    read = 100 + is.read(data, 100, 900);
                }

                s = new String(data, 0, read);
                if ((i = s.indexOf(nssHeader)) > 0) {
                    found = true;
                    // If the nssHeader is before 920 we can break, otherwise
                    // we may not have the whole header so do another read.  If
                    // no bytes are in the stream, that is ok, found is true.
                    if (i < 920) {
                        break;
                    }
                }
            }

            is.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!found) {
            System.out.println("NSS version not found, set to 0.0: "+libfile);
            nss_version = 0.0;
            return;
        }

        // the index after whitespace after nssHeader
        int afterheader = s.indexOf("NSS", i) + 4;
        String version = s.substring(afterheader, s.indexOf(' ', afterheader));

        // If a "dot dot" release, strip the extra dots for double parsing
        String[] dot = version.split("\\.");
        if (dot.length > 2) {
            version = dot[0]+"."+dot[1];
            for (int j = 2; dot.length > j; j++) {
                version += dot[j];
            }
        }

        // Convert to double for easier version value checking
        try {
            nss_version = Double.parseDouble(version);
        } catch (NumberFormatException e) {
            System.out.println("Failed to parse NSS version. Set to 0.0");
            e.printStackTrace();
        }

        System.out.print("NSS version = "+version+".  ");

        // Check for ECC
        if (s.indexOf("Basic") > 0) {
            nss_ecc_status = ECCState.Basic;
            System.out.println("ECC Basic.");
        } else if (s.indexOf("Extended") > 0) {
            nss_ecc_status = ECCState.Extended;
            System.out.println("ECC Extended.");
        }
    }

    // Used to set the nss_library file to search for libsoftokn3.so
    public static void useNSS() {
        nss_library = "nss3";
    }

    public static void testNSS(PKCS11Test test) throws Exception {
        String libdir = getNSSLibDir();
        if (libdir == null) {
            return;
        }
        String base = getBase();

        if (loadNSPR(libdir) == false) {
            return;
        }

        String libfile = libdir + System.mapLibraryName(nss_library);

        String customDBdir = System.getProperty("CUSTOM_DB_DIR");
        String dbdir = (customDBdir != null) ?
                                customDBdir :
                                base + SEP + "nss" + SEP + "db";
        // NSS always wants forward slashes for the config path
        dbdir = dbdir.replace('\\', '/');

        String customConfig = System.getProperty("CUSTOM_P11_CONFIG");
        String customConfigName = System.getProperty("CUSTOM_P11_CONFIG_NAME", "p11-nss.txt");
        String p11config = (customConfig != null) ?
                                customConfig :
                                base + SEP + "nss" + SEP + customConfigName;

        System.setProperty("pkcs11test.nss.lib", libfile);
        System.setProperty("pkcs11test.nss.db", dbdir);
        Provider p = getSunPKCS11(p11config);
        test.premain(p);
    }


    private static final Map<String,String[]> osMap;

    // Location of the NSS libraries on each supported platform
    static {
        osMap = new HashMap<String,String[]>();
        osMap.put("SunOS-sparc-32", new String[]{"/usr/lib/mps/"});
        osMap.put("SunOS-sparcv9-64", new String[]{"/usr/lib/mps/64/"});
        osMap.put("SunOS-x86-32", new String[]{"/usr/lib/mps/"});
        osMap.put("SunOS-amd64-64", new String[]{"/usr/lib/mps/64/"});
        osMap.put("Linux-i386-32", new String[]{
            "/usr/lib/i386-linux-gnu/", "/usr/lib/"});
        osMap.put("Linux-amd64-64", new String[]{
            "/usr/lib/x86_64-linux-gnu/", "/usr/lib/x86_64-linux-gnu/nss/",
            "/usr/lib64/"});
        osMap.put("Windows-x86-32", new String[]{
            PKCS11_BASE + "/nss/lib/windows-i586/".replace('/', SEP)});
        osMap.put("Windows-amd64-64", new String[]{
            PKCS11_BASE + "/nss/lib/windows-amd64/".replace('/', SEP)});
    }

    private final static char[] hexDigits = "0123456789abcdef".toCharArray();

    public static String toString(byte[] b) {
        if (b == null) {
            return "(null)";
        }
        StringBuffer sb = new StringBuffer(b.length * 3);
        for (int i = 0; i < b.length; i++) {
            int k = b[i] & 0xff;
            if (i != 0) {
                sb.append(':');
            }
            sb.append(hexDigits[k >>> 4]);
            sb.append(hexDigits[k & 0xf]);
        }
        return sb.toString();
    }

    public static byte[] parse(String s) {
        if (s.equals("(null)")) {
            return null;
        }
        try {
            int n = s.length();
            ByteArrayOutputStream out = new ByteArrayOutputStream(n / 3);
            StringReader r = new StringReader(s);
            while (true) {
                int b1 = nextNibble(r);
                if (b1 < 0) {
                    break;
                }
                int b2 = nextNibble(r);
                if (b2 < 0) {
                    throw new RuntimeException("Invalid string " + s);
                }
                int b = (b1 << 4) | b2;
                out.write(b);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int nextNibble(StringReader r) throws IOException {
        while (true) {
            int ch = r.read();
            if (ch == -1) {
                return -1;
            } else if ((ch >= '0') && (ch <= '9')) {
                return ch - '0';
            } else if ((ch >= 'a') && (ch <= 'f')) {
                return ch - 'a' + 10;
            } else if ((ch >= 'A') && (ch <= 'F')) {
                return ch - 'A' + 10;
            }
        }
    }

    <T> T[] concat(T[] a, T[] b) {
        if ((b == null) || (b.length == 0)) {
            return a;
        }
        T[] r = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), a.length + b.length);
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

}
