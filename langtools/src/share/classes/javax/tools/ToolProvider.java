/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.tools;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.logging.Level;
import static java.util.logging.Level.*;

/**
 * Provides methods for locating tool providers, for example,
 * providers of compilers.  This class complements the
 * functionality of {@link java.util.ServiceLoader}.
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
public class ToolProvider {

    private ToolProvider() {}

    private static final String propertyName = "sun.tools.ToolProvider";
    private static final String loggerName   = "javax.tools";

    /*
     * Define the system property "sun.tools.ToolProvider" to enable
     * debugging:
     *
     *     java ... -Dsun.tools.ToolProvider ...
     */
    static <T> T trace(Level level, Object reason) {
        // NOTE: do not make this method private as it affects stack traces
        try {
            if (System.getProperty(propertyName) != null) {
                StackTraceElement[] st = Thread.currentThread().getStackTrace();
                String method = "???";
                String cls = ToolProvider.class.getName();
                if (st.length > 2) {
                    StackTraceElement frame = st[2];
                    method = String.format((Locale)null, "%s(%s:%s)",
                                           frame.getMethodName(),
                                           frame.getFileName(),
                                           frame.getLineNumber());
                    cls = frame.getClassName();
                }
                Logger logger = Logger.getLogger(loggerName);
                if (reason instanceof Throwable) {
                    logger.logp(level, cls, method,
                                reason.getClass().getName(), (Throwable)reason);
                } else {
                    logger.logp(level, cls, method, String.valueOf(reason));
                }
            }
        } catch (SecurityException ex) {
            System.err.format((Locale)null, "%s: %s; %s%n",
                              ToolProvider.class.getName(),
                              reason,
                              ex.getLocalizedMessage());
        }
        return null;
    }

    /**
     * Gets the Java&trade; programming language compiler provided
     * with this platform.
     * @return the compiler provided with this platform or
     * {@code null} if no compiler is provided
     */
    public static JavaCompiler getSystemJavaCompiler() {
        if (Lazy.compilerClass == null)
            return trace(WARNING, "Lazy.compilerClass == null");
        try {
            return Lazy.compilerClass.newInstance();
        } catch (Throwable e) {
            return trace(WARNING, e);
        }
    }

    /**
     * Returns the class loader for tools provided with this platform.
     * This does not include user-installed tools.  Use the
     * {@linkplain java.util.ServiceLoader service provider mechanism}
     * for locating user installed tools.
     *
     * @return the class loader for tools provided with this platform
     * or {@code null} if no tools are provided
     */
    public static ClassLoader getSystemToolClassLoader() {
        if (Lazy.compilerClass == null)
            return trace(WARNING, "Lazy.compilerClass == null");
        return Lazy.compilerClass.getClassLoader();
    }

    /**
     * This class will not be initialized until one of the above
     * methods are called.  This ensures that searching for the
     * compiler does not affect platform start up.
     */
    static class Lazy  {
        private static final String defaultJavaCompilerName
            = "com.sun.tools.javac.api.JavacTool";
        private static final String[] defaultToolsLocation
            = { "lib", "tools.jar" };
        static final Class<? extends JavaCompiler> compilerClass;
        static {
            Class<? extends JavaCompiler> c = null;
            try {
                c = findClass().asSubclass(JavaCompiler.class);
            } catch (Throwable t) {
                trace(WARNING, t);
            }
            compilerClass = c;
        }

        private static Class<?> findClass()
            throws MalformedURLException, ClassNotFoundException
        {
            try {
                return enableAsserts(Class.forName(defaultJavaCompilerName, false, null));
            } catch (ClassNotFoundException e) {
                trace(FINE, e);
            }
            File file = new File(System.getProperty("java.home"));
            if (file.getName().equalsIgnoreCase("jre"))
                file = file.getParentFile();
            for (String name : defaultToolsLocation)
                file = new File(file, name);
            URL[] urls = {file.toURI().toURL()};
            trace(FINE, urls[0].toString());
            ClassLoader cl = URLClassLoader.newInstance(urls);
            cl.setPackageAssertionStatus("com.sun.tools.javac", true);
            return Class.forName(defaultJavaCompilerName, false, cl);
        }

        private static Class<?> enableAsserts(Class<?> cls) {
            try {
                ClassLoader loader = cls.getClassLoader();
                if (loader != null)
                    loader.setPackageAssertionStatus("com.sun.tools.javac", true);
                else
                    trace(FINE, "loader == null");
            } catch (SecurityException ex) {
                trace(FINE, ex);
            }
            return cls;
        }
    }
}
