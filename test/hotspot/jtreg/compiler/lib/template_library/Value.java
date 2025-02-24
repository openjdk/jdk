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

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.Name;
import compiler.lib.template_framework.TemplateWithArgs;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.addName;
import static compiler.lib.template_framework.Template.weighNames;
import static compiler.lib.template_framework.Template.sampleName;

/**
 * TODO: description
 * How are we allowed to use this? To make sure that the Names are available!
 */
public record Value(Object defTokens, Object useTokens) {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static Value fromUseToken(Object useToken) {
        return new Value("", useToken);
    }

    public static Value makeRandom(Type type) {
        // TODO: more cases
        // Method argument Names?

        // Load from existing Name (e.g. variable or field).
        if (RANDOM.nextInt(3) == 0 && weighNames(type, false) > 0) {
            Name name = sampleName(type, false);
            return fromUseToken(name.name());
        }
        // Define new field, load from it.
        if (RANDOM.nextInt(4) == 0 && Library.CLASS_HOOK.isSet()) {
        }
        return fromUseToken(type.con());
    }
}
