/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.util;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Position;

/** A subtype of Log for use in APT.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Bark extends Log {
    /** The context key for the bark. */
    protected static final Context.Key<Bark> barkKey =
        new Context.Key<Bark>();

    /**
     * Preregisters factories to create and use a Bark object for use as
     * both a Log and a Bark.
     */
    public static void preRegister(final Context context) {
        context.put(barkKey, new Context.Factory<Bark>() {
            public Bark make() {
                return new Bark(context);
            }
        });
        context.put(Log.logKey, new Context.Factory<Log>() {
            public Log make() {
                return Bark.instance(context);
            }
        });
    }

    /** Get the Bark instance for this context. */
    public static Bark instance(Context context) {
        Bark instance = context.get(barkKey);
        if (instance == null)
            instance = new Bark(context);
        return instance;
    }

    /** Specifies whether or not to ignore any diagnostics that are reported.
     */
    private boolean ignoreDiagnostics;

    /**
     * Factory for APT-specific diagnostics.
     */
    private JCDiagnostic.Factory aptDiags;


    /**
     * Creates a Bark.
     */
    protected Bark(Context context) {
        super(context); // will register this object in context with Log.logKey
        context.put(barkKey, this);

        // register additional resource bundle for APT messages.
        JavacMessages aptMessages = JavacMessages.instance(context);
        aptMessages.add("com.sun.tools.apt.resources.apt");
        aptDiags = new JCDiagnostic.Factory(aptMessages, "apt");

        multipleErrors = true;
    }

    /**
     * Sets a flag indicating whether or not to ignore all diagnostics.
     * When ignored, they are not reported to the output writers, not are they
     * counted in the various counters.
     * @param b If true, subsequent diagnostics will be ignored.
     * @return the previous state of the flag
     */
    public boolean setDiagnosticsIgnored(boolean b) {
        boolean prev = ignoreDiagnostics;
        ignoreDiagnostics = b;
        return prev;
    }

    /**
     * Report a diagnostic if they are not currently being ignored.
     */
    @Override
    public void report(JCDiagnostic diagnostic) {
        if (ignoreDiagnostics)
            return;

        super.report(diagnostic);
    }

    /** Report an error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void aptError(String key, Object... args) {
        aptError(Position.NOPOS, key, args);
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param pos    The source position at which to report the error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void aptError(int pos, String key, Object ... args) {
        report(aptDiags.error(source, new SimpleDiagnosticPosition(pos), key, args));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void aptWarning(String key, Object... args) {
        aptWarning(Position.NOPOS, key, args);
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void aptWarning(int pos, String key, Object ... args) {
        report(aptDiags.warning(source, new SimpleDiagnosticPosition(pos), key, args));
    }

    /** Report a note, unless suppressed by the  -nowarn option.
     *  @param key    The key for the localized note message.
     *  @param args   Fields of the note message.
     */
    public void aptNote(String key, Object... args) {
        aptNote(Position.NOPOS, key, args);
    }

    /** Report a note, unless suppressed by the  -nowarn option.
     *  @param pos    The source position at which to report the note.
     *  @param key    The key for the localized note message.
     *  @param args   Fields of the note message.
     */
    public void aptNote(int pos, String key, Object ... args) {
        report(aptDiags.note(source, new SimpleDiagnosticPosition(pos), key, args));
    }
}
