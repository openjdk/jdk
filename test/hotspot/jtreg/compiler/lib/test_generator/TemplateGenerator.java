/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */
package compiler.lib.test_generator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * TemplateGenerator is responsible for generating, compiling, and executing Java test cases
 * based on predefined templates. It supports multithreading to efficiently handle multiple
 * test generation tasks concurrently.
 */
public class TemplateGenerator {
    /*
     * Use-cases:
     * - very narrow: systematically find a bug. very well defined testcase. run extensively
     *   => manual execution, for limited time, no IR, no JTreg?
     *
     * - smarter regression tests. start with failing case. make it randomized to search in local search space
     *   => taking a JTreg test and add holes
     *
     * - a more guided complete java fuzzer. make use of known code-shapes. use in different combinations
     *
     * goals: fast, easy to use, general
     * - failure modes: assert, crashes, interp vs c1 vs c2
     *
     * flags: always on, always off, randomized flags
     *
     * timeout: - failure vs ok to have
     *
     * combination with JTReg and IRFramework
     */

    // Path to the Java home directory
    static final String JAVA_HOME = System.getProperty("java.home");

    // Compilation flags to control JVM behavior during test execution
    static final String[] FLAGS = {
            "-XX:CompileCommand=compileonly,GeneratedTest::test*",
            "-XX:-TieredCompilation"
    };

    // Timeout for test execution in seconds
    static final int TIMEOUT = 120;

    // Number of threads based on available processors
    static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    // Maximum number of tasks that can be queued in the thread pool
    static final int MAX_TASKS_IN_QUEUE = NUM_THREADS * 330;

    // List of Java template files to be used for test generation
    static String[] TEMPLATE_FILES = {
            "InputTemplate1.java",
            "InputTemplate2.java",
            "InputTemplate3.java",
            "InputTemplate4.java",
            "InputTemplate5.java",
            "InputTemplate6.java",
            "InputTemplate7.java",
            "InputTemplate8.java",
            "InputTemplate9.java",
            "InputTemplate10.java"
    };

    // Atomic counter for generating unique test IDs
    private static long testId = 0L;

    /**
     * Generates and returns a unique test ID.
     *
     * @return unique test ID
     */
    public static long getID() {
        return testId++;
    }

    /**
     * The main entry point of the TemplateGenerator. It initializes the thread pool,
     * sets the output directory, compiles and loads each input template, and starts
     * the test generation process.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        // Initialize a thread pool executor with fixed number of threads and a bounded queue
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                NUM_THREADS,
                NUM_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_TASKS_IN_QUEUE)
        );

        // Set the output folder where generated tests will be stored
        setOutputFolder(OUTPUT_FOLDER);

        // Iterate over each template file to compile, load, and generate tests
        for (String filePath : TEMPLATE_FILES) {
            try {
                // Compile and load the template class
                Class<?> inputTemplateClass = compileAndLoadClass(filePath);

                // Instantiate the InputTemplate
                InputTemplate inputTemplate = (InputTemplate) inputTemplateClass.getDeclaredConstructor().newInstance();

                // Start generating tests based on the template
                runTestGen(inputTemplate, threadPool);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Shutdown the thread pool after all tasks are submitted
        threadPool.shutdown();
    }

    // Counter to ensure unique replacements across test methods
    static int nextUniqueId = 0;

    /**
     * Generates tests based on the provided InputTemplate and submits them to the thread pool.
     *
     * @param inputTemplate the template defining how tests should be generated
     * @param threadPool    the thread pool executor to handle test generation tasks
     */
    public static void runTestGen(InputTemplate inputTemplate, ThreadPoolExecutor threadPool) {
        // Retrieve compilation flags specific to the input template
        String[] compileFlags = inputTemplate.getCompileFlags();

        // Get the code segment template from the input template
        CodeSegment template = inputTemplate.getTemplate();

        // Loop to generate the specified number of tests
        for (int i = 0; i < inputTemplate.getNumberOfTests(); i++) {
            // List to hold replacement maps for each test method
            ArrayList<Map<String, String>> replacements = new ArrayList<>();

            // Generate replacements for each test method within the test
            for (int j = 0; j < inputTemplate.getNumberOfTestMethods(); j++) {
                // Generate a random replacement map using a unique ID
                Map<String, String> replacement = inputTemplate.getRandomReplacements(nextUniqueId);
                replacements.add(replacement);
                nextUniqueId++;
            }

            // Get a unique ID for the current test
            long id = getID();

            // Submit the test generation task to the thread pool
            threadPool.submit(() -> doWork(template, replacements, compileFlags, id));
        }
    }

    /**
     * Performs the actual work of generating, writing, compiling, and executing a test.
     *
     * @param template     the code segment template
     * @param replacements the list of replacement maps for the template
     * @param compileFlags the compilation flags to use
     * @param num          the unique test ID
     */
    public static void doWork(CodeSegment template, ArrayList<Map<String, String>> replacements, String[] compileFlags, long num) {
        try {
            // Generate the Java code by applying replacements to the template
            String javaCode = InputTemplate.getJavaCode(template, replacements, num);

            // Write the generated Java code to a file
            String fileName = writeJavaCodeToFile(javaCode, num);

            // Compile and execute the generated Java file
            boolean success = executeJavaFile(fileName, compileFlags);
        } catch (Exception e) {
            // Wrap and rethrow any exceptions encountered during test generation
            throw new RuntimeException("Couldn't find test method: ", e);
        }
    }

    // Directory where generated test files will be stored
    public static String OUTPUT_FOLDER;

    // Static block to initialize the output folder with a timestamp
    static {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        OUTPUT_FOLDER = System.getProperty("user.dir") + File.separator + timeStamp;
        new File(OUTPUT_FOLDER).mkdirs();
    }

    /**
     * Sets the output folder for generated test files.
     *
     * @param outputFolder the path to the output folder
     */
    public static void setOutputFolder(String outputFolder) {
        OUTPUT_FOLDER = outputFolder;
    }

    /**
     * Writes the generated Java code to a file within the output directory.
     *
     * @param javaCode the Java source code to write
     * @param num      the unique test ID used to name the file
     * @return the name of the generated Java file
     * @throws IOException if an I/O error occurs
     */
    private static String writeJavaCodeToFile(String javaCode, long num) throws IOException {
        // Generate a unique file name based on the test ID
        String fileName = String.format("GeneratedTest%d.java", num);

        // Ensure the output directory exists
        File OutputFolder = new File(OUTPUT_FOLDER);
        if (!OutputFolder.exists()) {
            OutputFolder.mkdirs();
        }

        // Create the new Java file
        File file = new File(OUTPUT_FOLDER, fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Write the Java code to the file
            writer.write(javaCode);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return fileName;
    }

    /**
     * Executes the generated Java file by running it as a separate process.
     *
     * @param fileName     the name of the Java file to execute
     * @param compileFlags additional compilation flags to use
     * @return true if the test passed successfully, false otherwise
     * @throws Exception if an error occurs during execution
     */
    private static boolean executeJavaFile(String fileName, String[] compileFlags) throws Exception {
        // Build the process command to execute the Java file
        ProcessBuilder builder = getProcessBuilder(fileName, compileFlags);

        // Start the process
        Process process = builder.start();

        // Wait for the process to complete within the specified timeout
        boolean exited = process.waitFor(TIMEOUT, TimeUnit.SECONDS);

        if (!exited) {
            // If the process times out, forcibly terminate it
            process.destroyForcibly();
            throw new RuntimeException("Process timeout: execution took too long.");
        }

        // Read the output from the process
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Get the exit code of the process
        int exitCode = process.exitValue();

        // Determine if the test passed based on the exit code and output content
        if (exitCode == 0 && Objects.requireNonNull(output).contains("Passed")) {
            System.out.printf("Test passed successfully %s%n", fileName);
            return true;
        } else {
            // If the test failed, print the error output
            System.err.println("Test failed with exit code :");
            System.err.println(output);
            return false;
        }
    }

    /**
     * Constructs a ProcessBuilder to execute the Java file with the specified flags.
     *
     * @param fileName     the name of the Java file to execute
     * @param compileFlags additional compilation flags to use
     * @return a configured ProcessBuilder instance
     */
    private static ProcessBuilder getProcessBuilder(String fileName, String[] compileFlags) {
        List<String> command = new ArrayList<>();

        // Add the path to the Java executable
        command.add("%s/bin/java".formatted(JAVA_HOME));

        // Add general JVM flags
        command.addAll(Arrays.asList(FLAGS));

        // Add any template-specific compilation flags
        if (compileFlags != null) {
            command.addAll(Arrays.asList(compileFlags));
        }

        // Set the classpath to the current directory
        command.add("-cp");
        command.add(".");

        // Add the name of the Java file to execute
        command.add(fileName);

        // Set the working directory to the output folder
        File workingDir = new File(OUTPUT_FOLDER);

        // Initialize the ProcessBuilder with the command
        ProcessBuilder builder = new ProcessBuilder(command);

        // Redirect error stream to the standard output
        builder.redirectErrorStream(true);

        // Set the working directory for the process
        builder.directory(workingDir);

        return builder;
    }

    /**
     * Compiles a Java source file and loads the resulting class.
     *
     * @param filePath the path to the Java source file
     * @return the compiled Class object
     * @throws Exception if compilation or class loading fails
     */
    public static Class<?> compileAndLoadClass(String filePath) throws Exception {
        // Compute the fully qualified class name from the file path
        String className = computeClassName(filePath);

        // Obtain the system Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // Compile the Java source file
        int compilationResult = compiler.run(null, null, null, filePath);

        // Check if the compilation was successful
        if (compilationResult != 0) {
            throw new RuntimeException("Compilation failed");
        }

        // Create a new instance of the custom class loader
        DynamicClassLoader loader = new DynamicClassLoader();

        // Load and return the compiled class
        return loader.loadClass(className);
    }

    /**
     * Computes the fully qualified class name from a given file path.
     *
     * @param filePath the path to the Java source file
     * @return the fully qualified class name
     */
    private static String computeClassName(String filePath) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();

        // Extract the class name by removing the file extension
        String className = fileName.substring(0, fileName.lastIndexOf('.'));

        String packageName = "";

        // Determine the package name based on the parent directories
        Path parent = path.getParent();
        if (parent != null) {
            packageName = parent.toString().replace(File.separator, ".");
        }

        // Combine package name and class name if package exists
        if (!packageName.isEmpty()) {
            className = packageName + "." + className;
        }

        return className;
    }

    /**
     * Custom class loader to load classes from compiled .class files.
     */
    private static class DynamicClassLoader extends ClassLoader {
        /**
         * Finds and loads the class with the specified name.
         *
         * @param name the fully qualified name of the class
         * @return the resulting Class object
         * @throws ClassNotFoundException if the class cannot be found
         */
        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            // Construct the path to the .class file
            File file = new File(name + ".class");

            if (file.exists()) {
                try {
                    // Read all bytes from the .class file
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());

                    // Define the class from the byte array
                    return defineClass(null, bytes, 0, bytes.length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Delegate to the parent class loader if the class file is not found
            return super.findClass(name);
        }
    }
}
