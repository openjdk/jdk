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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * The TemplateGenerator class is responsible for generating and executing test cases
 * based on predefined input templates. It compiles the generated Java code and runs
 * the tests concurrently using a thread pool.
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
    private static final String JAVA_HOME = System.getProperty("java.home");

    // JVM flags for compiling the generated tests
    private static final String[] FLAGS = {
            "-XX:CompileCommand=compileonly,GeneratedTest::test*",
            "-XX:-TieredCompilation"
    };

    // Timeout for each test execution in seconds
    private static final int TIMEOUT = 120;

    // Number of threads in the thread pool, based on available processors
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    // Maximum number of tasks that can be queued in the thread pool
    private static final int MAX_TASKS_IN_QUEUE = NUM_THREADS * 330;

    // List of template files to be used for generating tests
    private static final String[] TEMPLATE_FILES = {
            "InputTemplate1.java", "InputTemplate2.java", "InputTemplate3.java",
            "InputTemplate4.java", "InputTemplate5.java", "InputTemplate6.java",
            "InputTemplate7.java", "InputTemplate8.java", "InputTemplate9.java",
            "InputTemplate10.java"
    };

    // Counter for assigning unique IDs to tests
    private static final AtomicLong testId = new AtomicLong(0L);

    // Counter for generating unique identifiers within replacements
    private static final AtomicInteger nextUniqueId = new AtomicInteger(0);

    // Directory where generated tests will be stored
    private static String outputFolder;

    // Static block to initialize the output folder with a timestamped directory
    static {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        outputFolder = System.getProperty("user.dir") + File.separator + timeStamp;
        new File(outputFolder).mkdirs(); // Create the directory if it doesn't exist
    }

    /**
     * The main method initializes the thread pool, sets up the output folder,
     * loads each input template, and initiates test generation for each template.
     *
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        // Create a thread pool executor for concurrent test generation
        ThreadPoolExecutor threadPool = createThreadPool();

        // Set up the output folder where generated tests will be saved
        setupOutputFolder(outputFolder);

        // Iterate over each template file and process it
        Arrays.stream(TEMPLATE_FILES).forEach(filePath -> {
            try {
                // Load the input template from the specified file
                InputTemplate inputTemplate = loadInputTemplate(filePath);
                // Generate and run tests based on the loaded template
                runTestGeneration(inputTemplate, threadPool);
            } catch (Exception e) {
                // Print stack trace if an exception occurs during processing
                e.printStackTrace();
            }
        });

        // Shutdown the thread pool after all tasks have been submitted
        threadPool.shutdown();
    }

    /**
     * Creates a ThreadPoolExecutor with a fixed number of threads and a bounded queue.
     *
     * @return A configured ThreadPoolExecutor instance
     */
    private static ThreadPoolExecutor createThreadPool() {
        return new ThreadPoolExecutor(
                NUM_THREADS, // Core pool size
                NUM_THREADS, // Maximum pool size
                0L, // Keep-alive time for idle threads
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_TASKS_IN_QUEUE) // Task queue with fixed capacity
        );
    }

    /**
     * Sets up the output folder for storing generated tests.
     *
     * @param folder The path to the output folder
     */
    private static void setupOutputFolder(String folder) {
        outputFolder = folder;
    }

    /**
     * Loads an input template by compiling and loading the specified Java file.
     *
     * @param filePath The path to the input template Java file
     * @return An instance of InputTemplate loaded from the compiled class
     * @throws Exception If there is an error during compilation or loading
     */
    private static InputTemplate loadInputTemplate(String filePath) throws Exception {
        // Compile the Java file and load the resulting class
        Class<?> templateClass = compileAndLoadClass(filePath);
        // Instantiate the InputTemplate from the loaded class
        return (InputTemplate) templateClass.getDeclaredConstructor().newInstance();
    }

    /**
     * Initiates the generation of tests based on the provided input template.
     *
     * @param inputTemplate The input template defining test parameters
     * @param threadPool    The thread pool executor for concurrent test generation
     */
    private static void runTestGeneration(InputTemplate inputTemplate, ThreadPoolExecutor threadPool) {
        // Retrieve compile flags specific to the input template
        String[] compileFlags = inputTemplate.getCompileFlags();

        // Retrieve the code segment template from the input template
        CodeSegment template = inputTemplate.getTemplate();

        // Loop to generate the specified number of tests
        for (int i = 0; i < inputTemplate.getNumberOfTests(); i++) {
            // Generate replacement mappings for the test methods
            ArrayList<Map<String, String>> replacements = generateReplacements(inputTemplate);
            // Assign a unique ID to the test
            long id = getNextTestId();
            // Submit a task to the thread pool to generate and execute the test
            threadPool.submit(() -> generateAndRunTest(template, replacements, compileFlags, id));
        }
    }

    /**
     * Generates a list of replacement mappings for test methods based on the input template.
     *
     * @param inputTemplate The input template defining replacement rules
     * @return A list of maps containing replacement key-value pairs for each test method
     */
    private static ArrayList<Map<String, String>> generateReplacements(InputTemplate inputTemplate) {
        ArrayList<Map<String, String>> replacements = new ArrayList<>();
        // Loop to generate replacements for each test method
        for (int j = 0; j < inputTemplate.getNumberOfTestMethods(); j++) {
            // Retrieve a random set of replacements and increment the unique ID
            replacements.add(inputTemplate.getRandomReplacements(nextUniqueId.getAndIncrement()));
        }
        return replacements;
    }

    /**
     * Generates the Java code for a test, writes it to a file, and executes the test.
     *
     * @param template     The code segment template to be used for generating the test
     * @param replacements The list of replacement mappings for the test methods
     * @param compileFlags The compile flags to be used during test execution
     * @param id           The unique identifier for the test
     */
    private static void generateAndRunTest(CodeSegment template, ArrayList<Map<String, String>> replacements, String[] compileFlags, long id) {
        try {
            // Generate the complete Java code by applying replacements to the template
            String javaCode = generateJavaCode(template, replacements, id);
            // Write the generated Java code to a file and retrieve the filename
            String fileName = writeJavaCodeToFile(javaCode, id);
            // Execute the generated Java file with the specified compile flags
            executeJavaFile(fileName, compileFlags);
        } catch (Exception e) {
            // Wrap and rethrow any exceptions as a runtime exception
            throw new RuntimeException("Test generation error", e);
        }
    }

    /**
     * Generates Java code based on the provided template and replacements.
     *
     * @param inputTemplate      The code segment template.
     * @param inputReplacements  A list of maps containing replacements for each test.
     * @param testNumber         The unique number for the generated test class.
     * @return A string containing the generated Java code.
     */
    public static String generateJavaCode(CodeSegment inputTemplate,
                                          ArrayList<Map<String, String>> inputReplacements,
                                          long testNumber) {
        String template = """
                import java.util.Objects;
                \\{imports}
                public class GeneratedTest\\{num} {
                    \\{statics}
                    public static void main(String args[]) throws Exception {
                        \\{calls}
                        System.out.println("Passed");
                    }
                    \\{methods}
                }
                """;

        if (inputReplacements == null || inputReplacements.isEmpty()) {
            throw new IllegalArgumentException("Input replacements must not be null or empty.");
        }

        CodeSegment aggregatedCodeSegment = fillTemplate(inputTemplate, inputReplacements.get(0));

        for (int i = 1; i < inputReplacements.size(); i++) {
            CodeSegment currentSegment = fillTemplate(inputTemplate, inputReplacements.get(i));
            aggregatedCodeSegment.appendCalls(currentSegment.getCalls());
            aggregatedCodeSegment.appendMethods(currentSegment.getMethods());
            // Assuming imports are common and handled separately
        }

        Map<String, String> replacements = Map.of(
                "num", String.valueOf(testNumber),
                "statics", aggregatedCodeSegment.getStatics(),
                "calls", aggregatedCodeSegment.getCalls(),
                "methods", aggregatedCodeSegment.getMethods(),
                "imports", aggregatedCodeSegment.getImports()
        );

        return performReplacements(template, replacements);
    }


    /**
     * Performs placeholder replacements in the template string based on the provided map.
     * It preserves the indentation of the placeholders.
     *
     * @param template     The template string containing placeholders.
     * @param replacements A map of placeholder keys and their replacement values.
     * @return The template string with all placeholders replaced.
     */
    public static String performReplacements(String template, Map<String, String> replacements) {
        if (template == null || replacements == null) {
            throw new IllegalArgumentException("Template and replacements must not be null.");
        }

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String pattern = "\\{%s}".formatted(entry.getKey());
            String replacement = entry.getValue();

            // Find the pattern in the template and determine its indentation level
            int index = template.indexOf(pattern);
            if (index != -1) {
                int lineStart = template.lastIndexOf('\n', index);
                String indentation = template.substring(lineStart + 1, index).replaceAll("\\S", "");

                // Add indentation to all lines of the replacement (except the first one)
                String indentedReplacement = replacement.lines()
                        .collect(Collectors.joining("\n%s".formatted(indentation)));
                template = template.replace(pattern, indentedReplacement);
            }
        }
        return template;
    }

    /**
     * Fills the template with the provided replacements.
     *
     * @param codeSegment  The original code segment template.
     * @param replacements A map containing replacement values.
     * @return A new CodeSegment with replacements applied.
     */
    private static CodeSegment fillTemplate(CodeSegment codeSegment, Map<String, String> replacements) {
        String statics = performReplacements(codeSegment.getStatics(), replacements);
        String calls = performReplacements(codeSegment.getCalls(), replacements);
        String methods = performReplacements(codeSegment.getMethods(), replacements);
        String imports = performReplacements(codeSegment.getImports(), replacements);
        return new CodeSegment(statics, calls, methods, imports);
    }

    /**
     * Retrieves the next unique test ID in a thread-safe manner.
     *
     * @return The next unique test ID
     */
    private static long getNextTestId() {
        return testId.getAndIncrement();
    }

    /**
     * Writes the generated Java code to a file in the output folder.
     *
     * @param javaCode The Java source code to be written
     * @param id       The unique identifier for the test, used in the filename
     * @return The name of the file to which the Java code was written
     * @throws IOException If an error occurs during file writing
     */
    private static String writeJavaCodeToFile(String javaCode, long id) throws IOException {
        // Construct the filename using the unique test ID
        String fileName = String.format("GeneratedTest%d.java", id);
        File file = new File(outputFolder, fileName);
        // Use BufferedWriter to write the Java code to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(javaCode);
        }
        return fileName;
    }

    /**
     * Executes the generated Java file using the specified compile flags.
     *
     * @param fileName     The name of the Java file to execute
     * @param compileFlags The compile flags to be used during execution
     * @throws Exception If an error occurs during process execution or if the test fails
     */
    private static void executeJavaFile(String fileName, String[] compileFlags) throws Exception {
        // Create a ProcessBuilder configured to execute the Java file with the given flags
        ProcessBuilder builder = createProcessBuilder(fileName, compileFlags);
        Process process = builder.start(); // Start the process

        // Wait for the process to complete within the specified timeout
        if (!process.waitFor(TIMEOUT, TimeUnit.SECONDS)) {
            process.destroyForcibly(); // Forcefully terminate the process if it times out
            throw new RuntimeException("Process timeout: execution took too long.");
        }

        // Read the output from the process
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.exitValue(); // Get the exit code of the process

        // Check if the test passed based on exit code and output content
        if (exitCode == 0 && output.contains("Passed")) {
            System.out.printf("Test passed: %s%n", fileName);
        } else {
            // Print error messages if the test failed
            System.err.println("Test failed with exit code: " + exitCode);
            System.err.println(output);
        }
    }

    /**
     * Creates a ProcessBuilder configured to execute the specified Java file with given compile flags.
     *
     * @param fileName     The name of the Java file to execute
     * @param compileFlags The compile flags to be used during execution
     * @return A configured ProcessBuilder instance
     */
    private static ProcessBuilder createProcessBuilder(String fileName, String[] compileFlags) {
        // Initialize the command list with the Java executable and classpath
        List<String> command = new ArrayList<>(Arrays.asList(
                String.format("%s/bin/java", JAVA_HOME),
                "-cp", "."
        ));

        // Add predefined JVM flags
        command.addAll(Arrays.asList(FLAGS));

        // Add any additional compile flags from the input template
        if (compileFlags != null) command.addAll(Arrays.asList(compileFlags));

        // Add the name of the Java file to be executed
        command.add(fileName);

        // Configure the ProcessBuilder with the command and set the working directory
        return new ProcessBuilder(command)
                .directory(new File(outputFolder)) // Set the working directory to the output folder
                .redirectErrorStream(true); // Redirect error stream to the standard output
    }

    /**
     * Compiles the specified Java file and loads the resulting class dynamically.
     *
     * @param filePath The path to the Java file to compile
     * @return The Class object representing the compiled class
     * @throws Exception If compilation fails or the class cannot be loaded
     */
    private static Class<?> compileAndLoadClass(String filePath) throws Exception {
        // Compute the fully qualified class name based on the file path
        String className = computeClassName(filePath);

        // Obtain the system Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler.run(null, null, null, filePath) != 0) {
            // Throw an exception if the compilation fails
            throw new RuntimeException("Compilation failed for " + filePath);
        }

        // Use the custom class loader to load the compiled class
        return new DynamicClassLoader().loadClass(className);
    }

    /**
     * Computes the fully qualified class name from the given file path.
     *
     * @param filePath The path to the Java file
     * @return The fully qualified class name
     */
    private static String computeClassName(String filePath) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        // Extract the class name by removing the file extension
        String className = fileName.substring(0, fileName.lastIndexOf('.'));

        // Determine the package name based on the parent directory structure
        Path parent = path.getParent();
        String packageName = (parent != null) ? parent.toString().replace(File.separator, ".") : "";

        // Combine package name and class name if a package exists
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    /**
     * A custom ClassLoader for dynamically loading compiled classes from the file system.
     */
    private static class DynamicClassLoader extends ClassLoader {
        /**
         * Attempts to find and load the class with the specified name.
         *
         * @param name The fully qualified name of the class
         * @return The Class object representing the loaded class
         * @throws ClassNotFoundException If the class cannot be found or loaded
         */
        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            // Construct the expected class file path
            File file = new File(name + ".class");
            if (file.exists()) {
                try {
                    // Read the class file bytes
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    // Define the class using the read bytes
                    return defineClass(null, bytes, 0, bytes.length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Delegate to the parent ClassLoader if the class file does not exist
            return super.findClass(name);
        }
    }

    private static final Random RANDOM = new Random(32);

    // Integer test values including edge cases
    public static final Integer[] INTEGER_VALUES = {
            -2, -1, 0, 1, 2,
            Integer.MIN_VALUE, Integer.MAX_VALUE
    };

    // Positive integer test values including edge cases
    public static final Integer[] POSITIVE_INTEGER_VALUES = {
            1, 2, Integer.MAX_VALUE
    };

    // Array sizes for testing
    public static final Integer[] ARRAY_SIZES = {
            1, 10, 100, 1_000, 10_000, 100_000, 200_000, 500_000, 1_000_000
    };

    // Non-zero integer values
    public static final Integer[] INTEGER_VALUES_NON_ZERO = {
            -2, -1, 1, 2,
            Integer.MIN_VALUE, Integer.MAX_VALUE
    };

    // Long test values including edge cases
    public static final Long[] LONG_VALUES = {
            -2L, -1L, 0L, 1L, 2L,
            (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE,
            Long.MIN_VALUE, Long.MAX_VALUE
    };

    // Non-zero long values
    public static final Long[] LONG_VALUES_NON_ZERO = {
            -2L, -1L, 1L, 2L,
            (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE,
            Long.MIN_VALUE, Long.MAX_VALUE
    };

    // Short test values including edge cases
    public static final Short[] SHORT_VALUES = {
            -2, -1, 0, 1, 2,
            Short.MIN_VALUE, Short.MAX_VALUE
    };

    // Non-zero short values
    public static final Short[] SHORT_VALUES_NON_ZERO = {
            -2, -1, 1, 2,
            Short.MIN_VALUE, Short.MAX_VALUE
    };


    /**
     * Retrieves a random value from the provided array.
     *
     * @param array Array of values to choose from.
     * @param <T>   Type of the array elements.
     * @return A randomly selected element from the array.
     */
    public static <T> T getRandomValue(T[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array must not be null or empty.");
        }
        return array[RANDOM.nextInt(array.length)];
    }

    /**
     * Retrieves a random value from the provided array and converts it to a string.
     *
     * @param array Array of values to choose from.
     * @param <T>   Type of the array elements.
     * @return A string representation of a randomly selected element from the array.
     */
    public static <T> String getRandomValueAsString(T[] array) {
        T value = getRandomValue(array);
        return String.valueOf(value);
    }

    /**
     * Generates a unique identifier based on the current system time in nanoseconds.
     *
     * @return A unique identifier as a string.
     */
    public static String getUniqueId() {
        return String.valueOf(System.nanoTime());
    }

    // Atomic counter to ensure thread-safe unique number generation
    private static final AtomicInteger uniqueCounter = new AtomicInteger(1);

    // Pattern to identify placeholders in the format $variableName
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$(\\w+)");

    /**
     * Processes a template string to prevent variable name conflicts by appending a unique identifier.
     *
     * <p>This method scans the input template for placeholders, which are denoted by a '$' followed by a word
     * representing the variable name. Each detected placeholder is replaced with the variable name concatenated
     * with a unique number, ensuring that variable names remain distinct and do not clash within the processed string.
     *
     * @param template The template string containing placeholders to be processed.
     * @return The processed string with unique identifiers appended to variable names.
     */
    public static String avoidConflict(String template) {
        int uniqueId = uniqueCounter.getAndIncrement();
        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variableName + uniqueId;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
