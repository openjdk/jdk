/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import jdk.internal.access.SharedSecrets;
import jdk.internal.util.StaticProperty;

public class CDS {
    // Must be in sync with cdsConfig.hpp
    private static final int IS_DUMPING_ARCHIVE              = 1 << 0;
    private static final int IS_DUMPING_METHOD_HANDLES       = 1 << 1;
    private static final int IS_DUMPING_STATIC_ARCHIVE       = 1 << 2;
    private static final int IS_LOGGING_LAMBDA_FORM_INVOKERS = 1 << 3;
    private static final int IS_USING_ARCHIVE                = 1 << 4;
    private static final int configStatus = getCDSConfigStatus();

    /**
     * Should we log the use of lambda form invokers?
     */
    public static boolean isLoggingLambdaFormInvokers() {
        return (configStatus & IS_LOGGING_LAMBDA_FORM_INVOKERS) != 0;
    }

    /**
      * Is the VM writing to a (static or dynamic) CDS archive.
      */
    public static boolean isDumpingArchive() {
        return (configStatus & IS_DUMPING_ARCHIVE) != 0;
    }

    /**
      * Is the VM using at least one CDS archive?
      */
    public static boolean isUsingArchive() {
        return (configStatus & IS_USING_ARCHIVE) != 0;
    }

    /**
      * Is dumping static archive.
      */
    public static boolean isDumpingStaticArchive() {
        return (configStatus & IS_DUMPING_STATIC_ARCHIVE) != 0;
    }

    public static boolean isSingleThreadVM() {
        return isDumpingStaticArchive();
    }

    private static native int getCDSConfigStatus();
    private static native void logLambdaFormInvoker(String line);


    // Used only when dumping static archive to keep weak references alive to
    // ensure that Soft/Weak Reference objects can be reliably archived.
    private static ArrayList<Object> keepAliveList;

    public static void keepAlive(Object s) {
        assert isSingleThreadVM(); // no need for synchronization
        assert isDumpingStaticArchive();
        if (keepAliveList == null) {
            keepAliveList = new ArrayList<>();
        }
        keepAliveList.add(s);
    }

    // This is called by native JVM code at the very end of Java execution before
    // dumping the static archive.
    // It collects the objects from keepAliveList so that they can be easily processed
    // by the native JVM code to check that any Reference objects that need special
    // clean up must have been registed with keepAlive()
    private static Object[] getKeepAliveObjects() {
        return keepAliveList.toArray();
    }

    /**
     * Initialize archived static fields in the given Class using archived
     * values from CDS dump time. Also initialize the classes of objects in
     * the archived graph referenced by those fields.
     *
     * Those static fields remain as uninitialized if there is no mapped CDS
     * java heap data or there is any error during initialization of the
     * object class in the archived graph.
     */
    public static native void initializeFromArchive(Class<?> c);

    /**
     * Ensure that the native representation of all archived java.lang.Module objects
     * are properly restored.
     */
    public static native void defineArchivedModules(ClassLoader platformLoader, ClassLoader systemLoader);

    /**
     * Returns a predictable "random" seed derived from the VM's build ID and version,
     * to be used by java.util.ImmutableCollections to ensure that archived
     * ImmutableCollections are always sorted the same order for the same VM build.
     */
    public static native long getRandomSeedForDumping();

    /**
     * log lambda form invoker holder, name and method type
     */
    public static void logLambdaFormInvoker(String prefix, String holder, String name, String type) {
        if (isLoggingLambdaFormInvokers()) {
            logLambdaFormInvoker(prefix + " " + holder + " " + name + " " + type);
        }
    }

    /**
      * log species
      */
    public static void logSpeciesType(String prefix, String cn) {
        if (isLoggingLambdaFormInvokers()) {
            logLambdaFormInvoker(prefix + " " + cn);
        }
    }

    static final String DIRECT_HOLDER_CLASS_NAME  = "java.lang.invoke.DirectMethodHandle$Holder";
    static final String DELEGATING_HOLDER_CLASS_NAME = "java.lang.invoke.DelegatingMethodHandle$Holder";
    static final String BASIC_FORMS_HOLDER_CLASS_NAME = "java.lang.invoke.LambdaForm$Holder";
    static final String INVOKERS_HOLDER_CLASS_NAME = "java.lang.invoke.Invokers$Holder";

    private static boolean isValidHolderName(String name) {
        return name.equals(DIRECT_HOLDER_CLASS_NAME)      ||
               name.equals(DELEGATING_HOLDER_CLASS_NAME)  ||
               name.equals(BASIC_FORMS_HOLDER_CLASS_NAME) ||
               name.equals(INVOKERS_HOLDER_CLASS_NAME);
    }

    private static boolean isBasicTypeChar(char c) {
         return "LIJFDV".indexOf(c) >= 0;
    }

    private static boolean isValidMethodType(String type) {
        String[] typeParts = type.split("_");
        // check return type (second part)
        if (typeParts.length != 2 || typeParts[1].length() != 1
                || !isBasicTypeChar(typeParts[1].charAt(0))) {
            return false;
        }
        // first part
        if (!isBasicTypeChar(typeParts[0].charAt(0))) {
            return false;
        }
        for (int i = 1; i < typeParts[0].length(); i++) {
            char c = typeParts[0].charAt(i);
            if (!isBasicTypeChar(c)) {
                if (!(c >= '0' && c <= '9')) {
                    return false;
                }
            }
        }
        return true;
    }

    // Throw exception on invalid input
    private static void validateInputLines(String[] lines) {
        for (String s: lines) {
            if (!s.startsWith("[LF_RESOLVE]") && !s.startsWith("[SPECIES_RESOLVE]")) {
                throw new IllegalArgumentException("Wrong prefix: " + s);
            }

            String[] parts = s.split(" ");
            boolean isLF = s.startsWith("[LF_RESOLVE]");

            if (isLF) {
                if (parts.length != 4) {
                    throw new IllegalArgumentException("Incorrect number of items in the line: " + parts.length);
                }
                if (!isValidHolderName(parts[1])) {
                    throw new IllegalArgumentException("Invalid holder class name: " + parts[1]);
                }
                if (!isValidMethodType(parts[3])) {
                    throw new IllegalArgumentException("Invalid method type: " + parts[3]);
                }
            } else {
                if (parts.length != 2) {
                   throw new IllegalArgumentException("Incorrect number of items in the line: " + parts.length);
                }
           }
      }
    }

    /**
     * called from vm to generate MethodHandle holder classes
     * @return {@code Object[]} if holder classes can be generated.
     * @param lines in format of LF_RESOLVE or SPECIES_RESOLVE output
     */
    private static Object[] generateLambdaFormHolderClasses(String[] lines) {
        Objects.requireNonNull(lines);
        validateInputLines(lines);
        Stream<String> lineStream = Arrays.stream(lines);
        Map<String, byte[]> result = SharedSecrets.getJavaLangInvokeAccess().generateHolderClasses(lineStream);
        int size = result.size();
        Object[] retArray = new Object[size * 2];
        int index = 0;
        for (Map.Entry<String, byte[]> entry : result.entrySet()) {
            retArray[index++] = entry.getKey();
            retArray[index++] = entry.getValue();
        };
        return retArray;
    }

    private static native void dumpClassList(String listFileName);
    private static native void dumpDynamicArchive(String archiveFileName);

    private static String drainOutput(InputStream stream, long pid, String tail, List<String> cmds) {
        String fileName  = "java_pid" + pid + "_" + tail;
        new Thread( ()-> {
            try (InputStreamReader isr = new InputStreamReader(stream);
                 BufferedReader rdr = new BufferedReader(isr);
                 PrintStream prt = new PrintStream(fileName)) {
                prt.println("Command:");
                for (String s : cmds) {
                    prt.print(s + " ");
                }
                prt.println("");
                String line;
                while((line = rdr.readLine()) != null) {
                    prt.println(line);
                }
            } catch (IOException e) {
                throw new RuntimeException("IOException happens during drain stream to file " +
                                           fileName + ": " + e.getMessage());
            }}).start();
        return fileName;
    }

    private static String[] excludeFlags = {
         "-XX:DumpLoadedClassList=",
         "-XX:+RecordDynamicDumpInfo",
         "-Xshare:",
         "-XX:SharedClassListFile=",
         "-XX:SharedArchiveFile=",
         "-XX:ArchiveClassesAtExit="};
    private static boolean containsExcludedFlags(String testStr) {
       for (String e : excludeFlags) {
           if (testStr.contains(e)) {
               return true;
           }
       }
       return false;
    }

    /**
    * called from jcmd VM.cds to dump static or dynamic shared archive
    * @param isStatic true for dump static archive or false for dynnamic archive.
    * @param fileName user input archive name, can be null.
    * @return The archive name if successfully dumped.
    */
    private static String dumpSharedArchive(boolean isStatic, String fileName) throws Exception {
        String cwd = new File("").getAbsolutePath(); // current dir used for printing message.
        String currentPid = String.valueOf(ProcessHandle.current().pid());
        String archiveFileName =  fileName != null ? fileName :
            "java_pid" + currentPid + (isStatic ? "_static.jsa" : "_dynamic.jsa");

        String tempArchiveFileName = archiveFileName + ".temp";
        File tempArchiveFile = new File(tempArchiveFileName);
        // The operation below may cause exception if the file or its dir is protected.
        if (!tempArchiveFile.exists()) {
            tempArchiveFile.createNewFile();
        }
        tempArchiveFile.delete();

        if (isStatic) {
            String listFileName = archiveFileName + ".classlist";
            File listFile = new File(listFileName);
            if (listFile.exists()) {
                listFile.delete();
            }
            dumpClassList(listFileName);
            String jdkHome = StaticProperty.javaHome();
            String classPath = System.getProperty("java.class.path");
            List<String> cmds = new ArrayList<String>();
            cmds.add(jdkHome + File.separator + "bin" + File.separator + "java"); // java
            cmds.add("-cp");
            cmds.add(classPath);
            cmds.add("-Xlog:cds");
            cmds.add("-Xshare:dump");
            cmds.add("-XX:SharedClassListFile=" + listFileName);
            cmds.add("-XX:SharedArchiveFile=" + tempArchiveFileName);

            // All runtime args.
            String[] vmArgs = VM.getRuntimeArguments();
            if (vmArgs != null) {
                for (String arg : vmArgs) {
                    if (arg != null && !containsExcludedFlags(arg)) {
                        cmds.add(arg);
                    }
                }
            }

            Process proc = Runtime.getRuntime().exec(cmds.toArray(new String[0]));

            // Drain stdout/stderr to files in new threads.
            String stdOutFileName = drainOutput(proc.getInputStream(), proc.pid(), "stdout", cmds);
            String stdErrFileName = drainOutput(proc.getErrorStream(), proc.pid(), "stderr", cmds);

            proc.waitFor();
            // done, delete classlist file.
            listFile.delete();

            // Check if archive has been successfully dumped. We won't reach here if exception happens.
            // Throw exception if file is not created.
            if (!tempArchiveFile.exists()) {
                throw new RuntimeException("Archive file " + tempArchiveFileName +
                                           " is not created, please check stdout file " +
                                            cwd + File.separator + stdOutFileName + " or stderr file " +
                                            cwd + File.separator + stdErrFileName + " for more detail");
            }
        } else {
            dumpDynamicArchive(tempArchiveFileName);
            if (!tempArchiveFile.exists()) {
                throw new RuntimeException("Archive file " + tempArchiveFileName +
                                           " is not created, please check current working directory " +
                                           cwd  + " for process " +
                                           currentPid + " output for more detail");
            }
        }
        // Override the existing archive file
        File archiveFile = new File(archiveFileName);
        if (archiveFile.exists()) {
            archiveFile.delete();
        }
        if (!tempArchiveFile.renameTo(archiveFile)) {
            throw new RuntimeException("Cannot rename temp file " + tempArchiveFileName + " to archive file" + archiveFileName);
        }
        // Everything goes well, print out the file name.
        String archiveFilePath = new File(archiveFileName).getAbsolutePath();
        System.out.println("The process was attached by jcmd and dumped a " + (isStatic ? "static" : "dynamic") + " archive " + archiveFilePath);
        return archiveFilePath;
    }

    /**
     * Detects if we need to emit explicit class initialization checks in
     * AOT-cached MethodHandles and VarHandles before accessing static fields
     * and methods.
     * @see jdk.internal.misc.Unsafe::shouldBeInitialized
     *
     * @return false only if a call to {@code ensureClassInitialized} would have
     * no effect during the application's production run.
     */
    public static boolean needsClassInitBarrier(Class<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        if ((configStatus & IS_DUMPING_METHOD_HANDLES) == 0) {
            return false;
        } else {
            return needsClassInitBarrier0(c);
        }
    }

    private static native boolean needsClassInitBarrier0(Class<?> c);

    /**
     * This class is used only by native JVM code at CDS dump time for loading
     * "unregistered classes", which are archived classes that are intended to
     * be loaded by custom class loaders during runtime.
     * See src/hotspot/share/cds/unregisteredClasses.cpp.
     */
    private static class UnregisteredClassLoader extends ClassLoader {
        static {
            registerAsParallelCapable();
        }

        static interface Source {
            public byte[] readClassFile(String className) throws IOException;
        }

        static class JarSource implements Source {
            private final JarFile jar;

            JarSource(File file) throws IOException {
                jar = new JarFile(file);
            }

            @Override
            public byte[] readClassFile(String className) throws IOException {
                final var entryName = className.replace('.', '/').concat(".class");
                final var entry = jar.getEntry(entryName);
                if (entry == null) {
                    throw new IOException("No such entry: " + entryName + " in " + jar.getName());
                }
                try (final var in = jar.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            }
        }

        static class DirSource implements Source {
            private final String basePath;

            DirSource(File dir) {
                assert dir.isDirectory();
                basePath = dir.toString();
            }

            @Override
            public byte[] readClassFile(String className) throws IOException {
                final var subPath = className.replace('.', File.separatorChar).concat(".class");
                final var fullPath = Path.of(basePath, subPath);
                return Files.readAllBytes(fullPath);
            }
        }

        private final HashMap<String, Source> sources = new HashMap<>();

        private Source resolveSource(String path) throws IOException {
            Source source = sources.get(path);
            if (source != null) {
                return source;
            }

            final var file = new File(path);
            if (!file.exists()) {
                throw new IOException("No such file: " + path);
            }
            if (file.isFile()) {
                source = new JarSource(file);
            } else if (file.isDirectory()) {
                source = new DirSource(file);
            } else {
                throw new IOException("Not a normal file: " + path);
            }
            sources.put(path, source);

            return source;
        }

        /**
         * Load the class of the given <code>name</code> from the given <code>source</code>.
         * <p>
         * All super classes and interfaces of the named class must have already been loaded:
         * either defined by this class loader (unregistered ones) or loaded, possibly indirectly,
         * by the system class loader (registered ones).
         * <p>
         * If the named class has a registered super class or interface named N there should be no
         * unregistered class or interface named N loaded yet.
         *
         * @param name the name of the class to be loaded.
         * @param source path to a directory or a JAR file from which the named class should be
         *               loaded.
         */
        private Class<?> load(String name, String source) throws IOException {
            final Source resolvedSource = resolveSource(source);
            final byte[] bytes = resolvedSource.readClassFile(name);
            // 'defineClass()' may cause loading of supertypes of this unregistered class by VM
            // calling 'this.loadClass()'.
            //
            // For any supertype S named SN specified in the classlist the following is ensured by
            // the CDS implementation:
            // - if S is an unregistered class it must have already been defined by this class
            //   loader and thus will be found by 'this.findLoadedClass(SN)',
            // - if S is not an unregistered class there should be no unregistered class named SN
            //   loaded yet so either S has previously been (indirectly) loaded by this class loader
            //   and thus it will be found when calling 'this.findLoadedClass(SN)' or it will be
            //   found when delegating to the system class loader, which must have already loaded S,
            //   by calling 'this.getParent().loadClass(SN, false)'.
            // See the implementation of 'ClassLoader.loadClass()' for details.
            //
            // Therefore, we should resolve all supertypes to the expected ones as specified by the
            // "super:" and "interfaces:" attributes in the classlist. This invariant is validated
            // by the C++ function 'ClassListParser::load_class_from_source()'.
            assert getParent() == getSystemClassLoader();
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    /**
     * This class is used only by native JVM code to spawn a child JVM process to assemble
     * the AOT cache. <code>args[]</code> are passed in the <code>JAVA_TOOL_OPTIONS</code>
     * environment variable.
     */
    private static class ProcessLauncher {
        static int execWithJavaToolOptions(String javaLauncher, String args[]) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder().inheritIO().command(javaLauncher);
            StringBuilder sb = new StringBuilder();

            // Encode the args as described in
            // https://docs.oracle.com/en/java/javase/24/docs/specs/jvmti.html#tooloptions
            String prefix = "";
            for (String arg : args) {
                sb.append(prefix);

                for (int i = 0; i < arg.length(); i++) {
                    char c = arg.charAt(i);
                    if (c == '"' || Character.isWhitespace(c)) {
                        sb.append('\'');
                        sb.append(c);
                        sb.append('\'');
                    } else if (c == '\'') {
                        sb.append('"');
                        sb.append(c);
                        sb.append('"');
                    } else {
                        sb.append(c);
                    }
                }

                prefix = " ";
            }

            Map<String, String> env = pb.environment();
            env.put("JAVA_TOOL_OPTIONS", sb.toString());
            env.remove("_JAVA_OPTIONS");
            env.remove("CLASSPATH");
            Process process = pb.start();
            return process.waitFor();
        }
    }
}
