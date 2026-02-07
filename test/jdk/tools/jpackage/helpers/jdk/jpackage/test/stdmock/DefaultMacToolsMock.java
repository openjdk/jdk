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

package jdk.jpackage.test.stdmock;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockSpec;

record DefaultMacToolsMock() implements MacToolsMock {

    @Override
    public Collection<CommandMockSpec> mocks() {

        var setfile = CommandActionSpec.create("/Developer/Tools/SetFile", context -> {
            if (context.args().contains("-h")) {
                return Optional.of(0);
            } else {
                return Optional.of(1);
            }
        });

        return Stream.of(setfile).map(action -> {
            return new CommandMockSpec(action.description(), CommandActionSpecs.build().action(action).create());
        }).toList();
    }

    @Override
    public String toString() {
        return "Mac Env";
    }
}
