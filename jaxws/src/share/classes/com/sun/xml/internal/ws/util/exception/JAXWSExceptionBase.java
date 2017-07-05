/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.util.exception;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableImpl;
import com.sun.xml.internal.ws.util.localization.Localizer;
import com.sun.xml.internal.ws.util.localization.NullLocalizable;

import javax.xml.ws.WebServiceException;

/**
 * Represents a {@link WebServiceException} with
 * localizable message.
 *
 * @author WS Development Team
 */
public abstract class JAXWSExceptionBase
    extends WebServiceException implements Localizable {

    private final Localizable msg;

    /**
     * @deprecated
     *      Should use the localizable constructor instead.
     */
    protected JAXWSExceptionBase(String key, Object... args) {
        super(findNestedException(args));
        this.msg = new LocalizableImpl(key,fixNull(args),getDefaultResourceBundleName());
    }


    protected JAXWSExceptionBase(String message) {
        super(message);
        msg=null;
    }

    private static Object[] fixNull(Object[] x) {
        if(x==null)     return new Object[0];
        else            return x;
    }

    /**
     * Creates a new exception that wraps the specified exception.
     */
    protected JAXWSExceptionBase(Throwable throwable) {
        this(new NullLocalizable(throwable.toString()),throwable);
    }

    protected JAXWSExceptionBase(Localizable msg) {
        this.msg = msg;
    }

    protected JAXWSExceptionBase(Localizable msg, Throwable cause) {
        super(cause);
        this.msg = msg;
    }

    private static Throwable findNestedException(Object[] args) {
        if (args == null)
            return null;

        for( Object o : args )
            if(o instanceof Throwable)
                return (Throwable)o;
        return null;
    }

    public String getMessage() {
        Localizer localizer = new Localizer();
        return localizer.localize(this);
    }

    /**
     * Gets the default resource bundle name for this kind of exception.
     * Used for {@link #JAXWSExceptionBase(String, Object[])}.
     */
    protected abstract String getDefaultResourceBundleName();

//
// Localizable delegation
//
    public final String getKey() {
        return msg.getKey();
    }

    public final Object[] getArguments() {
        return msg.getArguments();
    }

    public final String getResourceBundleName() {
        return msg.getResourceBundleName();
    }
}
