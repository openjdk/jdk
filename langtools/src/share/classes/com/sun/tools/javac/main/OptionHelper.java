/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.main;

import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.PrefixKind;
import java.io.File;

/**
 * Helper object to be used by {@link Option#process}, providing access to
 * the compilation environment.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */

public abstract class OptionHelper {

    /** Get the current value of an option. */
    public abstract String get(Option option);

    /** Set the value of an option. */
    public abstract void put(String name, String value);

    /** Remove any prior value for an option. */
    public abstract void remove(String name);

    /** Get access to the Log for the compilation. */
    public abstract Log getLog();

    /** Get the name of the tool, such as "javac", to be used in info like -help. */
    public abstract String getOwnName();

    /** Report an error. */
    abstract void error(String key, Object... args);

    /** Record a file to be compiled. */
    abstract void addFile(File f);

    /** Record the name of a class for annotation processing. */
    abstract void addClassName(String s);

    /** An implementation of OptionHelper that mostly throws exceptions. */
    public static class GrumpyHelper extends OptionHelper {
        private final Log log;

        public GrumpyHelper(Log log) {
            this.log = log;
        }

        @Override
        public Log getLog() {
            return log;
        }

        @Override
        public String getOwnName() {
            throw new IllegalStateException();
        }

        @Override
        public String get(Option option) {
            throw new IllegalArgumentException();
        }

        @Override
        public void put(String name, String value) {
            throw new IllegalArgumentException();
        }

        @Override
        public void remove(String name) {
            throw new IllegalArgumentException();
        }

        @Override
        void error(String key, Object... args) {
            throw new IllegalArgumentException(log.localize(PrefixKind.JAVAC, key, args));
        }

        @Override
        public void addFile(File f) {
            throw new IllegalArgumentException(f.getPath());
        }

        @Override
        public void addClassName(String s) {
            throw new IllegalArgumentException(s);
        }
    }

}
