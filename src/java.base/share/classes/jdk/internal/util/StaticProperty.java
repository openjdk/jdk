/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import java.util.Properties;
import java.nio.charset.Charset;

/**
 * System Property access for internal use only.
 * Read-only access to System property values initialized during Phase 1
 * are cached.  Setting, clearing, or modifying the value using
 * {@link System#setProperty} or {@link System#getProperties()} is ignored.
 * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
 * in these access methods. The caller of these methods should take care to ensure
 * that the returned property is not made accessible to untrusted code.</strong>
 */
public final class StaticProperty {

    // The class static initialization is triggered to initialize these final
    // fields during init Phase 1 and before a security manager is set.
    private static final String JAVA_HOME;
    private static final String USER_HOME;
    private static final String USER_DIR;
    private static final String USER_NAME;
    private static final String JAVA_LIBRARY_PATH;
    private static final String SUN_BOOT_LIBRARY_PATH;
    private static final String JDK_SERIAL_FILTER;
    private static final String JDK_SERIAL_FILTER_FACTORY;
    private static final String JAVA_IO_TMPDIR;
    private static final String NATIVE_ENCODING;
    private static final String FILE_ENCODING;
    private static final String JAVA_PROPERTIES_DATE;
    private static final String SUN_JNU_ENCODING;
    private static final Charset jnuCharset;

    private StaticProperty() {}

    static {
        Properties props = System.getProperties();
        JAVA_HOME = getProperty(props, "java.home");
        USER_HOME = getProperty(props, "user.home");
        USER_DIR  = getProperty(props, "user.dir");
        USER_NAME = getProperty(props, "user.name");
        JAVA_IO_TMPDIR = getProperty(props, "java.io.tmpdir");
        JAVA_LIBRARY_PATH = getProperty(props, "java.library.path", "");
        SUN_BOOT_LIBRARY_PATH = getProperty(props, "sun.boot.library.path", "");
        JDK_SERIAL_FILTER = getProperty(props, "jdk.serialFilter", null);
        JDK_SERIAL_FILTER_FACTORY = getProperty(props, "jdk.serialFilterFactory", null);
        NATIVE_ENCODING = getProperty(props, "native.encoding");
        FILE_ENCODING = getProperty(props, "file.encoding");
        JAVA_PROPERTIES_DATE = getProperty(props, "java.properties.date", null);
        SUN_JNU_ENCODING = getProperty(props, "sun.jnu.encoding");
        jnuCharset = Charset.forName(SUN_JNU_ENCODING, Charset.defaultCharset());
    }

    private static String getProperty(Properties props, String key) {
        String v = props.getProperty(key);
        if (v == null) {
            throw new InternalError("null property: " + key);
        }
        return v;
    }

    private static String getProperty(Properties props, String key,
                                      String defaultVal) {
        String v = props.getProperty(key);
        return (v == null) ? defaultVal : v;
    }

    /**
     * {@return the {@code java.home} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String javaHome() {
        return JAVA_HOME;
    }

    /**
     * {@return the {@code user.home} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String userHome() {
        return USER_HOME;
    }

    /**
     * {@return the {@code user.dir} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String userDir() {
        return USER_DIR;
    }

    /**
     * {@return the {@code user.name} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String userName() {
        return USER_NAME;
    }

    /**
     * {@return the {@code java.library.path} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String javaLibraryPath() {
        return JAVA_LIBRARY_PATH;
    }

    /**
     * {@return the {@code java.io.tmpdir} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String javaIoTmpDir() {
        return JAVA_IO_TMPDIR;
    }

    /**
     * {@return the {@code sun.boot.library.path} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String sunBootLibraryPath() {
        return SUN_BOOT_LIBRARY_PATH;
    }


    /**
     * {@return the {@code jdk.serialFilter} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String jdkSerialFilter() {
        return JDK_SERIAL_FILTER;
    }


    /**
     * {@return the {@code jdk.serialFilterFactory} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String jdkSerialFilterFactory() {
        return JDK_SERIAL_FILTER_FACTORY;
    }

    /**
     * {@return the {@code native.encoding} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String nativeEncoding() {
        return NATIVE_ENCODING;
    }

    /**
     * {@return the {@code file.encoding} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String fileEncoding() {
        return FILE_ENCODING;
    }

    /**
     * {@return the {@code java.properties.date} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method.</strong>
     */
    public static String javaPropertiesDate() {
        return JAVA_PROPERTIES_DATE;
    }

    /**
     * {@return the {@code sun.jnu.encoding} system property}
     *
     * <strong>{@link SecurityManager#checkPropertyAccess} is NOT checked
     * in this method. The caller of this method should take care to ensure
     * that the returned property is not made accessible to untrusted code.</strong>
     */
    public static String jnuEncoding() {
        return SUN_JNU_ENCODING;
    }

    /**
     * {@return {@code Charset} for {@code sun.jnu.encoding} system property}
     *
     * <strong>If {@code sun.jnu.encoding} system property has invalid
     * encoding name, {@link Charset#defaultCharset()} is returned.</strong>
     */
    public static Charset jnuCharset() {
        return jnuCharset;
    }
}
