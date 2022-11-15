/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.nio.charset.Charset;
import jdk.internal.io.JdkConsole;

/**
 * A proxy for the Console for internal use. Providers of a Console
 * can extend jdk.internal.io.JdkConsole class
 */

final class ProxyingConsole extends Console {
    private final JdkConsole delegate;

    ProxyingConsole(JdkConsole delegate) {
        this.delegate = delegate;
    }

    public PrintWriter writer() {
        return delegate.writer();
    }

    public Reader reader() {
        return delegate.reader();
    }

    public Console format(String fmt, Object ...args) {
        delegate.format(fmt, args);
        return this;
    }

    public Console printf(String format, Object ... args) {
        delegate.printf(format, args);
        return this;
    }

    public String readLine(String fmt, Object ... args) {
        return delegate.readLine(fmt, args);
    }

    public String readLine() {
        return delegate.readLine();
    }

    public char[] readPassword(String fmt, Object ... args) {
        return delegate.readPassword(fmt, args);
    }

    public char[] readPassword() {
        return delegate.readPassword();
    }

    public void flush() {
        delegate.flush();
    }

    public Charset charset() {
        return delegate.charset();
    }
}
