/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.io;

import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Delegate interface for custom Console implementations.
 * Methods defined here duplicates the ones in Console class.
 * Providers should implement jdk.internal.io.JdkConsoleProvider
 * to instantiate an implementation of this interface.
 */
public interface JdkConsole {
    PrintWriter writer();
    Reader reader();
    JdkConsole println(Object obj);
    JdkConsole print(Object obj);
    String readln(String prompt);
    String readln();
    JdkConsole format(Locale locale, String format, Object ... args);
    String readLine(Locale locale, String format, Object ... args);
    String readLine();
    char[] readPassword(Locale locale, String format, Object ... args);
    char[] readPassword();
    void flush();
    Charset charset();
}
