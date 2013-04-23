/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.resources;

import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class JavacompilerMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.javacompiler");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableJAVACOMPILER_CLASSPATH_ERROR(Object arg0) {
        return messageFactory.getMessage("javacompiler.classpath.error", arg0);
    }

    /**
     * {0} is not available in the classpath, requires Sun's JDK version 5.0 or latter.
     *
     */
    public static String JAVACOMPILER_CLASSPATH_ERROR(Object arg0) {
        return localizer.localize(localizableJAVACOMPILER_CLASSPATH_ERROR(arg0));
    }

    public static Localizable localizableJAVACOMPILER_NOSUCHMETHOD_ERROR(Object arg0) {
        return messageFactory.getMessage("javacompiler.nosuchmethod.error", arg0);
    }

    /**
     * There is no such method {0} available, requires Sun's JDK version 5.0 or latter.
     *
     */
    public static String JAVACOMPILER_NOSUCHMETHOD_ERROR(Object arg0) {
        return localizer.localize(localizableJAVACOMPILER_NOSUCHMETHOD_ERROR(arg0));
    }

    public static Localizable localizableJAVACOMPILER_ERROR(Object arg0) {
        return messageFactory.getMessage("javacompiler.error", arg0);
    }

    /**
     * error : {0}.
     *
     */
    public static String JAVACOMPILER_ERROR(Object arg0) {
        return localizer.localize(localizableJAVACOMPILER_ERROR(arg0));
    }

}
