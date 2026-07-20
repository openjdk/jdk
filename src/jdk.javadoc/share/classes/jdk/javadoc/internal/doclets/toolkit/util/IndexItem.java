/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

    /**
     * The kind of index item. Index items can represent program elements,
     * various doc comment tags, or other resources such as summary pages.
     *
     * Values in this enum must match elements in array itemDesc in
     * JavaScript file search.js.template!
     */
    public enum Kind {
        // Members
        ENUM_CONSTANT,
        FIELD,
        STATIC_FIELD,
        CONSTRUCTOR,
        ANNOTATION_ELEMENT,
        METHOD,
        STATIC_METHOD,
        RECORD_COMPONENT,
        // Types
        ANNOTATION_TYPE,
        ENUM,
        INTERFACE,
        RECORD,
        CLASS,
        EXCEPTION_CLASS,
        // Tags
        SEARCH_ITEM,
        SYSTEM_PROPERTY,
        SECTION,
        EXTERNAL_SPEC,
        // Other
        SUMMARY_PAGE;

        /**
         * {@return the kind of index item for a program element}
         */
        public static Kind ofElement(Element elem, Utils utils) {
            return switch (elem.getKind()) {
                case ENUM_CONSTANT -> ENUM_CONSTANT;
                case FIELD -> utils.isStatic(elem) ? STATIC_FIELD : FIELD;
                case CONSTRUCTOR -> CONSTRUCTOR;
                case METHOD -> utils.isAnnotationInterface(elem.getEnclosingElement())
                        ? ANNOTATION_ELEMENT
                        : utils.isStatic(elem) ? STATIC_METHOD : METHOD;
                case RECORD_COMPONENT -> RECORD_COMPONENT;
                case ANNOTATION_TYPE -> ANNOTATION_TYPE;
                case ENUM -> ENUM;
                case CLASS -> utils.isThrowable((TypeElement) elem) ? EXCEPTION_CLASS : CLASS;
                case INTERFACE -> INTERFACE;
                case RECORD -> RECORD;
                default -> throw new IllegalArgumentException(elem.toString());
            };
        }

        /**
         * {@return the kind of index item for a doc comment tag}
         */
        public static Kind ofDocTree(DocTree tree) {
            return switch (tree.getKind()) {
                case DocTree.Kind.INDEX -> SEARCH_ITEM;
                case DocTree.Kind.SYSTEM_PROPERTY -> SYSTEM_PROPERTY;
                case DocTree.Kind.SPEC -> EXTERNAL_SPEC;
                case DocTree.Kind.START_ELEMENT -> SECTION;
                default -> throw new IllegalArgumentException(tree.getKind().toString());
            };
        }
    }

    /**
     * The presentation string for the item. It must be non-empty.
     */
    private final String label;

    /**
     * The element for the item. It is only null for items for summary pages that are not
     * associated with any specific element.
     *
     */
    private final Element element;

    /**
     * The URL pointing to the element, doc tree or page being indexed.
     * It may be empty if the information can be determined from other fields.
     */
    private String url = "";

    /**
     * The containing module, if any, for the item.
     * It will be empty if the element is not in a package, and may be omitted if the
     * name of the package is unique.
     */
    private String containingModule = "";

    /**
     * The containing package, if any, for the item.
     */
    private String containingPackage = "";

    /**
     * The containing class, if any, for the item.
     */
    private String containingClass = "";

    /**
     * The kind of the item.
     */
    private final Kind kind;

    /**
     * Creates an index item for a module element.
     *
     * @param moduleElement the element
     * @param utils         the common utilities class
     *
     * @return the item
     */
    public static IndexItem of(ModuleElement moduleElement, Utils utils) {
        return new IndexItem(moduleElement, utils.getFullyQualifiedName(moduleElement), null);
    }

    /**
     * Creates an index item for a package element.
     *
     * @param packageElement the element
     * @param utils          the common utilities class
     *
     * @return the item
     */
    public static IndexItem of(PackageElement packageElement, Utils utils) {
        return new IndexItem(packageElement, utils.getPackageName(packageElement), null);
    }

    /**
     * Creates an index item for a type element.
     * Note: use {@code getElement()} to access this value, not {@code getTypeElement}.
     *
     * @param typeElement the element
     * @param utils       the common utilities class
     *
     * @return the item
     */
    public static IndexItem of(TypeElement typeElement, Utils utils) {
        return new IndexItem(typeElement, utils.getSimpleName(typeElement), Kind.ofElement(typeElement, utils));
    }

    /**
     * Creates an index item for a member element.
     * Note: the given type element may not be the same as the enclosing element of the member
     *       in cases where the enclosing element is not visible in the documentation.
     *
     * @param typeElement the element that contains the member
     * @param member      the member
     * @param utils       the common utilities class
     *
     * @return the item
     *
     * @see #getContainingTypeElement()
     */
    public static IndexItem of(TypeElement typeElement, Element member, Utils utils) {
        String name = utils.getSimpleName(member);
        if (utils.isExecutableElement(member)) {
            ExecutableElement ee = (ExecutableElement)member;
            name += utils.makeSignature(ee, typeElement, false, true);
        }
        return new IndexItem(member, name, Kind.ofElement(member, utils)) {
            @Override
            public TypeElement getContainingTypeElement() {
                return typeElement;
            }
        };
    }

    /**
     * Creates an index item for a node in the doc comment for an element.
     * The node should only be one that gives rise to an entry in the index.
     *
     * @param element     the element
     * @param docTree     the node in the doc comment
     * @param label       the label
     * @param holder      the holder for the comment
     * @param description the description of the item
     * @param link        the root-relative link to the item in the generated docs
     *
     * @return the item
     */
    public static IndexItem of(Element element, DocTree docTree, String label,
                               String holder, String description, DocLink link) {
        Objects.requireNonNull(element);
        Objects.requireNonNull(holder);
        Objects.requireNonNull(link);

        switch (docTree.getKind()) {
            case INDEX, SPEC, SYSTEM_PROPERTY, START_ELEMENT -> { }
            default -> throw new IllegalArgumentException(docTree.getKind().toString());
        }

        return new IndexItem(element, label, Kind.ofDocTree(docTree), link.toString()) {
            @Override
            public DocTree getDocTree() {
                return docTree;
            }
            @Override
            public Category getCategory() {
                return getCategory(docTree);
            }
            @Override
            public String getHolder() {
                return holder;
            }
            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    /**
     * Creates an index item for a summary page, that is not associated with any element or
     * node in a doc comment.
     *
     * @param category the category for the item
     * @param label the label for the item
     * @param path the path for the page
     *
     * @return the item
     */
    public static IndexItem of(Category category, String label, DocPath path) {
        Objects.requireNonNull(category);
        return new IndexItem(null, label, Kind.SUMMARY_PAGE, path.getPath()) {
            @Override
            public Category getCategory() {
                return category;
            }
            @Override
            public String getHolder() {
                return "";
            }
        };
    }

    private IndexItem(Element element, String label, Kind kind) {
        if (label.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (label.contains("\n") || label.contains("\r")) {
            throw new IllegalArgumentException();
        }

        this.element = element;
        this.label = label;
        this.kind = kind;
    }

    private IndexItem(Element element, String label, Kind kind, String url) {
        this(element, label, kind);
        setUrl(url);
    }

    /**
     * Returns the label of the item.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the part of the label after the last dot, or the whole label if there are no dots.
     *
     * @return the simple name
     */
    public String getSimpleName() {
        return label.substring(label.lastIndexOf('.') + 1);
    }

    /**
     * Returns the label with a fully-qualified type name.
     * (Used to determine if labels are unique or need to be qualified.)
     *
     * @param utils the common utilities class
     *
     * @return the fully qualified name
     */
    public String getFullyQualifiedLabel(Utils utils) {
        TypeElement typeElement = getContainingTypeElement();
        if (typeElement != null) {
            return utils.getFullyQualifiedName(typeElement) + "." + label;
        } else if (isElementItem()) {
            return utils.getFullyQualifiedName(element);
        } else {
            return label;
        }
    }

    /**
     * Returns the element associate with this item, or {@code null}.
     *
     * @return the element
     */
    public Element getElement() {
        return element;
    }

    /**
     * Returns the category for this item, that indicates the JavaScript file
     * in which this item should be written.
     *
     * @return the category
     */
    public Category getCategory() {
        return getCategory(element);
    }

    protected Category getCategory(DocTree docTree) {
        return switch (docTree.getKind()) {
            case INDEX, SPEC, SYSTEM_PROPERTY, START_ELEMENT -> Category.TAGS;
            default -> throw new IllegalArgumentException(docTree.getKind().toString());
        };
    }

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

    /**
     * Returns the type element that is documented as containing a member element,
     * or {@code null} if this item does not represent a member element.
     *
     * @return the type element
     */
    public TypeElement getContainingTypeElement() {
        return null;
    }

    /**
     * Returns the documentation tree node for this item, of {@code null} if this item
     * does not represent a documentation tree node.
     *
     * @return the documentation tree node
     */
    public DocTree getDocTree() {
        return null;
    }

    /**
     * Returns {@code true} if this index is for an element.
     *
     * @return {@code true} if this index is for an element
     */
    public boolean isElementItem() {
        return element != null && getDocTree() == null;
    }

    /**
     * Returns {@code true} if this index is for a tag in a doc comment.
     *
     * @return {@code true} if this index is for a tag in a doc comment
     */
    public boolean isTagItem() {
        return getDocTree() != null;
    }

    /**
     * Returns {@code true} if this index is for a specific kind of tag in a doc comment.
     *
     * @return {@code true} if this index is for a specific kind of tag in a doc comment
     */
    public boolean isKind(DocTree.Kind kind) {
        DocTree dt = getDocTree();
        return dt != null && dt.getKind() == kind;
    }

    /**
     * Sets the URL for the item, when it cannot otherwise be inferred from other fields.
     *
     * @param u the url
     *
     * @return this item
     */
    public IndexItem setUrl(String u) {
        url = Objects.requireNonNull(u);
        return this;
    }

    /**
     * Returns the URL for this item, or an empty string if no value has been set.
     *
     * @return the URL for this item, or an empty string if no value has been set
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the name of the containing module for this item.
     *
     * @param m the module
     *
     * @return this item
     */
    public IndexItem setContainingModule(String m) {
        containingModule = Objects.requireNonNull(m);
        return this;
    }

    /**
     * Sets the name of the containing package for this item.
     *
     * @param p the package
     *
     * @return this item
     */
    public IndexItem setContainingPackage(String p) {
        containingPackage = Objects.requireNonNull(p);
        return this;
    }

    /**
     * Sets the name of the containing class for this item.
     *
     * @param c the class
     *
     * @return this item
     */
    public IndexItem setContainingClass(String c) {
        containingClass = Objects.requireNonNull(c);
        return this;
    }

    /**
     * Returns a description of the element owning the documentation comment for this item,
     * or {@code null} if this is not a item for a tag for an item in a documentation tag.
     *
     * @return the description of the element that owns this item
     */
    public String getHolder() {
        return null;
    }

    /**
     * Returns the kind of this item.
     *
     * @return the item kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Returns a description of the tag for this item or an empty string if no
     * description is available for this item.
     *
     * @return the description of the tag
     */
    public String getDescription() {
        return "";
    }

    /**
     * Returns a string representing this item in JSON notation.
     *
     * @return a string representing this item in JSON notation
     */
    public String toJSON() {
        // TODO: Additional processing is required, see JDK-8238495
        JSONBuilder builder = new JSONBuilder();
        Category category = getCategory();
        switch (category) {
            case MODULES:
                builder.append("l", label);
                break;

            case PACKAGES:
                if (!containingModule.isEmpty()) {
                    builder.append("m", containingModule);
                }
                builder.append("l", label);
                if (!url.isEmpty()) {
                    builder.append("u", url);
                }
                if (kind != null) {
                    builder.append("k", kind.ordinal());
                }
                break;

            case TYPES:
                if (!containingPackage.isEmpty()) {
                    builder.append("p", containingPackage);
                }
                if (!containingModule.isEmpty()) {
                    builder.append("m", containingModule);
                }
                builder.append("l", label);
                if (!url.isEmpty()) {
                    builder.append("u", url);
                }
                if (kind != null && kind != Kind.CLASS) {
                    builder.append("k", kind.ordinal());
                }
                break;

            case MEMBERS:
                if (!containingModule.isEmpty()) {
                    builder.append("m", containingModule);
                }
                builder.append("p", containingPackage)
                        .append("c", containingClass)
                        .append("l", label);
                if (!url.isEmpty()) {
                    builder.append("u", url);
                }
                if (kind != null && kind != Kind.METHOD) {
                    builder.append("k", kind.ordinal());
                }
                break;

            case TAGS:
                String holder = getHolder();
                String description = getDescription();
                builder.append("l", escapeQuotes(label))
                       .append("h", holder);
                if (!description.isEmpty()) {
                    builder.append("d", escapeQuotes(description));
                }
                if (kind != null && kind != Kind.SEARCH_ITEM) {
                    builder.append("k", kind.ordinal());
                }
                builder.append("u", escapeQuotes(url));
                break;

            default:
                throw new AssertionError("Unexpected category: " + category);
        }
        return builder.toString();
    }

    private static String escapeQuotes(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Simple JSON object string builder class
    private static class JSONBuilder {

        StringBuilder b = new StringBuilder("{");

        public JSONBuilder append(String name, Object value) {
            if (b.length() > 1) {
                b.append(",");
            }
            b.append("\"").append(name).append("\":\"").append(value).append("\"");
            return this;
        }

        @Override
        public String toString() {
            b.append("}");
            return b.toString();
        }
    }
 }
