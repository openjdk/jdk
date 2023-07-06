/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.EnumSet;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.BaseTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.InheritDocTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.SummaryTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletManager;

import static com.sun.source.doctree.DocTree.Kind.AUTHOR;
import static com.sun.source.doctree.DocTree.Kind.EXCEPTION;
import static com.sun.source.doctree.DocTree.Kind.HIDDEN;
import static com.sun.source.doctree.DocTree.Kind.PROVIDES;
import static com.sun.source.doctree.DocTree.Kind.SERIAL;
import static com.sun.source.doctree.DocTree.Kind.SERIAL_DATA;
import static com.sun.source.doctree.DocTree.Kind.SERIAL_FIELD;
import static com.sun.source.doctree.DocTree.Kind.SINCE;
import static com.sun.source.doctree.DocTree.Kind.USES;
import static com.sun.source.doctree.DocTree.Kind.VERSION;

public class HtmlTagletManager extends TagletManager {
    private final HtmlConfiguration config;
    private final Resources resources;

    public HtmlTagletManager(HtmlConfiguration config) {
        super(config);
        this.config = config;
        resources = config.docResources;
        initStandardTaglets();
    }

    /**
     * Initialize standard Javadoc tags for ordering purposes.
     */
    private void initStandardTaglets() {
        if (javafx) {
            initJavaFXTaglets();
        }

        addStandardTaglet(new HtmlParamTaglet(config));
        addStandardTaglet(new HtmlReturnTaglet(config));
        addStandardTaglet(new HtmlThrowsTaglet(config), EXCEPTION);
        addStandardTaglet(
                new HtmlSimpleTaglet(config, SINCE, resources.getText("doclet.Since"),
                        EnumSet.allOf(Taglet.Location.class), !nosince));
        addStandardTaglet(
                new HtmlSimpleTaglet(config, VERSION, resources.getText("doclet.Version"),
                        EnumSet.of(Taglet.Location.OVERVIEW, Taglet.Location.MODULE, Taglet.Location.PACKAGE, Taglet.Location.TYPE), showversion));
        addStandardTaglet(
                new HtmlSimpleTaglet(config, AUTHOR, resources.getText("doclet.Author"),
                        EnumSet.of(Taglet.Location.OVERVIEW, Taglet.Location.MODULE, Taglet.Location.PACKAGE, Taglet.Location.TYPE), showauthor));
        addStandardTaglet(
                new HtmlSimpleTaglet(config, SERIAL_DATA, resources.getText("doclet.SerialData"),
                        EnumSet.noneOf(Taglet.Location.class)));
        addStandardTaglet(
                new HtmlSimpleTaglet(config, HIDDEN, null,
                        EnumSet.of(Taglet.Location.TYPE, Taglet.Location.METHOD, Taglet.Location.FIELD)));

        // This appears to be a default custom (non-standard) taglet
        jdk.javadoc.internal.doclets.toolkit.taglets.Taglet factoryTaglet =
                new HtmlSimpleTaglet(config, "factory", resources.getText("doclet.Factory"),
                        EnumSet.of(Taglet.Location.METHOD));
        allTaglets.put(factoryTaglet.getName(), factoryTaglet);

        addStandardTaglet(new HtmlSeeTaglet(config));
        addStandardTaglet(new HtmlSpecTaglet(config));

        // Standard inline tags
        addStandardTaglet(new HtmlDocRootTaglet(config));
        addStandardTaglet(new InheritDocTaglet(config));
        addStandardTaglet(new HtmlValueTaglet(config));
        addStandardTaglet(new HtmlLinkTaglet(config, DocTree.Kind.LINK));
        addStandardTaglet(new HtmlLinkTaglet(config, DocTree.Kind.LINK_PLAIN));
        addStandardTaglet(new HtmlLiteralTaglet(config));
        addStandardTaglet(new HtmlCodeTaglet(config));
        addStandardTaglet(new HtmlSnippetTaglet(config));
        addStandardTaglet(new HtmlIndexTaglet(config));
        addStandardTaglet(new SummaryTaglet(config));
        addStandardTaglet(new HtmlSystemPropertyTaglet(config));

        // Keep track of the names of standard tags for error checking purposes.
        // The following are not handled above.
        addStandardTaglet(new HtmlDeprecatedTaglet(config));
        addStandardTaglet(new BaseTaglet(config, USES, false, EnumSet.of(Taglet.Location.MODULE)));
        addStandardTaglet(new BaseTaglet(config, PROVIDES, false, EnumSet.of(Taglet.Location.MODULE)));
        addStandardTaglet(
                new HtmlSimpleTaglet(config, SERIAL, null,
                        EnumSet.of(Taglet.Location.PACKAGE, Taglet.Location.TYPE, Taglet.Location.FIELD)));
        addStandardTaglet(
                new HtmlSimpleTaglet(config, SERIAL_FIELD, null, EnumSet.of(Taglet.Location.FIELD)));
    }

    /**
     * Initialize JavaFX-related tags.
     */
    private void initJavaFXTaglets() {
        addStandardTaglet(new HtmlSimpleTaglet(config, "propertyDescription",
                resources.getText("doclet.PropertyDescription"),
                EnumSet.of(Taglet.Location.METHOD, Taglet.Location.FIELD)));
        addStandardTaglet(new HtmlSimpleTaglet(config, "defaultValue", resources.getText("doclet.DefaultValue"),
                EnumSet.of(Taglet.Location.METHOD, Taglet.Location.FIELD)));
        addStandardTaglet(new HtmlSimpleTaglet(config, "treatAsPrivate", null,
                EnumSet.of(Taglet.Location.TYPE, Taglet.Location.METHOD, Taglet.Location.FIELD)));
    }

    @Override
    protected jdk.javadoc.internal.doclets.toolkit.taglets.Taglet wrapTaglet(Taglet instance) {
        return new UserTaglet(instance);
    }

    protected jdk.javadoc.internal.doclets.toolkit.taglets.Taglet newSimpleTaglet(String tagName, String header, String locations) {
        return new HtmlSimpleTaglet(config, tagName, header, locations);
    }
}
