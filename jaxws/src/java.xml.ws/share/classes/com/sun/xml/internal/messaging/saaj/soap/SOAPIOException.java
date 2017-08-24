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

/**
 * Created on Nov 19, 2002
 *
 * To change this generated comment edit the template variable "filecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of file comments go to
 * Window>Preferences>Java>Code Generation.
 */
package com.sun.xml.internal.messaging.saaj.soap;

import java.io.*;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;

public class SOAPIOException extends IOException {
    SOAPExceptionImpl soapException;

    public SOAPIOException() {
        super();
        soapException = new SOAPExceptionImpl();
        soapException.fillInStackTrace();
    }

    public SOAPIOException(String s) {
        super();
        soapException = new SOAPExceptionImpl(s);
        soapException.fillInStackTrace();
    }

    public SOAPIOException(String reason, Throwable cause) {
        super();
        soapException = new SOAPExceptionImpl(reason, cause);
        soapException.fillInStackTrace();
    }

    public SOAPIOException(Throwable cause) {
        super(cause.toString());
        soapException = new SOAPExceptionImpl(cause);
        soapException.fillInStackTrace();
    }

    @Override
    public Throwable fillInStackTrace() {
        if (soapException != null) {
            soapException.fillInStackTrace();
        }
        return this;
    }

    @Override
    public String getLocalizedMessage() {
        return soapException.getLocalizedMessage();
    }

    @Override
    public String getMessage() {
        return soapException.getMessage();
    }

    @Override
    public void printStackTrace() {
        soapException.printStackTrace();
    }

    @Override
    public void printStackTrace(PrintStream s) {
        soapException.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        soapException.printStackTrace(s);
    }

    @Override
    public String toString() {
        return soapException.toString();
    }

}
