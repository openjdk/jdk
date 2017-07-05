/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.failurehandler;

import jdk.test.failurehandler.action.ActionSet;
import jdk.test.failurehandler.action.ActionHelper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ToolKit implements EnvironmentInfoGatherer, ProcessInfoGatherer {
    private final List<ActionSet> actions = new ArrayList<>();
    private final ActionHelper helper;

    public ToolKit(ActionHelper helper, PrintWriter log, String... names) {
        this.helper = helper;
        for (String name : names) {
            actions.add(new ActionSet(helper, log, name));
        }
    }

    @Override
    public void gatherEnvironmentInfo(HtmlSection section) {
        for (ActionSet set : actions) {
            set.gatherEnvironmentInfo(section);
        }
    }

    @Override
    public void gatherProcessInfo(HtmlSection section, long pid) {
        Queue<Long> pids = new LinkedList<>();
        pids.add(pid);
        for (Long p = pids.poll(); p != null; p = pids.poll()) {
            HtmlSection pidSection = section.createChildren("" + p);
            for (ActionSet set : actions) {
                set.gatherProcessInfo(pidSection, p);
            }
            List<Long> children = helper.getChildren(pidSection, p);
            if (!children.isEmpty()) {
                HtmlSection s = pidSection.createChildren("children");
                for (Long c : children) {
                    s.link(section, c.toString(), c.toString());
                }
                pids.addAll(children);
            }
        }
    }
}
