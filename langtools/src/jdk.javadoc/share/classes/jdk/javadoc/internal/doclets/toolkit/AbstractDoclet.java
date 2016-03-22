/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.SourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.toolkit.builders.AbstractBuilder;
import jdk.javadoc.internal.doclets.toolkit.builders.BuilderFactory;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;
import jdk.javadoc.internal.doclets.toolkit.util.PackageListWriter;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static javax.tools.Diagnostic.Kind.*;

/**
 * An abstract implementation of a Doclet.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public abstract class AbstractDoclet {

    /**
     * The global configuration information for this run.
     */
    public Configuration configuration;
    /*
     *  a handle to our utility methods
     */
    protected Utils utils;

    /**
     * The only doclet that may use this toolkit is {@value}
     */
    private static final String TOOLKIT_DOCLET_NAME =
        jdk.javadoc.internal.doclets.formats.html.HtmlDoclet.class.getName();

    /**
     * Verify that the only doclet that is using this toolkit is
     * {@value #TOOLKIT_DOCLET_NAME}.
     */
    private boolean isValidDoclet() {
        if (!getClass().getName().equals(TOOLKIT_DOCLET_NAME)) {
            configuration.message.error("doclet.Toolkit_Usage_Violation",
                TOOLKIT_DOCLET_NAME);
            return false;
        }
        return true;
    }

    /**
     * The method that starts the execution of the doclet.
     *
     * @param root   the {@link DocletEnvironment} that points to the source to document.
     * @return true if the doclet executed without error.  False otherwise.
     */
    public boolean startDoclet(DocletEnvironment root) {
        configuration = configuration();
        configuration.root = root;
        configuration.cmtUtils = new CommentUtils(configuration);
        configuration.utils = new Utils(configuration);
        utils = configuration.utils;
        configuration.workArounds = new WorkArounds(configuration);
        if (!isValidDoclet()) {
            return false;
        }

        try {
            startGeneration(root);
        } catch (Configuration.Fault f) {
            configuration.reporter.print(ERROR, f.getMessage());
            return false;
        } catch (DocletAbortException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause.getLocalizedMessage() != null) {
                    configuration.reporter.print(ERROR, cause.getLocalizedMessage());
                } else {
                    configuration.reporter.print(ERROR, cause.toString());
                }
            }
            return false;
        } catch (Exception exc) {
            return false;
        }
        return true;
    }

    /**
     * Returns the SourceVersion indicating the features supported by this doclet.
     * @return SourceVersion
     */
    public SourceVersion sourceVersion() {
        return SourceVersion.RELEASE_8;
    }


    /**
     * Create the configuration instance and returns it.
     * @return the configuration of the doclet.
     */
    public abstract Configuration configuration();

    /**
     * Start the generation of files. Call generate methods in the individual
     * writers, which will in turn generate the documentation files. Call the
     * TreeWriter generation first to ensure the Class Hierarchy is built
     * first and then can be used in the later generation.
     *
     * @see jdk.doclet.DocletEnvironment
     */
    private void startGeneration(DocletEnvironment root) throws Configuration.Fault, Exception {
        if (root.getIncludedClasses().isEmpty()) {
            configuration.message.
                error("doclet.No_Public_Classes_To_Document");
            return;
        }
        if (!configuration.setOptions()) {
            return;
        }
        configuration.getDocletSpecificMsg().notice("doclet.build_version",
            configuration.getDocletSpecificBuildDate());
        ClassTree classtree = new ClassTree(configuration, configuration.nodeprecated);

        generateClassFiles(root, classtree);
        configuration.utils.copyDocFiles(DocPaths.DOC_FILES);

        PackageListWriter.generate(configuration);
        generatePackageFiles(classtree);
        generateModuleFiles();

        generateOtherFiles(root, classtree);
        configuration.tagletManager.printReport();
    }

    /**
     * Generate additional documentation that is added to the API documentation.
     *
     * @param root     the DocletEnvironment of source to document.
     * @param classtree the data structure representing the class tree.
     */
    protected void generateOtherFiles(DocletEnvironment root, ClassTree classtree) throws Exception {
        BuilderFactory builderFactory = configuration.getBuilderFactory();
        AbstractBuilder constantsSummaryBuilder = builderFactory.getConstantsSummaryBuilder();
        constantsSummaryBuilder.build();
        AbstractBuilder serializedFormBuilder = builderFactory.getSerializedFormBuilder();
        serializedFormBuilder.build();
    }

    /**
     * Generate the module documentation.
     *
     */
    protected abstract void generateModuleFiles() throws Exception;

    /**
     * Generate the package documentation.
     *
     * @param classtree the data structure representing the class tree.
     */
    protected abstract void generatePackageFiles(ClassTree classtree) throws Exception;

    /**
     * Generate the class documentation.
     *
     * @param classtree the data structure representing the class tree.
     */
    protected abstract void generateClassFiles(SortedSet<TypeElement> arr, ClassTree classtree);

    /**
     * Iterate through all classes and construct documentation for them.
     *
     * @param root      the DocletEnvironment of source to document.
     * @param classtree the data structure representing the class tree.
     */
    protected void generateClassFiles(DocletEnvironment root, ClassTree classtree) {
        generateClassFiles(classtree);
        SortedSet<PackageElement> packages = new TreeSet<>(utils.makePackageComparator());
        packages.addAll(utils.getSpecifiedPackages());
        packages.stream().forEach((pkg) -> {
            generateClassFiles(utils.getAllClasses(pkg), classtree);
        });
    }

    /**
     * Generate the class files for single classes specified on the command line.
     *
     * @param classtree the data structure representing the class tree.
     */
    private void generateClassFiles(ClassTree classtree) {
        SortedSet<PackageElement> packages = configuration.typeElementCatalog.packages();
        packages.stream().forEach((pkg) -> {
            generateClassFiles(configuration.typeElementCatalog.allClasses(pkg), classtree);
        });
    }
}
