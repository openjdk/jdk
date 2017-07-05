/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.locale.provider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.spi.LocaleServiceProvider;

/**
 * LocaleProviderAdapter implementation for the host locale data.
 * Currently it is only implemented on Windows Vista or later.
 *
 * @author Naoto Sato
 */
public class HostLocaleProviderAdapter extends AuxLocaleProviderAdapter {

    /**
     * Returns the type of this LocaleProviderAdapter
     */
    @Override
    public LocaleProviderAdapter.Type getAdapterType() {
        return LocaleProviderAdapter.Type.HOST;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <P extends LocaleServiceProvider> P findInstalledProvider(final Class<P> c) {
        try {
            Method getter = HostLocaleProviderAdapterImpl.class.getMethod(
                    "get" + c.getSimpleName(), (Class<?>[]) null);
            return (P)getter.invoke(null, (Object[]) null);
        }  catch (NoSuchMethodException |
                  IllegalAccessException |
                  IllegalArgumentException |
                  InvocationTargetException ex) {
            LocaleServiceProviderPool.config(HostLocaleProviderAdapter.class, ex.toString());
        }
        return null;
    }
}
