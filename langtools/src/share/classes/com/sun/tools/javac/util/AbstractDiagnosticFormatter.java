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
import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticType.*;
import static com.sun.tools.javac.util.LayoutCharacters.*;

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
     * JavacMessages object used by this formatter for i18n
     */
    protected JavacMessages messages;
    protected boolean showSource;

    /**
     * Initialize an AbstractDiagnosticFormatter by setting its JavacMessages object
     * @param messages
     */
    protected AbstractDiagnosticFormatter(JavacMessages messages, Options options, boolean showSource) {
        this.messages = messages;
        this.showSource = options.get("showSource") == null ? showSource :
                          options.get("showSource").equals("true");
    }

    protected AbstractDiagnosticFormatter(JavacMessages messages, boolean showSource) {
        this.messages = messages;
        this.showSource = showSource;
    }

    public String formatMessage(JCDiagnostic d, Locale l) {
        //this code should rely on the locale settings but it's not! See RFE 6443132
        StringBuilder buf = new StringBuilder();
        Collection<String> args = formatArguments(d, l);
        buf.append(localize(l, d.getCode(), args.toArray()));
        if (d.isMultiline()) {
            buf.append(formatSubdiagnostics(d, l));
        }
        return buf.toString();
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
            return ((Formattable)arg).toString(l, messages);
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
     * Format all the subdiagnostics attached to a given diagnostic
     *
     * @param d diagnostic whose subdiagnostics are to be formatted
     * @param l locale object to be used for i18n
     * @return string representation of the subdiagnostics
     */
    protected String formatSubdiagnostics(JCDiagnostic d, Locale l) {
        StringBuilder buf = new StringBuilder();
        for (JCDiagnostic d2 : d.getSubdiagnostics()) {
            buf.append('\n');
            String subdiagMsg = format(d2, l);
            buf.append(indent(subdiagMsg, DiagInc));
        }
        return buf.toString();
    }

    /** Format the faulty source code line and point to the error.
     *  @param d The diagnostic for which the error line should be printed
     */
    protected String formatSourceLine(JCDiagnostic d) {
        StringBuilder buf = new StringBuilder();
        DiagnosticSource source = d.getDiagnosticSource();
        int pos = d.getIntPosition();
        if (d.getIntPosition() != Position.NOPOS) {
            String line = (source == null ? null : source.getLine(pos));
            if (line == null)
                return "";
            buf.append(line+"\n");
            int col = source.getColumnNumber(pos, false);
            for (int i = 0; i < col - 1; i++)  {
                buf.append((line.charAt(i) == '\t') ? "\t" : " ");
            }
            buf.append("^");
         }
         return buf.toString();
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
        return messages.getLocalizedString(l, key, args);
    }

    public boolean displaySource(JCDiagnostic d) {
        return showSource && d.getType() != FRAGMENT;
    }

    /**
     * Creates a string with a given amount of empty spaces. Useful for
     * indenting the text of a diagnostic message.
     *
     * @param nSpaces the amount of spaces to be added to the result string
     * @return the indentation string
     */
    protected String indentString(int nSpaces) {
        String spaces = "                        ";
        if (nSpaces <= spaces.length())
            return spaces.substring(0, nSpaces);
        else {
            StringBuilder buf = new StringBuilder();
            for (int i = 0 ; i < nSpaces ; i++)
                buf.append(" ");
            return buf.toString();
        }
    }

    /**
     * Indent a string by prepending a given amount of empty spaces to each line
     * of the string
     *
     * @param s the string to be indented
     * @param nSpaces the amount of spaces that should be prepended to each line
     * of the string
     * @return an indented string
     */
    protected String indent(String s, int nSpaces) {
        String indent = indentString(nSpaces);
        StringBuilder buf = new StringBuilder();
        String nl = "";
        for (String line : s.split("\n")) {
            buf.append(nl);
            buf.append(indent + line);
            nl = "\n";
        }
        return buf.toString();
    }
}
