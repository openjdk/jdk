/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8341342
 * @summary Test Elements methods programmatically
 * @modules jdk.compiler
 * @run junit TestElementsProgrammatic
 */

import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Programmatically test workings of various methods of Elements.
 */
public class TestElementsProgrammatic {

    private final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();

    @ParameterizedTest
    @MethodSource
    public void automaticallyEnter(Consumer<JavacTask> verify) {
        //make sure the get{All,}{Module,Package,Type}Element{s,} methods will automatically enter:
        JavaFileObject input =
                SimpleJavaFileObject.forSource(URI.create("mem://Test.java"), "");
        List<JavaFileObject> inputs = List.of(input);
        JavacTask task = (JavacTask) systemCompiler.getTask(null, null, null, null, null, inputs);
        verify.accept(task);
    }

    private static List<Consumer<JavacTask>> automaticallyEnter() {
        return List.of(
            task -> assertFalse(task.getElements().getAllModuleElements().isEmpty()),
            task -> assertNotNull(task.getElements().getModuleElement("java.base")),
            task -> assertNotNull(task.getElements().getPackageElement("java.lang")),
            task -> assertFalse(task.getElements().getAllPackageElements("java.lang").isEmpty()),
            task -> assertNotNull(task.getElements().getTypeElement("java.lang.Object")),
            task -> assertFalse(task.getElements().getAllTypeElements("java.lang.Object").isEmpty())
        );
    }
}
