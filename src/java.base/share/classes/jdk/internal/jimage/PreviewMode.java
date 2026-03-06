/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jimage;

import java.lang.reflect.InvocationTargetException;

/**
 * Specifies the preview mode used to open a jimage file via {@link ImageReader}.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 * */
public enum PreviewMode {
    /**
     * Preview mode is disabled. No preview classes or resources will be available
     * in this mode.
     */
    DISABLED,
    /**
     * Preview mode is enabled. If preview classes or resources exist in the jimage file,
     * they will be made available.
     */
    ENABLED,
    /**
     * The preview mode of the current run-time, typically determined by the
     * {@code --enable-preview} flag.
     */
    FOR_RUNTIME;

    /**
     * Resolves whether preview mode should be enabled for an {@link ImageReader}.
     */
    public boolean isPreviewModeEnabled() {
        // A switch, instead of an abstract method, saves 3 subclasses.
        switch (this) {
            case DISABLED:
                return false;
            case ENABLED:
                return true;
            case FOR_RUNTIME:
                // We want to call jdk.internal.misc.PreviewFeatures.isEnabled(), but
                // is not available in older JREs, so we must look to it reflectively.
                Class<?> clazz;
                try {
                    clazz = Class.forName("jdk.internal.misc.PreviewFeatures");
                } catch (ClassNotFoundException e) {
                    // It is valid and expected that the class might not exist (JDK-8).
                    return false;
                }
                try {
                    return (Boolean) clazz.getDeclaredMethod("isEnabled").invoke(null);
                } catch (NoSuchMethodException | IllegalAccessException |
                         InvocationTargetException e) {
                    // But if the class exists, the method must exist and be callable.
                    throw new ExceptionInInitializerError(e);
                }
            default:
                throw new IllegalStateException("Invalid mode: " + this);
        }
    }
}
