/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.Plugin.Category;
import jdk.internal.org.objectweb.asm.ClassReader;
import static jdk.internal.org.objectweb.asm.ClassReader.*;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.LineNumberNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.Plugin;

public final class ClassForNamePlugin implements Plugin {
    public static final String NAME = "class-for-name";

    private static String binaryClassName(String path) {
        return path.substring(path.indexOf('/', 1) + 1,
                              path.length() - ".class".length());
    }

    private static int getAccess(ResourcePoolEntry resource) {
        ClassReader cr = new ClassReader(resource.contentBytes());

        return cr.getAccess();
    }

    private static String getPackage(String binaryName) {
        int index = binaryName.lastIndexOf("/");

        return index == -1 ? "" : binaryName.substring(0, index);
    }

    private ResourcePoolEntry transform(ResourcePoolEntry resource, ResourcePool pool) {
        byte[] inBytes = resource.contentBytes();
        ClassReader cr = new ClassReader(inBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, EXPAND_FRAMES);
        List<MethodNode> ms = cn.methods;
        boolean modified = false;
        LdcInsnNode ldc = null;

        String thisPackage = getPackage(binaryClassName(resource.path()));

        for (MethodNode mn : ms) {
            InsnList il = mn.instructions;
            Iterator<AbstractInsnNode> it = il.iterator();

            while (it.hasNext()) {
                AbstractInsnNode insn = it.next();

                if (insn instanceof LdcInsnNode) {
                    ldc = (LdcInsnNode)insn;
                } else if (insn instanceof MethodInsnNode && ldc != null) {
                    MethodInsnNode min = (MethodInsnNode)insn;

                    if (min.getOpcode() == Opcodes.INVOKESTATIC &&
                        min.name.equals("forName") &&
                        min.owner.equals("java/lang/Class") &&
                        min.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                        String ldcClassName = ldc.cst.toString();
                        String thatClassName = ldcClassName.replaceAll("\\.", "/");
                        Optional<ResourcePoolEntry> thatClass =
                            pool.findEntryInContext(thatClassName + ".class", resource);

                        if (thatClass.isPresent()) {
                            int thatAccess = getAccess(thatClass.get());
                            String thatPackage = getPackage(thatClassName);

                            if ((thatAccess & Opcodes.ACC_PRIVATE) != Opcodes.ACC_PRIVATE &&
                                ((thatAccess & Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC ||
                                  thisPackage.equals(thatPackage))) {
                                Type type = Type.getObjectType(thatClassName);
                                il.remove(ldc);
                                il.set(min, new LdcInsnNode(type));
                                modified = true;
                            }
                        }
                    }

                    ldc = null;
                } else if (!(insn instanceof LabelNode) &&
                           !(insn instanceof LineNumberNode)) {
                    ldc = null;
                }

            }
        }

        if (modified) {
            ClassWriter cw = new ClassWriter(cr, 0);
            cn.accept(cw);
            byte[] outBytes = cw.toByteArray();

            return resource.copyWithContent(outBytes);
        }

        return resource;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        Objects.requireNonNull(in);
        Objects.requireNonNull(out);

        in.entries()
            .forEach(resource -> {
                String path = resource.path();

                if (path.endsWith(".class") && !path.endsWith("/module-info.class")) {
                    out.add(transform(resource, in));
                } else {
                    out.add(resource);
                }
            });
        return out.build();
    }

    @Override
    public Category getType() {
        return Category.TRANSFORMER;
    }

    @Override
    public boolean hasArguments() {
        return false;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public void configure(Map<String, String> config) {

    }
}
