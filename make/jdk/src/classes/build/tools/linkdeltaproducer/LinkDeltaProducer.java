/*
 * Copyright (c) 2024, Red Hat, Inc.
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

package build.tools.linkdeltaproducer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import jdk.tools.jlink.internal.runtimelink.ImageReader;
import jdk.tools.jlink.internal.runtimelink.JimageDiffGenerator;
import jdk.tools.jlink.internal.runtimelink.JimageDiffGenerator.ImageResource;
import jdk.tools.jlink.internal.runtimelink.JmodsReader;
import jdk.tools.jlink.internal.runtimelink.ResourceDiff;

/**
 * Produces a serialized delta file between packaged modules and an optimized
 * jimage so as to be able to use a jimage for runtime-image-based jlinking.
 */
@SuppressWarnings("try")
public class LinkDeltaProducer {

    private static final boolean DEBUG = Boolean.getBoolean("build.tools.runtimeimagelinkdeltaproducer.debug");

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java --add-modules jdk.jlink " +
                                   "--add-exports=jdk.jlink/jdk.tools.jlink.internal.runtimelink=ALL-UNNAMED" +
                                   "--add-exports=java.base/jdk.internal.module=ALL-UNNAMED" +
                                   "--add-exports=java.base/jdk.internal.jimage=ALL-UNNAMED" +
                                   "build.tools.linkdeltaproducer.LinkDeltaProducer <packaged-modules> <jimage-to-compare> <output-file>");
            System.exit(1);
        }
        List<ResourceDiff> diffs = null;
        try (ImageResource base = new JmodsReader(Path.of(args[0]));
             ImageResource opt = new ImageReader(Path.of(args[1]));) {

            JimageDiffGenerator diffGen = new JimageDiffGenerator();
            diffs = diffGen.generateDiff(base, opt);

        }
        if (DEBUG) {
            printDiffs(diffs);
        }
        try (FileOutputStream fout = new FileOutputStream(new File(args[2]))) {
            ResourceDiff.write(diffs, fout);
        };
    }

    public static void printDiffs(List<ResourceDiff> diffs) {
        for (ResourceDiff diff: diffs.stream().sorted().collect(Collectors.toList())) {
            switch (diff.getKind()) {
            case ADDED:
                System.out.println("Only added in opt: " + diff.getName());
                break;
            case MODIFIED:
                System.out.println("Modified in opt: " + diff.getName());
                break;
            case REMOVED:
                System.out.println("Removed in opt: " + diff.getName());
                break;
            default:
                break;
            }
        }
    }

}
