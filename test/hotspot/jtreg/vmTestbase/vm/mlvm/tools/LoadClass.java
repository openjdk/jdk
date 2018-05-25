/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.mlvm.tools;

import java.io.File;
import vm.share.UnsafeAccess;
import vm.share.FileUtils;
import vm.mlvm.share.CustomClassLoaders;
import vm.mlvm.anonloader.share.AnonkTestee01;

import vm.mlvm.share.Env;

/**
 * A tool, which loads a class file specified in command line into VM using:
 * <ul>
 *   <li>A custom class loader,
 *   <li>{@link sun.misc.Unsafe#defineAnonymousClass(Class, byte[], Object[])}
 *   call.
 * </ul>
 *
 * <p>Syntax:
 * <pre>{@code
 * $ java [options...] vm.mlvm.tool.LoadClass class-file-name [class-FQDN]
 * }</pre>
 *
 * The first argument, class file name is mandatory.
 * The second one is optional &mdash; a fully qualified class name.
 * If the second argument is not specified, it is constructed from the first
 * argument, replacing '/' with '.'
 *
 * <p>The tool can be used for investigating failures of vm.mlvm.anon tests.
 *
 */
public class LoadClass {

    private static final Class<?> HOST_CLASS = AnonkTestee01.class;

    private static void usage() {
        System.out.println("Usage: java " + LoadClass.class.getName()
                + " <class-file-to-load> [class-name]");
    }

    /**
     * Runs the tool.
     * @param args Tool arguments
     */
    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            System.exit(1);
        }

        try {
            final String classFileName = args[0];
            final String className = (args.length > 1) ? args[1]
                    : classFileName.replaceAll("\\.class$", "")
                            .replace("/", ".");
            final byte[] classBytes = FileUtils
                    .readFile(new File(classFileName));

            Env.traceImportant("Loading class '%s' from file '%s'...",
                    className, classFileName);

            Env.traceImportant("...using custom ClassLoader");
            try {
                ClassLoader cl = CustomClassLoaders
                        .makeClassBytesLoader(classBytes, className);
                Class<?> c = cl.loadClass(className);
                c.newInstance();
                Env.traceImportant("OK");
            } catch (Throwable e) {
                Env.traceImportant(e,
                        "Couldn't load class '%s' via custom ClassLoader",
                        classFileName);
            }

            Env.traceImportant(
                    "...using sun.misc.Unsafe.defineAnonymousClass():");
            try {
                Class<?> c = UnsafeAccess.unsafe.defineAnonymousClass(HOST_CLASS,
                        classBytes, new Object[0]);
                c.newInstance();
                Env.traceImportant("OK");
            } catch (Throwable e) {
                Env.traceImportant(e, "Couldn't load class '%s' via sun.misc."
                        + "Unsafe.defineAnonymousClass()", classFileName);
            }
        } catch (Throwable e) {
            Env.traceImportant(e, "Can't load class");
        }
    }
}
