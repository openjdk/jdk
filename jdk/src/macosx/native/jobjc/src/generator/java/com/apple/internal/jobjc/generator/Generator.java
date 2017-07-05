/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.apple.internal.jobjc.generator.classes.MixedPrimitiveCoderClassFile;
import com.apple.internal.jobjc.generator.classes.OutputFile;
import com.apple.internal.jobjc.generator.model.Framework;
import com.apple.internal.jobjc.generator.model.coders.ComplexCoderDescriptor;
import com.apple.internal.jobjc.generator.model.types.Type;
import com.apple.internal.jobjc.generator.model.types.TypeCache;
import com.apple.internal.jobjc.generator.utils.Fp.Pair;

public class Generator {
    private static final String DEFAULT_FRAMEWORKS_PATH = "/System/Library/Frameworks";
    private static final String DEFAULT_OUTPUT_PATH = "/tmp/JObjC";

    public static void main(final String...args) throws Throwable {
        final Map<String, String> argMap = Utils.getArgs(args);

        final String dst = get(argMap, "dst", DEFAULT_OUTPUT_PATH);
        System.out.println("Cleaning up: " + dst);
        final File dstLoc = new File(dst);
        Utils.recDelete(dstLoc);
        dstLoc.mkdirs();
        System.out.println("Outputting classes to: " + dst);

        final String frameworksPath = get(argMap, "frameworks", DEFAULT_FRAMEWORKS_PATH);
        System.out.println("Searching for bridged frameworks in: " + frameworksPath);

        final List<File> bridgeSupportFiles = FrameworkGenerator.findFrameworkFilesIn(new File(frameworksPath));
        final List<Framework> frameworks = FrameworkGenerator.parseFrameworksFrom(bridgeSupportFiles);

        System.out.println("--1-- Generator: consolidateClassesForFrameworks");
        ClassConsolidator.consolidateClassesForFrameworks(frameworks);

        System.out.println("--1-- Generator: TypeCache load");
        TypeCache.inst().load(frameworks);

        System.out.println("--1-- Generator: disambiguateMethodNames");
        MethodDisambiguator.disambiguateMethodNames();

        System.out.println("--1-- Generator: disambiguateFunctionsIn");
        MethodDisambiguator.disambiguateFunctionsIn(frameworks);

        System.out.println("--1-- Generator: generateClasses");
        final List<OutputFile> sourceFiles = ClassGenerator.generateClasses(frameworks);
        sourceFiles.add(new MixedPrimitiveCoderClassFile(ComplexCoderDescriptor.getMixedEncoders()));

        System.out.println("--1-- Generator: writing " + sourceFiles.size() + " files");
        for (final OutputFile sourceFile : sourceFiles) sourceFile.write(dstLoc);

        System.out.println("I have " + TypeCache.inst().getUnknownTypes().size() + " unresolved types.");
        for (final Type type : TypeCache.inst().getUnknownTypes())
            System.out.println("[Warning] unknown type: " + type);

        for(Type type : TypeCache.inst().typesByNTypes.values()){
            if(!type.type32.getClass().equals(type.type64.getClass())){
                System.out.format("Type with differing NTypes: %1$15s: %2$s\n", type.name, new Pair(type.type32, type.type64));
            }
        }
    }

    private static String get(final Map<String, String> defaults, final String key, final String defaultValue) {
        final String value = defaults.get(key);
        if (value != null) return value;
        return defaultValue;
    }
}
