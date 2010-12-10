/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.launcher;

/*
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.
 *  If you write code that depends on this, you do so at your own
 *  risk.  This code and its internal interfaces are subject to change
 *  or deletion without notice.</b>
 *
 */

/**
 * A utility package for the java(1), javaw(1) launchers.
 * The following are helper methods that the native launcher uses
 * to perform checks etc. using JNI, see src/share/bin/java.c
 */
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ResourceBundle;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public enum LauncherHelper {
    INSTANCE;
    private static final String defaultBundleName =
            "sun.launcher.resources.launcher";
    private static final String MAIN_CLASS = "Main-Class";

    private static StringBuilder outBuf = new StringBuilder();

    private static ResourceBundle javarb = null;

    private static final String INDENT = "    ";
    private static final String VM_SETTINGS     = "VM settings:";
    private static final String PROP_SETTINGS   = "Property settings:";
    private static final String LOCALE_SETTINGS = "Locale settings:";

    private static final long K = 1024;
    private static final long M = K * K;
    private static final long G = M * K;
    private static final long T = G * K;

    private static synchronized ResourceBundle getLauncherResourceBundle() {
        if (javarb == null) {
            javarb = ResourceBundle.getBundle(defaultBundleName);
        }
        return javarb;
    }

    /*
     * A method called by the launcher to print out the standard settings,
     * by default -XshowSettings is equivalent to -XshowSettings:all,
     * Specific information may be gotten by using suboptions with possible
     * values vm, properties and locale.
     *
     * printToStderr: choose between stdout and stderr
     *
     * optionFlag: specifies which options to print default is all other
     *    possible values are vm, properties, locale.
     *
     * maxHeapSize: in bytes, as set by the launcher, a zero-value indicates
     *    this code should determine this value, using a suitable method.
     *
     * stackSize: in bytes, as set by the launcher, a zero-value indicates
     * this code determine this value, using a suitable method.
     */
    static void showSettings(boolean printToStderr, String optionFlag,
            long maxHeapSize, long stackSize, boolean isServer) {

        PrintStream ostream = (printToStderr) ? System.err : System.out;
        String opts[] = optionFlag.split(":");
        String optStr = (opts.length > 1 && opts[1] != null)
                ? opts[1].trim()
                : "all";
        switch (optStr) {
            case "vm":
                printVmSettings(ostream, maxHeapSize, stackSize, isServer);
                break;
            case "properties":
                printProperties(ostream);
                break;
            case "locale":
                printLocale(ostream);
                break;
            default:
                printVmSettings(ostream, maxHeapSize, stackSize, isServer);
                printProperties(ostream);
                printLocale(ostream);
                break;
        }
    }

    /*
     * prints the main vm settings subopt/section
     */
    private static void printVmSettings(PrintStream ostream, long maxHeapSize,
            long stackSize, boolean isServer) {

        ostream.println(VM_SETTINGS);
        if (stackSize != 0L) {
            ostream.println(INDENT + "Stack Size: " + scaleValue(stackSize));
        }
        if (maxHeapSize != 0L) {
            ostream.println(INDENT + "Max. Heap Size: " + scaleValue(maxHeapSize));
        } else {
            ostream.println(INDENT + "Max. Heap Size (Estimated): "
                    + scaleValue(Runtime.getRuntime().maxMemory()));
        }
        ostream.println(INDENT + "Ergonomics Machine Class: "
                + ((isServer) ? "server" : "client"));
        ostream.println(INDENT + "Using VM: "
                + System.getProperty("java.vm.name"));
        ostream.println();
    }

    /*
     * scale the incoming values to a human readable form, represented as
     * K, M, G and T, see java.c parse_size for the scaled values and
     * suffixes.
     */

    private static String scaleValue(double v) {
        MathContext mc2 = new MathContext(3, RoundingMode.HALF_EVEN);

        if (v >= K && v < M) {
            return (new BigDecimal(v / K, mc2)).toPlainString() + "K";
        } else if (v >= M && v < G) {
            return (new BigDecimal(v / M, mc2)).toPlainString() + "M";
        } else if (v >= G && v < T) {
            return (new BigDecimal(v / G, mc2)).toPlainString() + "G";
        } else if (v >= T) {
            return (new BigDecimal(v / T, mc2)).toPlainString() + "T";
        } else {
            return String.format("%.0f", v);
        }
    }

    /*
     * prints the properties subopt/section
     */
    private static void printProperties(PrintStream ostream) {
        Properties p = System.getProperties();
        ostream.println(PROP_SETTINGS);
        List<String> sortedPropertyKeys = new ArrayList<>();
        sortedPropertyKeys.addAll(p.stringPropertyNames());
        Collections.sort(sortedPropertyKeys);
        for (String x : sortedPropertyKeys) {
            printPropertyValue(ostream, x, p.getProperty(x));
        }
        ostream.println();
    }

    private static boolean isPath(String key) {
        return key.endsWith(".dirs") || key.endsWith(".path");
    }

    private static void printPropertyValue(PrintStream ostream,
            String key, String value) {
        ostream.print(INDENT + key + " = ");
        if (key.equals("line.separator")) {
            byte[] bytes = value.getBytes();
            for (byte b : bytes) {
                switch (b) {
                    case 0xd:
                        ostream.print("CR ");
                        break;
                    case 0xa:
                        ostream.print("LF ");
                        break;
                    default:
                        ostream.printf("0x%02X", b & 0xff);
                        break;
                }
            }
            ostream.println();
            return;
        }
        if (!isPath(key)) {
            ostream.println(value);
            return;
        }
        // pretty print the path values as a list
        String[] values = value.split(System.getProperty("path.separator"));
        int len = values.length;
        for (int i = 0 ; i < len ; i++) {
            if (i == 0) { // first line treated specially
                ostream.println(values[i]);
            } else { // following lines prefix with indents
                ostream.print(INDENT + INDENT);
                ostream.println(values[i]);
            }
        }
    }

    /*
     * prints the locale subopt/section
     */
    private static void printLocale(PrintStream ostream) {
        Locale locale = Locale.getDefault();
        ostream.println(LOCALE_SETTINGS);
        ostream.println(INDENT + "default locale = " + locale.getDisplayLanguage());
        printLocales(ostream);
        ostream.println();
    }

    private static void printLocales(PrintStream ostream) {
        Locale[] locales = Locale.getAvailableLocales();
        final int len = locales == null ? 0 : locales.length;
        if (len < 1 ) {
            return;
        }
        ostream.print(INDENT + "available locales = ");
        final int last = len - 1 ;
        for (int i = 0; i < last ; i++) {
            ostream.print(locales[i]);
            if (i != last) {
                ostream.print(", ");
            }
            // print columns of 8
            if ((i + 1) % 8 == 0) {
                ostream.println();
                ostream.print(INDENT + INDENT);
            }
        }
        ostream.println(locales[last]);
    }

    /**
     * A private helper method to get a localized message and also
     * apply any arguments that we might pass.
     */
    private static String getLocalizedMessage(String key, Object... args) {
        String msg = getLauncherResourceBundle().getString(key);
        return (args != null) ? MessageFormat.format(msg, args) : msg;
    }

    /**
     * The java -help message is split into 3 parts, an invariant, followed
     * by a set of platform dependent variant messages, finally an invariant
     * set of lines.
     * This method initializes the help message for the first time, and also
     * assembles the invariant header part of the message.
     */
    static void initHelpMessage(String progname) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.header",
                (progname == null) ? "java" : progname ));
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.datamodel",
                32));
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.datamodel",
                64));
    }

    /**
     * Appends the vm selection messages to the header, already created.
     * initHelpSystem must already be called.
     */
    static void appendVmSelectMessage(String vm1, String vm2) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.vmselect",
                vm1, vm2));
    }

    /**
     * Appends the vm synoym message to the header, already created.
     * initHelpSystem must be called before using this method.
     */
    static void appendVmSynonymMessage(String vm1, String vm2) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.hotspot",
                vm1, vm2));
    }

    /**
     * Appends the vm Ergo message to the header, already created.
     * initHelpSystem must be called before using this method.
     */
    static void appendVmErgoMessage(boolean isServerClass, String vm) {
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.ergo.message1",
                vm));
        outBuf = (isServerClass)
             ? outBuf.append(",\n" +
                getLocalizedMessage("java.launcher.ergo.message2") + "\n\n")
             : outBuf.append(".\n\n");
    }

    /**
     * Appends the last invariant part to the previously created messages,
     * and finishes up the printing to the desired output stream.
     * initHelpSystem must be called before using this method.
     */
    static void printHelpMessage(boolean printToStderr) {
        PrintStream ostream = (printToStderr) ? System.err : System.out;
        outBuf = outBuf.append(getLocalizedMessage("java.launcher.opt.footer",
                File.pathSeparator));
        ostream.println(outBuf.toString());
    }

    /**
     * Prints the Xusage text to the desired output stream.
     */
    static void printXUsageMessage(boolean printToStderr) {
        PrintStream ostream =  (printToStderr) ? System.err : System.out;
        ostream.println(getLocalizedMessage("java.launcher.X.usage",
                File.pathSeparator));
    }

    static String getMainClassFromJar(String jarname) throws IOException {
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(jarname);
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                throw new IOException("manifest not found in " + jarname);
            }
            Attributes mainAttrs = manifest.getMainAttributes();
            if (mainAttrs == null) {
                throw new IOException("no main mainifest attributes, in " +
                        jarname);
            }
            return mainAttrs.getValue(MAIN_CLASS).trim();
        } finally {
            if (jarFile != null) {
                jarFile.close();
            }
        }
    }

    /**
     * This method does the following:
     * 1. gets the classname from a Jar's manifest, if necessary
     * 2. loads the class using the System ClassLoader
     * 3. ensures the availability and accessibility of the main method,
     *    using signatureDiagnostic method.
     *    a. does the class exist
     *    b. is there a main
     *    c. is the main public
     *    d. is the main static
     *    c. does the main take a String array for args
     * 4. and off we go......
     *
     * @param printToStderr
     * @param isJar
     * @param name
     * @return
     * @throws java.io.IOException
     */
    public static Object checkAndLoadMain(boolean printToStderr,
            boolean isJar, String name) throws IOException {
        // get the class name
        String classname = (isJar) ? getMainClassFromJar(name) : name;
        classname = classname.replace('/', '.');
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> clazz = null;
        PrintStream ostream = (printToStderr) ? System.err : System.out;
        try {
            clazz = loader.loadClass(classname);
        } catch (ClassNotFoundException cnfe) {
            ostream.println(getLocalizedMessage("java.launcher.cls.error1", classname));
            NoClassDefFoundError ncdfe = new NoClassDefFoundError(classname);
            ncdfe.initCause(cnfe);
            throw ncdfe;
        }
        signatureDiagnostic(ostream, clazz);
        return clazz;
    }

    static void signatureDiagnostic(PrintStream ostream, Class<?> clazz) {
        String classname = clazz.getName();
        Method method = null;
        try {
            method = clazz.getMethod("main", String[].class);
        } catch (NoSuchMethodException nsme) {
            ostream.println(getLocalizedMessage("java.launcher.cls.error4",
                    classname));
            throw new RuntimeException("Main method not found in " + classname);
        }
        /*
         * getMethod (above) will choose the correct method, based
         * on its name and parameter type, however, we still have to
         * ensure that the method is static and returns a void.
         */
        int mod = method.getModifiers();
        if (!Modifier.isStatic(mod)) {
            ostream.println(getLocalizedMessage("java.launcher.cls.error2",
                    "static", classname));
            throw new RuntimeException("Main method is not static in class " +
                    classname);
        }
        if (method.getReturnType() != java.lang.Void.TYPE) {
            ostream.println(getLocalizedMessage("java.launcher.cls.error3",
                    classname));
            throw new RuntimeException("Main method must return a value" +
                    " of type void in class " +
                    classname);
        }
        return;
    }
}
