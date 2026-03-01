/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

package tools.javac.combo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class for template-driven JUnit javac tests that support on-the-fly
 * source file generation, compilation, classloading, execution, and separate
 * compilation.
 *
 * <p>Manages a set of templates (which have embedded tags of the form
 * {@code #\{NAME\}}), source files (which are also templates), and compile
 * options.  Test cases can register templates and source files, cause them to
 * be compiled, validate whether the set of diagnostic messages output by the
 * compiler is correct, and optionally load and run the compiled classes.
 *
 * @author Brian Goetz
 */
@ExtendWith(ComboWatcher.class)
public abstract class JavacTemplateTestBase {
    private static final AtomicInteger counter = new AtomicInteger();
    private static final File root = new File("gen");
    private static final File nullDir = new File("empty");

    protected final Map<String, Template> templates = new HashMap<>();
    protected final Diagnostics diags = new Diagnostics();
    protected final List<SourceFile> sourceFiles = new ArrayList<>();
    protected final List<String> compileOptions = new ArrayList<>();
    protected final List<File> classpaths = new ArrayList<>();

    /** Add a template with a specified name */
    protected void addTemplate(String name, Template t) {
        templates.put(name, t);
    }

    /** Add a template with a specified name */
    protected void addTemplate(String name, String s) {
        templates.put(name, new StringTemplate(s));
    }

    /** Add a source file */
    protected void addSourceFile(String name, String template) {
        sourceFiles.add(new SourceFile(name, template));
    }

    /** Add a File to the class path to be used when loading classes; File values
     * will generally be the result of a previous call to {@link #compile()}.
     * This enables testing of separate compilation scenarios if the class path
     * is set up properly.
     */
    protected void addClassPath(File path) {
        classpaths.add(path);
    }

    /**
     * Add a set of compilation command-line options
     */
    protected void addCompileOptions(String... opts) {
        Collections.addAll(compileOptions, opts);
    }

    /** Reset the compile options to the default (empty) value */
    protected void resetCompileOptions() { compileOptions.clear(); }

    /** Remove all templates */
    protected void resetTemplates() { templates.clear(); }

    /** Remove accumulated diagnostics */
    protected void resetDiagnostics() { diags.reset(); }

    /** Remove all source files */
    protected void resetSourceFiles() { sourceFiles.clear(); }

    /** Remove registered class paths */
    protected void resetClassPaths() { classpaths.clear(); }

    // Before each test method, reset everything
    @BeforeEach
    public void reset() {
        resetCompileOptions();
        resetDiagnostics();
        resetSourceFiles();
        resetTemplates();
        resetClassPaths();
    }

    /** Assert that all previous calls to compile() succeeded */
    protected void assertCompileSucceeded() {
        if (diags.errorsFound())
            fail("Expected successful compilation");
    }

    /** Assert that all previous calls to compile() succeeded, also accepts a diagnostics consumer */
    protected void assertCompileSucceeded(Consumer<Diagnostic<?>> diagConsumer) {
        if (diags.errorsFound())
            fail("Expected successful compilation");
        diags.getAllDiags().stream().forEach(diagConsumer);
    }

    /** Assert that all previous calls to compile() succeeded */
    protected void assertCompileSucceededWithWarning(String warning) {
        if (diags.errorsFound())
            fail("Expected successful compilation");
        if (!diags.containsWarningKey(warning)) {
            fail(String.format("Expected compilation warning with %s, found %s", warning, diags.keys()));
        }
    }

    /**
     * If the provided boolean is true, assert all previous compiles succeeded,
     * otherwise assert that a compile failed.
     * */
    protected void assertCompileSucceededIff(boolean b) {
        if (b)
            assertCompileSucceeded();
        else
            assertCompileFailed();
    }

    /** Assert that a previous call to compile() failed */
    protected void assertCompileFailed() {
        if (!diags.errorsFound())
            fail("Expected failed compilation");
    }

    /** Assert that a previous call to compile() failed with a specific error key */
    protected void assertCompileFailed(String key) {
        if (!diags.errorsFound())
            fail("Expected failed compilation: " + key);
        if (!diags.containsErrorKey(key)) {
            fail(String.format("Expected compilation error with %s, found %s", key, diags.keys()));
        }
    }

    protected void assertCompileFailed(String key, Consumer<Diagnostic<?>> diagConsumer) {
        if (!diags.errorsFound())
            fail("Expected failed compilation: " + key);
        if (!diags.containsErrorKey(key)) {
            fail(String.format("Expected compilation error with %s, found %s", key, diags.keys()));
        } else {
            // for additional checks
            diagConsumer.accept(diags.getDiagWithKey(key));
        }
    }

    /** Assert that a previous call to compile() failed with a specific error key */
    protected void assertCompileFailedOneOf(String... keys) {
        if (!diags.errorsFound())
            fail("Expected failed compilation with one of: " + Arrays.asList(keys));
        boolean found = false;
        for (String k : keys)
            if (diags.containsErrorKey(k))
                found = true;
        fail(String.format("Expected compilation error with one of %s, found %s", Arrays.asList(keys), diags.keys()));
    }

    /** Assert that a previous call to compile() failed with all of the specified error keys */
    protected void assertCompileErrors(String... keys) {
        if (!diags.errorsFound())
            fail("Expected failed compilation");
        for (String k : keys)
            if (!diags.containsErrorKey(k))
                fail("Expected compilation error " + k);
    }

    /** Compile all registered source files */
    protected void compile() throws IOException {
        compile(false);
    }

    /** Compile all registered source files, optionally generating class files
     * and returning a File describing the directory to which they were written */
    protected File compile(boolean generate) throws IOException {
        var files = sourceFiles.stream().map(FileAdapter::new).toList();
        return compile(classpaths, files, generate);
    }

    /** Compile all registered source files, using the provided list of class paths
     * for finding required classfiles, optionally generating class files
     * and returning a File describing the directory to which they were written */
    protected File compile(List<File> classpaths, boolean generate) throws IOException {
        var files = sourceFiles.stream().map(FileAdapter::new).toList();
        return compile(classpaths, files, generate);
    }

    private File compile(List<File> classpaths, List<? extends JavaFileObject> files, boolean generate) throws IOException {
        JavaCompiler systemJavaCompiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = systemJavaCompiler.getStandardFileManager(null, null, null)) {
            if (classpaths.size() > 0)
                fm.setLocation(StandardLocation.CLASS_PATH, classpaths);
            JavacTask ct = (JavacTask) systemJavaCompiler.getTask(null, fm, diags, compileOptions, null, files);
            File destDir;
            if (generate) {
                destDir = new File(root, Integer.toString(counter.incrementAndGet()));
                // @@@ Assert that this directory didn't exist, or start counter at max+1
                destDir.mkdirs();
                fm.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(destDir));
            } else {
                destDir = nullDir;
            }
            ct.generate(); // throws ISE if javac crashes
            return destDir;
        }
    }

    /** Load the given class using the provided list of class paths */
    protected Class<?> loadClass(String className, File... destDirs) {
        try {
            List<URL> list = new ArrayList<>();
            for (File f : destDirs)
                list.add(new URL("file:" + f.toString().replace("\\", "/") + "/"));
            return Class.forName(className, true, new URLClassLoader(list.toArray(new URL[list.size()])));
        } catch (ClassNotFoundException | MalformedURLException e) {
            throw new RuntimeException("Error loading class " + className, e);
        }
    }

    /** An implementation of Template which is backed by a String */
    protected class StringTemplate implements Template {
        protected final String template;

        public StringTemplate(String template) {
            this.template = template;
        }

        public String expand(String selectorIgnored) {
            return Template.expandTemplate(template, templates);
        }

        public String toString() {
            return expand("");
        }
    }

    private class FileAdapter extends SimpleJavaFileObject {
        private final String templateString;

        FileAdapter(SourceFile file) {
            super(URI.create("myfo:/" + file.name()), Kind.SOURCE);
            this.templateString = file.template();
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return toString();
        }

        public String toString() {
            return Template.expandTemplate(templateString, templates);
        }
    }
}
