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

package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.tools.internal.ws.resources.WscompileMessages;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import org.xml.sax.SAXParseException;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.UnknownHostException;

public class ConsoleErrorReporter extends ErrorReceiver {

    private boolean hasError;
    private PrintStream output;
    private boolean debug;

    public ConsoleErrorReporter(PrintStream stream) {
        this.output = stream;
    }

    public ConsoleErrorReporter(OutputStream outputStream) {
        this.output = new PrintStream(outputStream);
    }

    public boolean hasError() {
        return hasError;
    }

    public void error(SAXParseException e) {
        if(debug)
            e.printStackTrace();
        hasError = true;
        if((e.getSystemId() == null && e.getPublicId() == null) && (e.getCause() instanceof UnknownHostException)) {
            print(WscompileMessages.WSIMPORT_ERROR_MESSAGE(e.toString()), e);
        } else {
            print(WscompileMessages.WSIMPORT_ERROR_MESSAGE(e.getMessage()), e);
        }
    }



    public void fatalError(SAXParseException e) {
        if(debug)
            e.printStackTrace();
        hasError = true;
        print(WscompileMessages.WSIMPORT_ERROR_MESSAGE(e.getMessage()), e);
    }

    public void warning(SAXParseException e) {
        print(WscompileMessages.WSIMPORT_WARNING_MESSAGE(e.getMessage()), e);
    }

    /**
     * Used to report possibly verbose information that
     * can be safely ignored.
     */
    public void info(SAXParseException e) {
        print(WscompileMessages.WSIMPORT_INFO_MESSAGE(e.getMessage()), e);
    }

    public void debug(SAXParseException e){
        print(WscompileMessages.WSIMPORT_DEBUG_MESSAGE(e.getMessage()), e);
    }


    private void print(String message, SAXParseException e) {
        output.println(message);
        output.println(getLocationString(e));
        output.println();
    }

    public void enableDebugging(){
        this.debug = true;
    }

}
