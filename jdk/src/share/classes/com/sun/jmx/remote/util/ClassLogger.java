/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.remote.util;

import java.util.logging.Logger;

public class ClassLogger {

    private static final boolean ok;
    private final String className;
    private final Logger logger;

    static {
        /* We attempt to work even if we are running in J2SE 1.3, where
           there is no java.util.logging.  The technique we use here is
           not strictly portable, but it does work with Sun's J2SE 1.3
           at least.  This is just a best effort: the Right Thing is for
           people to use at least J2SE 1.4.  */
        boolean loaded = false;
        try {
            Class<?> c = java.util.logging.Logger.class;
            loaded = true;
        } catch (Error e) {
            // OK.
            // java.util.logger package is not available in this jvm.
        }
        ok = loaded;
    }

    public ClassLogger(String subsystem, String className) {
        if (ok)
            logger = Logger.getLogger(subsystem);
        else
            logger = null;
        this.className = className;
    }

    public final boolean traceOn() {
        return finerOn();
    }

    public final boolean debugOn() {
        return finestOn();
    }

    public final boolean warningOn() {
        return ok && logger.isLoggable(java.util.logging.Level.WARNING);
    }

    public final boolean infoOn() {
        return ok && logger.isLoggable(java.util.logging.Level.INFO);
    }

    public final boolean configOn() {
        return ok && logger.isLoggable(java.util.logging.Level.CONFIG);
    }

    public final boolean fineOn() {
        return ok && logger.isLoggable(java.util.logging.Level.FINE);
    }

    public final boolean finerOn() {
        return ok && logger.isLoggable(java.util.logging.Level.FINER);
    }

    public final boolean finestOn() {
        return ok && logger.isLoggable(java.util.logging.Level.FINEST);
    }

    public final void debug(String func, String msg) {
        finest(func,msg);
    }

    public final void debug(String func, Throwable t) {
        finest(func,t);
    }

    public final void debug(String func, String msg, Throwable t) {
        finest(func,msg,t);
    }

    public final void trace(String func, String msg) {
        finer(func,msg);
    }

    public final void trace(String func, Throwable t) {
        finer(func,t);
    }

    public final void trace(String func, String msg, Throwable t) {
        finer(func,msg,t);
    }

    public final void error(String func, String msg) {
        severe(func,msg);
    }

    public final void error(String func, Throwable t) {
        severe(func,t);
    }

    public final void error(String func, String msg, Throwable t) {
        severe(func,msg,t);
    }

    public final void finest(String func, String msg) {
        if (ok)
            logger.logp(java.util.logging.Level.FINEST, className, func, msg);
    }

    public final void finest(String func, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.FINEST, className, func,
                        t.toString(), t);
    }

    public final void finest(String func, String msg, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.FINEST, className, func, msg,
                        t);
    }

    public final void finer(String func, String msg) {
        if (ok)
            logger.logp(java.util.logging.Level.FINER, className, func, msg);
    }

    public final void finer(String func, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.FINER, className, func,
                        t.toString(), t);
    }

    public final void finer(String func, String msg, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.FINER, className, func, msg,t);
    }

    public final void fine(String func, String msg) {
        if (ok)
            logger.logp(java.util.logging.Level.FINE, className, func, msg);
    }

    public final void fine(String func, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.FINE, className, func,
                        t.toString(), t);
    }

    public final void fine(String func, String msg, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.FINE, className, func, msg,
                        t);
    }

    public final void config(String func, String msg) {
        if (ok)
            logger.logp(java.util.logging.Level.CONFIG, className, func, msg);
    }

    public final void config(String func, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.CONFIG, className, func,
                        t.toString(), t);
    }

    public final void config(String func, String msg, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.CONFIG, className, func, msg,
                        t);
    }

    public final void info(String func, String msg) {
        if (ok)
            logger.logp(java.util.logging.Level.INFO, className, func, msg);
    }

    public final void info(String func, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.INFO, className, func,
                        t.toString(), t);
    }

    public final void info(String func, String msg, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.INFO, className, func, msg,
                        t);
    }

    public final void warning(String func, String msg) {
        if (ok)
            logger.logp(java.util.logging.Level.WARNING, className, func, msg);
    }

    public final void warning(String func, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.WARNING, className, func,
                        t.toString(), t);
    }

    public final void warning(String func, String msg, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.WARNING, className, func, msg,
                        t);
    }

    public final void severe(String func, String msg) {
        if (ok)
            logger.logp(java.util.logging.Level.SEVERE, className, func, msg);
    }

    public final void severe(String func, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.SEVERE, className, func,
                        t.toString(), t);
    }

    public final void severe(String func, String msg, Throwable t) {
        if (ok)
            logger.logp(java.util.logging.Level.SEVERE, className, func, msg,
                        t);
    }
}
