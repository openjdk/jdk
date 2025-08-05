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

/**
 * {@link Hook}s can be {@link #anchor}ed for a certain scope in a Template, and all nested
 * Templates in this scope, and then from within this scope, any Template can
 * {@link #insert} code to where the {@link Hook} was {@link #anchor}ed. This can be useful to reach
 * "back" or to some outer scope, e.g. while generating code for a method, one can reach out
 * to the class scope to insert fields.
 *
 * <p>
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
 *     // Anchor the hook here.
 *     myHook.anchor(
 *         """
 *         public static void main(String[] args) {
 *         System.out.println("$field: " + $field)
 *         """,
 *         // Reach out to where the hook was anchored, and insert the code of template1.
 *         myHook.insert(template1.asToken($("field"))),
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
 * @param name The name of the Hook, for debugging purposes only.
 */
public record Hook(String name) {
    /**
     * Anchor this {@link Hook} for the scope of the provided {@code 'tokens'}.
     * From anywhere inside this scope, even in nested Templates, code can be
     * {@link #insert}ed back to the location where this {@link Hook} was {@link #anchor}ed.
     *
     * @param tokens A list of tokens, which have the same restrictions as {@link Template#body}.
     * @return A {@link Token} that captures the anchoring of the scope and the list of validated {@link Token}s.
     */
    public Token anchor(Object... tokens) {
        return new HookAnchorToken(this, TokenParser.parse(tokens));
    }

    /**
     * Inserts a {@link TemplateToken} to the innermost location where this {@link Hook} was {@link #anchor}ed.
     * This could be in the same Template, or one nested further out.
     *
     * @param templateToken The Template with applied arguments to be inserted at the {@link Hook}.
     * @return The {@link Token} which when used inside a {@link Template#body} performs the code insertion into the {@link Hook}.
     */
    public Token insert(TemplateToken templateToken) {
        return new HookInsertToken(this, templateToken);
    }

    /**
     * Checks if the {@link Hook} was {@link Hook#anchor}ed for the current scope or an outer scope.
     *
     * @return If the {@link Hook} was {@link Hook#anchor}ed for the current scope or an outer scope.
     */
    public boolean isAnchored() {
        return Renderer.getCurrent().isAnchored(this);
    }
}
