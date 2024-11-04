/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.test_generator;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
public class DynamicClassLoader {
    public static Class<?> compileAndLoadClass(String filePath) throws Exception {
        String className = computeClassName(filePath);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int compilationResult = compiler.run(null, null, null, filePath);
        if (compilationResult != 0) {
            throw new RuntimeException("Compilation failed");
        }
        DynamicClassLoaderInternal loader = new DynamicClassLoaderInternal();
        return loader.loadClass(className);
    }
    private static String computeClassName(String filePath) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        String className = fileName.substring(0, fileName.lastIndexOf('.'));
        String packageName = "";
        Path parent = path.getParent();
        if (parent != null) {
            packageName = parent.toString().replace(File.separator, ".");
        }
        if (!packageName.isEmpty()) {
            className = packageName + "." + className;
        }
        return className;
    }
    private static class DynamicClassLoaderInternal extends ClassLoader {
        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            File file = new File(name + ".class");
            if (file.exists()) {
                try {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    return defineClass(null, bytes, 0, bytes.length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return super.findClass(name);
        }
    }
}
