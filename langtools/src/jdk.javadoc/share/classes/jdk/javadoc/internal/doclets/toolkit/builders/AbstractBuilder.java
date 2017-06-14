/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.lang.reflect.*;
import java.util.*;

import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.UncheckedDocletException;
import jdk.javadoc.internal.doclets.toolkit.util.InternalException;
import jdk.javadoc.internal.doclets.toolkit.util.SimpleDocletException;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static javax.tools.Diagnostic.Kind.*;

/**
 * The superclass for all builders.  A builder is a class that provides
 * the structure and content of API documentation.  A builder is completely
 * doclet independent which means that any doclet can use builders to
 * construct documentation, as long as it impelements the appropriate
 * writer interfaces.  For example, if a doclet wanted to use
 * {@link ConstantsSummaryBuilder} to build a constant summary, all it has to
 * do is implement the ConstantsSummaryWriter interface and pass it to the
 * builder using a WriterFactory.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */

public abstract class AbstractBuilder {
    public static class Context {
        /**
         * The configuration used in this run of the doclet.
         */
        final BaseConfiguration configuration;

        /**
         * Keep track of which packages we have seen for
         * efficiency purposes.  We don't want to copy the
         * doc files multiple times for a single package.
         */
        final Set<PackageElement> containingPackagesSeen;

        /**
         * Shared parser for the builder XML file
         */
        final LayoutParser layoutParser;

        Context(BaseConfiguration configuration,
                Set<PackageElement> containingPackagesSeen,
                LayoutParser layoutParser) {
            this.configuration = configuration;
            this.containingPackagesSeen = containingPackagesSeen;
            this.layoutParser = layoutParser;
        }
    }

    /**
     * The configuration used in this run of the doclet.
     */
    protected final BaseConfiguration configuration;

    protected final Messages messages;
    protected final Resources resources;
    protected final Utils utils;

    /**
     * Keep track of which packages we have seen for
     * efficiency purposes.  We don't want to copy the
     * doc files multiple times for a single package.
     */
    protected final Set<PackageElement> containingPackagesSeen;

    protected final LayoutParser layoutParser;

    /**
     * True if we want to print debug output.
     */
    protected static final boolean DEBUG = false;

    /**
     * Construct a Builder.
     * @param c a context providing information used in this run of the doclet
     */
    public AbstractBuilder(Context c) {
        this.configuration = c.configuration;
        this.messages = configuration.getMessages();
        this.resources = configuration.getResources();
        this.utils = configuration.utils;
        this.containingPackagesSeen = c.containingPackagesSeen;
        this.layoutParser = c.layoutParser;
    }

    /**
     * Return the name of this builder.
     *
     * @return the name of the builder.
     */
    public abstract String getName();

    /**
     * Build the documentation.
     *
     * @throws DocletException if there is a problem building the documentation
     */
    public abstract void build() throws DocletException;

    /**
     * Build the documentation, as specified by the given XML element.
     *
     * @param node the XML element that specifies which component to document.
     * @param contentTree content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    protected void build(XMLNode node, Content contentTree) throws DocletException {
        String component = node.name;
        try {
            String methodName = "build" + component;
            if (DEBUG) {
                configuration.reporter.print(ERROR,
                        "DEBUG: " + getClass().getName() + "." + methodName);
            }
            Method method = getClass().getMethod(methodName, XMLNode.class, Content.class);
            method.invoke(this, node, contentTree);

        } catch (NoSuchMethodException e) {
            // Use SimpleDocletException instead of InternalException because there is nothing
            // informative about about the place the exception occurred, here in this method.
            // The problem is either a misconfigured doclet.xml file or a missing method in the
            // user-supplied(?) doclet
            String message = resources.getText("doclet.builder.unknown.component", component);
            throw new SimpleDocletException(message, e);

        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DocletException) {
                throw (DocletException) cause;
            } else if (cause instanceof UncheckedDocletException) {
                throw (DocletException) cause.getCause();
            } else {
                // use InternalException, so that a stacktrace showing the position of
                // the internal exception is generated
                String message = resources.getText("doclet.builder.exception.in.component", component,
                        e.getCause());
                throw new InternalException(message, e.getCause());
            }

        } catch (ReflectiveOperationException e) {
            // Use SimpleDocletException instead of InternalException because there is nothing
            // informative about about the place the exception occurred, here in this method.
            // The problem is specific to the method being invoked, such as illegal access
            // or illegal argument.
            String message = resources.getText("doclet.builder.exception.in.component", component, e);
            throw new SimpleDocletException(message, e.getCause());
        }
    }

    /**
     * Build the documentation, as specified by the children of the given XML element.
     *
     * @param node the XML element that specifies which components to document.
     * @param contentTree content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the children
     */
    protected void buildChildren(XMLNode node, Content contentTree) throws DocletException {
        for (XMLNode child : node.children)
            build(child, contentTree);
    }
}
