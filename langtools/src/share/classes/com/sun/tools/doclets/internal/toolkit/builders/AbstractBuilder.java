/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.builders;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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
 * @since 1.5
 */

public abstract class AbstractBuilder {
    public static class Context {
        /**
         * The configuration used in this run of the doclet.
         */
        final Configuration configuration;

        /**
         * Keep track of which packages we have seen for
         * efficiency purposes.  We don't want to copy the
         * doc files multiple times for a single package.
         */
        final Set<String> containingPackagesSeen;

        /**
         * Shared parser for the builder XML file
         */
        final LayoutParser layoutParser;

        Context(Configuration configuration,
                Set<String> containingPackagesSeen,
                LayoutParser layoutParser) {
            this.configuration = configuration;
            this.containingPackagesSeen = containingPackagesSeen;
            this.layoutParser = layoutParser;
        }
    }

    /**
     * The configuration used in this run of the doclet.
     */
    protected final Configuration configuration;

    /**
     * Keep track of which packages we have seen for
     * efficiency purposes.  We don't want to copy the
     * doc files multiple times for a single package.
     */
    protected final Set<String> containingPackagesSeen;

    protected final LayoutParser layoutParser;

    /**
     * True if we want to print debug output.
     */
    protected static final boolean DEBUG = false;

    /**
     * Construct a Builder.
     * @param configuration the configuration used in this run
     *        of the doclet.
     */
    public AbstractBuilder(Context c) {
        this.configuration = c.configuration;
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
     * @throws IOException there was a problem writing the output.
     */
    public abstract void build() throws IOException;

    /**
     * Build the documentation, as specified by the given XML element.
     *
     * @param node the XML element that specifies which component to document.
     * @param contentTree content tree to which the documentation will be added
     */
    protected void build(XMLNode node, Content contentTree) {
        String component = node.name;
        try {
            invokeMethod("build" + component,
                    new Class<?>[]{XMLNode.class, Content.class},
                    new Object[]{node, contentTree});
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            configuration.root.printError("Unknown element: " + component);
            throw new DocletAbortException(e);
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            configuration.root.printError("Exception " +
                    e.getClass().getName() +
                    " thrown while processing element: " + component);
            throw new DocletAbortException(e);
        }
    }

    /**
     * Build the documentation, as specified by the children of the given XML element.
     *
     * @param node the XML element that specifies which components to document.
     * @param contentTree content tree to which the documentation will be added
     */
    protected void buildChildren(XMLNode node, Content contentTree) {
        for (XMLNode child : node.children)
            build(child, contentTree);
    }

    /**
     * Given the name and parameters, invoke the method in the builder.  This
     * method is required to invoke the appropriate build method as instructed
     * by the builder XML file.
     *
     * @param methodName   the name of the method that we would like to invoke.
     * @param paramClasses the types for each parameter.
     * @param params       the parameters of the method.
     */
    protected void invokeMethod(String methodName, Class<?>[] paramClasses,
            Object[] params)
    throws Exception {
        if (DEBUG) {
            configuration.root.printError("DEBUG: " + this.getClass().getName() + "." + methodName);
        }
        Method method = this.getClass().getMethod(methodName, paramClasses);
        method.invoke(this, params);
    }
}
