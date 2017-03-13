/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.istack.internal.localization;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * {@link Localizable} that wraps a non-localizable string.
 *
 * @author WS Development Team
 */
public final class NullLocalizable implements Localizable {
    private final String msg;

    public NullLocalizable(String msg) {
        if(msg==null)
            throw new IllegalArgumentException();
        this.msg = msg;
    }

    @Override
    public String getKey() {
        return Localizable.NOT_LOCALIZABLE;
    }
    @Override
    public Object[] getArguments() {
        return new Object[]{msg};
    }
    @Override
    public String getResourceBundleName() {
        return "";
    }
    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
        return null;
    }
}
