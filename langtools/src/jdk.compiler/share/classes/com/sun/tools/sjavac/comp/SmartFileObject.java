/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac.comp;

import java.io.*;
import java.net.URI;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

/**
 * The SmartFileObject will return an outputstream that cache the written data
 * and compare the new content with the old content on disk. Only if they differ,
 * will the file be updated.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SmartFileObject implements JavaFileObject {

    JavaFileObject file;
    PrintWriter stdout;

    public SmartFileObject(JavaFileObject r, PrintWriter pw) {
        file = r;
        stdout = pw;
    }

    @Override
    public boolean equals(Object other) {
        return file.equals(other);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    public Kind getKind() {
        return file.getKind();
    }

    public boolean isNameCompatible(String simpleName, Kind kind) {
        return file.isNameCompatible(simpleName, kind);
    }

    public URI toUri() {
        return file.toUri();
    }

    public String getName() {
        return file.getName();
    }

    public InputStream openInputStream() throws IOException {
        return file.openInputStream();
    }

    public OutputStream openOutputStream() throws IOException {
        return file.openOutputStream();
    }

    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return file.getCharContent(ignoreEncodingErrors);
    }

    static String lineseparator = System.getProperty("line.separator");

    public Writer openWriter() throws IOException {
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(file.openReader(true))) {
            while (r.ready()) {
                s.append(r.readLine()+lineseparator);
            }
        } catch (FileNotFoundException e) {
            // Perfectly ok.
        }
        return new SmartWriter(file, s.toString(), file.getName(), stdout);
    }

    public long getLastModified() {
        return file.getLastModified();
    }

    public boolean delete() {
        return file.delete();
    }

    public Modifier getAccessLevel() {
        return file.getAccessLevel();
    }

    public NestingKind getNestingKind() {
        return file.getNestingKind();
    }

    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return file.openReader(ignoreEncodingErrors);
    }

}
