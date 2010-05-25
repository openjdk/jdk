/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.tools.javah;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

/**
 * Messages, verbose and error handling support.
 *
 * For errors, the failure modes are:
 *      error -- User did something wrong
 *      bug   -- Bug has occurred in javah
 *      fatal -- We can't even find resources, so bail fast, don't localize
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Util {
    /** Exit is used to replace the use of System.exit in the original javah.
     */
    public static class Exit extends Error {
        private static final long serialVersionUID = 430820978114067221L;
        Exit(int exitValue) {
            this(exitValue, null);
        }

        Exit(int exitValue, Throwable cause) {
            super(cause);
            this.exitValue = exitValue;
            this.cause = cause;
        }

        Exit(Exit e) {
            this(e.exitValue, e.cause);
        }

        public final int exitValue;
        public final Throwable cause;
    }

    /*
     * Help for verbosity.
     */
    public boolean verbose = false;

    public PrintWriter log;
    public DiagnosticListener<? super JavaFileObject> dl;

    Util(PrintWriter log, DiagnosticListener<? super JavaFileObject> dl) {
        this.log = log;
        this.dl = dl;
    }

    public void log(String s) {
        log.println(s);
    }


    /*
     * Help for loading localized messages.
     */
    private ResourceBundle m;

    private void initMessages() throws Exit {
        try {
            m = ResourceBundle.getBundle("com.sun.tools.javah.resources.l10n");
        } catch (MissingResourceException mre) {
            fatal("Error loading resources.  Please file a bug report.", mre);
        }
    }

    private String getText(String key, Object... args) throws Exit {
        if (m == null)
            initMessages();
        try {
            return MessageFormat.format(m.getString(key), args);
        } catch (MissingResourceException e) {
            fatal("Key " + key + " not found in resources.", e);
        }
        return null; /* dead code */
    }

    /*
     * Usage message.
     */
    public void usage() throws Exit {
        log.println(getText("usage"));
    }

    public void version() throws Exit {
        log.println(getText("javah.version",
                                   System.getProperty("java.version"), null));
    }

    /*
     * Failure modes.
     */
    public void bug(String key) throws Exit {
        bug(key, null);
    }

    public void bug(String key, Exception e) throws Exit {
        dl.report(createDiagnostic(Diagnostic.Kind.ERROR, key));
        dl.report(createDiagnostic(Diagnostic.Kind.NOTE, "bug.report"));
        throw new Exit(11, e);
    }

    public void error(String key, Object... args) throws Exit {
        dl.report(createDiagnostic(Diagnostic.Kind.ERROR, key, args));
        throw new Exit(15);
    }

    private void fatal(String msg) throws Exit {
        fatal(msg, null);
    }

    private void fatal(String msg, Exception e) throws Exit {
        dl.report(createDiagnostic(Diagnostic.Kind.ERROR, "", msg));
        throw new Exit(10, e);
    }

    private Diagnostic<JavaFileObject> createDiagnostic(
            final Diagnostic.Kind kind, final String code, final Object... args) {
        return new Diagnostic<JavaFileObject>() {
            public String getCode() {
                return code;
            }
            public long getColumnNumber() {
                return Diagnostic.NOPOS;
            }
            public long getEndPosition() {
                return Diagnostic.NOPOS;
            }
            public Kind getKind() {
                return kind;
            }
            public long getLineNumber() {
                return Diagnostic.NOPOS;
            }
            public String getMessage(Locale locale) {
                if (code.length() == 0)
                    return (String) args[0];
                return getText(code, args); // FIXME locale
            }
            public long getPosition() {
                return Diagnostic.NOPOS;
            }
            public JavaFileObject getSource() {
                return null;
            }
            public long getStartPosition() {
                return Diagnostic.NOPOS;
            }
        };
    }
}
