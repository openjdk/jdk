/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework.library;

import java.util.List;

import compiler.lib.template_framework.Template;

/**
 * TODO: desc
 */
public class Statement {
    public interface ApplicabilityPredicate {
        boolean check();
    }

    private final Template.OneArg<Context> template;
    private final ApplicabilityPredicate predicate;

    public Statement(Template.OneArg<Context> template, ApplicabilityPredicate predicate) {
        this.template = template;
        this.predicate = predicate;
    }

    public Statement(Template.OneArg<Context> template) {
        this(template, () -> { return true; });
    }

    Template.OneArg<Context> getTemplate() {
        return template;
    }

    boolean isApplicable() {
        return predicate.check();
    }

    public static class Context {
        private final List<Statement> statements;

        public Context(List<Statement> statements) {
            this.statements = statements;
        }

        public Object dispatch() {
            var filtered = statements.stream().filter(Statement::isApplicable).toList();
            if (Template.fuel() <= 0) {
                return "// out of fuel!\n";
            }
            var statement = filtered.get(0);
            return statement.getTemplate().asToken(this);
        }
    }
}
