/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.markdown;

import java.util.List;

import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTrees;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.DCTree;
import com.sun.tools.javac.tree.DocTreeMaker;

/**
 * A transformer for DocTrees.
 */
public class DCTransformer {

    protected final DocTreeMaker m;

    public DCTransformer(DocTrees trees) {
        if (!(trees instanceof JavacTrees t)) {
            throw new IllegalArgumentException("class not supported: " + trees.getClass());
        }
        m = t.getDocTreeFactory();
    }

    /**
     * Transforms a doc tree node.
     * This node dispatches to a more specific overload, based on the kind of the node.
     * The result may be the same as the argument if no transformations were made.
     *
     * @param tree the tree node
     * @return a tree with any "extensions" converted into tags
     */
    public DCTree transform(DCTree tree) {
        // The following switch statement could eventually be converted to a
        // pattern switch. It is intended to be a total switch, with a default
        // to catch any omissions.
        return switch (tree.getKind()) {
            // The following cannot contain Markdown and so always transform to themselves.
            case ATTRIBUTE,
                    CODE, COMMENT,
                    DOC_ROOT, DOC_TYPE,
                    END_ELEMENT, ENTITY, ERRONEOUS, ESCAPE,
                    IDENTIFIER, INHERIT_DOC,
                    LITERAL,
                    REFERENCE,
                    SNIPPET, START_ELEMENT, SYSTEM_PROPERTY,
                    TEXT,
                    VALUE -> tree;

            // The following may contain Markdown in at least one of their fields.
            case AUTHOR -> transform((DCTree.DCAuthor) tree);
            case DEPRECATED -> transform((DCTree.DCDeprecated) tree);
            case DOC_COMMENT -> transform((DCTree.DCDocComment) tree);
            case EXCEPTION, THROWS -> transform((DCTree.DCThrows) tree);
            case HIDDEN -> transform((DCTree.DCHidden) tree);
            case INDEX -> transform((DCTree.DCIndex) tree);
            case LINK, LINK_PLAIN -> transform((DCTree.DCLink) tree);
            case PARAM -> transform((DCTree.DCParam) tree);
            case PROVIDES -> transform((DCTree.DCProvides) tree);
            case RETURN -> transform((DCTree.DCReturn) tree);
            case SEE -> transform((DCTree.DCSee) tree);
            case SERIAL -> transform((DCTree.DCSerial) tree);
            case SERIAL_DATA -> transform((DCTree.DCSerialData) tree);
            case SERIAL_FIELD -> transform((DCTree.DCSerialField) tree);
            case SINCE -> transform((DCTree.DCSince) tree);
            case SPEC -> transform((DCTree.DCSpec) tree);
            case SUMMARY -> transform((DCTree.DCSummary) tree);
            case UNKNOWN_BLOCK_TAG -> transform((DCTree.DCUnknownBlockTag) tree);
            case UNKNOWN_INLINE_TAG -> transform((DCTree.DCUnknownInlineTag) tree);
            case USES -> transform((DCTree.DCUses) tree);
            case VERSION -> transform((DCTree.DCVersion) tree);

            // This should never be handled directly; instead it should be handled as part of
            //   transform(List<? extends DocTree>);
            // because a Markdown node has the potential to be split into multiple nodes.
            case MARKDOWN -> throw new IllegalArgumentException(tree.getKind().toString());

            // Catch in case new kinds are added
            default -> throw new IllegalArgumentException(tree.getKind().toString());
        };
    }

    /**
     * Transforms a list of doc tree nodes.
     * Any nodes are individually transformed.
     *
     * @param trees the list of tree nodes to be transformed
     * @return the transformed list
     */
    public List<? extends DCTree> transform(List<? extends DCTree> trees) {
        var list2 = trees.stream()
                .map(this::transform)
                .toList();
        return equal(list2, trees) ? trees : list2;
    }

    //-----------------------------------------------------------------------------
    //
    // The following {@code transform} methods invoke {@code transform} on
    // any children that may contain Markdown. If the transformations on
    // the children are all identify transformations (that is the result
    // of the transformations are the same as the originals) then the
    // result of the overall transform is the original object. But if
    // any transformation on the children is not an identity transformation
    // then the result is a new node containing the transformed values.
    //
    // Thus, we only duplicate the parts of the tree that have changed,
    // and we do not duplicate the parts of the tree that have not changed.

    public DCTree.DCAuthor transform(DCTree.DCAuthor tree) {
        var name2 = transform(tree.name);
        return (equal(name2, tree.name))
                ? tree
                : m.at(tree.pos).newAuthorTree(name2);
    }

    public DCTree.DCDeprecated transform(DCTree.DCDeprecated tree) {
        var body2 = transform(tree.body);
        return (equal(body2, tree.body))
                ? tree
                : m.at(tree.pos).newDeprecatedTree(body2);
    }

    public DCTree.DCDocComment transform(DCTree.DCDocComment tree) {
        var fullBody2 = transform(tree.fullBody);
        var tags2 = transform(tree.tags);
        // Note: preamble and postamble only appear in HTML files, so should always be
        // null or empty for doc comments and/or Markdown files
        var pre2 = transform(tree.preamble);
        var post2 = transform(tree.postamble);
        return (equal(fullBody2, tree.fullBody) && equal(tags2, tree.tags)
                && equal(pre2, tree.preamble) && equal(post2, tree.postamble))
                ? tree
                : m.at(tree.pos).newDocCommentTree(tree.comment, fullBody2, tags2, pre2, post2);
    }

    public DCTree.DCHidden transform(DCTree.DCHidden tree) {
        var body2 = transform(tree.body);
        return (equal(body2, tree.body))
                ? tree
                : m.at(tree.pos).newHiddenTree(body2);
    }

    public DCTree.DCIndex transform(DCTree.DCIndex tree) {
        // The public API permits a DocTree, although in the implementation, it is always a TextTree.
        var term2 = transform(tree.term);
        var desc2 = transform(tree.description);
        return (equal(term2, tree.term) && equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newIndexTree(term2, desc2).setEndPos(tree.getEndPos());
    }

    public DCTree.DCLink transform(DCTree.DCLink tree) {
        var label2 = transform(tree.label);
        return (equal(label2, tree.label))
                ? tree
                : switch (tree.getKind()) {
            case LINK -> m.at(tree.pos).newLinkTree(tree.ref, label2).setEndPos(tree.getEndPos());
            case LINK_PLAIN -> m.at(tree.pos).newLinkPlainTree(tree.ref, label2).setEndPos(tree.getEndPos());
            default -> throw new IllegalArgumentException(tree.getKind().toString());
        };
    }

    public DCTree.DCParam transform(DCTree.DCParam tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newParamTree(tree.isTypeParameter, tree.name, desc2);
    }

    public DCTree.DCProvides transform(DCTree.DCProvides tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newProvidesTree(tree.serviceType, desc2);
    }

    public DCTree.DCReturn transform(DCTree.DCReturn tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newReturnTree(tree.inline, desc2).setEndPos(tree.getEndPos());
    }

    public DCTree.DCSee transform(DCTree.DCSee tree) {
        List<? extends DocTree> ref2 = transform(tree.reference);
        return (equal(ref2, tree.getReference()))
                ? tree
                : m.at(tree.pos).newSeeTree(ref2);
    }

    public DCTree.DCSerial transform(DCTree.DCSerial tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newSerialTree(desc2);
    }

    public DCTree.DCSerialData transform(DCTree.DCSerialData tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newSerialDataTree(desc2);
    }

    public DCTree.DCSerialField transform(DCTree.DCSerialField tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newSerialFieldTree(tree.name, tree.type, desc2);
    }

    public DCTree.DCSince transform(DCTree.DCSince tree) {
        var body2 = transform(tree.body);
        return (equal(body2, tree.body))
                ? tree
                : m.at(tree.pos).newSinceTree(body2);
    }

    public DCTree.DCSpec transform(DCTree.DCSpec tree) {
        var title2 = transform(tree.title);
        return (equal(title2, tree.title))
                ? tree
                : m.at(tree.pos).newSpecTree(tree.uri, title2);
    }

    public DCTree.DCSummary transform(DCTree.DCSummary tree) {
        var summ2 = transform(tree.summary);
        return (equal(summ2, tree.summary))
                ? tree
                : m.at(tree.pos).newSummaryTree(summ2).setEndPos(tree.getEndPos());
    }

    public DCTree.DCThrows transform(DCTree.DCThrows tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : switch (tree.getKind()) {
            case EXCEPTION -> m.at(tree.pos).newExceptionTree(tree.name, desc2);
            case THROWS -> m.at(tree.pos).newThrowsTree(tree.name, desc2);
            default -> throw new IllegalArgumentException(tree.getKind().toString());
        };
    }

    public DCTree.DCUnknownBlockTag transform(DCTree.DCUnknownBlockTag tree) {
        var cont2 = transform(tree.content);
        return (equal(cont2, tree.content))
                ? tree
                : m.at(tree.pos).newUnknownBlockTagTree(tree.name, cont2);
    }

    public DCTree.DCUnknownInlineTag transform(DCTree.DCUnknownInlineTag tree) {
        var cont2 = transform(tree.content);
        return (equal(cont2, tree.content))
                ? tree
                : m.at(tree.pos).newUnknownInlineTagTree(tree.name, cont2).setEndPos(tree.getEndPos());
    }

    public DCTree.DCUses transform(DCTree.DCUses tree) {
        var desc2 = transform(tree.description);
        return (equal(desc2, tree.description))
                ? tree
                : m.at(tree.pos).newUsesTree(tree.serviceType, desc2);
    }

    public DCTree.DCVersion transform(DCTree.DCVersion tree) {
        var body2 = transform(tree.body);
        return (equal(body2, tree.body))
                ? tree
                : m.at(tree.pos).newVersionTree(body2);
    }

    /**
     * Shallow "equals" for two doc tree nodes.
     *
     * @param <T> the type of the items
     * @param item1 the first item
     * @param item2 the second item
     * @return {@code true} if the items are reference-equal, and {@code false} otherwise
     */
    private static <T extends DocTree> boolean equal(T item1, T item2) {
        return item1 == item2;
    }

    /**
     * Shallow "equals" for two lists of doc tree nodes.
     *
     * @param <T> the type of the items
     * @param list1 the first item
     * @param list2 the second item
     * @return {@code true} if the items are reference-equal, and {@code false} otherwise
     */
    private static <T extends DocTree> boolean equal(List<? extends T> list1, List<? extends T> list2) {
        if (list1 == null || list2 == null) {
            return (list1 == list2);
        }

        if (list1.size() != list2.size()) {
            return false;
        }

        var iter1 = list1.iterator();
        var iter2 = list2.iterator();
        while (iter1.hasNext()) {
            var item1 = iter1.next();
            var item2 = iter2.next();
            if (item1 != item2) {
                return false;
            }
        }

        return true;
    }
}
