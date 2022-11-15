/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac.comp;

import com.sun.tools.javac.Main;
import com.sun.tools.sjavac.Log;
import com.sun.tools.sjavac.Result;
import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.options.Option;
import com.sun.tools.sjavac.options.Options;
import com.sun.tools.sjavac.options.SourceLocation;
import com.sun.tools.sjavac.server.Sjavac;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The sjavac implementation that interacts with javac and performs the actual
 * compilation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SjavacImpl implements Sjavac {

    @Override
    @SuppressWarnings("deprecated")
    public Result compile(String[] args) {
        Options options;
        try {
            options = Options.parseArgs(args);
        } catch (IllegalArgumentException e) {
            Log.error(e.getMessage());
            return Result.CMDERR;
        }

        if (!validateOptions(options))
            return Result.CMDERR;

        if (srcDstOverlap(options.getSources(), options.getDestDir())) {
            return Result.CMDERR;
        }

        if (!createIfMissing(options.getDestDir()))
            return Result.ERROR;

        Path stateDir = options.getStateDir();
        if (stateDir != null && !createIfMissing(options.getStateDir()))
            return Result.ERROR;

        Path gensrc = options.getGenSrcDir();
        if (gensrc != null && !createIfMissing(gensrc))
            return Result.ERROR;

        Path hdrdir = options.getHeaderDir();
        if (hdrdir != null && !createIfMissing(hdrdir))
            return Result.ERROR;

        // Direct logging to our byte array stream.
        StringWriter strWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(strWriter);

        // Prepare arguments
        String[] passThroughArgs = Stream.of(args)
                                         .filter(arg -> !arg.startsWith(Option.SERVER.arg))
                                         .toArray(String[]::new);
        // Compile
        int exitcode = Main.compile(passThroughArgs, printWriter);
        Result result = Result.of(exitcode);

        // Process compiler output (which is always errors)
        printWriter.flush();
        Util.getLines(strWriter.toString()).forEach(Log::error);

        return result;

    }

    @Override
    public void shutdown() {
        // Nothing to clean up
    }

    private static boolean validateOptions(Options options) {

        String err = null;

        if (options.getDestDir() == null) {
            err = "Please specify output directory.";
        } else if (options.isJavaFilesAmongJavacArgs()) {
            err = "Sjavac does not handle explicit compilation of single .java files.";
        } else if (!options.getImplicitPolicy().equals("none")) {
            err = "The only allowed setting for sjavac is -implicit:none";
        } else if (options.getSources().isEmpty() && options.getStateDir() != null) {
            err = "You have to specify -src when using --state-dir.";
        }

        if (err != null)
            Log.error(err);

        return err == null;

    }

    private static boolean srcDstOverlap(List<SourceLocation> locs, Path dest) {
        for (SourceLocation loc : locs) {
            if (isOverlapping(loc.getPath(), dest)) {
                Log.error("Source location " + loc.getPath() + " overlaps with destination " + dest);
                return true;
            }
        }
        return false;
    }

    private static boolean isOverlapping(Path p1, Path p2) {
        p1 = p1.toAbsolutePath().normalize();
        p2 = p2.toAbsolutePath().normalize();
        return p1.startsWith(p2) || p2.startsWith(p1);
    }

    private static boolean createIfMissing(Path dir) {

        if (Files.isDirectory(dir))
            return true;

        if (Files.exists(dir)) {
            Log.error(dir + " is not a directory.");
            return false;
        }

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Log.error("Could not create directory: " + e.getMessage());
            return false;
        }

        return true;
    }

}
