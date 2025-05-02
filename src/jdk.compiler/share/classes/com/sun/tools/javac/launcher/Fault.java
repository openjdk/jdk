/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.launcher;

import com.sun.tools.javac.util.JCDiagnostic.Error;

import java.io.Serial;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * A runtime exception used to report errors.
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
public final class Fault extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 2L;

    private static final String BUNDLE_NAME = "com.sun.tools.javac.resources.launcher";

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private static final String ERROR_PREFIX = RESOURCE_BUNDLE.getString("launcher.error");

    /**
     * Returns a localized string from a resource bundle.
     *
     * @param error the error for which to get the localized text
     * @return the localized string
     */
    private static String getMessage(Error error) {
        String key = error.key();
        Object[] args = error.getArgs();
        try {
            String resource = RESOURCE_BUNDLE.getString(key);
            String message = MessageFormat.format(resource, args);
            return ERROR_PREFIX + message;
        } catch (MissingResourceException e) {
            return "Cannot access resource; " + key + Arrays.toString(args);
        }
    }

    Fault(Error error) {
        super(getMessage(error));
    }
}
