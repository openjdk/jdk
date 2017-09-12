/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.tools.nasgen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;

/**
 * Main class for the "nasgen" tool.
 *
 */
public class Main {
    /**
     * ASM version to be used by nasgen tool.
     */
    public static final int ASM_VERSION = Opcodes.ASM5;

    private static final boolean DEBUG = Boolean.getBoolean("nasgen.debug");

    private interface ErrorReporter {
        public void error(String msg);
    }

    /**
     * Public entry point for Nasgen if invoked from command line. Nasgen takes three arguments
     * in order: input directory, package list, output directory
     *
     * @param args argument vector
     */
    public static void main(final String[] args) {
        final ErrorReporter reporter = new ErrorReporter() {
            @Override
            public void error(final String msg) {
                Main.error(msg, 1);
            }
        };
        if (args.length == 3) {
            processAll(args[0], args[1], args[2], reporter);
        } else {
            error("Usage: nasgen <input-dir> <package-list> <output-dir>", 1);
        }
    }

    private static void processAll(final String in, final String pkgList, final String out, final ErrorReporter reporter) {
        final File inDir = new File(in);
        if (!inDir.exists() || !inDir.isDirectory()) {
            reporter.error(in + " does not exist or not a directory");
            return;
        }

        final File outDir = new File(out);
        if (!outDir.exists() || !outDir.isDirectory()) {
            reporter.error(out + " does not exist or not a directory");
            return;
        }

        final String[] packages = pkgList.split(":");
        for (String pkg : packages) {
            pkg = pkg.replace('.', File.separatorChar);
            final File dir = new File(inDir, pkg);
            final File[] classes = dir.listFiles();
            for (final File clazz : classes) {
                if (clazz.isFile() && clazz.getName().endsWith(".class")) {
                    if (! process(clazz, new File(outDir, pkg), reporter)) {
                        return;
                    }
                }
            }
        }
    }

    private static boolean process(final File inFile, final File outDir, final ErrorReporter reporter) {
        try {
            byte[] buf = new byte[(int)inFile.length()];

            try (FileInputStream fin = new FileInputStream(inFile)) {
                fin.read(buf);
            }

            final ScriptClassInfo sci = ClassGenerator.getScriptClassInfo(buf);

            if (sci != null) {
                try {
                    sci.verify();
                } catch (final Exception e) {
                    reporter.error(e.getMessage());
                    return false;
                }

                // create necessary output package dir
                outDir.mkdirs();

                // instrument @ScriptClass
                final ClassWriter writer = ClassGenerator.makeClassWriter();
                final ClassReader reader = new ClassReader(buf);
                final ScriptClassInstrumentor inst = new ScriptClassInstrumentor(writer, sci);
                reader.accept(inst, 0);
                //noinspection UnusedAssignment

                // write instrumented class
                try (FileOutputStream fos = new FileOutputStream(new File(outDir, inFile.getName()))) {
                    buf = writer.toByteArray();
                    if (DEBUG) {
                        verify(buf);
                    }
                    fos.write(buf);
                }

                // simple class name without package prefix
                String simpleName = inFile.getName();
                simpleName = simpleName.substring(0, simpleName.indexOf(".class"));

                if (sci.isPrototypeNeeded()) {
                    // generate prototype class
                    final PrototypeGenerator protGen = new PrototypeGenerator(sci);
                    buf = protGen.getClassBytes();
                    if (DEBUG) {
                        verify(buf);
                    }
                    try (FileOutputStream fos = new FileOutputStream(new File(outDir, simpleName + StringConstants.PROTOTYPE_SUFFIX + ".class"))) {
                        fos.write(buf);
                    }
                }

                if (sci.isConstructorNeeded()) {
                    // generate constructor class
                    final ConstructorGenerator consGen = new ConstructorGenerator(sci);
                    buf = consGen.getClassBytes();
                    if (DEBUG) {
                        verify(buf);
                    }
                    try (FileOutputStream fos = new FileOutputStream(new File(outDir, simpleName + StringConstants.CONSTRUCTOR_SUFFIX + ".class"))) {
                        fos.write(buf);
                    }
                }
            }
            return true;
        } catch (final IOException | RuntimeException e) {
            if (DEBUG) {
                e.printStackTrace(System.err);
            }
            reporter.error(e.getMessage());

            return false;
        }
    }

    private static void verify(final byte[] buf) {
        final ClassReader cr = new ClassReader(buf);
        CheckClassAdapter.verify(cr, false, new PrintWriter(System.err));
    }

    private static void error(final String msg, final int exitCode) {
        System.err.println(msg);
        System.exit(exitCode);
    }
}
