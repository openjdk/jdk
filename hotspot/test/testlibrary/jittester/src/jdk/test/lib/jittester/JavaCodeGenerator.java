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

import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiFunction;

/**
 * Generates class files from java source code
 */
class JavaCodeGenerator implements BiFunction<IRNode, IRNode, String> {
    private final Path testbase = Paths.get(ProductionParams.testbaseDir.value(), "java_tests");

    private String generateJavaCode(IRNode mainClass, IRNode privateClasses) {
        StringBuilder code = new StringBuilder();
        JavaCodeVisitor vis = new JavaCodeVisitor();

        code.append(Automatic.getJtregHeader(mainClass.getName(), true));
        if (privateClasses != null) {
            code.append(privateClasses.accept(vis));
        }
        code.append(mainClass.accept(vis));

        return code.toString();
    }

    public Path getTestbase() {
        return testbase;
    }

    @Override
    public String apply(IRNode mainClass, IRNode privateClasses) {
        String code = generateJavaCode(mainClass, privateClasses);
        Automatic.ensureExisting(testbase);
        Path fileName = testbase.resolve(mainClass.getName() + ".java");
        try (FileWriter file = new FileWriter(fileName.toFile())) {
            file.write(code);
            return fileName.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return "";
    }
}
