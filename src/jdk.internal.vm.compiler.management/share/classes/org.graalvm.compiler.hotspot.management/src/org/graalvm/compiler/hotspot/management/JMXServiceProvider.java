/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.management;

import static java.lang.Thread.currentThread;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.serviceprovider.JMXService;

import com.sun.management.ThreadMXBean;

/**
 * Implementation of {@link JMXService} for JDK 11 and later.
 */
@ServiceProvider(JMXService.class)
public class JMXServiceProvider extends JMXService {
    private final ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Override
    protected long getThreadAllocatedBytes(long id) {
        return threadMXBean.getThreadAllocatedBytes(id);
    }

    @Override
    protected long getCurrentThreadCpuTime() {
        long[] times = threadMXBean.getThreadCpuTime(new long[]{currentThread().getId()});
        return times[0];
    }

    @Override
    protected boolean isThreadAllocatedMemorySupported() {
        return threadMXBean.isThreadAllocatedMemorySupported();
    }

    @Override
    protected boolean isCurrentThreadCpuTimeSupported() {
        return threadMXBean.isThreadCpuTimeSupported();
    }

    @Override
    protected List<String> getInputArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }
}
