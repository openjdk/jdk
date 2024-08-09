/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=default-rotation
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx1g -Xms1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+ShenandoahRegionSampling -XX:+ShenandoahRegionSampling
 *      -Xlog:gc+region=trace:region-snapshots-%p.log::filesize=100,filecount=3
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive
 *      TestRegionSamplingLogging
 */

/*
 * @test id=generational-rotation
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -Xmx1g -Xms1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+ShenandoahRegionSampling -XX:+ShenandoahRegionSampling
 *      -Xlog:gc+region=trace:region-snapshots-%p.log::filesize=100,filecount=3
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive -XX:ShenandoahGCMode=generational
 *      TestRegionSamplingLogging
 */
import java.io.File;
import java.util.Arrays;

public class TestRegionSamplingLogging {

    static final long TARGET_MB = Long.getLong("target", 2_000); // 2 Gb allocation

    static volatile Object sink;

    public static void main(String[] args) throws Exception {
        long count = TARGET_MB * 1024 * 1024 / 16;
        for (long c = 0; c < count; c++) {
            sink = new Object();
        }

        File directory = new File(".");
        File[] files = directory.listFiles((dir, name) -> name.startsWith("region-snapshots") && name.endsWith(".log"));
        System.out.println(Arrays.toString(files));
        if (files == null || files.length == 0) {
            throw new IllegalStateException("Did not find expected snapshot log file.");
        }
    }
}
