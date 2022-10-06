/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class performs matching on a collection of {@link Matchable} objects. It returns a list of {@link MatchResult}
 * objects which failed or an empty list of there was not failure.
 */
public class MatchableMatcher {
    private final Collection<? extends Matchable> matchables;

    public MatchableMatcher(Collection<? extends Matchable> matchables) {
        this.matchables = matchables;
    }

    public List<MatchResult> match() {
        List<MatchResult> results = new ArrayList<>();
        for (Matchable matchable : matchables) {
            MatchResult matchResult = matchable.match();
            if (matchResult.fail()) {
                results.add(matchResult);
            }
        }
        return results;
    }
}
