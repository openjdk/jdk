/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Testing Classfile ModuleExamples compilation.
 * @compile ModuleExamples.java
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.function.Consumer;

import jdk.internal.classfile.Annotation;
import jdk.internal.classfile.AnnotationElement;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.attribute.ModuleAttribute.ModuleAttributeBuilder;
import jdk.internal.classfile.attribute.ModuleMainClassAttribute;
import jdk.internal.classfile.attribute.ModulePackagesAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.internal.classfile.Attributes;
import java.lang.constant.PackageDesc;
import java.lang.constant.ModuleDesc;

public class ModuleExamples {
    private static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));

    public void examineModule() throws IOException {
        ClassModel cm = Classfile.of().parse(JRT.getPath("modules/java.base/module-info.class"));
        System.out.println("Is JVMS $4.7 compatible module-info: " + cm.isModuleInfo());

        ModuleAttribute ma = cm.findAttribute(Attributes.MODULE).orElseThrow();
        System.out.println("Module name: " + ma.moduleName().name().stringValue());
        System.out.println("Exports: " + ma.exports());

        ModuleMainClassAttribute mmca = cm.findAttribute(Attributes.MODULE_MAIN_CLASS).orElse(null);
        System.out.println("Does the module have a MainClassAttribute?: " + (mmca != null));

        ModulePackagesAttribute mmp = cm.findAttribute(Attributes.MODULE_PACKAGES).orElseThrow();
        System.out.println("Packages?: " + mmp.packages());
    }

    public void buildModuleFromScratch() {
        var moduleName = ModuleDesc.of("the.very.best.module");
        int moduleFlags = 0;

        Consumer<ModuleAttributeBuilder> handler = (mb -> {mb
                .moduleFlags(moduleFlags)
                .exports(PackageDesc.of("export.some.pkg"), 0)
                .exports(PackageDesc.of("qualified.export.to") , 0, ModuleDesc.of("to.first.module"), ModuleDesc.of("to.another.module"));
        });

        // Build it
        var cc = Classfile.of();
        byte[] moduleInfo = cc.buildModule(ModuleAttribute.of(moduleName, handler), clb -> {

                // Add an annotation to the module
                clb.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.ofDescriptor("Ljava/lang/Deprecated;"),
                                                                          AnnotationElement.ofBoolean("forRemoval", true),
                                                                          AnnotationElement.ofString("since", "17"))));
        });

        // Examine it
        ClassModel mm = cc.parse(moduleInfo);
        System.out.println("Is module info?: " + mm.isModuleInfo());
    }
}
