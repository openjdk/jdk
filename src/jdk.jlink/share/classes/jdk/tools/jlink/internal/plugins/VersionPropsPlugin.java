/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jlink.internal.plugins;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import jdk.tools.jlink.plugin.*;
import jdk.internal.org.objectweb.asm.*;

import static java.lang.System.out;

/**
 * Base plugin to update a static field in java.lang.VersionProps
 */
abstract class VersionPropsPlugin implements Plugin {

    private static final String VERSION_PROPS_CLASS
        = "/java.base/java/lang/VersionProps.class";

    private final String name;
    private final String field;
    private String value;

    /**
     * @param field The name of the java.lang.VersionProps field to be redefined
     * @param option The option name
     */
    protected VersionPropsPlugin(String field, String option) {
        this.field = field;
        this.name = option;
    }

    /**
     * Shorthand constructor for when the option name can be derived from the
     * name of the field.
     *
     * @param field The name of the java.lang.VersionProps field to be redefined
     */
    protected VersionPropsPlugin(String field) {
        this(field, field.toLowerCase().replace('_', '-'));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(name);
    }

    @Override
    public Category getType() {
        return Category.TRANSFORMER;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public boolean hasRawArgument() {
        return true;
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(name);
    }

    @Override
    public void configure(Map<String, String> config) {
        var v = config.get(name);
        if (v == null)
            throw new AssertionError();
        value = v;
    }

    private boolean redefined = false;

    private byte[] redefine(byte[] classFile) {

        var cr = new ClassReader(classFile);
        var cw = new ClassWriter(0);

        cr.accept(new ClassVisitor(Opcodes.ASM7, cw) {

                public MethodVisitor visitMethod(int access,
                                                 String name,
                                                 String desc,
                                                 String sig,
                                                 String[] xs)
                {
                    if (name.equals("<clinit>"))
                        return new MethodVisitor(Opcodes.ASM7,
                                                 super.visitMethod(access,
                                                                   name,
                                                                   desc,
                                                                   sig,
                                                                   xs))
                            {

                                public void visitFieldInsn(int opcode,
                                                           String owner,
                                                           String name,
                                                           String desc)
                                {
                                    if (opcode == Opcodes.PUTSTATIC
                                        && name.equals(field))
                                    {
                                        // Discard the original value
                                        super.visitInsn(Opcodes.POP);
                                        // Load the value that we want
                                        super.visitLdcInsn(value);
                                        redefined = true;
                                    }
                                    super.visitFieldInsn(opcode, owner,
                                                         name, desc);
                                }

                        };
                    else
                        return super.visitMethod(access, name, desc, sig, xs);
                }

            }, 0);

        return cw.toByteArray();

    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        in.transformAndCopy(res -> {
                if (res.type().equals(ResourcePoolEntry.Type.CLASS_OR_RESOURCE)) {
                    if (res.path().equals(VERSION_PROPS_CLASS)) {
                        return res.copyWithContent(redefine(res.contentBytes()));
                    }
                }
                return res;
            }, out);
        if (!redefined)
            throw new AssertionError(field);
        return out.build();
    }

}
