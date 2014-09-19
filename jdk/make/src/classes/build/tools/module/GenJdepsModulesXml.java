/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.module;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GenJdepsModulesXml augments the input modules.xml file(s)
 * to include the module membership from the given path to
 * the JDK exploded image.  The output file is used by jdeps
 * to analyze dependencies and enforce module boundaries.
 *
 * The input modules.xml file defines the modular structure of
 * the JDK as described in JEP 200: The Modular JDK
 * (http://openjdk.java.net/jeps/200).
 *
 * $ java build.tools.module.GenJdepsModulesXml \
 *        -o com/sun/tools/jdeps/resources/modules.xml \
 *        -mp $OUTPUTDIR/modules \
 *        top/modules.xml
 */
public final class GenJdepsModulesXml {
    private final static String USAGE =
        "Usage: GenJdepsModulesXml -o <output file> -mp build/modules path-to-modules-xml";

    public static void main(String[] args) throws Exception {
        Path outfile = null;
        Path modulepath = null;
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (arg.equals("-o")) {
                outfile = Paths.get(args[i+1]);
                i = i+2;
            } else if (arg.equals("-mp")) {
                modulepath = Paths.get(args[i+1]);
                i = i+2;
                if (!Files.isDirectory(modulepath)) {
                    System.err.println(modulepath + " is not a directory");
                    System.exit(1);
                }
            } else {
                break;
            }
        }
        if (outfile == null || modulepath == null || i >= args.length) {
            System.err.println(USAGE);
            System.exit(-1);
        }

        GenJdepsModulesXml gentool = new GenJdepsModulesXml(modulepath);
        Set<Module> modules = new HashSet<>();
        for (; i < args.length; i++) {
            Path p = Paths.get(args[i]);
            modules.addAll(ModulesXmlReader.readModules(p)
                    .stream()
                    .map(gentool::buildIncludes)
                    .collect(Collectors.toSet()));
        }

        Files.createDirectories(outfile.getParent());
        ModulesXmlWriter.writeModules(modules, outfile);
    }

    final Path modulepath;
    public GenJdepsModulesXml(Path modulepath) {
        this.modulepath = modulepath;
    }

    private static String packageName(Path p) {
        return packageName(p.toString().replace(File.separatorChar, '/'));
    }
    private static String packageName(String name) {
        int i = name.lastIndexOf('/');
        return (i > 0) ? name.substring(0, i).replace('/', '.') : "";
    }

    private static boolean includes(String name) {
        return name.endsWith(".class");
    }

    public Module buildIncludes(Module module) {
        Module.Builder mb = new Module.Builder(module);
        Path mclasses = modulepath.resolve(module.name());
        try {
            Files.find(mclasses, Integer.MAX_VALUE, (Path p, BasicFileAttributes attr)
                         -> includes(p.getFileName().toString()))
                 .map(p -> packageName(mclasses.relativize(p)))
                 .forEach(mb::include);
        } catch (NoSuchFileException e) {
            // aggregate module may not have class
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return mb.build();
    }
}
