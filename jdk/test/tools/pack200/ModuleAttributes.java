/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/*
 * @test
 * @bug 8048100
 * @summary test the new Module attributes
 * @compile -XDignore.symbol.file Utils.java ModuleAttributes.java
 * @run main ModuleAttributes
 */
public class ModuleAttributes {

    public static void main(String... args) throws Exception {
        new ModuleAttributes().run();
    }

    public void run() throws Exception {
        File file = createModuleJar();
        Utils.testWithRepack(file,
                "--effort=1",
                "--unknown-attribute=error");
    }

    File createModuleJar() throws IOException {
        File libDir = new File(Utils.JavaHome, "lib");
        File modules = new File(libDir, "modules");
        File outDir = new File("out");

        List<String> cmdList = new ArrayList<>();
        cmdList.add(Utils.getJimageCmd());
        cmdList.add("extract");
        cmdList.add(modules.getAbsolutePath());
        cmdList.add("--dir");
        cmdList.add(outDir.getName());
        Utils.runExec(cmdList);

        FileFilter filter = (File file) -> file.getName().equals("module-info.class");
        List<File> mfiles = Utils.findFiles(outDir, filter);

        List<String> contents = new ArrayList<>(mfiles.size());
        mfiles.stream().forEach((f) -> {
            contents.add(f.getAbsolutePath());
        });

        File listFile = new File("mfiles.list");
        Utils.createFile(listFile, contents);
        File testFile = new File("test.jar");
        Utils.jar("cvf", testFile.getName(), "@" + listFile.getName());
        Utils.recursiveDelete(outDir);
        return testFile;
    }
}
