/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.util.Objects;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor14;

import com.sun.source.doctree.DocTree;

/**
 * An item to be included in the index pages and in interactive search.
 *
 * <p>
 * Items are primarily defined by their position in the documentation,
 * which is one of:
 *
 * <ul>
 * <li>An element (module, package, type or member)
 * <li>One of a small set of tags in the doc comment for an element:
 *     {@code {@index ...}}, {@code {@systemProperty ...}}, etc
 * <li>One of a small set of outliers, corresponding to summary pages:
 *     "All Classes", "All Packages", etc
 * </ul>
 *
 * <p>
 * All items have a "label", which is the presentation string used
 * to display the item in the list of matching choices. The
 * label is specified when the item is created.  Items also
 * have a "url" and a "description", which are provided by
 * the specific doclet.
 *
 * <p>
 * Each item provides details to be included in the search index files
 * read and processed by JavaScript.
 * Items have a "category", which is normally derived from the element
 * kind or doc tree kind; it corresponds to the JavaScript file
 * in which this item will be written.
 *
 * <p>
 * Items for an element may have one or more of the following:
 * "containing module", "containing package", "containing type".
 *
 * <p>
 * Items for a node in a doc tree have a "holder", which is a
 * text form of the enclosing element or page.
 * They will typically also have a "description" derived from
 * content in the doc tree node.
 *
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class IndexItem {

    /**
     * The "category" used to group items for the interactive search index.
     * Categories correspond directly to the JavaScript files that will be generated.
     */
    public enum Category {
        MODULES,
        PACKAGES,
        TYPES,
        MEMBERS,
        TAGS
    }

    private final String label;
    private final Element element;

    private String url = "";
    private String description = "";
    private String containingModule = "";
    private String containingPackage = "";
    private String containingClass = "";
    private String holder = "";

    public static IndexItem of(ModuleElement moduleElement, Utils utils) {
        return new IndexItem(moduleElement, utils.getFullyQualifiedName(moduleElement));
    }

    public static IndexItem of(PackageElement packageElement, Utils utils) {
        return new IndexItem(packageElement, utils.getPackageName(packageElement));
    }

    public static IndexItem of(TypeElement typeElement, Utils utils) {
        return new IndexItem(typeElement, utils.getSimpleName(typeElement));
    }

    public static IndexItem of(TypeElement typeElement, Element member, Utils utils) {
        String name = utils.getSimpleName(member);
        if (utils.isExecutableElement(member)) {
            ExecutableElement ee = (ExecutableElement)member;
            name += utils.flatSignature(ee, typeElement);
        }
        return new IndexItem(member, name) {
            @Override
            public TypeElement getTypeElement() {
                return typeElement;
            }
        };
    }

    /**
     * Creates an index item for a node in the doc comment for an element.
     *
     * @param element the element
     * @param docTree the node in the doc comment
     * @param label   the label
     *
     * @return the item
     */
    public static IndexItem of(Element element, DocTree docTree, String label) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(docTree);
        return new IndexItem(element, label) {
            @Override
            public DocTree getDocTree() {
                return docTree;
            }
            @Override
            public Category getCategory() {
                return getCategory(docTree);
            }
        };
    }

    /**
     * Creates an index item that is not associated with any element or
     * node in a doc comment.
     *
     * @param category the category for the item
     *
     * @return the item
     */
    public static IndexItem of(Category category, String label) {
        Objects.requireNonNull(category);
        return new IndexItem(null, label) {
            @Override
            public DocTree getDocTree() {
                return null;
            }
            @Override
            public Category getCategory() {
                return category;
            }
        };
    }

    private IndexItem(Element element, String label) {
        this.element = element;
        this.label = Objects.requireNonNull(label);
    }

    public String getLabel() {
        return label;
    }

    /**
     * Get the part of the label after the last dot, or whole label if no dots.
     *
     * @return the simple name
     */
    public String getSimpleName() {
        return label.substring(label.lastIndexOf('.') + 1);
    }

    public String getFullyQualifiedLabel(Utils utils) {
        TypeElement typeElement = getTypeElement();
        if (typeElement != null) {
            return utils.getFullyQualifiedName(typeElement) + "." + label;
        } else if (isElementItem()) {
            return utils.getFullyQualifiedName(element);
        } else {
            return label;
        }
    }

    public Element getElement() {
        return element;
    }

    public Category getCategory() {
        return getCategory(element);
    }

    protected Category getCategory(DocTree docTree) {
        return switch (docTree.getKind()) {
            case INDEX, SYSTEM_PROPERTY -> Category.TAGS;
            default -> throw new IllegalArgumentException(docTree.getKind().toString());
        };
    }

    @SuppressWarnings("preview")
    protected Category getCategory(Element element) {
        return new SimpleElementVisitor14<Category, Void>() {
            @Override
            public Category visitModule(ModuleElement t, Void v) {
                return Category.MODULES;
            }

            @Override
            public Category visitPackage(PackageElement e, Void v) {
                return Category.PACKAGES;
            }

            @Override
            public Category visitType(TypeElement e, Void v) {
                return Category.TYPES;
            }

            @Override
            public Category visitVariable(VariableElement e, Void v) {
                return Category.MEMBERS;
            }

            @Override
            public Category visitExecutable(ExecutableElement e, Void v) {
                return Category.MEMBERS;
            }

            @Override
            public Category defaultAction(Element e, Void v) {
                throw new IllegalArgumentException(e.toString());
            }
        }.visit(element);
    }

    public TypeElement getTypeElement() {
        return null;
    }

    public DocTree getDocTree() {
        return null;
    }

    public boolean isElementItem() {
        return element != null && getDocTree() == null;
    }

    public boolean isTagItem() {
        return getDocTree() != null;
    }

    public IndexItem setUrl(String u) {
        url = u;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public IndexItem setContainingModule(String m) {
        containingModule = m;
        return this;
    }

    public IndexItem setContainingPackage(String p) {
        containingPackage = p;
        return this;
    }

    public IndexItem setContainingClass(String c) {
        containingClass = c;
        return this;
    }

    public IndexItem setHolder(String h) {
        holder = h;
        return this;
    }

    public String getHolder() {
        return holder;
    }

    public IndexItem setDescription(String d) {
        description = d;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public String toJavaScript() {
        // TODO: Additional processing is required, see JDK-8238495
        StringBuilder item = new StringBuilder();
        Category category = getCategory();
        switch (category) {
            case MODULES:
                item.append("{")
                        .append("\"l\":\"").append(label).append("\"")
                        .append("}");
                break;

            case PACKAGES:
                item.append("{");
                if (!containingModule.isEmpty()) {
                    item.append("\"m\":\"").append(containingModule).append("\",");
                }
                item.append("\"l\":\"").append(label).append("\"");
                if (!url.isEmpty()) {
                    item.append(",\"u\":\"").append(url).append("\"");
                }
                item.append("}");
                break;

            case TYPES:
                item.append("{");
                if (!containingPackage.isEmpty()) {
                    item.append("\"p\":\"").append(containingPackage).append("\",");
                }
                if (!containingModule.isEmpty()) {
                    item.append("\"m\":\"").append(containingModule).append("\",");
                }
                item.append("\"l\":\"").append(label).append("\"");
                if (!url.isEmpty()) {
                    item.append(",\"u\":\"").append(url).append("\"");
                }
                item.append("}");
                break;

            case MEMBERS:
                item.append("{");
                if (!containingModule.isEmpty()) {
                    item.append("\"m\":\"").append(containingModule).append("\",");
                }
                item.append("\"p\":\"").append(containingPackage).append("\",")
                        .append("\"c\":\"").append(containingClass).append("\",")
                        .append("\"l\":\"").append(label).append("\"");
                if (!url.isEmpty()) {
                    item.append(",\"u\":\"").append(url).append("\"");
                }
                item.append("}");
                break;

            case TAGS:
                item.append("{")
                        .append("\"l\":\"").append(label).append("\",")
                        .append("\"h\":\"").append(holder).append("\",");
                if (!description.isEmpty()) {
                    item.append("\"d\":\"").append(description).append("\",");
                }
                item.append("\"u\":\"").append(url).append("\"")
                        .append("}");
                break;

            default:
                throw new AssertionError("Unexpected category: " + category);
        }
        return item.toString();
    }
}
