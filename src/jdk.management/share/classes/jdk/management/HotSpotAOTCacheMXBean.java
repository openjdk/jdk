/*
 * Copyright (c) 2025, Microsoft, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.management;

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Management interface for the JDK's Ahead of Time (AOT) Cache.
 *
 * <p> The management interface is registered with the platform {@link MBeanServer
 * MBeanServer}. The {@link ObjectName ObjectName} that uniquely identifies the management
 * interface within the {@code MBeanServer} is {@code jdk.management:type=HotSpotAOTCache}.
 *
 * <p> Direct access to the MXBean interface can be obtained with
 * {@link ManagementFactory#getPlatformMXBean(Class)}.
 *
 * @since 26
 */
public interface HotSpotAOTCacheMXBean extends PlatformManagedObject {
    /**
     * If an AOT recording is in progress, ends the recording. This method returns
     * after the AOT artifacts have been completely written.
     *
     * <p>The JVM will start recording AOT artifacts upon start-up if appropriate JVM options are
     * given in the command-line. The recording will stop when the JVM exits, or when
     * the {@code endRecording} method is called. Examples:
     *
     * <p> ${@code java -XX:AOTCacheOutput=app.aot ....}
     *
     * <blockquote>
     *    The JVM records optimization information for the current application in the AOT cache file
     *    {@code app.aot}. In a future run of the application, the option {@code -XX:AOTCache=app.aot} will
     *    cause the JVM to use the cache to improve the application's startup and warmup performance.
     * </blockquote>
     *
     * <p>  ${@code java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconfig ....}
     *
     * <blockquote>
     *    The JVM records optimization information for the current application in the AOT configuration
     *    file {@code app.aotconfig}. Subsequently, an AOT cache file can be created with the command:
     *
     *    <p>${@code java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconfig  -XX:AOTCache=app.aot ...}
     *  </blockquote>
     *
     * <p>For more information about creating and using the AOT artifacts, and detailed
     * specification of the corresponding JVM command-line options, please refer
     * to <a href="https://openjdk.org/jeps/483">JEP 483</a> and <a href="https://openjdk.org/jeps/514">JEP 514</a>.
     *
     * <p>Currently there are no APIs to start an AOT recording. AOT recordings must be
     * started using JVM command-line options such as {@code -XX:AOTCacheOutput}.
     * There are also no APIs to query whether an AOT recording is in progress, or what AOT
     * artifacts are being recorded.
     *
     * <p> This method enables an application to end its own AOT recording
     * programatically, but that is not necessarily the best approach. Doing so
     * requires changing the application’s code, which might not be
     * feasible. Even when it is feasible, injecting training-specific logic
     * into the application reduces the similarity between training runs and
     * production runs, potentially making the AOT cache less effective. It may
     * be better to arrange for an external agent to end the training run,
     * thereby creating an AOT cache without interfering with the application’s
     * code.
     *
     * @return {@code true} if a recording was in progress and has been ended
     * successfully; {@code false} otherwise.
     */
    public boolean endRecording();
}
