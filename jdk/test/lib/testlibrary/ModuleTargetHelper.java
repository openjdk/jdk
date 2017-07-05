/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.internal.module.ClassFileConstants;
import jdk.internal.module.ClassFileAttributes;
import jdk.internal.module.ClassFileAttributes.ModuleTargetAttribute;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public class ModuleTargetHelper {
    private ModuleTargetHelper() {}

    public static final class ModuleTarget {
        private String targetPlatform;

        public ModuleTarget(String targetPlatform) {
            this.targetPlatform = targetPlatform;
        }

        public String targetPlatform() {
            return targetPlatform;
        }
    }

    public static ModuleTarget getJavaBaseTarget() throws IOException {
        Path p = Paths.get(URI.create("jrt:/modules/java.base/module-info.class"));
        try (InputStream in = Files.newInputStream(p)) {
            return read(in);
        }
    }

    public static ModuleTarget read(InputStream in) throws IOException {
        ModuleTargetAttribute[] modTargets = new ModuleTargetAttribute[1];
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visitAttribute(Attribute attr) {
                if (attr instanceof ModuleTargetAttribute) {
                    modTargets[0] = (ModuleTargetAttribute)attr;
                }
            }
        };

        // prototype of attributes that should be parsed
        Attribute[] attrs = new Attribute[] {
            new ModuleTargetAttribute()
        };

        // parse module-info.class
        ClassReader cr = new ClassReader(in);
        cr.accept(cv, attrs, 0);
        if (modTargets[0] != null) {
            return new ModuleTarget(modTargets[0].targetPlatform());
        }

        return null;
    }

    public static ModuleTarget read(ModuleReference modRef) throws IOException {
        ModuleReader reader = modRef.open();
        try (InputStream in = reader.open("module-info.class").get()) {
            return read(in);
        } finally {
            reader.close();
        }
    }
}
