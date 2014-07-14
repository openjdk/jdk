package com.sun.tools.sjavac.comp;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.sjavac.Util;
import com.sun.tools.sjavac.server.CompilationResult;
import com.sun.tools.sjavac.server.JavacServer;
import com.sun.tools.sjavac.server.JavacService;
import com.sun.tools.sjavac.server.SysInfo;

public class JavacServiceImpl implements JavacService {

    JavacServer javacServer;
    private ThreadLocal<Boolean> forcedExit;

    public JavacServiceImpl(JavacServer javacServer) {
        this.javacServer = javacServer;

    }

    public void logError(String msg) {
//        stderr.println(msg);
        forcedExit.set(true);
    }

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
        ResolveWithDeps.preRegister(context);
        AttrWithDeps.preRegister(context);
        JavaCompilerWithDeps.preRegister(context, this);

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
        // Log the options to be used.
        StringBuilder options = new StringBuilder();
        for (String s : args) {
            options.append(">").append(s).append("< ");
        }
        javacServer.log(protocolId+" <"+invocationId+"> options "+options.toString());

        forcedExit.set(false);
        // Create a new logger.
        StringWriter stdoutLog = new StringWriter();
        StringWriter stderrLog = new StringWriter();
        PrintWriter stdout = new PrintWriter(stdoutLog);
        PrintWriter stderr = new PrintWriter(stderrLog);
        com.sun.tools.javac.main.Main.Result rc = com.sun.tools.javac.main.Main.Result.OK;
        try {
            if (compilationUnits.size() > 0) {
                smartFileManager.setVisibleSources(visibleSources);
                smartFileManager.cleanArtifacts();
                smartFileManager.setLog(stdout);


                // Do the compilation!
                CompilationTask task = compiler.getTask(stderr, smartFileManager, null, Arrays.asList(args), null, compilationUnits, context);
                rc = ((JavacTaskImpl) task).doCall();
                smartFileManager.flush();
            }
        } catch (Exception e) {
            stderr.println(e.getMessage());
            forcedExit.set(true);
        }

        compilationResult.packageArtifacts = smartFileManager.getPackageArtifacts();

        Dependencies deps = Dependencies.instance(context);
        compilationResult.packageDependencies = deps.getDependencies();
        compilationResult.packagePubapis = deps.getPubapis();

        compilationResult.stdout = stdoutLog.toString();
        compilationResult.stderr = stderrLog.toString();
        compilationResult.returnCode = rc.exitCode == 0 && forcedExit.get() ? -1 : rc.exitCode;

        return compilationResult;
    }
}
