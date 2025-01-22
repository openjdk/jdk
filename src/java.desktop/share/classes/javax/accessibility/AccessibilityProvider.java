/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.accessibility;

/**
 * Service Provider Interface (SPI) for Assistive Technology.
 * <p>
 * This service provider class provides mappings from the platform specific
 * accessibility APIs to the Java Accessibility API.
 * <p>
 * Each service provider implementation is named and can be activated via the
 * {@link #activate} method. Service providers can be loaded when the default
 * {@link java.awt.Toolkit toolkit} is initialized.
 *
 * @apiNote There will typically be one provider per platform, such as Windows
 *          or Linux, to support accessibility for screen readers and
 *          magnifiers. However, more than one service provider can be
 *          activated. For example, a test tool which provides visual results
 *          obtained by interrogating the Java Accessibility API can be
 *          activated along with the activation of the support for screen
 *          readers and screen magnifiers.
 * @see java.awt.Toolkit#getDefaultToolkit
 * @see java.util.ServiceLoader
 * @since 9
 */
public abstract class AccessibilityProvider {

    /**
     * Initializes a new accessibility provider.
     */
    protected AccessibilityProvider() {
    }

    /**
     * Returns the name of this service provider. This name is used to locate a
     * requested service provider.
     *
     * @return the name of this service provider
     */
    public abstract String getName();

    /**
     * Activates the support provided by this service provider.
     */
    public abstract void activate();
}
