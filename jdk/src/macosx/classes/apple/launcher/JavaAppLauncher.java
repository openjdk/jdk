/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package apple.launcher;

import java.io.*;
import java.lang.reflect.*;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.*;
import java.util.jar.*;

import javax.swing.*;

class JavaAppLauncher implements Runnable {
    static {
        java.security.AccessController.doPrivileged((PrivilegedAction<?>)new sun.security.action.LoadLibraryAction("osx"));
    }

    private static native <T> T nativeConvertAndRelease(final long ptr);
    private static native void nativeInvokeNonPublic(Class<? extends Method> cls, Method m, String[] args);

    // entry point from native
    static void launch(final long javaDictionaryPtr, final boolean verbose) {
        final Map<String, ?> javaDictionary = nativeConvertAndRelease(javaDictionaryPtr);
        (new JavaAppLauncher(javaDictionary, verbose)).run();
    }

        // these are the values for the enumeration JavaFailureMode
        static final String kJavaFailureMainClassNotSpecified = "MainClassNotSpecified";
        static final String kJavaFailureMainClassNotFound = "CannotLoadMainClass";
        static final String kJavaFailureMainClassHasNoMain = "NoMainMethod";
        static final String kJavaFailureMainClassMainNotStatic = "MainNotStatic";
        static final String kJavaFailureMainThrewException = "MainThrewException";
        static final String kJavaFailureMainInitializerException = "MainInitializerException";

        final boolean verbose; // Normally set by environment variable JAVA_LAUNCHER_VERBOSE.
        final Map<String, ?> javaDictionary;

        JavaAppLauncher(final Map<String, ?> javaDictionary, final boolean verbose) {
                this.verbose = verbose;
                this.javaDictionary = javaDictionary;
        }

        @Override
        public void run() {
                final Method m = loadMainMethod(getMainMethod());
                final String methodName = m.getDeclaringClass().getName() + ".main(String[])";
                try {
                        log("Calling " + methodName + " method");
                        m.invoke(null, new Object[] { getArguments() });
                        log(methodName + " has returned");
                } catch (final IllegalAccessException x) {
                        try {
                                nativeInvokeNonPublic(m.getClass(), m, getArguments());
                        } catch (final Throwable excpt) {
                                logError(methodName + " threw an exception:");
                                if ((excpt instanceof UnsatisfiedLinkError) && excpt.getMessage().equals("nativeInvokeNonPublic")) {
                                        showFailureAlertAndKill(kJavaFailureMainThrewException, "nativeInvokeNonPublic not registered");
                                } else {
                                        excpt.printStackTrace();
                                        showFailureAlertAndKill(kJavaFailureMainThrewException, excpt.toString());
                                }
                        }
                } catch (final InvocationTargetException invokeExcpt) {
                        logError(methodName + " threw an exception:");
                        invokeExcpt.getTargetException().printStackTrace();
                        showFailureAlertAndKill(kJavaFailureMainThrewException, invokeExcpt.getTargetException().toString());
                }
        }

        Method loadMainMethod(final String mainClassName) {
                try {
                        final Class<?> mainClass = Class.forName(mainClassName, true, sun.misc.Launcher.getLauncher().getClassLoader());
                        final Method mainMethod = mainClass.getDeclaredMethod("main", new Class[] { String[].class });
                        if ((mainMethod.getModifiers() & Modifier.STATIC) == 0) {
                                logError("The main(String[]) method of class " + mainClassName + " is not static!");
                                showFailureAlertAndKill(kJavaFailureMainClassMainNotStatic, mainClassName);
                        }
                        return mainMethod;
                } catch (final ExceptionInInitializerError x) {
                        logError("The main class \"" + mainClassName + "\" had a static initializer throw an exception.");
                        x.getException().printStackTrace();
                        showFailureAlertAndKill(kJavaFailureMainInitializerException, x.getException().toString());
                } catch (final ClassNotFoundException x) {
                        logError("The main class \"" + mainClassName + "\" could not be found.");
                        showFailureAlertAndKill(kJavaFailureMainClassNotFound, mainClassName);
                } catch (final NoSuchMethodException x) {
                        logError("The main class \"" + mainClassName + "\" has no static main(String[]) method.");
                        showFailureAlertAndKill(kJavaFailureMainClassHasNoMain, mainClassName);
                } catch (final NullPointerException x) {
                        logError("No main class specified");
                        showFailureAlertAndKill(kJavaFailureMainClassNotSpecified, null);
                }

                return null;
        }

        // get main class name from 'Jar' key, or 'MainClass' key
        String getMainMethod() {
                final Object javaJar = javaDictionary.get("Jar");
                if (javaJar != null) {
                        if (!(javaJar instanceof String)) {
                                logError("'Jar' key in 'Java' sub-dictionary of Info.plist requires a string value");
                                return null;
                        }

                        final String jarPath = (String)javaJar;
                        if (jarPath.length() == 0) {
                                log("'Jar' key of sub-dictionary 'Java' of Info.plist key is empty");
                        } else {
                                // extract main class from manifest of this jar
                                final String main = getMainFromManifest(jarPath);
                                if (main == null) {
                                        logError("jar file '" + jarPath + "' does not have Main-Class: attribute in its manifest");
                                        return null;
                                }

                                log("Main class " + main + " found in jar manifest");
                                return main;
                        }
                }

                final Object javaMain = javaDictionary.get("MainClass");
                if (!(javaMain instanceof String)) {
                        logError("'MainClass' key in 'Java' sub-dictionary of Info.plist requires a string value");
                        return null;
                }

                final String main = (String)javaMain;
                if (main.length() == 0) {
                        log("'MainClass' key of sub-dictionary 'Java' of Info.plist key is empty");
                        return null;
                }

                log("Main class " + (String)javaMain + " found via 'MainClass' key of sub-dictionary 'Java' of Info.plist key");
                return (String)javaMain;
        }

        // get arguments for main(String[]) out of Info.plist and command line
        String[] getArguments() {
                // check for 'Arguments' key, which contains the main() args if not defined in Info.plist
                final Object javaArguments = javaDictionary.get("Arguments");
                if (javaArguments == null) {
                        // no arguments
                        log("No arguments for main(String[]) specified");
                        return new String[0];
                }

                if (javaArguments instanceof List) {
                        final List<?> args = (List<?>)javaArguments;
                        final int count = args.size();
                        log("Arguments to main(String[" + count + "]):");

                        final String[] result = new String[count];
                        for (int i = 0; i < count; ++i) {
                                final Object element = args.get(i);
                                if (element instanceof String) {
                                        result[i] = (String)element;
                                } else {
                                        logError("Found non-string in array");
                                }
                                log("   arg[" + i + "]=" + result[i]);
                        }
                        return result;
                }

                logError("'Arguments' key in 'Java' sub-dictionary of Info.plist requires a string value or an array of strings");
                return new String[0];
        }

        // returns name of main class, or null
        String getMainFromManifest(final String jarpath) {
                JarFile jar = null;
                try {
                        jar = new JarFile(jarpath);
                        final Manifest man = jar.getManifest();
                        final Attributes attr = man.getMainAttributes();
                        return attr.getValue("Main-Class");
                } catch (final IOException x) {
                        // shrug
                } finally {
                        if (jar != null) {
                                try {
                                        jar.close();
                                } catch (final IOException x) { }
                        }
                }
                return null;
        }

        void log(final String s) {
                if (!verbose) return;
                System.out.println("[LaunchRunner] " + s);
        }

        static void logError(final String s) {
                System.err.println("[LaunchRunner Error] " + s);
        }

        // This kills the app and does not return!
        static void showFailureAlertAndKill(final String msg, String arg) {
                if (arg == null) arg = "<<null>>";
                JOptionPane.showMessageDialog(null, getMessage(msg, arg), "", JOptionPane.ERROR_MESSAGE);
                System.exit(-1);
        }

        static String getMessage(final String msgKey, final Object ... args) {
            final String msg = ResourceBundle.getBundle("appLauncherErrors").getString(msgKey);
            return MessageFormat.format(msg, args);
        }
}
