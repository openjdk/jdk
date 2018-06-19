/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.org.slf4j.internal;

// Bridge to java.util.logging.
public class Logger {

    private final java.util.logging.Logger impl;

    public Logger(String name) {
        impl = java.util.logging.Logger.getLogger(name);
    }

    public boolean isDebugEnabled() {
        return impl.isLoggable(java.util.logging.Level.FINE);
    }

    public boolean isTraceEnabled() {
        return impl.isLoggable(java.util.logging.Level.FINE);
    }

    public void debug(String s) {
        impl.log(java.util.logging.Level.FINE, s);
    }

    public void debug(String s, Throwable e) {
        impl.log(java.util.logging.Level.FINE, s, e);
    }

    public void debug(String s, Object... o) {
        impl.log(java.util.logging.Level.FINE, s, o);
    }

    public void trace(String s) {
        impl.log(java.util.logging.Level.FINE, s);
    }

    public void error(String s) {
        impl.log(java.util.logging.Level.SEVERE, s);
    }

    public void error(String s, Throwable e) {
        impl.log(java.util.logging.Level.SEVERE, s, e);
    }

    public void error(String s, Object... o) {
        impl.log(java.util.logging.Level.SEVERE, s, o);
    }

    public void warn(String s) {
        impl.log(java.util.logging.Level.WARNING, s);
    }

    public void warn(String s, Throwable e) {
        impl.log(java.util.logging.Level.WARNING, s, e);
    }
}
