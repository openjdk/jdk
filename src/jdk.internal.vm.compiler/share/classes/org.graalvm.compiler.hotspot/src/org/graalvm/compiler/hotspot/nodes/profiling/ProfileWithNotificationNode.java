/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.nodes.profiling;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public abstract class ProfileWithNotificationNode extends ProfileNode {
    public static final NodeClass<ProfileWithNotificationNode> TYPE = NodeClass.create(ProfileWithNotificationNode.class);

    protected int freqLog;

    protected ProfileWithNotificationNode(NodeClass<? extends ProfileNode> c, ResolvedJavaMethod method, int freqLog, int probabilityLog) {
        super(c, method, probabilityLog);
        this.freqLog = freqLog;
    }

    public ProfileWithNotificationNode(ResolvedJavaMethod method, int freqLog, int probabilityLog) {
        super(TYPE, method, probabilityLog);
        this.freqLog = freqLog;
    }

    /**
     * Get the logarithm base 2 of the notification frequency.
     */
    public int getNotificationFreqLog() {
        return freqLog;
    }

    /**
     * Set the logarithm base 2 of the notification frequency.
     */
    public void setNotificationFreqLog(int freqLog) {
        assert freqLog < 32;
        this.freqLog = freqLog;
    }

    public void setNotificationOff() {
        setNotificationFreqLog(-1);
    }

}
