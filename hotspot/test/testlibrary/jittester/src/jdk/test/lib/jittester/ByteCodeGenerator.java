/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import jdk.test.lib.jittester.visitors.ByteCodeVisitor;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.function.BiFunction;

/**
 * Generates class files from bytecode
 */
class ByteCodeGenerator implements BiFunction<IRNode, IRNode, String> {
    private final Path testbase = Paths.get(ProductionParams.testbaseDir.value(),
            "bytecode_tests");

    public void writeJtregBytecodeRunner(String name) {
        try (FileWriter file = new FileWriter(testbase.resolve(name + ".java").toFile())) {
            file.write(Automatic.getJtregHeader(name, false));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String apply(IRNode mainClass, IRNode privateClasses) {
        Automatic.ensureExisting(testbase);
        try {
            ByteCodeVisitor vis = new ByteCodeVisitor();
            if (privateClasses != null) {
                privateClasses.accept(vis);
            }
            mainClass.accept(vis);

            Path mainClassPath = testbase.resolve(mainClass.getName() + ".class");
            writeToClassFile(mainClassPath, vis.getByteCode(mainClass.getName()));
            if (privateClasses != null) {
                privateClasses.getChildren().forEach(c -> {
                    String name = c.getName();
                    Path classPath = testbase.resolve(name + ".class");
                    writeToClassFile(classPath, vis.getByteCode(name));
                });
            }
            return mainClassPath.toString();
        } catch (Throwable t) {
            Path errFile = testbase.resolve(mainClass.getName() + ".err");
            try (PrintWriter pw = new PrintWriter(Files.newOutputStream(errFile,
                    StandardOpenOption.CREATE_NEW))) {
                t.printStackTrace(pw);
            } catch (IOException e) {
                t.printStackTrace();
                throw new Error("can't write error to error file " + errFile, e);
            }
            return null;
        }
    }

    public Path getTestbase() {
        return testbase;
    }

    private void writeToClassFile(Path path, byte[] bytecode) {
        try (FileOutputStream file = new FileOutputStream(path.toString())) {
            file.write(bytecode);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
