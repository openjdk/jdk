/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.classfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.HashSet;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ClassWriter;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Method;

public class RemoveMethods {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java RemoveMethods classfile output [method...]");
            System.exit(-1);
        }

        // class file to read
        Path input = Paths.get(args[0]);

        // class file to write, if directory then use the name of the input
        Path output = Paths.get(args[1]);
        if (Files.isDirectory(output))
            output = output.resolve(input.getFileName());

        // the methods to remove
        Set<String> methodsToRemove = new HashSet<>();
        int i = 2;
        while (i < args.length)
            methodsToRemove.add(args[i++]);

        // read class file
        ClassFile cf;
        try (InputStream in = Files.newInputStream(input)) {
             cf = ClassFile.read(in);
        }

        final int magic = cf.magic;
        final int major_version = cf.major_version;
        final int minor_version = cf.minor_version;
        final ConstantPool cp = cf.constant_pool;
        final AccessFlags access_flags = cf.access_flags;
        final int this_class = cf.this_class;
        final int super_class = cf.super_class;
        final int[] interfaces = cf.interfaces;
        final Field[] fields = cf.fields;
        final Attributes class_attrs = cf.attributes;

        // remove the requested methods, no signature check at this time
        Method[] methods = cf.methods;
        i = 0;
        while (i < methods.length) {
            Method m = methods[i];
            String name = m.getName(cp);
            if (methodsToRemove.contains(name)) {
                int len = methods.length;
                Method[] newMethods = new Method[len-1];
                if (i > 0)
                    System.arraycopy(methods, 0, newMethods, 0, i);
                int after = methods.length - i - 1;
                if (after > 0)
                    System.arraycopy(methods, i+1, newMethods, i, after);
                methods = newMethods;
                String paramTypes = m.descriptor.getParameterTypes(cp);
                System.out.format("Removed method %s%s from %s%n",
                    name, paramTypes, cf.getName());
                continue;
            }
            i++;
        }

        // TBD, prune constant pool of entries that are no longer referenced

        // re-write class file
        cf = new ClassFile(magic, minor_version, major_version, cp, access_flags,
                this_class, super_class, interfaces, fields, methods, class_attrs);
        try (OutputStream out = Files.newOutputStream(output)) {
             new ClassWriter().write(cf, out);
        }
    }
}
