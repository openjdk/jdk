/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * NOT_STARTED indicates that the Symbol this instance belongs to have not been
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
 * it directly. You can also move back to the IN_PROGRESS sate using reset().
 *
 * <p><b>This is NOT part of any supported API. If you write code that depends
 * on this, you do so at your own risk. This code and its internal interfaces
 * are subject to change or deletion without notice.</b>
 */
public class Annotations {

    private static final List<Attribute.Compound> NOT_STARTED = List.of(null);
    private static final List<Attribute.Compound> IN_PROGRESS = List.of(null);
    /*
     * This field should never be null
     */
    private List<Attribute.Compound> attributes = NOT_STARTED;
    /*
     * The Symbol this Annotatios belong to
     */
    private final Symbol s;

    public Annotations(Symbol s) {
        this.s = s;
    }

    public List<Attribute.Compound> getAttributes() {
        return filterSentinels(attributes);
    }

    public void setAttributes(List<Attribute.Compound> a) {
        Assert.check(pendingCompletion() || !isStarted());
        if (a == null) {
            throw new NullPointerException();
        }
        attributes = a;
    }

    public void setAttributes(Annotations other) {
        if (other == null) {
            throw new NullPointerException();
        }
        setAttributes(other.getAttributes());
    }

    public void setAttributesWithCompletion(final Annotate.AnnotateRepeatedContext ctx) {
        Assert.check(pendingCompletion() || (!isStarted() && s.kind == PCK));

        Map<Symbol.TypeSymbol, ListBuffer<Attribute.Compound>> annotated = ctx.annotated;
        boolean atLeastOneRepeated = false;
        List<Attribute.Compound> buf = List.<Attribute.Compound>nil();
        for (ListBuffer<Attribute.Compound> lb : annotated.values()) {
            if (lb.size() == 1) {
                buf = buf.prepend(lb.first());
            } else { // repeated
                buf = buf.prepend(new Placeholder(lb.toList(), s));
                atLeastOneRepeated = true;
            }
        }

        // Add non-repeating attributes
        setAttributes(buf.reverse());

        if (atLeastOneRepeated) {
            // The Symbol s is now annotated with a combination of
            // finished non-repeating annotations and placeholders for
            // repeating annotations.
            //
            // We need to do this in two passes because when creating
            // a container for a repeating annotation we must
            // guarantee that the @ContainedBy on the
            // contained annotation is fully annotated
            //
            // The way we force this order is to do all repeating
            // annotations in a pass after all non-repeating are
            // finished. This will work because @ContainedBy
            // is non-repeating and therefore will be annotated in the
            // fist pass.

            // Queue a pass that will replace Attribute.Placeholders
            // with Attribute.Compound (made from synthesized containers).
            ctx.annotateRepeated(new Annotate.Annotator() {

                @Override
                public String toString() {
                    return "repeated annotation pass of: " + s + " in: " + s.owner;
                }

                @Override
                public void enterAnnotation() {
                    complete(ctx);
                }
            });
        }
    }

    public Annotations reset() {
        attributes = IN_PROGRESS;
        return this;
    }

    public boolean isEmpty() {
        return !isStarted()
                || pendingCompletion()
                || attributes.isEmpty();
    }

    public boolean pendingCompletion() {
        return attributes == IN_PROGRESS;
    }

    public Annotations append(List<Attribute.Compound> l) {
        attributes = filterSentinels(attributes);

        if (l.isEmpty()) {
            ; // no-op
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.appendList(l);
        }
        return this;
    }

    public Annotations prepend(List<Attribute.Compound> l) {
        attributes = filterSentinels(attributes);

        if (l.isEmpty()) {
            ; // no-op
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.prependList(l);
        }
        return this;
    }

    private List<Attribute.Compound> filterSentinels(List<Attribute.Compound> a) {
        return (a == IN_PROGRESS || a == NOT_STARTED)
                ? List.<Attribute.Compound>nil()
                : a;
    }

    private boolean isStarted() {
        return attributes != NOT_STARTED;
    }

    private List<Attribute.Compound> getPlaceholders() {
        List<Attribute.Compound> res = List.<Attribute.Compound>nil();
        for (Attribute.Compound a : filterSentinels(attributes)) {
            if (a instanceof Placeholder) {
                res = res.prepend(a);
            }
        }
        return res.reverse();
    }

    /*
     * Replace Placeholders for repeating annotations with their containers
     */
    private void complete(Annotate.AnnotateRepeatedContext ctx) {
        Assert.check(!pendingCompletion());
        Log log = ctx.log;
        Env<AttrContext> env = ctx.env;
        JavaFileObject oldSource = log.useSource(env.toplevel.sourcefile);
        try {

            if (isEmpty()) {
                return;
            }

            List<Attribute.Compound> result = List.nil();
            for (Attribute.Compound a : getAttributes()) {
                if (a instanceof Placeholder) {
                    Attribute.Compound replacement = replaceOne((Placeholder) a, ctx);

                    if (null != replacement) {
                        result = result.prepend(replacement);
                    }
                } else {
                    result = result.prepend(a);
                }
            }

            attributes = result.reverse();

            Assert.check(Annotations.this.getPlaceholders().isEmpty());
        } finally {
            log.useSource(oldSource);
        }
    }

    private Attribute.Compound replaceOne(Placeholder placeholder, Annotate.AnnotateRepeatedContext ctx) {
        Log log = ctx.log;

        // Process repeated annotations
        Attribute.Compound validRepeated =
                ctx.processRepeatedAnnotations(placeholder.getPlaceholderFor());

        if (validRepeated != null) {
            // Check that the container isn't manually
            // present along with repeated instances of
            // its contained annotation.
            ListBuffer<Attribute.Compound> manualContainer = ctx.annotated.get(validRepeated.type.tsym);
            if (manualContainer != null) {
                log.error(ctx.pos.get(manualContainer.first()), "invalid.containedby.annotation.repeated.and.container.present",
                        manualContainer.first().type.tsym);
            }
        }

        // A null return will delete the Placeholder
        return validRepeated;

    }

    private static class Placeholder extends Attribute.Compound {

        private List<Attribute.Compound> placeholderFor;
        private Symbol on;

        public Placeholder(List<Attribute.Compound> placeholderFor, Symbol on) {
            super(Type.noType, List.<Pair<Symbol.MethodSymbol, Attribute>>nil());
            this.placeholderFor = placeholderFor;
            this.on = on;
        }

        @Override
        public String toString() {
            return "<placeholder: " + placeholderFor + " on: " + on + ">";
        }

        public List<Attribute.Compound> getPlaceholderFor() {
            return placeholderFor;
        }
    }
}
