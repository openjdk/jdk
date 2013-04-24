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

package com.sun.xml.internal.ws.util.exception;

import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessage;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.Localizer;
import com.sun.istack.internal.localization.NullLocalizable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import javax.xml.ws.WebServiceException;

/**
 * Represents a {@link WebServiceException} with
 * localizable message.
 *
 * @author WS Development Team
 */
public abstract class JAXWSExceptionBase
    extends WebServiceException implements Localizable {

    //Don't worry about previous  serialVersionUID = 4818235090198755494L;, this class was not serializable before.
    private static final long serialVersionUID = 1L;

    private transient Localizable msg;

    /**
     * @deprecated
     *      Should use the localizable constructor instead.
     */
    protected JAXWSExceptionBase(String key, Object... args) {
        super(findNestedException(args));
        this.msg = new LocalizableMessage(getDefaultResourceBundleName(), key, args);
    }


    protected JAXWSExceptionBase(String message) {
        this(new NullLocalizable(message));
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

    /**
     * @serialData Default fields,  followed by information in Localizable which comprises of.
     *  ResourceBundle name, then key and followed by arguments array.
     *  If there is no arguments array, then -1 is written.  If there is a argument array (possible of zero
     * length) then the array length is written as an integer, followed by each argument (Object).
     * If the Object is serializable, the argument is written. Otherwise the output of Object.toString()
     * is written.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        // We have to call defaultWriteObject first.
        out.defaultWriteObject();

        out.writeObject(msg.getResourceBundleName());
        out.writeObject(msg.getKey());
        Object[] args = msg.getArguments();
        if (args == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(args.length);
        // Write Object values for the parameters, if it is serializable otherwise write String form of it..
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null || args[i] instanceof Serializable) {
                out.writeObject(args[i]);
            } else {
                out.writeObject(args[i].toString());
            }
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // We have to call defaultReadObject first.
        in.defaultReadObject();
        Object[] args;
        String resourceBundleName = (String) in.readObject();
        String key = (String) in.readObject();
        int len = in.readInt();
        if (len == -1) {
            args = null;
        } else {
            args = new Object[len];
            for (int i = 0; i < args.length; i++) {
                args[i] = in.readObject();
            }
        }
        msg = new LocalizableMessageFactory(resourceBundleName).getMessage(key,args);
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
