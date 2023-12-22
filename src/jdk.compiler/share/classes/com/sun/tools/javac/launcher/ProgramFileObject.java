/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.launcher;

import com.sun.tools.javac.resources.LauncherProperties.Errors;

import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static javax.tools.JavaFileObject.Kind.SOURCE;

/**
 * The program to launch as Java file object.
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
final class ProgramFileObject extends SimpleJavaFileObject {

    /**
     * Reads a source file, ignoring the first line if it is not a Java source file and
     * it begins with {@code #!}.
     *
     * <p>If it is not a Java source file, and if the first two bytes are {@code #!},
     * indicating a "magic number" of an executable text file, the rest of the first line
     * up to but not including the newline is ignored. All characters after the first two are
     * read in the {@link Charset#defaultCharset()} default platform encoding}.
     *
     * @param file the file
     * @return a file object containing the content of the file
     * @throws Fault if an error occurs while reading the file
     */
    static ProgramFileObject of(Path file) throws Fault {
        // use a BufferedInputStream to guarantee that we can use mark and reset.
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            boolean ignoreFirstLine;
            if (file.getFileName().toString().endsWith(".java")) {
                ignoreFirstLine = false;
            } else {
                in.mark(2);
                ignoreFirstLine = (in.read() == '#') && (in.read() == '!');
                if (!ignoreFirstLine) {
                    in.reset();
                }
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()))) {
                StringBuilder sb = new StringBuilder();
                if (ignoreFirstLine) {
                    r.readLine();
                    sb.append(System.lineSeparator()); // preserve line numbers
                }
                char[] buf = new char[1024];
                int n;
                while ((n = r.read(buf, 0, buf.length)) != -1) {
                    sb.append(buf, 0, n);
                }
                return new ProgramFileObject(file, sb, ignoreFirstLine);
            }
        } catch (IOException e) {
            throw new Fault(Errors.CantReadFile(file, e));
        }
    }

    private final Path file;
    private final CharSequence chars;
    private final boolean ignoreFirstLine;

    ProgramFileObject(Path file, CharSequence chars, boolean ignoreFirstLine) {
        super(file.toUri(), SOURCE);
        this.file = file;
        this.chars = chars;
        this.ignoreFirstLine = ignoreFirstLine;
    }

    public Path getFile() {
        return file;
    }

    public boolean isFirstLineIgnored() {
        return ignoreFirstLine;
    }

    @Override
    public String getName() {
        return file.toString();
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return chars;
    }

    @Override
    public boolean isNameCompatible(String simpleName, JavaFileObject.Kind kind) {
        // reject package-info and module-info; accept other names
        return (kind == JavaFileObject.Kind.SOURCE)
                && SourceVersion.isIdentifier(simpleName);
    }

    @Override
    public String toString() {
        return "JavacSourceLauncher[" + file + "]";
    }
}
