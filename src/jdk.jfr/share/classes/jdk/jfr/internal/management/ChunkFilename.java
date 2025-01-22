/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.management;

import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.io.IOException;

import jdk.jfr.internal.util.ValueFormatter;

// Allows a remote streaming client to create chunk files
// with same naming scheme as the JVM.
public final class ChunkFilename {
   private static final int MAX_CHUNK_NAMES = 100_000;
   private static final String FILE_EXTENSION = ".jfr";

   private final Path directory;

   private Path lastPath;
   private int counter;

   public ChunkFilename(Path directory) {
       this.directory = directory;
   }

   public String next(LocalDateTime time) throws IOException {
       String filename = ValueFormatter.formatDateTime(time);
       Path p = directory.resolve(filename + FILE_EXTENSION);

       // If less than one file per second (typically case)
       if (lastPath == null || !p.equals(lastPath)) {
           if (!Files.exists(p)) {
               counter = 1; // reset counter
               lastPath = p;
               return p.toString();
           }
       }

       // If more than one file per second
       while (counter < MAX_CHUNK_NAMES) {
           String extendedName = makeExtendedName(filename, counter);
           p = directory.resolve(extendedName);
           counter++;
           if (!Files.exists(p)) {
               return p.toString();
           }
       }
       throw new IOException("Unable to find unused filename after " + counter + " attempts");
   }

   private String makeExtendedName(String filename, int counter) {
       StringBuilder sb = new StringBuilder();
       sb.append(filename);
       sb.append('_');
       if (counter < 10) { // chronological sorted
           sb.append('0');
       }
       sb.append(counter);
       sb.append(FILE_EXTENSION);
       return sb.toString();
   }
}
