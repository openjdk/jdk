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
import java.util.ArrayList;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateWithArgs;
import compiler.lib.template_framework.Name;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

// TODO: desc
// simplicity: all fields are public and non-static.
public final class ClassType extends Type {
    public final ClassType superClass;
    public final String className;

    public static record ClassField(String name, Type type) {}
    protected final List<ClassField> fields = new ArrayList<ClassField>();

    public ClassType(String className, ClassType superClass) {
        this.className = className;
        this.superClass = superClass;
    }

    @Override
    public boolean isSubtypeOf(Name.Type other) {
        ClassType ct = this;
        while (ct != null) {
            if (ct == other) {
                return true;
            }
            ct = ct.superClass;
        }
        return false;
    }

    @Override
    public final String name() { return className; }

    @Override
    public final Object con() {
        return "new " + className + "()";
    }

    public final void addField(String name, Type type) {
        fields.add(new ClassField(name, type));
    }

    public final List<ClassField> allFields() {
        List<ClassField> list = new ArrayList<>();
        ClassType ct = this;
        while (ct != null) {
            list.addAll(ct.fields);
            ct = ct.superClass;
        }
        return list;
    }

    public TemplateWithArgs templateWithArgs() {
        var fieldTemplate = Template.make("cf", (ClassField cf) -> body(
                let("name", cf.name()),
                let("type", cf.type()),
                "public #type #name = ", cf.type().con(), ";\n"
        ));
        var template = Template.make(() -> body(
                let("class", className),
                """
                public static class #class {
                """,
                fields.stream().map((ClassField cf) -> fieldTemplate.withArgs(cf)).toList(),
                """
                    public #class() {}
                }
                """
        ));
        return template.withArgs();
    }
}
