/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.tools.JavaFileObject;

import static com.sun.tools.javac.util.BasicDiagnosticFormatter.BasicFormatKind.*;
import static com.sun.tools.javac.api.DiagnosticFormatter.PositionKind.*;

/**
 * A basic formatter for diagnostic messages.
 * The basic formatter will format a diagnostic according to one of three format patterns, depending on whether
 * or not the source name and position are set. The formatter supports a printf-like string for patterns
 * with the following special characters:
 * <ul>
 * <li>%b: the base of the source name
 * <li>%f: the source name (full absolute path)
 * <li>%l: the line number of the diagnostic, derived from the character offset
 * <li>%c: the column number of the diagnostic, derived from the character offset
 * <li>%o: the character offset of the diagnostic if set
 * <li>%p: the prefix for the diagnostic, derived from the diagnostic type
 * <li>%t: the prefix as it normally appears in standard diagnostics. In this case, no prefix is
 *        shown if the type is ERROR and if a source name is set
 * <li>%m: the text or the diagnostic, including any appropriate arguments
 * <li>%_: space delimiter, useful for formatting purposes
 * </ul>
 */
public class BasicDiagnosticFormatter extends AbstractDiagnosticFormatter {

    protected Map<BasicFormatKind, String> availableFormats;

    /**
     * Create a basic formatter based on the supplied options.
     *
     * @param opts list of command-line options
     * @param msgs JavacMessages object used for i18n
     */
    @SuppressWarnings("fallthrough")
    BasicDiagnosticFormatter(Options opts, JavacMessages msgs) {
        super(msgs, opts, true);
        initAvailableFormats();
        String fmt = opts.get("diags");
        if (fmt != null) {
            String[] formats = fmt.split("\\|");
            switch (formats.length) {
                case 3:
                    availableFormats.put(DEFAULT_CLASS_FORMAT, formats[2]);
                case 2:
                    availableFormats.put(DEFAULT_NO_POS_FORMAT, formats[1]);
                default:
                    availableFormats.put(DEFAULT_POS_FORMAT, formats[0]);
            }
        }
    }

    /**
     * Create a standard basic formatter
     *
     * @param msgs JavacMessages object used for i18n
     */
    public BasicDiagnosticFormatter(JavacMessages msgs) {
        super(msgs, true);
        initAvailableFormats();
    }

    public void initAvailableFormats() {
        availableFormats = new HashMap<BasicFormatKind, String>();
        availableFormats.put(DEFAULT_POS_FORMAT, "%f:%l:%_%t%m");
        availableFormats.put(DEFAULT_NO_POS_FORMAT, "%p%m");
        availableFormats.put(DEFAULT_CLASS_FORMAT, "%f:%_%t%m");
    }

    public String format(JCDiagnostic d, Locale l) {
        if (l == null)
            l = messages.getCurrentLocale();
        String format = selectFormat(d);
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            boolean meta = false;
            if (c == '%' && i < format.length() - 1) {
                meta = true;
                c = format.charAt(++i);
            }
            buf.append(meta ? formatMeta(c, d, l) : String.valueOf(c));
        }
        if (displaySource(d)) {
            buf.append("\n" + formatSourceLine(d));
        }
        return buf.toString();
    }

    protected String formatMeta(char c, JCDiagnostic d, Locale l) {
        switch (c) {
            case 'b':
                return formatSource(d, false, l);
            case 'e':
                return formatPosition(d, END, l);
            case 'f':
                return formatSource(d, true, l);
            case 'l':
                return formatPosition(d, LINE, l);
            case 'c':
                return formatPosition(d, COLUMN, l);
            case 'o':
                return formatPosition(d, OFFSET, l);
            case 'p':
                return formatKind(d, l);
            case 's':
                return formatPosition(d, START, l);
            case 't': {
                boolean usePrefix;
                switch (d.getType()) {
                case FRAGMENT:
                    usePrefix = false;
                    break;
                case ERROR:
                    usePrefix = (d.getIntPosition() == Position.NOPOS);
                    break;
                default:
                    usePrefix = true;
                }
                if (usePrefix)
                    return formatKind(d, l);
                else
                    return "";
            }
            case 'm':
                return formatMessage(d, l);
            case '_':
                return " ";
            case '%':
                return "%";
            default:
                return String.valueOf(c);
        }
    }

    private String selectFormat(JCDiagnostic d) {
        DiagnosticSource source = d.getDiagnosticSource();
        String format = availableFormats.get(DEFAULT_NO_POS_FORMAT);
        if (source != null) {
            if (d.getIntPosition() != Position.NOPOS) {
                format = availableFormats.get(DEFAULT_POS_FORMAT);
            } else if (source.getFile() != null &&
                       source.getFile().getKind() == JavaFileObject.Kind.CLASS) {
                format = availableFormats.get(DEFAULT_CLASS_FORMAT);
            }
        }
        return format;
    }

    /**
     * This enum contains all the kinds of formatting patterns supported
     * by a basic diagnostic formatter.
     */
    public enum BasicFormatKind {
        /**
        * A format string to be used for diagnostics with a given position.
        */
        DEFAULT_POS_FORMAT,
        /**
        * A format string to be used for diagnostics without a given position.
        */
        DEFAULT_NO_POS_FORMAT,
        /**
        * A format string to be used for diagnostics regarding classfiles
        */
        DEFAULT_CLASS_FORMAT;
    }
}
