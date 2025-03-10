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

package compiler.lib.template_library;

import java.util.List;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateWithArgs;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

/**
 *
 */
public abstract class IRTestClass {
    public record Info(String classpath, String packageName, String className, List<String> imports, List<String> vmFlags) {};

    public static final Template.TwoArgs<Info, List<TemplateWithArgs>> TEMPLATE =
        Template.make("info", "templates", (Info info, List<TemplateWithArgs> templates) -> body(
            let("classpath", info.classpath),
            let("packageName", info.packageName),
            let("className", info.className),
            """
            package #packageName;

            // --- IMPORTS start ---
            import compiler.lib.ir_framework.*;
            """,
            info.imports.stream().map(i -> "import " + i + ";\n").toList(),
            """
            // --- IMPORTS end   ---

            public class #className {

            // --- CLASS_HOOK insertions start ---
            """,
            Library.CLASS_HOOK.set(
            """
            // --- CLASS_HOOK insertions end   ---

                public static void main() {
                    TestFramework framework = new TestFramework(#className.class);
                    framework.addFlags("-classpath", "#classpath"
            """,
            info.vmFlags().stream().map(f -> ", \"" + f + "\"").toList(),
            """
            );
                    framework.start();
                }

            // --- LIST OF TEMPLATES start ---
            """,
            templates
            ),
            """
            // --- LIST OF TEMPLATES end   ---
            }
            """
        ));
} 
