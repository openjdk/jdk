/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Options;
import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.comp.dependencies.DependencyCollector;
import com.sun.tools.sjavac.comp.dependencies.PublicApiCollector;
import com.sun.tools.sjavac.server.CompilationResult;
import com.sun.tools.sjavac.server.Sjavac;
import com.sun.tools.sjavac.server.SysInfo;

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
    public SysInfo getSysInfo() {
        return new SysInfo(Runtime.getRuntime().availableProcessors(),
                           Runtime.getRuntime().maxMemory());
    }

    @Override
    public CompilationResult compile(String protocolId,
                                     String invocationId,
                                     String[] args,
                                     List<File> explicitSources,
                                     Set<URI> sourcesToCompile,
                                     Set<URI> visibleSources) {
        JavacTool compiler = JavacTool.create();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        SmartFileManager smartFileManager = new SmartFileManager(fileManager);
        Context context = new Context();

        // Now setup the actual compilation....
        CompilationResult compilationResult = new CompilationResult(0);

        // First deal with explicit source files on cmdline and in at file.
        ListBuffer<JavaFileObject> compilationUnits = new ListBuffer<>();
        for (JavaFileObject i : fileManager.getJavaFileObjectsFromFiles(explicitSources)) {
            compilationUnits.append(i);
        }
        // Now deal with sources supplied as source_to_compile.
        ListBuffer<File> sourcesToCompileFiles = new ListBuffer<>();
        for (URI u : sourcesToCompile) {
            sourcesToCompileFiles.append(new File(u));
        }
        for (JavaFileObject i : fileManager.getJavaFileObjectsFromFiles(sourcesToCompileFiles)) {
            compilationUnits.append(i);
        }

        // Create a new logger.
        StringWriter stdoutLog = new StringWriter();
        StringWriter stderrLog = new StringWriter();
        PrintWriter stdout = new PrintWriter(stdoutLog);
        PrintWriter stderr = new PrintWriter(stderrLog);
        com.sun.tools.javac.main.Main.Result rc = com.sun.tools.javac.main.Main.Result.OK;
        DependencyCollector depsCollector = new DependencyCollector();
        PublicApiCollector pubApiCollector = new PublicApiCollector();
        PathAndPackageVerifier papVerifier = new PathAndPackageVerifier();
        try {
            if (compilationUnits.size() > 0) {
                smartFileManager.setVisibleSources(visibleSources);
                smartFileManager.cleanArtifacts();
                smartFileManager.setLog(stdout);

                // Do the compilation!
                JavacTaskImpl task =
                        (JavacTaskImpl) compiler.getTask(stderr,
                                                         smartFileManager,
                                                         null,
                                                         Arrays.asList(args),
                                                         null,
                                                         compilationUnits,
                                                         context);
                smartFileManager.setSymbolFileEnabled(!Options.instance(context).isSet("ignore.symbol.file"));
                task.addTaskListener(depsCollector);
                task.addTaskListener(pubApiCollector);
                task.addTaskListener(papVerifier);
                rc = task.doCall();
                smartFileManager.flush();
            }
        } catch (Exception e) {
            stderrLog.append(Util.getStackTrace(e));
            rc = com.sun.tools.javac.main.Main.Result.ERROR;
        }

        compilationResult.packageArtifacts = smartFileManager.getPackageArtifacts();

        Dependencies deps = Dependencies.instance(context);
        for (PackageSymbol from : depsCollector.getSourcePackages()) {
            for (PackageSymbol to : depsCollector.getDependenciesForPkg(from))
                deps.collect(from.fullname, to.fullname);
        }

        for (ClassSymbol cs : pubApiCollector.getClassSymbols())
            deps.visitPubapi(cs);

        if (papVerifier.getMisplacedCompilationUnits().size() > 0) {
            for (CompilationUnitTree cu : papVerifier.getMisplacedCompilationUnits()) {
                System.err.println("Misplaced compilation unit.");
                System.err.println("    Directory: " + Paths.get(cu.getSourceFile().toUri()).getParent());
                System.err.println("    Package:   " + cu.getPackageName());
            }
            rc = com.sun.tools.javac.main.Main.Result.ERROR;
        }

        compilationResult.packageDependencies = deps.getDependencies();
        compilationResult.packagePubapis = deps.getPubapis();
        compilationResult.stdout = stdoutLog.toString();
        compilationResult.stderr = stderrLog.toString();
        compilationResult.returnCode = rc.exitCode;

        return compilationResult;
    }

    @Override
    public void shutdown() {
        // Nothing to clean up
        // ... maybe we should wait for any current request to finish?
    }


    @Override
    public String serverSettings() {
        return "";
    }

}
