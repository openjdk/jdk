/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.UncheckedDocletException;
import jdk.javadoc.internal.doclets.toolkit.util.InternalException;
import jdk.javadoc.internal.doclets.toolkit.util.ElementListWriter;
import jdk.javadoc.internal.doclets.toolkit.util.ResourceIOException;
import jdk.javadoc.internal.doclets.toolkit.util.SimpleDocletException;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static javax.tools.Diagnostic.Kind.*;

/**
 * An abstract implementation of a Doclet.
 */
public abstract class AbstractDoclet implements Doclet {

    /**
     * The global configuration information for this run.
     */
    private BaseConfiguration configuration;

    protected Messages messages;

    /*
     *  a handle to our utility methods
     */
    protected Utils utils;

    /**
     * The method that starts the execution of the doclet.
     *
     * @param docEnv   the {@link DocletEnvironment}.
     * @return true if the doclet executed without error.  False otherwise.
     */
    @Override
    public boolean run(DocletEnvironment docEnv) {
        configuration = getConfiguration();
        configuration.initConfiguration(docEnv, getResourceKeyMapper(docEnv));
        utils = configuration.utils;
        messages = configuration.getMessages();
        BaseOptions options = configuration.getOptions();

        try {
            try {
                generateFiles();
                return true;
            } catch (UncheckedDocletException e) {
                throw (DocletException) e.getCause();
            }

        } catch (DocFileIOException e) {
            switch (e.mode) {
                case READ:
                    messages.error("doclet.exception.read.file",
                            e.fileName.getPath(), e.getCause());
                    break;
                case WRITE:
                    messages.error("doclet.exception.write.file",
                            e.fileName.getPath(), e.getCause());
            }
            dumpStack(options.dumpOnError(), e);

        } catch (ResourceIOException e) {
            messages.error("doclet.exception.read.resource",
                    e.resource.getPath(), e.getCause());
            dumpStack(options.dumpOnError(), e);

        } catch (SimpleDocletException e) {
            configuration.reporter.print(ERROR, e.getMessage());
            dumpStack(options.dumpOnError(), e);

        } catch (InternalException e) {
            configuration.reporter.print(ERROR, e.getMessage());
            reportInternalError(e.getCause());

        } catch (DocletException | RuntimeException | Error e) {
            messages.error("doclet.internal.exception", e);
            reportInternalError(e);
        }

        return false;
    }

    protected Function<String, String> getResourceKeyMapper(DocletEnvironment docEnv) {
        return null;
    }

    private void reportInternalError(Throwable t) {
        if (getClass().getModule() == AbstractDoclet.class.getModule()) {
            System.err.println(configuration.getDocResources().getText("doclet.internal.report.bug"));
        }
        dumpStack(true, t);
    }

    private void dumpStack(boolean enabled, Throwable t) {
        if (enabled && t != null) {
            t.printStackTrace(System.err);
        }
    }

    /**
     * Returns the SourceVersion indicating the features supported by this doclet.
     *
     * @return SourceVersion
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_9;
    }

    /**
     * Create the configuration instance and returns it.
     *
     * @return the configuration of the doclet.
     */
    public abstract BaseConfiguration getConfiguration();

    /**
     * Start the generation of files. Call generate methods in the individual
     * writers, which will in turn generate the documentation files. Call the
     * TreeWriter generation first to ensure the Class Hierarchy is built
     * first and then can be used in the later generation.
     *
     * @throws DocletException if there is a problem while generating the documentation
     */
    protected void generateFiles() throws DocletException {

        // Modules with no documented classes may be specified on the
        // command line to specify a service provider, allow these.
        if (configuration.getSpecifiedModuleElements().isEmpty() &&
                configuration.getIncludedTypeElements().isEmpty()) {
            messages.error("doclet.No_Public_Classes_To_Document");
            return;
        }
        if (!configuration.setOptions()) {
            return;
        }
        messages.notice("doclet.build_version",
            configuration.getDocletVersion());
        ClassTree classTree = new ClassTree(configuration);

        generateClassFiles(classTree);

        ElementListWriter.generate(configuration);
        generatePackageFiles(classTree);
        generateModuleFiles();

        generateOtherFiles(classTree);
    }

    /**
     * Generate additional documentation that is added to the API documentation.
     *
     * @param classTree the data structure representing the class tree
     * @throws DocletException if there is a problem while generating the documentation
     */
    protected void generateOtherFiles(ClassTree classTree) throws DocletException { }

    /**
     * Generate the module documentation.
     *
     * @throws DocletException if there is a problem while generating the documentation
     *
     */
    protected abstract void generateModuleFiles() throws DocletException;

    /**
     * Generate the package documentation.
     *
     * @param classTree the data structure representing the class tree
     * @throws DocletException if there is a problem while generating the documentation
     */
    protected abstract void generatePackageFiles(ClassTree classTree) throws DocletException;

    /**
     * Generate the class documentation.
     *
     * @param arr the set of types to be documented
     * @param classTree the data structure representing the class tree
     * @throws DocletException if there is a problem while generating the documentation
     */
    protected abstract void generateClassFiles(SortedSet<TypeElement> arr, ClassTree classTree)
            throws DocletException;

    /**
     * Iterate through all classes and construct documentation for them.
     *
     * @param classTree the data structure representing the class tree
     * @throws DocletException if there is a problem while generating the documentation
     */
    protected void generateClassFiles(ClassTree classTree)
            throws DocletException {

        SortedSet<TypeElement> classes = new TreeSet<>(utils.comparators.generalPurposeComparator());

        // handle classes specified as files on the command line
        for (PackageElement pkg : configuration.typeElementCatalog.packages()) {
            classes.addAll(configuration.typeElementCatalog.allClasses(pkg));
        }

        // handle classes specified in modules and packages on the command line
        SortedSet<PackageElement> packages = new TreeSet<>(utils.comparators.packageComparator());
        packages.addAll(configuration.getSpecifiedPackageElements());
        configuration.modulePackages.values().stream().forEach(packages::addAll);
        for (PackageElement pkg : packages) {
            classes.addAll(utils.getAllClasses(pkg));
        }

        generateClassFiles(classes, classTree);
    }
}
