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

package compiler.lib.template_framework;

import java.util.List;

/**
 * Let a {@link TemplateWithArgs} generate code at the innermost location where the
 * {@link Hook} was set with {@link Hook#set}.
 *
 * Example:
 * {@snippet lang=java :
 * var myHook = new Hook("MyHook");
 *
 * var template1 = Template.make("name", (String name) -> body(
 *     """
 *     public static int #name = 42;
 *     """
 * ));
 *
 * var template2 = Template.make(() -> body(
 *     """
 *     public class Test {
 *     """,
 *     // Set the hook here.
 *     myHook.set(
 *         """
 *         public static void main(String[] args) {
 *         System.out.println("$field: " + $field)
 *         """,
 *         // Reach up to where the hook was set, and insert the code of template1.
 *         myHook.insert(template1.withArgs($("field"))),
 *         """
 *         }
 *         """
 *     ),
 *     """
 *     }
 *     """
 * ));
 * }
 *
 * @param hook The {@link Hook} the code is to be generated at.
 * @param templateWithArgs The {@link Template} with applied arguments to be generated at the {@link Hook}.
 * @return The {@link Token} which when used inside a {@link Template#body} performs the code generation into the {@link Hook}.
 * @throws RendererException if there is no active {@link Hook#set}.
 */
public record Hook(String name) {
    public Token set(Object... tokens) {
        return new HookSetToken(this, Token.parse(tokens));
    }

    public Token insert(TemplateWithArgs templateWithArgs) {
        return new HookInsertToken(this, templateWithArgs);
    }
}
