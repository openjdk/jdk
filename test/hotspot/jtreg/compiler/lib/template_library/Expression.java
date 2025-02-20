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
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.setFuelCost;
import static compiler.lib.template_framework.Template.defineName;
import static compiler.lib.template_framework.Template.countNames;
import static compiler.lib.template_framework.Template.sampleName;

import compiler.lib.template_library.types.Type;

/**
 * TODO: description
 * Idea: generates a template that has a list of {@link Type} holes.
 */
public final class Expression {
    private final Template.OneArgs<List<Object>> template;
    private final List<Type> types;

    Expression(final Template.OneArgs<List<Object>> template, final List<Type> types) {
        this.template = template;
        this.types = types;
    }

    public final List<Type> types() { return this.types; }

    public final TemplateWithArgs withArgs(List<Object> args) {
        // TODO: check length?
        return template.withArgs(args);
    }
} 
