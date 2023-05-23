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
    * @test id=rotation
    * @requires vm.gc.Shenandoah
    *
    * @run main/othervm -Xmx1g -Xms1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
    *      -XX:+ShenandoahRegionSampling -XX:+ShenandoahRegionSampling
    *      -Xlog:gc+region=trace:region-snapshots-%p.log::filesize=100,filecount=3
    *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive
    *      TestShenandoahLogRotation
    */

   import java.io.File;
   import java.util.Arrays;
   import java.nio.file.Files;



   public class TestShenandoahLogRotation {

       static final long TARGET_MB = Long.getLong("target", 1);

       static volatile Object sink;

       public static void main(String[] args) throws Exception {
           long count = TARGET_MB * 1024 * 1024 / 16;
           for (long c = 0; c < count; c++) {
               sink = new Object();
               Thread.sleep(1);
           }

           File directory = new File(".");
           File[] files = directory.listFiles((dir, name) -> name.startsWith("region-snapshots"));
           System.out.println(Arrays.toString(files));
           int smallFilesNumber = 0;
           for (File file : files) {
               if (file.length() < 100) {
                   smallFilesNumber++;
               }
           }
           // Expect one more log file since the ShenandoahLogFileCount doesn't include the active log file
           int expectedNumberOfFiles = 4;
           if (files.length != expectedNumberOfFiles) {
               throw new Error("There are " + files.length + " logs instead of the expected " + expectedNumberOfFiles + " " + files[0].getAbsolutePath());
           }
           if (smallFilesNumber > 1) {
               throw new Error("There should maximum one log with size < " + 100 + "B");
           }
       }

   }
