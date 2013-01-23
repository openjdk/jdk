/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.Map;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;
import static com.sun.tools.javac.code.Kinds.PCK;

/**
 * Container for all annotations (attributes in javac) on a Symbol.
 *
 * This class is explicitly mutable. Its contents will change when attributes
 * are annotated onto the Symbol. However this class depends on the facts that
 * List (in javac) is immutable.
 *
 * An instance of this class can be in one of three states:
 *
 * NOT_STARTED indicates that the Symbol this instance belongs to has not been
 * annotated (yet). Specifically if the declaration is not annotated this
 * instance will never move past NOT_STARTED. You can never go back to
 * NOT_STARTED.
 *
 * IN_PROGRESS annotations have been found on the declaration. Will be processed
 * later. You can reset to IN_PROGRESS. While IN_PROGRESS you can set the list
 * of attributes (and this moves out of the IN_PROGRESS state).
 *
 * "unnamed" this Annotations contains some attributes, possibly the final set.
 * While in this state you can only prepend or append to the attributes not set
 * it directly. You can also move back to the IN_PROGRESS state using reset().
 *
 * <p><b>This is NOT part of any supported API. If you write code that depends
 * on this, you do so at your own risk. This code and its internal interfaces
 * are subject to change or deletion without notice.</b>
 */
public class Annotations {

    private static final List<Attribute.Compound> DECL_NOT_STARTED = List.of(null);
    private static final List<Attribute.Compound> DECL_IN_PROGRESS = List.of(null);

    /*
     * This field should never be null
     */
    private List<Attribute.Compound> attributes = DECL_NOT_STARTED;

    /*
     * This field should never be null
     */
    private List<Attribute.TypeCompound> type_attributes = List.<Attribute.TypeCompound>nil();

    /*
     * The Symbol this Annotations instance belongs to
     */
    private final Symbol sym;

    public Annotations(Symbol sym) {
        this.sym = sym;
    }

    public List<Attribute.Compound> getDeclarationAttributes() {
        return filterDeclSentinels(attributes);
    }

    public List<Attribute.TypeCompound> getTypeAttributes() {
        return type_attributes;
    }

    public void setDeclarationAttributes(List<Attribute.Compound> a) {
        Assert.check(pendingCompletion() || !isStarted());
        if (a == null) {
            throw new NullPointerException();
        }
        attributes = a;
    }

    public void setTypeAttributes(List<Attribute.TypeCompound> a) {
        if (a == null) {
            throw new NullPointerException();
        }
        type_attributes = a;
    }

    public void setAttributes(Annotations other) {
        if (other == null) {
            throw new NullPointerException();
        }
        setDeclarationAttributes(other.getDeclarationAttributes());
        setTypeAttributes(other.getTypeAttributes());
    }

    public void setDeclarationAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.Compound> ctx) {
        Assert.check(pendingCompletion() || (!isStarted() && sym.kind == PCK));
        this.setDeclarationAttributes(getAttributesForCompletion(ctx));
    }

    public void appendTypeAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.TypeCompound> ctx) {
        this.appendUniqueTypes(getAttributesForCompletion(ctx));
    }

    private <T extends Attribute.Compound> List<T> getAttributesForCompletion(
            final Annotate.AnnotateRepeatedContext<T> ctx) {

        Map<Symbol.TypeSymbol, ListBuffer<T>> annotated = ctx.annotated;
        boolean atLeastOneRepeated = false;
        List<T> buf = List.<T>nil();
        for (ListBuffer<T> lb : annotated.values()) {
            if (lb.size() == 1) {
                buf = buf.prepend(lb.first());
            } else { // repeated
                // This will break when other subtypes of Attributs.Compound
                // are introduced, because PlaceHolder is a subtype of TypeCompound.
                T res;
                @SuppressWarnings("unchecked")
                T ph = (T) new Placeholder<T>(ctx, lb.toList(), sym);
                res = ph;
                buf = buf.prepend(res);
                atLeastOneRepeated = true;
            }
        }

        if (atLeastOneRepeated) {
            // The Symbol s is now annotated with a combination of
            // finished non-repeating annotations and placeholders for
            // repeating annotations.
            //
            // We need to do this in two passes because when creating
            // a container for a repeating annotation we must
            // guarantee that the @Repeatable on the
            // contained annotation is fully annotated
            //
            // The way we force this order is to do all repeating
            // annotations in a pass after all non-repeating are
            // finished. This will work because @Repeatable
            // is non-repeating and therefore will be annotated in the
            // fist pass.

            // Queue a pass that will replace Attribute.Placeholders
            // with Attribute.Compound (made from synthesized containers).
            ctx.annotateRepeated(new Annotate.Annotator() {
                @Override
                public String toString() {
                    return "repeated annotation pass of: " + sym + " in: " + sym.owner;
                }

                @Override
                public void enterAnnotation() {
                    complete(ctx);
                }
            });
        }
        // Add non-repeating attributes
        return buf.reverse();
    }

    public Annotations reset() {
        attributes = DECL_IN_PROGRESS;
        return this;
    }

    public boolean isEmpty() {
        return !isStarted()
                || pendingCompletion()
                || attributes.isEmpty();
    }

    public boolean isTypesEmpty() {
        return type_attributes.isEmpty();
    }

    public boolean pendingCompletion() {
        return attributes == DECL_IN_PROGRESS;
    }

    public Annotations append(List<Attribute.Compound> l) {
        attributes = filterDeclSentinels(attributes);

        if (l.isEmpty()) {
            ; // no-op
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.appendList(l);
        }
        return this;
    }

    public Annotations appendUniqueTypes(List<Attribute.TypeCompound> l) {
        if (l.isEmpty()) {
            ; // no-op
        } else if (type_attributes.isEmpty()) {
            type_attributes = l;
        } else {
            // TODO: in case we expect a large number of annotations, this
            // might be inefficient.
            for (Attribute.TypeCompound tc : l) {
                if (!type_attributes.contains(tc))
                    type_attributes = type_attributes.append(tc);
            }
        }
        return this;
    }

    public Annotations prepend(List<Attribute.Compound> l) {
        attributes = filterDeclSentinels(attributes);

        if (l.isEmpty()) {
            ; // no-op
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.prependList(l);
        }
        return this;
    }

    private List<Attribute.Compound> filterDeclSentinels(List<Attribute.Compound> a) {
        return (a == DECL_IN_PROGRESS || a == DECL_NOT_STARTED)
                ? List.<Attribute.Compound>nil()
                : a;
    }

    private boolean isStarted() {
        return attributes != DECL_NOT_STARTED;
    }

    private List<Attribute.Compound> getPlaceholders() {
        List<Attribute.Compound> res = List.<Attribute.Compound>nil();
        for (Attribute.Compound a : filterDeclSentinels(attributes)) {
            if (a instanceof Placeholder) {
                res = res.prepend(a);
            }
        }
        return res.reverse();
    }

    private List<Attribute.TypeCompound> getTypePlaceholders() {
        List<Attribute.TypeCompound> res = List.<Attribute.TypeCompound>nil();
        for (Attribute.TypeCompound a : type_attributes) {
            if (a instanceof Placeholder) {
                res = res.prepend(a);
            }
        }
        return res.reverse();
    }

    /*
     * Replace Placeholders for repeating annotations with their containers
     */
    private <T extends Attribute.Compound> void complete(Annotate.AnnotateRepeatedContext<T> ctx) {
        Log log = ctx.log;
        Env<AttrContext> env = ctx.env;
        JavaFileObject oldSource = log.useSource(env.toplevel.sourcefile);
        try {
            // TODO: can we reduce duplication in the following branches?
            if (ctx.isTypeCompound) {
                Assert.check(!isTypesEmpty());

                if (isTypesEmpty()) {
                    return;
                }

                List<Attribute.TypeCompound> result = List.nil();
                for (Attribute.TypeCompound a : getTypeAttributes()) {
                    if (a instanceof Placeholder) {
                        @SuppressWarnings("unchecked")
                        Placeholder<Attribute.TypeCompound> ph = (Placeholder<Attribute.TypeCompound>) a;
                        Attribute.TypeCompound replacement = replaceOne(ph, ph.getRepeatedContext());

                        if (null != replacement) {
                            result = result.prepend(replacement);
                        }
                    } else {
                        result = result.prepend(a);
                    }
                }

                type_attributes = result.reverse();

                Assert.check(Annotations.this.getTypePlaceholders().isEmpty());
            } else {
                Assert.check(!pendingCompletion());

                if (isEmpty()) {
                    return;
                }

                List<Attribute.Compound> result = List.nil();
                for (Attribute.Compound a : getDeclarationAttributes()) {
                    if (a instanceof Placeholder) {
                        @SuppressWarnings("unchecked")
                        Attribute.Compound replacement = replaceOne((Placeholder<T>) a, ctx);

                        if (null != replacement) {
                            result = result.prepend(replacement);
                        }
                    } else {
                        result = result.prepend(a);
                    }
                }

                attributes = result.reverse();

                Assert.check(Annotations.this.getPlaceholders().isEmpty());
            }
        } finally {
            log.useSource(oldSource);
        }
    }

    private <T extends Attribute.Compound> T replaceOne(Placeholder<T> placeholder, Annotate.AnnotateRepeatedContext<T> ctx) {
        Log log = ctx.log;

        // Process repeated annotations
        T validRepeated = ctx.processRepeatedAnnotations(placeholder.getPlaceholderFor(), sym);

        if (validRepeated != null) {
            // Check that the container isn't manually
            // present along with repeated instances of
            // its contained annotation.
            ListBuffer<T> manualContainer = ctx.annotated.get(validRepeated.type.tsym);
            if (manualContainer != null) {
                log.error(ctx.pos.get(manualContainer.first()), "invalid.repeatable.annotation.repeated.and.container.present",
                        manualContainer.first().type.tsym);
            }
        }

        // A null return will delete the Placeholder
        return validRepeated;
    }

    private static class Placeholder<T extends Attribute.Compound> extends Attribute.TypeCompound {

        private final Annotate.AnnotateRepeatedContext<T> ctx;
        private final List<T> placeholderFor;
        private final Symbol on;

        public Placeholder(Annotate.AnnotateRepeatedContext<T> ctx, List<T> placeholderFor, Symbol on) {
            super(on.type, List.<Pair<Symbol.MethodSymbol, Attribute>>nil(),
                    ctx.isTypeCompound ?
                            ((Attribute.TypeCompound)placeholderFor.head).position :
                                null);
            this.ctx = ctx;
            this.placeholderFor = placeholderFor;
            this.on = on;
        }

        @Override
        public String toString() {
            return "<placeholder: " + placeholderFor + " on: " + on + ">";
        }

        public List<T> getPlaceholderFor() {
            return placeholderFor;
        }

        public Annotate.AnnotateRepeatedContext<T> getRepeatedContext() {
            return ctx;
        }
    }
}
