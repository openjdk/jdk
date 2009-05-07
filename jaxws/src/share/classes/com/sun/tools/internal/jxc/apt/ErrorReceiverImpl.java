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
package com.sun.tools.internal.jxc.apt;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Messager;
import com.sun.tools.internal.xjc.ErrorReceiver;

import org.xml.sax.SAXParseException;

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

    public ErrorReceiverImpl(AnnotationProcessorEnvironment env) {
        this(env.getMessager());
    }

    public void error(SAXParseException exception) {
        messager.printError(exception.getMessage());
        messager.printError(getLocation(exception));
        printDetail(exception);
    }

    public void fatalError(SAXParseException exception) {
        messager.printError(exception.getMessage());
        messager.printError(getLocation(exception));
        printDetail(exception);
    }

    public void warning(SAXParseException exception) {
        messager.printWarning(exception.getMessage());
        messager.printWarning(getLocation(exception));
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
