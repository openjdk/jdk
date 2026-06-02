/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.launcher;

import com.sun.tools.javac.resources.LauncherProperties.Errors;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import jdk.internal.misc.MethodFinder;
import jdk.internal.misc.VM;

/**
 * Compiles a source file, and executes the main method it contains.
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
public final class SourceLauncher {
    /**
     * Compiles a source file, and executes the main method it contains.
     *
     * <p>This is normally invoked from the Java launcher, either when
     * the {@code --source} option is used, or when the first argument
     * that is not part of a runtime option ends in {@code .java}.
     *
     * <p>The first entry in the {@code args} array is the source file
     * to be compiled and run; all subsequent entries are passed as
     * arguments to the main method of the first class found in the file.
     *
     * <p>If any problem occurs before executing the main class, it will
     * be reported to the standard error stream, and the JVM will be
     * terminated by calling {@code System.exit} with a non-zero return code.
     *
     * @param args the arguments
     * @throws Throwable if the main method throws an exception
     */
    public static void main(String... args) throws Throwable {
        try {
            new SourceLauncher(System.err)
                    .run(VM.getRuntimeArguments(), args);
        } catch (Fault f) {
            System.err.println(f.getMessage());
            System.exit(1);
        } catch (InvocationTargetException e) {
            // leave VM to handle the stacktrace, in the standard manner
            throw e.getCause();
        }
    }

    /** Stream for reporting errors, such as compilation errors. */
    private final PrintWriter out;

    /**
     * Creates an instance of this class, providing a stream to which to report
     * any errors.
     *
     * @param out the stream
     */
    public SourceLauncher(PrintStream out) {
        this(new PrintWriter(new OutputStreamWriter(out), true));
    }

    /**
     * Creates an instance of this class, providing a stream to which to report
     * any errors.
     *
     * @param out the stream
     */
    public SourceLauncher(PrintWriter out) {
        this.out = out;
    }

    /**
     * Compiles a source file, and executes the main method it contains.
     *
     * <p>The first entry in the {@code args} array is the source file
     * to be compiled and run; all subsequent entries are passed as
     * arguments to the main method of the first class found in the file.
     *
     * <p>Options for {@code javac} are obtained by filtering the runtime arguments.
     *
     * <p>If the main method throws an exception, it will be propagated in an
     * {@code InvocationTargetException}. In that case, the stack trace of the
     * target exception will be truncated such that the main method will be the
     * last entry on the stack. In other words, the stack frames leading up to the
     * invocation of the main method will be removed.
     *
     * @param runtimeArgs the runtime arguments
     * @param args the arguments
     * @throws Fault if a problem is detected before the main method can be executed
     * @throws InvocationTargetException if the main method throws an exception
     */
    public Result run(String[] runtimeArgs, String[] args) throws Fault, InvocationTargetException {
        Path file = getFile(args);

        ProgramDescriptor program = ProgramDescriptor.of(ProgramFileObject.of(file));
        RelevantJavacOptions options = RelevantJavacOptions.of(program, runtimeArgs);
        MemoryContext context = new MemoryContext(out, program, options);
        context.compileProgram();

        String[] mainArgs = Arrays.copyOfRange(args, 1, args.length);
        var appClass = execute(context, mainArgs);

        return new Result(appClass, context.getNamesOfCompiledClasses());
    }

    /**
     * Returns the path for the filename found in the first of an array of arguments.
     *
     * @param args the array
     * @return the path, as given in the array of args
     * @throws Fault if there is a problem determining the path, or if the file does not exist
     */
    private Path getFile(String[] args) throws Fault {
        if (args.length == 0) {
            // should not happen when invoked from launcher
            throw new Fault(Errors.NoArgs);
        }
        Path file;
        try {
            file = Paths.get(args[0]);
        } catch (InvalidPathException e) {
            throw new Fault(Errors.InvalidFilename(args[0]));
        }
        if (!Files.exists(file)) {
            // should not happen when invoked from launcher
            throw new Fault(Errors.FileNotFound(file));
        }
        return file;
    }

    /**
     * Invokes the {@code main} method of a program class, using a class loader that
     * will load recently compiled classes from memory.
     *
     * @param mainArgs the arguments for the {@code main} method
     * @param context the context for the class to be executed
     * @throws Fault if there is a problem finding or invoking the {@code main} method
     * @throws InvocationTargetException if the {@code main} method throws an exception
     */
    private Class<?> execute(MemoryContext context, String[] mainArgs)
            throws Fault, InvocationTargetException {
        System.setProperty("jdk.launcher.sourcefile", context.getSourceFileAsString());
        ClassLoader parentLoader = ClassLoader.getSystemClassLoader();
        ProgramDescriptor program = context.getProgramDescriptor();

        // 1. Find a main method in the first class and if there is one - invoke it
        Class<?> mainClass;
        String firstClassName = program.qualifiedTypeNames().getFirst();
        ClassLoader loader = context.newClassLoaderFor(parentLoader, firstClassName);
        Thread.currentThread().setContextClassLoader(loader);
        try {
            mainClass = Class.forName(firstClassName, false, loader);
        } catch (ClassNotFoundException e) {
            throw new Fault(Errors.CantFindClass(firstClassName));
        }

        Method mainMethod = MethodFinder.findMainMethod(mainClass);
        if (mainMethod == null) {
            // 2. If the first class doesn't have a main method, look for a class with a matching name
            var compilationUnitName = program.fileObject().getFile().getFileName().toString();
            assert compilationUnitName.endsWith(".java");
            var expectedSimpleName = compilationUnitName.substring(0, compilationUnitName.length() - 5);
            var expectedPackageName = program.packageName().orElse("");
            var expectedName = expectedPackageName.isEmpty()
                    ? expectedSimpleName
                    : expectedPackageName + '.' + expectedSimpleName;
            var actualName = program.qualifiedTypeNames().stream()
                    .filter(name -> name.equals(expectedName))
                    .findFirst()
                    .orElseThrow(() -> new Fault(Errors.CantFindClass(expectedName)));

            try {
                mainClass = Class.forName(actualName, false, mainClass.getClassLoader());
            } catch (ClassNotFoundException ignore) {
                throw new Fault(Errors.CantFindClass(actualName));
            }
            mainMethod = MethodFinder.findMainMethod(mainClass);
            if (mainMethod == null) {
                throw new Fault(Errors.CantFindMainMethod(actualName));
            }
        }

        String mainClassName = mainClass.getName();
        var isStatic = Modifier.isStatic(mainMethod.getModifiers());

        Object instance = null;

        // Similar to sun.launcher.LauncherHelper#checkAndLoadMain, including
        // checks performed in LauncherHelper#validateMainMethod
        if (!isStatic) {
            if (Modifier.isAbstract(mainClass.getModifiers())) {
                throw new Fault(Errors.CantInstantiate(mainClassName));
            }

            Constructor<?> constructor;
            try {
                constructor = mainClass.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new Fault(Errors.CantFindConstructor(mainClassName));
            }

            if (Modifier.isPrivate(constructor.getModifiers())) {
                throw new Fault(Errors.CantUsePrivateConstructor(mainClassName));
            }

            try {
                constructor.setAccessible(true);
                instance = constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new Fault(Errors.CantAccessConstructor(mainClassName));
            }
        }

        try {
            // Similar to sun.launcher.LauncherHelper#executeMainClass
            // but duplicated here to prevent additional launcher frames
            mainMethod.setAccessible(true);
            Object receiver = isStatic ? mainClass : instance;

            if (mainMethod.getParameterCount() == 0) {
                mainMethod.invoke(receiver);
            } else {
                mainMethod.invoke(receiver, (Object)mainArgs);
            }
        } catch (IllegalAccessException e) {
            throw new Fault(Errors.CantAccessMainMethod(mainClassName));
        } catch (InvocationTargetException exception) {
            // remove stack frames for source launcher
            StackTraceElement[] invocationElements = exception.getStackTrace();
            if (invocationElements == null) throw exception;
            Throwable cause = exception.getCause();
            if (cause == null) throw exception;
            StackTraceElement[] causeElements = cause.getStackTrace();
            if (causeElements == null) throw exception;
            int range = causeElements.length - invocationElements.length;
            if (range >= 0) {
                cause.setStackTrace(Arrays.copyOfRange(causeElements, 0, range));
            }
            throw exception;
        }

        return mainClass;
    }
}
