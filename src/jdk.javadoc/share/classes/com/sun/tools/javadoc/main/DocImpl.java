/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.CollationKey;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.FileObject;

import com.sun.javadoc.*;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Position;

/**
 * abstract base class of all Doc classes.  Doc item's are representations
 * of java language constructs (class, package, method,...) which have
 * comments and have been processed by this run of javadoc.  All Doc items
 * are unique, that is, they are == comparable.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.2
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Neal Gafter (rewrite)
 */
@Deprecated(since="9", forRemoval=true)
@SuppressWarnings("removal")
public abstract class DocImpl implements Doc, Comparable<Object> {

    /**
     * Doc environment
     */
    protected final DocEnv env;   //### Rename this everywhere to 'docenv' ?

    /**
     * Back pointer to the tree node for this doc item.
     * May be null if there is no associated tree.
     */
    protected TreePath treePath;

    /**
     *  The complex comment object, lazily initialized.
     */
    private Comment comment;

    /**
     * The cached sort key, to take care of Natural Language Text sorting.
     */
    private CollationKey collationkey = null;

    /**
     *  Raw documentation string.
     */
    protected String documentation;  // Accessed in PackageDocImpl, RootDocImpl

    /**
     * Cached first sentence.
     */
    private Tag[] firstSentence;

    /**
     * Cached inline tags.
     */
    private Tag[] inlineTags;

    /**
     * Constructor.
     */
    DocImpl(DocEnv env, TreePath treePath) {
        this.treePath = treePath;
        this.documentation = getCommentText(treePath);
        this.env = env;
    }

    private static String getCommentText(TreePath p) {
        if (p == null)
            return null;

        JCCompilationUnit topLevel = (JCCompilationUnit) p.getCompilationUnit();
        JCTree tree = (JCTree) p.getLeaf();
        return topLevel.docComments.getCommentText(tree);
    }

    /**
     * So subclasses have the option to do lazy initialization of
     * "documentation" string.
     */
    protected String documentation() {
        if (documentation == null) documentation = "";
        return documentation;
    }

    /**
     * For lazy initialization of comment.
     */
    Comment comment() {
        if (comment == null) {
            String d = documentation();
            if (env.javaScriptScanner != null) {
                env.javaScriptScanner.parse(d, new JavaScriptScanner.Reporter() {
                    @Override
                    public void report() {
                        env.error(DocImpl.this, "javadoc.JavaScript_in_comment");
                        throw new Error();
                    }
                });
            }
            if (env.doclint != null
                    && treePath != null
                    && env.shouldCheck(treePath.getCompilationUnit())
                    && d.equals(getCommentText(treePath))) {
                env.doclint.scan(treePath);
            }
            comment = new Comment(this, d);
        }
        return comment;
    }

    /**
     * Return the text of the comment for this doc item.
     * TagImpls have been removed.
     */
    public String commentText() {
        return comment().commentText();
    }

    /**
     * Return all tags in this Doc item.
     *
     * @return an array of TagImpl containing all tags on this Doc item.
     */
    public Tag[] tags() {
        return comment().tags();
    }

    /**
     * Return tags of the specified kind in this Doc item.
     *
     * @param tagname name of the tag kind to search for.
     * @return an array of TagImpl containing all tags whose 'kind()'
     * matches 'tagname'.
     */
    public Tag[] tags(String tagname) {
        return comment().tags(tagname);
    }

    /**
     * Return the see also tags in this Doc item.
     *
     * @return an array of SeeTag containing all &#64;see tags.
     */
    public SeeTag[] seeTags() {
        return comment().seeTags();
    }

    public Tag[] inlineTags() {
        if (inlineTags == null) {
            inlineTags = Comment.getInlineTags(this, commentText());
        }
        return inlineTags;
    }

    public Tag[] firstSentenceTags() {
        if (firstSentence == null) {
            //Parse all sentences first to avoid duplicate warnings.
            inlineTags();
            try {
                env.setSilent(true);
                firstSentence = Comment.firstSentenceTags(this, commentText());
            } finally {
                env.setSilent(false);
            }
        }
        return firstSentence;
    }

    /**
     * Utility for subclasses which read HTML documentation files.
     */
    String readHTMLDocumentation(InputStream input, FileObject filename) throws IOException {
        byte[] filecontents = new byte[input.available()];
        try {
            DataInputStream dataIn = new DataInputStream(input);
            dataIn.readFully(filecontents);
        } finally {
            input.close();
        }
        String encoding = env.getEncoding();
        String rawDoc = (encoding!=null)
            ? new String(filecontents, encoding)
            : new String(filecontents);
        Pattern bodyPat = Pattern.compile("(?is).*<body\\b[^>]*>(.*)</body\\b.*");
        Matcher m = bodyPat.matcher(rawDoc);
        if (m.matches()) {
            return m.group(1);
        } else {
            String key = rawDoc.matches("(?is).*<body\\b.*")
                    ? "javadoc.End_body_missing_from_html_file"
                    : "javadoc.Body_missing_from_html_file";
            env.error(SourcePositionImpl.make(filename, Position.NOPOS, null), key);
            return "";
        }
    }

    /**
     * Return the full unprocessed text of the comment.  Tags
     * are included as text.  Used mainly for store and retrieve
     * operations like internalization.
     */
    public String getRawCommentText() {
        return documentation();
    }

    /**
     * Set the full unprocessed text of the comment.  Tags
     * are included as text.  Used mainly for store and retrieve
     * operations like internalization.
     */
    public void setRawCommentText(String rawDocumentation) {
        treePath = null;
        documentation = rawDocumentation;
        comment = null;
    }

    /**
     * Set the full unprocessed text of the comment and tree path.
     */
    void setTreePath(TreePath treePath) {
        this.treePath = treePath;
        documentation = getCommentText(treePath);
        comment = null;
    }

    /**
     * return a key for sorting.
     */
    CollationKey key() {
        if (collationkey == null) {
            collationkey = generateKey();
        }
        return collationkey;
    }

    /**
     * Generate a key for sorting.
     * <p>
     * Default is name().
     */
    CollationKey generateKey() {
        String k = name();
        // System.out.println("COLLATION KEY FOR " + this + " is \"" + k + "\"");
        return env.doclocale.collator.getCollationKey(k);
    }

    /**
     * Returns a string representation of this Doc item.
     */
    @Override
    public String toString() {
        return qualifiedName();
    }

    /**
     * Returns the name of this Doc item.
     *
     * @return  the name
     */
    public abstract String name();

    /**
     * Returns the qualified name of this Doc item.
     *
     * @return  the name
     */
    public abstract String qualifiedName();

    /**
     * Compares this Object with the specified Object for order.  Returns a
     * negative integer, zero, or a positive integer as this Object is less
     * than, equal to, or greater than the given Object.
     * <p>
     * Included so that Doc item are java.lang.Comparable.
     *
     * @param   obj the {@code Object} to be compared.
     * @return  a negative integer, zero, or a positive integer as this Object
     *          is less than, equal to, or greater than the given Object.
     * @exception ClassCastException the specified Object's type prevents it
     *            from being compared to this Object.
     */
    public int compareTo(Object obj) {
        // System.out.println("COMPARE \"" + this + "\" to \"" + obj + "\" = " + key().compareTo(((DocImpl)obj).key()));
        return key().compareTo(((DocImpl)obj).key());
    }

    /**
     * Is this Doc item a field?  False until overridden.
     *
     * @return true if it represents a field
     */
    public boolean isField() {
        return false;
    }

    /**
     * Is this Doc item an enum constant?  False until overridden.
     *
     * @return true if it represents an enum constant
     */
    public boolean isEnumConstant() {
        return false;
    }

    /**
     * Is this Doc item a constructor?  False until overridden.
     *
     * @return true if it represents a constructor
     */
    public boolean isConstructor() {
        return false;
    }

    /**
     * Is this Doc item a method (but not a constructor or annotation
     * type element)?
     * False until overridden.
     *
     * @return true if it represents a method
     */
    public boolean isMethod() {
        return false;
    }

    /**
     * Is this Doc item an annotation type element?
     * False until overridden.
     *
     * @return true if it represents an annotation type element
     */
    public boolean isAnnotationTypeElement() {
        return false;
    }

    /**
     * Is this Doc item a interface (but not an annotation type)?
     * False until overridden.
     *
     * @return true if it represents a interface
     */
    public boolean isInterface() {
        return false;
    }

    /**
     * Is this Doc item a exception class?  False until overridden.
     *
     * @return true if it represents a exception
     */
    public boolean isException() {
        return false;
    }

    /**
     * Is this Doc item a error class?  False until overridden.
     *
     * @return true if it represents a error
     */
    public boolean isError() {
        return false;
    }

    /**
     * Is this Doc item an enum type?  False until overridden.
     *
     * @return true if it represents an enum type
     */
    public boolean isEnum() {
        return false;
    }

    /**
     * Is this Doc item an annotation type?  False until overridden.
     *
     * @return true if it represents an annotation type
     */
    public boolean isAnnotationType() {
        return false;
    }

    /**
     * Is this Doc item an ordinary class (i.e. not an interface,
     * annotation type, enumeration, exception, or error)?
     * False until overridden.
     *
     * @return true if it represents an ordinary class
     */
    public boolean isOrdinaryClass() {
        return false;
    }

    /**
     * Is this Doc item a class
     * (and not an interface or annotation type)?
     * This includes ordinary classes, enums, errors and exceptions.
     * False until overridden.
     *
     * @return true if it represents a class
     */
    public boolean isClass() {
        return false;
    }

    /**
     * return true if this Doc is include in the active set.
     */
    public abstract boolean isIncluded();

    /**
     * Return the source position of the entity, or null if
     * no position is available.
     */
    public SourcePosition position() { return null; }
}
