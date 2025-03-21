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

// TODO: rm?
import compiler.lib.template_framework.Hook;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.Name;
import compiler.lib.template_framework.TemplateWithArgs;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.addName;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.setFuelCost;

/**
 * TODO
 */
public class Dispatcher {
    //// // Ok, now we want to add random code. We want some kind of dispatcher, that we return to
    //// // all the time. Can we also have conditions for Templates, to make sure we only choose
    //// // them when their condition is present?
    //// //
    //// // Dispatcher(list of Templates) -> chooses template, instanciates template with dispatcher.
    //// // -> is this a template or something else???
    //// // -> Dispatcher object, knows what types etc allowed... hmm ok. But then it may have to
    //// //    create templates on the fly to make decisions based on code state?
    //// public static List<Template.OneArgs<xxx>> basicStatements() {
    //// }

    private final List<Template.OneArgs<Dispatcher>> templates;

    public Dispatcher(List<Template.OneArgs<Dispatcher>> templates) {
        this.templates = templates;
    }

    public TemplateWithArgs call() {
        // TODO: something!
        var template = Template.make(() -> body(
            setFuelCost(0),
            let("fuel", fuel()),
            "// $dispatch fuel: #fuel\n",
            (fuel() <= 0) ? "// $empty\n"
                          : Library.choice(templates).withArgs(this)
        ));
        return template.withArgs();
    }
}
