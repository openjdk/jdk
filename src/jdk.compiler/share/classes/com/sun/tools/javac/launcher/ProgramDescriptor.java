/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.tree.ClassTree;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.resources.LauncherProperties.Errors;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Describes a launch-able Java compilation unit.
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
public record ProgramDescriptor(
        ProgramFileObject fileObject,
        Optional<String> packageName,
        List<String> qualifiedTypeNames,
        Path sourceRootPath) {
    static ProgramDescriptor of(ProgramFileObject fileObject) throws Fault {
        var file = fileObject.getFile();
        var packageName = ""; // empty string will be converted into an empty optional
        var packageNameAndDot = ""; // empty string or packageName + '.'
        var qualifiedTypeNames = new ArrayList<String>();
        try {
            var compiler = JavacTool.create();
            var standardFileManager = compiler.getStandardFileManager(null, null, null);
            var units = List.of(fileObject);
            var task = compiler.getTask(null, standardFileManager, diagnostic -> {}, null, null, units);
            var tree = task.parse().iterator().next(); // single compilation unit
            var packageTree = tree.getPackage();
            if (packageTree != null) {
                packageName = packageTree.getPackageName().toString();
                packageNameAndDot = packageName + '.';
            }
            for (var type : tree.getTypeDecls()) {
                if (type instanceof ClassTree classType) {
                    qualifiedTypeNames.add(packageNameAndDot + classType.getSimpleName());
                }
            }
        } catch (IOException ignore) {
            // fall through to let actual compilation determine the error message
        }
        if (qualifiedTypeNames.isEmpty()) {
            throw new Fault(Errors.NoClass);
        }
        return new ProgramDescriptor(
                fileObject,
                packageName.isEmpty() ? Optional.empty() : Optional.of(packageName),
                List.copyOf(qualifiedTypeNames),
                computeSourceRootPath(file, packageName));
    }

    public static Path computeSourceRootPath(Path program, String packageName) throws Fault {
        var absolute = program.normalize().toAbsolutePath();
        var absoluteRoot = absolute.getRoot();
        assert absoluteRoot != null;
        // unnamed package "": program's directory is the root path
        if (packageName.isEmpty()) {
            var parent = absolute.getParent();
            if (parent == null) return absoluteRoot;
            return parent;
        }
        // named package "a.b.c": ensure end of path to program is "a/b/c"
        var packagePath = Path.of(packageName.replace('.', '/'));
        var ending = packagePath.resolve(program.getFileName());
        if (absolute.endsWith(ending)) {
            var max = absolute.getNameCount() - ending.getNameCount();
            if (max == 0) return absoluteRoot;
            return absoluteRoot.resolve(absolute.subpath(0, max));
        }
        throw new Fault(Errors.MismatchEndOfPathAndPackageName(packageName, program));
    }

    public boolean isModular() {
        return Files.exists(sourceRootPath.resolve("module-info.java"));
    }

    public Set<String> computePackageNames() {
        try (var stream = Files.find(sourceRootPath, 99, (path, attr) -> attr.isDirectory())) {
            var names = new TreeSet<String>();
            stream.filter(ProgramDescriptor::containsAtLeastOneRegularFile)
                  .map(sourceRootPath::relativize)
                  .map(Path::toString)
                  .filter(string -> !string.isEmpty())
                  .map(string -> string.replace(File.separatorChar, '.'))
                  .forEach(names::add);
            return names;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean containsAtLeastOneRegularFile(Path directory) {
        try (var stream = Files.newDirectoryStream(directory, Files::isRegularFile)) {
            return stream.iterator().hasNext();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
