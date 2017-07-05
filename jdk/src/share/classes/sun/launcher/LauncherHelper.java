
/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.util.ResourceBundle;
import java.text.MessageFormat;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public enum LauncherHelper {
    INSTANCE;
    private static final String defaultBundleName =
            "sun.launcher.resources.launcher";
    private static ResourceBundle javarb =
            ResourceBundle.getBundle(defaultBundleName);
    private static final String MAIN_CLASS = "Main-Class";

    private static StringBuilder outBuf = new StringBuilder();

    /**
     * A private helper method to get a localized message and also
     * apply any arguments that we might pass.
     */
    private static String getLocalizedMessage(String key, Object... args) {
        String msg = javarb.getString(key);
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
        } catch (Exception e) {
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
        Class<?> rType = method.getReturnType();
        if (!rType.isPrimitive() || !rType.getName().equals("void")) {
            ostream.println(getLocalizedMessage("java.launcher.cls.error3",
                    classname));
            throw new RuntimeException("Main method must return a value" +
                    " of type void in class " +
                    classname);
        }
        return;
    }
}
