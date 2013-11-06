/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.util.Pair;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

/**
 * Base class for template-driven TestNG javac tests that support on-the-fly
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
@Test
public abstract class JavacTemplateTestBase {
    private static final Set<String> suiteErrors = Collections.synchronizedSet(new HashSet<>());
    private static final AtomicInteger counter = new AtomicInteger();
    private static final File root = new File("gen");
    private static final File nullDir = new File("empty");

    protected final Map<String, Template> templates = new HashMap<>();
    protected final Diagnostics diags = new Diagnostics();
    protected final List<Pair<String, Template>> sourceFiles = new ArrayList<>();
    protected final List<String> compileOptions = new ArrayList<>();
    protected final List<File> classpaths = new ArrayList<>();
    protected final Template.Resolver defaultResolver = new MapResolver(templates);

    private Template.Resolver currentResolver = defaultResolver;

    /** Add a template with a specified name */
    protected void addTemplate(String name, Template t) {
        templates.put(name, t);
    }

    /** Add a template with a specified name */
    protected void addTemplate(String name, String s) {
        templates.put(name, new StringTemplate(s));
    }

    /** Add a source file */
    protected void addSourceFile(String name, Template t) {
        sourceFiles.add(new Pair<>(name, t));
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
    @BeforeMethod
    public void reset() {
        resetCompileOptions();
        resetDiagnostics();
        resetSourceFiles();
        resetTemplates();
        resetClassPaths();
    }

    // After each test method, if the test failed, capture source files and diagnostics and put them in the log
    @AfterMethod
    public void copyErrors(ITestResult result) {
        if (!result.isSuccess()) {
            suiteErrors.addAll(diags.errorKeys());

            List<Object> list = new ArrayList<>();
            Collections.addAll(list, result.getParameters());
            list.add("Test case: " + getTestCaseDescription());
            for (Pair<String, Template> e : sourceFiles)
                list.add("Source file " + e.fst + ": " + e.snd);
            if (diags.errorsFound())
                list.add("Compile diagnostics: " + diags.toString());
            result.setParameters(list.toArray(new Object[list.size()]));
        }
    }

    @AfterSuite
    // After the suite is done, dump any errors to output
    public void dumpErrors() {
        if (!suiteErrors.isEmpty())
            System.err.println("Errors found in test suite: " + suiteErrors);
    }

    /**
     * Get a description of this test case; since test cases may be combinatorially
     * generated, this should include all information needed to describe the test case
     */
    protected String getTestCaseDescription() {
        return this.toString();
    }

    /** Assert that all previous calls to compile() succeeded */
    protected void assertCompileSucceeded() {
        if (diags.errorsFound())
            fail("Expected successful compilation");
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
    protected void assertCompileFailed(String message) {
        if (!diags.errorsFound())
            fail("Expected failed compilation: " + message);
    }

    /** Assert that a previous call to compile() failed with all of the specified error keys */
    protected void assertCompileErrors(String... keys) {
        if (!diags.errorsFound())
            fail("Expected failed compilation");
        for (String k : keys)
            if (!diags.containsErrorKey(k))
                fail("Expected compilation error " + k);
    }

    /** Convert an object, which may be a Template or a String, into a Template */
    protected Template asTemplate(Object o) {
        if (o instanceof Template)
            return (Template) o;
        else if (o instanceof String)
            return new StringTemplate((String) o);
        else
            return new StringTemplate(o.toString());
    }

    /** Compile all registered source files */
    protected void compile() throws IOException {
        compile(false);
    }

    /** Compile all registered source files, optionally generating class files
     * and returning a File describing the directory to which they were written */
    protected File compile(boolean generate) throws IOException {
        List<JavaFileObject> files = new ArrayList<>();
        for (Pair<String, Template> e : sourceFiles)
            files.add(new FileAdapter(e.fst, asTemplate(e.snd)));
        return compile(classpaths, files, generate);
    }

    /** Compile all registered source files, using the provided list of class paths
     * for finding required classfiles, optionally generating class files
     * and returning a File describing the directory to which they were written */
    protected File compile(List<File> classpaths, boolean generate) throws IOException {
        List<JavaFileObject> files = new ArrayList<>();
        for (Pair<String, Template> e : sourceFiles)
            files.add(new FileAdapter(e.fst, asTemplate(e.snd)));
        return compile(classpaths, files, generate);
    }

    private File compile(List<File> classpaths, List<JavaFileObject> files, boolean generate) throws IOException {
        JavaCompiler systemJavaCompiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = systemJavaCompiler.getStandardFileManager(null, null, null);
        if (classpaths.size() > 0)
            fm.setLocation(StandardLocation.CLASS_PATH, classpaths);
        JavacTask ct = (JavacTask) systemJavaCompiler.getTask(null, fm, diags, compileOptions, null, files);
        if (generate) {
            File destDir = new File(root, Integer.toString(counter.incrementAndGet()));
            // @@@ Assert that this directory didn't exist, or start counter at max+1
            destDir.mkdirs();
            fm.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(destDir));
            ct.generate();
            return destDir;
        }
        else {
            ct.analyze();
            return nullDir;
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

        public String expand(String selector) {
            return Behavior.expandTemplate(template, currentResolver);
        }

        public String toString() {
            return expand("");
        }

        public StringTemplate with(final String key, final String value) {
            return new StringTemplateWithResolver(template, new KeyResolver(key, value));
        }

    }

    /** An implementation of Template which is backed by a String and which
     * encapsulates a Resolver for resolving embedded tags. */
    protected class StringTemplateWithResolver extends StringTemplate {
        private final Resolver localResolver;

        public StringTemplateWithResolver(String template, Resolver localResolver) {
            super(template);
            this.localResolver = localResolver;
        }

        @Override
        public String expand(String selector) {
            Resolver saved = currentResolver;
            currentResolver = new ChainedResolver(currentResolver, localResolver);
            try {
                return super.expand(selector);
            }
            finally {
                currentResolver = saved;
            }
        }

        @Override
        public StringTemplate with(String key, String value) {
            return new StringTemplateWithResolver(template, new ChainedResolver(localResolver, new KeyResolver(key, value)));
        }
    }

    /** A Resolver which uses a Map to resolve tags */
    private class KeyResolver implements Template.Resolver {
        private final String key;
        private final String value;

        public KeyResolver(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Template lookup(String k) {
            return key.equals(k) ? new StringTemplate(value) : null;
        }
    }

    private class FileAdapter extends SimpleJavaFileObject {
        private final String filename;
        private final Template template;

        public FileAdapter(String filename, Template template) {
            super(URI.create("myfo:/" + filename), Kind.SOURCE);
            this.template = template;
            this.filename = filename;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return toString();
        }

        public String toString() {
            return Template.Behavior.expandTemplate(template.expand(filename), defaultResolver);
        }
    }
}
