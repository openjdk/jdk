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

import java.util.Locale;

import com.sun.tools.javac.api.Formattable;
import static com.sun.tools.javac.api.DiagnosticFormatter.PositionKind.*;

/**
 * A raw formatter for diagnostic messages.
 * The raw formatter will format a diagnostic according to one of two format patterns, depending on whether
 * or not the source name and position are set. This formatter provides a standardized, localize-independent
 * implementation of a diagnostic formatter; as such, this formatter is best suited for testing purposes.
 */
public class RawDiagnosticFormatter extends AbstractDiagnosticFormatter {

    /**
     * Create a formatter based on the supplied options.
     * @param msgs
     */
    public RawDiagnosticFormatter(Options opts) {
        super(null, opts, false);
    }

    //provide common default formats
    public String format(JCDiagnostic d, Locale l) {
        try {
            StringBuffer buf = new StringBuffer();
            if (d.getPosition() != Position.NOPOS) {
                buf.append(formatSource(d, false, null));
                buf.append(':');
                buf.append(formatPosition(d, LINE, null));
                buf.append(':');
                buf.append(formatPosition(d, COLUMN, null));
                buf.append(':');
            }
            else
                buf.append('-');
            buf.append(' ');
            buf.append(formatMessage(d, null));
            if (displaySource(d))
                buf.append("\n" + formatSourceLine(d));
            return buf.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected String formatArgument(JCDiagnostic diag, Object arg, Locale l) {
        String s;
        if (arg instanceof Formattable)
            s = arg.toString();
        else
            s = super.formatArgument(diag, arg, null);
        if (arg instanceof JCDiagnostic)
            return "(" + s + ")";
        else
            return s;
    }

    @Override
    protected String formatSubdiagnostics(JCDiagnostic d, Locale l) {
        StringBuilder buf = new StringBuilder();
        String sep = "";
        buf.append(",{");
        for (JCDiagnostic d2 : d.getSubdiagnostics()) {
            buf.append(sep);
            buf.append("(" + format(d2, l) + ")");
            sep = ",";
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    protected String localize(Locale l, String s, Object... args) {
        StringBuffer buf = new StringBuffer();
        buf.append(s);
        String sep = ": ";
        for (Object o : args) {
            buf.append(sep);
            buf.append(o);
            sep = ", ";
        }
        return buf.toString();
    }
}
