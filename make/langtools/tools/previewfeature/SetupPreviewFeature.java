/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package previewfeature;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.tools.ToolProvider;

/* Construct a hybrid PreviewFeature.Feature enum that includes constants both
 * from the current JDK sources (so that they can be used in the javac API sources),
 * and from the bootstrap JDK (so that they can be used in the bootstrap classfiles).
 *
 * This hybrid enum is only used for the interim javac.
 */
public class SetupPreviewFeature {
    public static void main(String... args) throws Exception {
        Class<?> runtimeFeature = Class.forName("jdk.internal.javac.PreviewFeature$Feature");
        Set<String> constantsToAdd = new HashSet<>();
        for (Field runtimeField : runtimeFeature.getDeclaredFields()) {
            if (runtimeField.isEnumConstant()) {
                constantsToAdd.add(runtimeField.getName());
            }
        }
        var dummy = new StringWriter();
        var compiler = ToolProvider.getSystemJavaCompiler();
        var source = Path.of(args[0]);
        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task =
                    (JavacTask) compiler.getTask(dummy, null, null, null, null, fm.getJavaFileObjects(source));
            task.analyze();
            var sourceFeature = task.getElements()
                                    .getTypeElement("jdk.internal.javac.PreviewFeature.Feature");
            int insertPosition = -1;
            for (var el : sourceFeature.getEnclosedElements()) {
                if (el.getKind() == ElementKind.ENUM_CONSTANT) {
                    constantsToAdd.remove(el.getSimpleName().toString());
                    if (insertPosition == (-1)) {
                        var trees = Trees.instance(task);
                        var elPath = trees.getPath(el);
                        insertPosition = (int) trees.getSourcePositions()
                                                    .getStartPosition(elPath.getCompilationUnit(),
                                                                      elPath.getLeaf());
                    }
                }
            }
            var target = Path.of(args[1]);
            Files.createDirectories(target.getParent());
            if (constantsToAdd.isEmpty()) {
                Files.copy(source, target);
            } else {
                String sourceCode = Files.readString(source);
                try (var out = Files.newBufferedWriter(target)) {
                    out.write(sourceCode, 0, insertPosition);
                    out.write(constantsToAdd.stream()
                                            .collect(Collectors.joining(", ",
                                                                        "/*compatibility constants:*/ ",
                                                                        ",\n")));
                    out.write(sourceCode, insertPosition, sourceCode.length() - insertPosition);
                }
            }
        }
    }
}