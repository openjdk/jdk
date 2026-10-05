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

import java.util.function.Function;

/**
 * A {@link Hook} can be {@link #anchor}ed for a certain scope ({@link ScopeToken}), and that
 * anchoring stays active for any nested scope or nested {@link Template}. With {@link #insert},
 * one can insert a template ({@link TemplateToken}) or scope ({@link ScopeToken}) to where the
 * {@link Hook} was {@link #anchor}'ed. If the hook was anchored for multiple outer scopes, the
 * innermost is chosen for insertion.
 *
 * <p>
 * This can be useful to reach "back" or to some outer scope, e.g. while generating code for a
 * method, one can reach out to the class scope to insert fields. Or one may want to reach back
 * to the beginning of a method to insert local variables that should be live for the whole method.
 *
 * <p>
 * The choice of {@link ScopeToken} is very important and powerful.
 * For example, if you want to insert a {@link DataName} to the scope of an anchor,
 * it is important that the scope of the insertion is transparent for {@link DataName}s,
 * e.g. using {@link Template#transparentScope}. In most cases, we want {@link DataName}s to escape
 * the inserted scope but not the anchor scope, so the anchor scope should be
 * non-transparent for {@link DataName}s, e.g. using {@link Template#scope}.
 * Example:
 *
 * <p>
 * {@snippet lang=java :
 * var myHook = new Hook("MyHook");
 *
 * var template = Template.make(() -> scope(
 *     """
 *     public class Test {
 *     """,
 *     // Anchor the hook here.
 *     myHook.anchor(scope(
 *         """
 *         public static void main(String[] args) {
 *         System.out.println("$field: " + $field)
 *         """,
 *         // Reach out to where the hook was anchored, and insert some code.
 *         myHook.insert(transparentScope(
 *             // The field (DataName) escapes because the inserted scope is "transparentScope"
 *             addDataName($("field"), Primitives.INTS, MUTABLE),
 *             """
 *             public static int $field = 42;
 *             """
 *         )),
 *         """
 *         }
 *         """
 *     )),
 *     """
 *     }
 *     """
 * ));
 * }
 *
 * <p>
 * Note that if we use {@link #insert} with {@link Template#transparentScope}, then
 * {@link DataName}s  and {@link StructuralName}s escape from the inserted scope to the
 * anchor scope, but hashtag replacements and {@link Template#setFuelCost} escape to
 * the caller, i.e. from where we inserted the scope. This makes sense if we consider
 * {@link DataName}s belonging to the structure of the generated code and the inserted
 * scope belonging to the anchor scope. On the other hand, hashtag replacements and
 * {@link Template#setFuelCost} rather belong to the code generation that happens
 * within the context of a template.
 *
 * @param name The name of the Hook, for debugging purposes only.
 */
public record Hook(String name) {
    /**
     * Anchor this {@link Hook} for the provided inner scope.
     * From anywhere inside this scope, even in nested Templates, code can be
     * {@link #insert}ed back to the location where this {@link Hook} was {@link #anchor}ed.
     *
     * @param innerScope An inner scope, for which the {@link Hook} is anchored.
     * @return A {@link Token} that captures the anchoring and the inner scope.
     */
    public Token anchor(ScopeToken innerScope) {
        return new HookAnchorToken(this, innerScope);
    }

    /**
     * Inserts a {@link TemplateToken} to the innermost location where this {@link Hook} was {@link #anchor}ed.
     * This could be in the same Template, or one nested further out.
     *
     * @param templateToken The Template with applied arguments to be inserted at the {@link Hook}.
     * @return The {@link Token} which represents the code insertion into the {@link Hook}.
     */
    public Token insert(TemplateToken templateToken) {
        return new HookInsertToken(this, Template.transparentScope(templateToken));
    }

    /**
     * Inserts a scope ({@link ScopeToken}) to the innermost location where this {@link Hook} was {@link #anchor}ed.
     * This could be in the same Template, or one nested further out.
     *
     * @param scopeToken The scope to be inserted at the {@link Hook}.
     * @return The {@link Token} which represents the code insertion into the {@link Hook}.
     */
    public Token insert(ScopeToken scopeToken) {
        return new HookInsertToken(this, scopeToken);
    }

    /**
     * Checks if the {@link Hook} was {@link Hook#anchor}ed for the current scope or an outer scope,
     * and makes the boolean result available to an inner scope.
     *
     * @param function the function that generates the inner scope given the boolean result.
     * @return the token that represents the check and inner scope.
     */
    public Token isAnchored(Function<Boolean, ScopeToken> function) {
        return new HookIsAnchoredToken(this, function);
    }
}
