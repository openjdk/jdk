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

package com.sun.tools.javac.comp;

import java.util.Map;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.TypeTag.ARRAY;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/** Enter annotations on symbols.  Annotations accumulate in a queue,
 *  which is processed at the top level of any set of recursive calls
 *  requesting it be processed.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Annotate {
    protected static final Context.Key<Annotate> annotateKey =
        new Context.Key<Annotate>();

    public static Annotate instance(Context context) {
        Annotate instance = context.get(annotateKey);
        if (instance == null)
            instance = new Annotate(context);
        return instance;
    }

    final Attr attr;
    final TreeMaker make;
    final Log log;
    final Symtab syms;
    final Names names;
    final Resolve rs;
    final Types types;
    final ConstFold cfolder;
    final Check chk;

    protected Annotate(Context context) {
        context.put(annotateKey, this);
        attr = Attr.instance(context);
        make = TreeMaker.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        names = Names.instance(context);
        rs = Resolve.instance(context);
        types = Types.instance(context);
        cfolder = ConstFold.instance(context);
        chk = Check.instance(context);
    }

/* ********************************************************************
 * Queue maintenance
 *********************************************************************/

    private int enterCount = 0;

    ListBuffer<Annotator> q = new ListBuffer<Annotator>();
    ListBuffer<Annotator> typesQ = new ListBuffer<Annotator>();
    ListBuffer<Annotator> repeatedQ = new ListBuffer<Annotator>();
    ListBuffer<Annotator> afterRepeatedQ = new ListBuffer<Annotator>();

    public void earlier(Annotator a) {
        q.prepend(a);
    }

    public void normal(Annotator a) {
        q.append(a);
    }

    public void typeAnnotation(Annotator a) {
        typesQ.append(a);
    }

    public void repeated(Annotator a) {
        repeatedQ.append(a);
    }

    public void afterRepeated(Annotator a) {
        afterRepeatedQ.append(a);
    }

    /** Called when the Enter phase starts. */
    public void enterStart() {
        enterCount++;
    }

    /** Called after the Enter phase completes. */
    public void enterDone() {
        enterCount--;
        flush();
    }

    public void flush() {
        if (enterCount != 0) return;
        enterCount++;
        try {
            while (q.nonEmpty()) {
                q.next().enterAnnotation();
            }
            while (typesQ.nonEmpty()) {
                typesQ.next().enterAnnotation();
            }
            while (repeatedQ.nonEmpty()) {
                repeatedQ.next().enterAnnotation();
            }
            while (afterRepeatedQ.nonEmpty()) {
                afterRepeatedQ.next().enterAnnotation();
            }
        } finally {
            enterCount--;
        }
    }

    /** A client that has annotations to add registers an annotator,
     *  the method it will use to add the annotation.  There are no
     *  parameters; any needed data should be captured by the
     *  Annotator.
     */
    public interface Annotator {
        void enterAnnotation();
        String toString();
    }

    /**
     * This context contains all the information needed to synthesize new
     * annotations trees by the completer for repeating annotations.
     */
    public class AnnotateRepeatedContext<T extends Attribute.Compound> {
        public final Env<AttrContext> env;
        public final Map<Symbol.TypeSymbol, ListBuffer<T>> annotated;
        public final Map<T, JCDiagnostic.DiagnosticPosition> pos;
        public final Log log;
        public final boolean isTypeCompound;

        public AnnotateRepeatedContext(Env<AttrContext> env,
                                       Map<Symbol.TypeSymbol, ListBuffer<T>> annotated,
                                       Map<T, JCDiagnostic.DiagnosticPosition> pos,
                                       Log log,
                                       boolean isTypeCompound) {
            Assert.checkNonNull(env);
            Assert.checkNonNull(annotated);
            Assert.checkNonNull(pos);
            Assert.checkNonNull(log);

            this.env = env;
            this.annotated = annotated;
            this.pos = pos;
            this.log = log;
            this.isTypeCompound = isTypeCompound;
        }

        /**
         * Process a list of repeating annotations returning a new
         * Attribute.Compound that is the attribute for the synthesized tree
         * for the container.
         *
         * @param repeatingAnnotations a List of repeating annotations
         * @return a new Attribute.Compound that is the container for the repeatingAnnotations
         */
        public T processRepeatedAnnotations(List<T> repeatingAnnotations, Symbol sym) {
            return Annotate.this.processRepeatedAnnotations(repeatingAnnotations, this, sym);
        }

        /**
         * Queue the Annotator a on the repeating annotations queue of the
         * Annotate instance this context belongs to.
         *
         * @param a the Annotator to enqueue for repeating annotation annotating
         */
        public void annotateRepeated(Annotator a) {
            Annotate.this.repeated(a);
        }
    }

/* ********************************************************************
 * Compute an attribute from its annotation.
 *********************************************************************/

    /** Process a single compound annotation, returning its
     *  Attribute. Used from MemberEnter for attaching the attributes
     *  to the annotated symbol.
     */
    Attribute.Compound enterAnnotation(JCAnnotation a,
                                       Type expected,
                                       Env<AttrContext> env) {
        return enterAnnotation(a, expected, env, false);
    }

    Attribute.TypeCompound enterTypeAnnotation(JCAnnotation a,
            Type expected,
            Env<AttrContext> env) {
        return (Attribute.TypeCompound) enterAnnotation(a, expected, env, true);
    }

    // boolean typeAnnotation determines whether the method returns
    // a Compound (false) or TypeCompound (true).
    Attribute.Compound enterAnnotation(JCAnnotation a,
            Type expected,
            Env<AttrContext> env,
            boolean typeAnnotation) {
        // The annotation might have had its type attributed (but not checked)
        // by attr.attribAnnotationTypes during MemberEnter, in which case we do not
        // need to do it again.
        Type at = (a.annotationType.type != null ? a.annotationType.type
                  : attr.attribType(a.annotationType, env));
        a.type = chk.checkType(a.annotationType.pos(), at, expected);
        if (a.type.isErroneous()) {
            if (typeAnnotation) {
                return new Attribute.TypeCompound(a.type, List.<Pair<MethodSymbol,Attribute>>nil(), null);
            } else {
                return new Attribute.Compound(a.type, List.<Pair<MethodSymbol,Attribute>>nil());
            }
        }
        if ((a.type.tsym.flags() & Flags.ANNOTATION) == 0) {
            log.error(a.annotationType.pos(),
                      "not.annotation.type", a.type.toString());
            if (typeAnnotation) {
                return new Attribute.TypeCompound(a.type, List.<Pair<MethodSymbol,Attribute>>nil(), null);
            } else {
                return new Attribute.Compound(a.type, List.<Pair<MethodSymbol,Attribute>>nil());
            }
        }
        List<JCExpression> args = a.args;
        if (args.length() == 1 && !args.head.hasTag(ASSIGN)) {
            // special case: elided "value=" assumed
            args.head = make.at(args.head.pos).
                Assign(make.Ident(names.value), args.head);
        }
        ListBuffer<Pair<MethodSymbol,Attribute>> buf =
            new ListBuffer<Pair<MethodSymbol,Attribute>>();
        for (List<JCExpression> tl = args; tl.nonEmpty(); tl = tl.tail) {
            JCExpression t = tl.head;
            if (!t.hasTag(ASSIGN)) {
                log.error(t.pos(), "annotation.value.must.be.name.value");
                continue;
            }
            JCAssign assign = (JCAssign)t;
            if (!assign.lhs.hasTag(IDENT)) {
                log.error(t.pos(), "annotation.value.must.be.name.value");
                continue;
            }
            JCIdent left = (JCIdent)assign.lhs;
            Symbol method = rs.resolveQualifiedMethod(left.pos(),
                                                          env,
                                                          a.type,
                                                          left.name,
                                                          List.<Type>nil(),
                                                          null);
            left.sym = method;
            left.type = method.type;
            if (method.owner != a.type.tsym)
                log.error(left.pos(), "no.annotation.member", left.name, a.type);
            Type result = method.type.getReturnType();
            Attribute value = enterAttributeValue(result, assign.rhs, env);
            if (!method.type.isErroneous())
                buf.append(new Pair<MethodSymbol,Attribute>
                           ((MethodSymbol)method, value));
            t.type = result;
        }
        if (typeAnnotation) {
            if (a.attribute == null || !(a.attribute instanceof Attribute.TypeCompound)) {
                // Create a new TypeCompound
                Attribute.TypeCompound tc = new Attribute.TypeCompound(a.type, buf.toList(), new TypeAnnotationPosition());
                a.attribute = tc;
                return tc;
            } else {
                // Use an existing TypeCompound
                return a.attribute;
            }
        } else {
            Attribute.Compound ac = new Attribute.Compound(a.type, buf.toList());
            a.attribute = ac;
            return ac;
        }
    }

    Attribute enterAttributeValue(Type expected,
                                  JCExpression tree,
                                  Env<AttrContext> env) {
        //first, try completing the attribution value sym - if a completion
        //error is thrown, we should recover gracefully, and display an
        //ordinary resolution diagnostic.
        try {
            expected.tsym.complete();
        } catch(CompletionFailure e) {
            log.error(tree.pos(), "cant.resolve", Kinds.kindName(e.sym), e.sym);
            return new Attribute.Error(expected);
        }
        if (expected.isPrimitive() || types.isSameType(expected, syms.stringType)) {
            Type result = attr.attribExpr(tree, env, expected);
            if (result.isErroneous())
                return new Attribute.Error(expected);
            if (result.constValue() == null) {
                log.error(tree.pos(), "attribute.value.must.be.constant");
                return new Attribute.Error(expected);
            }
            result = cfolder.coerce(result, expected);
            return new Attribute.Constant(expected, result.constValue());
        }
        if (expected.tsym == syms.classType.tsym) {
            Type result = attr.attribExpr(tree, env, expected);
            if (result.isErroneous())
                return new Attribute.Error(expected);
            if (TreeInfo.name(tree) != names._class) {
                log.error(tree.pos(), "annotation.value.must.be.class.literal");
                return new Attribute.Error(expected);
            }
            return new Attribute.Class(types,
                                       (((JCFieldAccess) tree).selected).type);
        }
        if ((expected.tsym.flags() & Flags.ANNOTATION) != 0) {
            if (!tree.hasTag(ANNOTATION)) {
                log.error(tree.pos(), "annotation.value.must.be.annotation");
                expected = syms.errorType;
            }
            return enterAnnotation((JCAnnotation)tree, expected, env);
        }
        if (expected.hasTag(ARRAY)) { // should really be isArray()
            if (!tree.hasTag(NEWARRAY)) {
                tree = make.at(tree.pos).
                    NewArray(null, List.<JCExpression>nil(), List.of(tree));
            }
            JCNewArray na = (JCNewArray)tree;
            if (na.elemtype != null) {
                log.error(na.elemtype.pos(), "new.not.allowed.in.annotation");
                return new Attribute.Error(expected);
            }
            ListBuffer<Attribute> buf = new ListBuffer<Attribute>();
            for (List<JCExpression> l = na.elems; l.nonEmpty(); l=l.tail) {
                buf.append(enterAttributeValue(types.elemtype(expected),
                                               l.head,
                                               env));
            }
            na.type = expected;
            return new Attribute.
                Array(expected, buf.toArray(new Attribute[buf.length()]));
        }
        if (expected.hasTag(CLASS) &&
            (expected.tsym.flags() & Flags.ENUM) != 0) {
            attr.attribExpr(tree, env, expected);
            Symbol sym = TreeInfo.symbol(tree);
            if (sym == null ||
                TreeInfo.nonstaticSelect(tree) ||
                sym.kind != Kinds.VAR ||
                (sym.flags() & Flags.ENUM) == 0) {
                log.error(tree.pos(), "enum.annotation.must.be.enum.constant");
                return new Attribute.Error(expected);
            }
            VarSymbol enumerator = (VarSymbol) sym;
            return new Attribute.Enum(expected, enumerator);
        }
        if (!expected.isErroneous())
            log.error(tree.pos(), "annotation.value.not.allowable.type");
        return new Attribute.Error(attr.attribExpr(tree, env, expected));
    }

    /* *********************************
     * Support for repeating annotations
     ***********************************/

    /* Process repeated annotations. This method returns the
     * synthesized container annotation or null IFF all repeating
     * annotation are invalid.  This method reports errors/warnings.
     */
    private <T extends Attribute.Compound> T processRepeatedAnnotations(List<T> annotations,
            AnnotateRepeatedContext<T> ctx,
            Symbol on) {
        T firstOccurrence = annotations.head;
        List<Attribute> repeated = List.nil();
        Type origAnnoType = null;
        Type arrayOfOrigAnnoType = null;
        Type targetContainerType = null;
        MethodSymbol containerValueSymbol = null;

        Assert.check(!annotations.isEmpty() &&
                     !annotations.tail.isEmpty()); // i.e. size() > 1

        int count = 0;
        for (List<T> al = annotations;
             !al.isEmpty();
             al = al.tail)
        {
            count++;

            // There must be more than a single anno in the annotation list
            Assert.check(count > 1 || !al.tail.isEmpty());

            T currentAnno = al.head;

            origAnnoType = currentAnno.type;
            if (arrayOfOrigAnnoType == null) {
                arrayOfOrigAnnoType = types.makeArrayType(origAnnoType);
            }

            // Only report errors if this isn't the first occurrence I.E. count > 1
            boolean reportError = count > 1;
            Type currentContainerType = getContainingType(currentAnno, ctx.pos.get(currentAnno), reportError);
            if (currentContainerType == null) {
                continue;
            }
            // Assert that the target Container is == for all repeated
            // annos of the same annotation type, the types should
            // come from the same Symbol, i.e. be '=='
            Assert.check(targetContainerType == null || currentContainerType == targetContainerType);
            targetContainerType = currentContainerType;

            containerValueSymbol = validateContainer(targetContainerType, origAnnoType, ctx.pos.get(currentAnno));

            if (containerValueSymbol == null) { // Check of CA type failed
                // errors are already reported
                continue;
            }

            repeated = repeated.prepend(currentAnno);
        }

        if (!repeated.isEmpty()) {
            repeated = repeated.reverse();
            TreeMaker m = make.at(ctx.pos.get(firstOccurrence));
            Pair<MethodSymbol, Attribute> p =
                    new Pair<MethodSymbol, Attribute>(containerValueSymbol,
                                                      new Attribute.Array(arrayOfOrigAnnoType, repeated));
            if (ctx.isTypeCompound) {
                /* TODO: the following code would be cleaner:
                Attribute.TypeCompound at = new Attribute.TypeCompound(targetContainerType, List.of(p),
                        ((Attribute.TypeCompound)annotations.head).position);
                JCTypeAnnotation annoTree = m.TypeAnnotation(at);
                at = enterTypeAnnotation(annoTree, targetContainerType, ctx.env);
                */
                // However, we directly construct the TypeCompound to keep the
                // direct relation to the contained TypeCompounds.
                Attribute.TypeCompound at = new Attribute.TypeCompound(targetContainerType, List.of(p),
                        ((Attribute.TypeCompound)annotations.head).position);

                // TODO: annotation applicability checks from below?

                at.setSynthesized(true);

                @SuppressWarnings("unchecked")
                T x = (T) at;
                return x;
            } else {
                Attribute.Compound c = new Attribute.Compound(targetContainerType, List.of(p));
                JCAnnotation annoTree = m.Annotation(c);

                if (!chk.annotationApplicable(annoTree, on))
                    log.error(annoTree.pos(), "invalid.repeatable.annotation.incompatible.target", targetContainerType, origAnnoType);

                if (!chk.validateAnnotationDeferErrors(annoTree))
                    log.error(annoTree.pos(), "duplicate.annotation.invalid.repeated", origAnnoType);

                c = enterAnnotation(annoTree, targetContainerType, ctx.env);
                c.setSynthesized(true);

                @SuppressWarnings("unchecked")
                T x = (T) c;
                return x;
            }
        } else {
            return null; // errors should have been reported elsewhere
        }
    }

    /** Fetches the actual Type that should be the containing annotation. */
    private Type getContainingType(Attribute.Compound currentAnno,
            DiagnosticPosition pos,
            boolean reportError)
    {
        Type origAnnoType = currentAnno.type;
        TypeSymbol origAnnoDecl = origAnnoType.tsym;

        // Fetch the Repeatable annotation from the current
        // annotation's declaration, or null if it has none
        Attribute.Compound ca = origAnnoDecl.attribute(syms.repeatableType.tsym);
        if (ca == null) { // has no Repeatable annotation
            if (reportError)
                log.error(pos, "duplicate.annotation.missing.container", origAnnoType, syms.repeatableType);
            return null;
        }

        return filterSame(extractContainingType(ca, pos, origAnnoDecl),
                          origAnnoType);
    }

    // returns null if t is same as 's', returns 't' otherwise
    private Type filterSame(Type t, Type s) {
        if (t == null || s == null) {
            return t;
        }

        return types.isSameType(t, s) ? null : t;
    }

    /** Extract the actual Type to be used for a containing annotation. */
    private Type extractContainingType(Attribute.Compound ca,
            DiagnosticPosition pos,
            TypeSymbol annoDecl)
    {
        // The next three checks check that the Repeatable annotation
        // on the declaration of the annotation type that is repeating is
        // valid.

        // Repeatable must have at least one element
        if (ca.values.isEmpty()) {
            log.error(pos, "invalid.repeatable.annotation", annoDecl);
            return null;
        }
        Pair<MethodSymbol,Attribute> p = ca.values.head;
        Name name = p.fst.name;
        if (name != names.value) { // should contain only one element, named "value"
            log.error(pos, "invalid.repeatable.annotation", annoDecl);
            return null;
        }
        if (!(p.snd instanceof Attribute.Class)) { // check that the value of "value" is an Attribute.Class
            log.error(pos, "invalid.repeatable.annotation", annoDecl);
            return null;
        }

        return ((Attribute.Class)p.snd).getValue();
    }

    /* Validate that the suggested targetContainerType Type is a valid
     * container type for repeated instances of originalAnnoType
     * annotations. Return null and report errors if this is not the
     * case, return the MethodSymbol of the value element in
     * targetContainerType if it is suitable (this is needed to
     * synthesize the container). */
    private MethodSymbol validateContainer(Type targetContainerType,
                                           Type originalAnnoType,
                                           DiagnosticPosition pos) {
        MethodSymbol containerValueSymbol = null;
        boolean fatalError = false;

        // Validate that there is a (and only 1) value method
        Scope scope = targetContainerType.tsym.members();
        int nr_value_elems = 0;
        boolean error = false;
        for(Symbol elm : scope.getElementsByName(names.value)) {
            nr_value_elems++;

            if (nr_value_elems == 1 &&
                elm.kind == Kinds.MTH) {
                containerValueSymbol = (MethodSymbol)elm;
            } else {
                error = true;
            }
        }
        if (error) {
            log.error(pos,
                      "invalid.repeatable.annotation.multiple.values",
                      targetContainerType,
                      nr_value_elems);
            return null;
        } else if (nr_value_elems == 0) {
            log.error(pos,
                      "invalid.repeatable.annotation.no.value",
                      targetContainerType);
            return null;
        }

        // validate that the 'value' element is a method
        // probably "impossible" to fail this
        if (containerValueSymbol.kind != Kinds.MTH) {
            log.error(pos,
                      "invalid.repeatable.annotation.invalid.value",
                      targetContainerType);
            fatalError = true;
        }

        // validate that the 'value' element has the correct return type
        // i.e. array of original anno
        Type valueRetType = containerValueSymbol.type.getReturnType();
        Type expectedType = types.makeArrayType(originalAnnoType);
        if (!(types.isArray(valueRetType) &&
              types.isSameType(expectedType, valueRetType))) {
            log.error(pos,
                      "invalid.repeatable.annotation.value.return",
                      targetContainerType,
                      valueRetType,
                      expectedType);
            fatalError = true;
        }
        if (error) {
            fatalError = true;
        }

        // The conditions for a valid containing annotation are made
        // in Check.validateRepeatedAnnotaton();

        return fatalError ? null : containerValueSymbol;
    }
}
