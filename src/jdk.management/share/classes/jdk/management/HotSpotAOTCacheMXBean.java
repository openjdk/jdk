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
 * {@code HotSpotAOTCacheMXBean} defines one operation at this time to end the AOT recording.
 * More operations and/or properties may be added in a future release.
 *
 * <p> The management interface is registered with the platform {@link MBeanServer
 * MBeanServer}. The {@link ObjectName ObjectName} that uniquely identifies the management
 * interface within the {@code MBeanServer} is: "jdk.management:type=HotSpotAOTCache".
 *
 * <p> Direct access to the MXBean interface can be obtained with
 * {@link ManagementFactory#getPlatformMXBean(Class)}.
 *
 * @since 26
 */
public interface HotSpotAOTCacheMXBean extends PlatformManagedObject {
       /**
       * If an AOT recording is in progress, ends the recording. This operation completes
       * after the AOT artifacts have been completely written.
       *
       * <p>The JVM will start recording AOT artifacts upon start-up if certain JVM options are
       *  given in the command-line. The recording will stop when the JVM exits, or when
       * the {@code endRecording} method is called. Examples:
       *
       * <p> java -XX:AOTCacheOutput=app.aot ....
       *
       * <blockquote>
       *       The JVM will record optimization information about the current application
       *       into the AOT cache file app.aot. In a future execution of this application,
       *       -XX:AOTCache=app.aot can be provided to improve the application's
       *       start-up and warm-up performance.
       * </blockquote>
       *
       * <p> java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconfig ....
       *
       * <blockquote>
       *       The JVM will record optimization information about the current application
       *       into the AOT configuration file app.aotconfig. Subsequently, an AOT cache
       *       file can be created with the command:
       *
       *       <p>java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconfig  -XX:AOTCache=app.aot ...
       * </blockquote>
       *
       * <p>For more information about creating and using the AOT artifacts, and detailed
       *  specification of the corresponding JVM command-line options, please refer
       * to https://openjdk.org/jeps/483 and https://openjdk.org/jeps/514.
       *
       * <p>Note: Currently there are no APIs to start an AOT recording. AOT recordings must be
       * started using JVM command-line options such as -XX:AOTCacheOutput.
       *
       * <p> There are also no APIs to querying whether the AOT recording is in progress, or what AOT
       * artifacts are being recorded. If such information is required by the application, it should be passed
       * to the application via system properties or command-line arguments. For example:
       *
       * <p> java -XX:AOTCacheOutput=app.aot -Dmyapp.cache.output=app.aot -jar myapp.jar MyApp
       *
       * <blockquote>
       *    The application can contain logic like the following. Note that it's possible
       *    to access the AOT cache file using regular file I/O APIs after the endRecording() function
       *    has returned {@code true}.
       * <pre>
       * HotSpotAOTCacheMXBean bean = ....;
       * String aotCache = System.getProperty("myapp.cache.output");
       * if (aotCache != null) {
       *     System.out.println("JVM is recording into " + aotCache);
       *     performSomeActionsThatNeedsToBeRecorded();
       *     if (bean.endRecording()) {
       *          System.out.println("Recording is successfully finished: " + aotCache);
       *     }
       * }
       * </pre></blockquote>
       *
       * @return {@code true} if a recording was in progress and has been ended successfully; {@code false} otherwise.
       */
      public boolean endRecording();
}