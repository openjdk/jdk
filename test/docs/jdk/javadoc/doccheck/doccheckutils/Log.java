/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package doccheckutils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Log {
    private final ArrayList<String> errors;

    private Path baseDir;

    public Log() {
        errors = new ArrayList<>();
    }

    public List<String> getErrors() {
        return errors;
    }

    public void log(Path path, int line, String message, Object... args) {
        errors.add(formatErrorMessage(path, line, message, args));
    }


    public String formatErrorMessage(Path path, int line, String message, Object... args) {
        return path + ":" + line + ": " + formatErrorMessage(message, args);
    }

    public String formatErrorMessage(Path path, int line, Throwable t) {
        return path + ":" + line + ": " + t;
    }

    public String formatErrorMessage(Path path, Throwable t) {
        return path + ": " + t;
    }


    public String formatErrorMessage(String message, Object... args) {
        return String.format(message, args);
    }

    public void log(String message) {
        errors.add(message);
    }

    public void log(Path path, int lineNumber, String s, int errorsOnLine) {
        log(formatErrorMessage(path, lineNumber, s, errorsOnLine));
    }

    public void log(Path path, int line, Throwable t) {
        log(formatErrorMessage(path, line, t));
    }

    public void log(Path path, Throwable t) {
        log(formatErrorMessage(path, t));
    }

    public void log(String message, Object... args) {
        log(formatErrorMessage(message, args));
    }

    public void setBaseDirectory(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath();
    }

    public Path relativize(Path path) {
        return baseDir != null && path.startsWith(baseDir) ? baseDir.relativize(path) : path;
    }

    public boolean noErrors() {
        return errors.isEmpty();
    }
}
