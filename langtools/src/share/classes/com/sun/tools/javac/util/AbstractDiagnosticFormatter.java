/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.javac.util;

import java.util.Collection;
import java.util.Locale;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.api.DiagnosticFormatter;
import com.sun.tools.javac.api.Formattable;
import com.sun.tools.javac.api.DiagnosticFormatter.PositionKind;
import com.sun.tools.javac.file.JavacFileManager;

/**
 * This abstract class provides a basic implementation of the functionalities that should be provided
 * by any formatter used by javac. Among the main features provided by AbstractDiagnosticFormatter are:
 *
 * <ul>
 *  <li> Provides a standard implementation of the visitor-like methods defined in the interface DiagnisticFormatter.
 *  Those implementations are specifically targeting JCDiagnostic objects.
 *  <li> Provides basic support for i18n and a method for executing all locale-dependent conversions
 *  <li> Provides the formatting logic for rendering the arguments of a JCDiagnostic object.
 * <ul>
 *
 */
public abstract class AbstractDiagnosticFormatter implements DiagnosticFormatter<JCDiagnostic> {

    /**
     * Messages object used by this formatter for i18n
     */
    protected Messages messages;

    /**
     * Initialize an AbstractDiagnosticFormatter by setting its Messages object
     * @param messages
     */
    protected AbstractDiagnosticFormatter(Messages messages) {
        this.messages = messages;
    }

    public String formatMessage(JCDiagnostic d, Locale l) {
        //this code should rely on the locale settings but it's not! See RFE 6443132
        Collection<String> args = formatArguments(d, l);
        return localize(l, d.getCode(), args.toArray());
    }

    public String formatKind(JCDiagnostic d, Locale l) {
        switch (d.getType()) {
            case FRAGMENT: return "";
            case NOTE:     return localize(l, "compiler.note.note");
            case WARNING:  return localize(l, "compiler.warn.warning");
            case ERROR:    return localize(l, "compiler.err.error");
            default:
                throw new AssertionError("Unknown diagnostic type: " + d.getType());
        }
    }

    public String formatPosition(JCDiagnostic d, PositionKind pk,Locale l) {
        assert (d.getPosition() != Position.NOPOS);
        return String.valueOf(getPosition(d, pk));
    }
    //WHERE
    public long getPosition(JCDiagnostic d, PositionKind pk) {
        switch (pk) {
            case START: return d.getIntStartPosition();
            case END: return d.getIntEndPosition();
            case LINE: return d.getLineNumber();
            case COLUMN: return d.getColumnNumber();
            case OFFSET: return d.getIntPosition();
            default:
                throw new AssertionError("Unknown diagnostic position: " + pk);
        }
    }

    public String formatSource(JCDiagnostic d, boolean fullname, Locale l) {
        assert (d.getSource() != null);
        return fullname ? d.getSourceName() : d.getSource().getName();
    }

    /**
     * Format the arguments of a given diagnostic.
     *
     * @param d diagnostic whose arguments are to be formatted
     * @param l locale object to be used for i18n
     * @return a Collection whose elements are the formatted arguments of the diagnostic
     */
    protected Collection<String> formatArguments(JCDiagnostic d, Locale l) {
        ListBuffer<String> buf = new ListBuffer<String>();
        for (Object o : d.getArgs()) {
           buf.append(formatArgument(d, o, l));
        }
        return buf.toList();
    }

    /**
     * Format a single argument of a given diagnostic.
     *
     * @param d diagnostic whose argument is to be formatted
     * @param arg argument to be formatted
     * @param l locale object to be used for i18n
     * @return string representation of the diagnostic argument
     */
    protected String formatArgument(JCDiagnostic d, Object arg, Locale l) {
        if (arg instanceof JCDiagnostic)
            return format((JCDiagnostic)arg, l);
        else if (arg instanceof Iterable<?>) {
            return formatIterable(d, (Iterable<?>)arg, l);
        }
        else if (arg instanceof JavaFileObject)
            return JavacFileManager.getJavacBaseFileName((JavaFileObject)arg);
        else if (arg instanceof Formattable)
            return ((Formattable)arg).toString(Messages.getDefaultBundle());
        else
            return String.valueOf(arg);
    }

    /**
     * Format an iterable argument of a given diagnostic.
     *
     * @param d diagnostic whose argument is to be formatted
     * @param it iterable argument to be formatted
     * @param l locale object to be used for i18n
     * @return string representation of the diagnostic iterable argument
     */
    protected String formatIterable(JCDiagnostic d, Iterable<?> it, Locale l) {
        StringBuilder sbuf = new StringBuilder();
        String sep = "";
        for (Object o : it) {
            sbuf.append(sep);
            sbuf.append(formatArgument(d, o, l));
            sep = ",";
        }
        return sbuf.toString();
    }

    /**
     * Converts a String into a locale-dependent representation accordingly to a given locale
     *
     * @param l locale object to be used for i18n
     * @param key locale-independent key used for looking up in a resource file
     * @param args localization arguments
     * @return a locale-dependent string
     */
    protected String localize(Locale l, String key, Object... args) {
        return messages.getLocalizedString(key, args);
    }
}
