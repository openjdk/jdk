/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins.asm;

import java.util.Objects;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.internal.ModulePoolImpl;

/**
 * Extend this class to develop your own plugin in order to transform jimage
 * resources.
 *
 */
public abstract class AsmPlugin implements Plugin {

    public AsmPlugin() {
    }

    @Override
    public void visit(ModulePool allContent, ModulePool outResources) {
        Objects.requireNonNull(allContent);
        Objects.requireNonNull(outResources);
        ModulePoolImpl resources = new ModulePoolImpl(allContent.getByteOrder());
        allContent.entries().forEach(md -> {
            if(md.getType().equals(ModuleEntry.Type.CLASS_OR_RESOURCE)) {
                resources.add(md);
            } else {
                outResources.add(md);
            }
        });
        AsmPools pools = new AsmPools(resources);
        visit(pools);
        pools.fillOutputResources(outResources);
    }

    /**
     * This is the method to implement in order to
     * apply Asm transformation to jimage contained classes.
     * @param pools The pool of Asm classes and other resource files.
     * @param strings To add a string to the jimage strings table.
     * @throws jdk.tools.jlink.plugin.PluginException
     */
    public abstract void visit(AsmPools pools);
}
