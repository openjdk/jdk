/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.jxc.ap;

import com.sun.tools.internal.xjc.ErrorReceiver;
import org.xml.sax.SAXParseException;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;

/**
 * @author Kohsuke Kawaguchi
 */
final class ErrorReceiverImpl extends ErrorReceiver {
    private final Messager messager;
    private final boolean debug;

    public ErrorReceiverImpl(Messager messager, boolean debug) {
        this.messager = messager;
        this.debug = debug;
    }

    public ErrorReceiverImpl(Messager messager) {
        this(messager,false);
    }

    public ErrorReceiverImpl(ProcessingEnvironment env) {
        this(env.getMessager());
    }

    public void error(SAXParseException exception) {
        messager.printMessage(Diagnostic.Kind.ERROR, exception.getMessage());
        messager.printMessage(Diagnostic.Kind.ERROR, getLocation(exception));
        printDetail(exception);
    }

    public void fatalError(SAXParseException exception) {
        messager.printMessage(Diagnostic.Kind.ERROR, exception.getMessage());
        messager.printMessage(Diagnostic.Kind.ERROR, getLocation(exception));
        printDetail(exception);
    }

    public void warning(SAXParseException exception) {
        messager.printMessage(Diagnostic.Kind.WARNING, exception.getMessage());
        messager.printMessage(Diagnostic.Kind.WARNING, getLocation(exception));
        printDetail(exception);
    }

    public void info(SAXParseException exception) {
        printDetail(exception);
    }

    private String getLocation(SAXParseException e) {
        // TODO: format the location information for printing
        return "";
    }

    private void printDetail(SAXParseException e) {
        if(debug) {
            e.printStackTrace(System.out);
        }
    }
}
