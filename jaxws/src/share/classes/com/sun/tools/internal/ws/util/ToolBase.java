/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.util;

import java.io.OutputStream;
import java.io.PrintStream;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;

/**
 * A base class for command-line tools.
 *
 * @author WS Development Team
 */
public abstract class ToolBase {

    public ToolBase(OutputStream out, String program) {
        this.out = out;
        this.program = program;
        initialize();
    }

    protected void initialize() {
        messageFactory = new LocalizableMessageFactory(getResourceBundleName());
        localizer = new Localizer();
    }

    public boolean run(String[] args) {
        if (!parseArguments(args)) {
            return false;
        }

        try {
            run();
            return wasSuccessful();
        } catch (Exception e) {
            if (e instanceof Localizable) {
                report((Localizable) e);
            } else {
                report(getMessage(getGenericErrorMessage(), e.toString()));
            }
            printStackTrace(e);
            return false;
        }
    }

    public boolean wasSuccessful() {
        return true;
    }

    protected abstract boolean parseArguments(String[] args);
    protected abstract void run() throws Exception;
    public void runProcessorActions() {}
    protected abstract String getGenericErrorMessage();
    protected abstract String getResourceBundleName();

    public void printStackTrace(Throwable t) {
        PrintStream outstream =
                out instanceof PrintStream
                        ? (PrintStream) out
                        : new PrintStream(out, true);
        t.printStackTrace(outstream);
        outstream.flush();
    }

    protected void report(String msg) {
        PrintStream outstream =
                out instanceof PrintStream
                        ? (PrintStream) out
                        : new PrintStream(out, true);
        outstream.println(msg);
        outstream.flush();
    }

    protected void report(Localizable msg) {
        report(localizer.localize(msg));
    }

    public Localizable getMessage(String key) {
        return getMessage(key, (Object[]) null);
    }

    public Localizable getMessage(String key, String arg) {
        return messageFactory.getMessage(key, new Object[] { arg });
    }

    public Localizable getMessage(String key, String arg1, String arg2) {
        return messageFactory.getMessage(key, new Object[] { arg1, arg2 });
    }

    public Localizable getMessage(
        String key,
        String arg1,
        String arg2,
        String arg3) {
        return messageFactory.getMessage(
                key,
                new Object[] { arg1, arg2, arg3 });
    }

    public Localizable getMessage(String key, Localizable localizable) {
        return messageFactory.getMessage(key, new Object[] { localizable });
    }

    public Localizable getMessage(String key, Object[] args) {
        return messageFactory.getMessage(key, args);
    }

    protected OutputStream out;
    protected String program;
    protected Localizer localizer;
    protected LocalizableMessageFactory messageFactory;

    protected final static String TRUE = "true";
    protected final static String FALSE = "false";

}
