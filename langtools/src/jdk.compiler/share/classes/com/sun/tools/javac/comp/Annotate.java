/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Kinds.*;
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
    protected static final Context.Key<Annotate> annotateKey = new Context.Key<>();

    public static Annotate instance(Context context) {
        Annotate instance = context.get(annotateKey);
        if (instance == null)
            instance = new Annotate(context);
        return instance;
    }

    private final Attr attr;
    private final TreeMaker make;
    private final Log log;
    private final Symtab syms;
    private final Names names;
    private final Resolve rs;
    private final Types types;
    private final ConstFold cfolder;
    private final Check chk;
    private final Lint lint;
    private final DeferredLintHandler deferredLintHandler;
    private final Source source;

    private boolean allowTypeAnnos;
    private boolean allowRepeatedAnnos;

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
        source = Source.instance(context);
        lint = Lint.instance(context);
        deferredLintHandler = DeferredLintHandler.instance(context);
        allowRepeatedAnnos = source.allowRepeatedAnnotations();
        allowTypeAnnos = source.allowTypeAnnotations();
    }

/* ********************************************************************
 * Queue maintenance
 *********************************************************************/

    private int enterCount = 0;

    ListBuffer<Worker> q = new ListBuffer<>();
    ListBuffer<Worker> typesQ = new ListBuffer<>();
    ListBuffer<Worker> repeatedQ = new ListBuffer<>();
    ListBuffer<Worker> afterRepeatedQ = new ListBuffer<>();
    ListBuffer<Worker> validateQ = new ListBuffer<>();

    public void earlier(Worker a) {
        q.prepend(a);
    }

    public void normal(Worker a) {
        q.append(a);
    }

    public void typeAnnotation(Worker a) {
        typesQ.append(a);
    }

    public void repeated(Worker a) {
        repeatedQ.append(a);
    }

    public void afterRepeated(Worker a) {
        afterRepeatedQ.append(a);
    }

    public void validate(Worker a) {
        validateQ.append(a);
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

    /** Variant which allows for a delayed flush of annotations.
     * Needed by ClassReader */
    public void enterDoneWithoutFlush() {
        enterCount--;
    }

    public void flush() {
        if (enterCount != 0) return;
        enterCount++;
        try {
            while (q.nonEmpty()) {
                q.next().run();
            }
            while (typesQ.nonEmpty()) {
                typesQ.next().run();
            }
            while (repeatedQ.nonEmpty()) {
                repeatedQ.next().run();
            }
            while (afterRepeatedQ.nonEmpty()) {
                afterRepeatedQ.next().run();
            }
            while (validateQ.nonEmpty()) {
                validateQ.next().run();
            }
        } finally {
            enterCount--;
        }
    }

    /** A client that needs to run during {@link #flush()} registers an worker
     *  into one of the queues defined in this class. The queues are: {@link #earlier(Worker)},
     *  {@link #normal(Worker)}, {@link #typeAnnotation(Worker)}, {@link #repeated(Worker)},
     *  {@link #afterRepeated(Worker)}, {@link #validate(Worker)}.
     *  The {@link Worker#run()} method will called inside the {@link #flush()}
     *  call. Queues are empties in the abovementioned order.
     */
    public interface Worker {
        void run();
        String toString();
    }

    /**
     * This context contains all the information needed to synthesize new
     * annotations trees by the completer for repeating annotations.
     */
    private class AnnotationContext<T extends Attribute.Compound> {
        public final Env<AttrContext> env;
        public final Map<Symbol.TypeSymbol, ListBuffer<T>> annotated;
        public final Map<T, JCDiagnostic.DiagnosticPosition> pos;
        public final boolean isTypeCompound;

        public AnnotationContext(Env<AttrContext> env,
                                 Map<Symbol.TypeSymbol, ListBuffer<T>> annotated,
                                 Map<T, JCDiagnostic.DiagnosticPosition> pos,
                                 boolean isTypeCompound) {
            Assert.checkNonNull(env);
            Assert.checkNonNull(annotated);
            Assert.checkNonNull(pos);

            this.env = env;
            this.annotated = annotated;
            this.pos = pos;
            this.isTypeCompound = isTypeCompound;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("RepeatedContext[");
            for (Map.Entry<Symbol.TypeSymbol, ListBuffer<T>> entry :
                     annotated.entrySet()) {
                sb.append(" ");
                sb.append(entry.getKey());
                sb.append(" = { ");
                sb.append(entry.getValue());
                sb.append(" }");
            }
            sb.append(" ]");
            return sb.toString();
        }
    }

    private static class Placeholder<T extends Attribute.Compound> extends Attribute.Compound {

        private final Annotate.AnnotationContext<T> ctx;
        private final List<T> placeholderFor;
        private final Symbol on;

        public Placeholder(Annotate.AnnotationContext<T> ctx,
                           List<T> placeholderFor, Symbol on) {
            super(on.type, List.<Pair<Symbol.MethodSymbol, Attribute>>nil(),
                  placeholderFor.head.position);
            this.ctx = ctx;
            this.placeholderFor = placeholderFor;
            this.on = on;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return "<placeholder: " + placeholderFor + " on: " + on + ">";
    }

        public List<T> getPlaceholderFor() {
            return placeholderFor;
        }

        public Annotate.AnnotationContext<T> getRepeatedContext() {
            return ctx;
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
        List<Pair<MethodSymbol,Attribute>> elems =
            enterAttributeValues(a, expected, env);
        Attribute.Compound ac = new Attribute.Compound(a.type, elems);
        a.attribute = ac;

        return ac;
    }

    Attribute.TypeCompound enterTypeAnnotation(JCAnnotation a,
                                               Type expected,
                                               Env<AttrContext> env) {
        List<Pair<MethodSymbol,Attribute>> elems =
            enterAttributeValues(a, expected, env);

        if (a.attribute == null || !(a.attribute instanceof Attribute.TypeCompound)) {
            // Create a new TypeCompound

            Attribute.TypeCompound tc =
                new Attribute.TypeCompound(a.type, elems,
                // TODO: Eventually, we will get rid of this use of
                // unknown, because we'll get a position from
                // MemberEnter (task 8027262).
                                           TypeAnnotationPosition.unknown);
            a.attribute = tc;
            return tc;
        } else {
            // Use an existing TypeCompound
            return (Attribute.TypeCompound)a.attribute;
        }
    }

    private List<Pair<MethodSymbol,Attribute>>
            enterAttributeValues(JCAnnotation a,
                                 Type expected,
                                 Env<AttrContext> env) {
        // The annotation might have had its type attributed (but not
        // checked) by attr.attribAnnotationTypes during MemberEnter,
        // in which case we do not need to do it again.
        Type at = (a.annotationType.type != null ? a.annotationType.type
                  : attr.attribType(a.annotationType, env));
        a.type = chk.checkType(a.annotationType.pos(), at, expected);
        boolean isError = a.type.isErroneous();
        if ((a.type.tsym.flags() & Flags.ANNOTATION) == 0 && !isError) {
            log.error(a.annotationType.pos(),
                      "not.annotation.type", a.type.toString());
            isError = true;
        }
        List<JCExpression> args = a.args;
        if (args.length() == 1 && !args.head.hasTag(ASSIGN)) {
            // special case: elided "value=" assumed
            args.head = make.at(args.head.pos).
                Assign(make.Ident(names.value), args.head);
        }
        ListBuffer<Pair<MethodSymbol,Attribute>> buf =
            new ListBuffer<>();
        for (List<JCExpression> tl = args; tl.nonEmpty(); tl = tl.tail) {
            JCExpression t = tl.head;
            if (!t.hasTag(ASSIGN)) {
                log.error(t.pos(), "annotation.value.must.be.name.value");
                enterAttributeValue(t.type = syms.errType, t, env);
                continue;
            }
            JCAssign assign = (JCAssign)t;
            if (!assign.lhs.hasTag(IDENT)) {
                log.error(t.pos(), "annotation.value.must.be.name.value");
                enterAttributeValue(t.type = syms.errType, t, env);
                continue;
            }
            JCIdent left = (JCIdent)assign.lhs;
            Symbol method = rs.resolveQualifiedMethod(assign.rhs.pos(),
                                                          env,
                                                          a.type,
                                                          left.name,
                                                          List.<Type>nil(),
                                                          null);
            left.sym = method;
            left.type = method.type;
            if (method.owner != a.type.tsym && !isError)
                log.error(left.pos(), "no.annotation.member", left.name, a.type);
            Type result = method.type.getReturnType();
            Attribute value = enterAttributeValue(result, assign.rhs, env);
            if (!method.type.isErroneous())
                buf.append(new Pair<>((MethodSymbol)method, value));
            t.type = result;
        }
        return buf.toList();
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
            expected = syms.errType;
        }
        if (expected.hasTag(ARRAY)) {
            if (!tree.hasTag(NEWARRAY)) {
                tree = make.at(tree.pos).
                    NewArray(null, List.<JCExpression>nil(), List.of(tree));
            }
            JCNewArray na = (JCNewArray)tree;
            if (na.elemtype != null) {
                log.error(na.elemtype.pos(), "new.not.allowed.in.annotation");
            }
            ListBuffer<Attribute> buf = new ListBuffer<>();
            for (List<JCExpression> l = na.elems; l.nonEmpty(); l=l.tail) {
                buf.append(enterAttributeValue(types.elemtype(expected),
                                               l.head,
                                               env));
            }
            na.type = expected;
            return new Attribute.
                Array(expected, buf.toArray(new Attribute[buf.length()]));
        }
        if (tree.hasTag(NEWARRAY)) { //error recovery
            if (!expected.isErroneous())
                log.error(tree.pos(), "annotation.value.not.allowable.type");
            JCNewArray na = (JCNewArray)tree;
            if (na.elemtype != null) {
                log.error(na.elemtype.pos(), "new.not.allowed.in.annotation");
            }
            for (List<JCExpression> l = na.elems; l.nonEmpty(); l=l.tail) {
                enterAttributeValue(syms.errType,
                                    l.head,
                                    env);
            }
            return new Attribute.Error(syms.errType);
        }
        if ((expected.tsym.flags() & Flags.ANNOTATION) != 0) {
            if (tree.hasTag(ANNOTATION)) {
                return enterAnnotation((JCAnnotation)tree, expected, env);
            } else {
                log.error(tree.pos(), "annotation.value.must.be.annotation");
                expected = syms.errType;
            }
        }
        if (tree.hasTag(ANNOTATION)) { //error recovery
            if (!expected.isErroneous())
                log.error(tree.pos(), "annotation.not.valid.for.type", expected);
            enterAnnotation((JCAnnotation)tree, syms.errType, env);
            return new Attribute.Error(((JCAnnotation)tree).annotationType.type);
        }
        if (expected.isPrimitive() ||
            (types.isSameType(expected, syms.stringType) && !expected.hasTag(TypeTag.ERROR))) {
            Type result = attr.attribExpr(tree, env, expected);
            if (result.isErroneous())
                return new Attribute.Error(result.getOriginalType());
            if (result.constValue() == null) {
                log.error(tree.pos(), "attribute.value.must.be.constant");
                return new Attribute.Error(expected);
            }
            result = cfolder.coerce(result, expected);
            return new Attribute.Constant(expected, result.constValue());
        }
        if (expected.tsym == syms.classType.tsym) {
            Type result = attr.attribExpr(tree, env, expected);
            if (result.isErroneous()) {
                // Does it look like an unresolved class literal?
                if (TreeInfo.name(tree) == names._class &&
                    ((JCFieldAccess) tree).selected.type.isErroneous()) {
                    Name n = (((JCFieldAccess) tree).selected).type.tsym.flatName();
                    return new Attribute.UnresolvedClass(expected,
                            types.createErrorType(n,
                                    syms.unknownSymbol, syms.classType));
                } else {
                    return new Attribute.Error(result.getOriginalType());
                }
            }

            // Class literals look like field accesses of a field named class
            // at the tree level
            if (TreeInfo.name(tree) != names._class) {
                log.error(tree.pos(), "annotation.value.must.be.class.literal");
                return new Attribute.Error(syms.errType);
            }
            return new Attribute.Class(types,
                                       (((JCFieldAccess) tree).selected).type);
        }
        if (expected.hasTag(CLASS) &&
            (expected.tsym.flags() & Flags.ENUM) != 0) {
            Type result = attr.attribExpr(tree, env, expected);
            Symbol sym = TreeInfo.symbol(tree);
            if (sym == null ||
                TreeInfo.nonstaticSelect(tree) ||
                sym.kind != Kinds.VAR ||
                (sym.flags() & Flags.ENUM) == 0) {
                log.error(tree.pos(), "enum.annotation.must.be.enum.constant");
                return new Attribute.Error(result.getOriginalType());
            }
            VarSymbol enumerator = (VarSymbol) sym;
            return new Attribute.Enum(expected, enumerator);
        }
        //error recovery:
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
            AnnotationContext<T> ctx,
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
        for(Symbol elm : scope.getSymbolsByName(names.value)) {
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

    private <T extends Attribute.Compound> AnnotationContext<T>
            prepareEnterAnnotations(List<JCAnnotation> annotations,
                                    Env<AttrContext> env,
                                    Symbol sym,
                                    AttributeCreator<T> creator,
                                    boolean isTypeCompound) {
        Map<TypeSymbol, ListBuffer<T>> annotated = new LinkedHashMap<>();
        Map<T, DiagnosticPosition> pos = new HashMap<>();

        for (List<JCAnnotation> al = annotations; !al.isEmpty(); al = al.tail) {
            JCAnnotation a = al.head;
            T c = creator.create(a, syms.annotationType, env);

            Assert.checkNonNull(c, "Failed to create annotation");

            if (annotated.containsKey(a.type.tsym)) {
                if (!allowRepeatedAnnos) {
                    log.error(a.pos(), "repeatable.annotations.not.supported.in.source");
                    allowRepeatedAnnos = true;
                }
                ListBuffer<T> l = annotated.get(a.type.tsym);
                l = l.append(c);
                annotated.put(a.type.tsym, l);
                pos.put(c, a.pos());
            } else {
                annotated.put(a.type.tsym, ListBuffer.of(c));
                pos.put(c, a.pos());
            }

            // Note: @Deprecated has no effect on local variables and parameters
            if (!c.type.isErroneous()
                && sym.owner.kind != MTH
                && types.isSameType(c.type, syms.deprecatedType)) {
                sym.flags_field |= Flags.DEPRECATED;
            }
        }

        return new AnnotationContext<>(env, annotated, pos,
                                             isTypeCompound);
    }

    // Gather up annotations into a map from type symbols to lists of
    // Compound attributes, then continue on with repeating
    // annotations processing
    private <T extends Attribute.Compound>
            void attachAttributesLater(final List<JCAnnotation> annotations,
                                       final Env<AttrContext> env,
                                       final Symbol sym,
                                       final boolean isTypeCompound,
                                       final AttributeCreator<T> creator,
                                       final AttributeAttacher<T> attacher) {
        final AnnotationContext<T> ctx =
            prepareEnterAnnotations(annotations, env, sym, creator, isTypeCompound);
        final Map<Symbol.TypeSymbol, ListBuffer<T>> annotated =
            ctx.annotated;
        boolean hasRepeated = false;

        List<T> buf = List.<T>nil();
        for (ListBuffer<T> lb : annotated.values()) {
            if (lb.size() == 1) {
                buf = buf.prepend(lb.first());
            } else {
                @SuppressWarnings("unchecked")
                T res = (T) new Placeholder<>(ctx, lb.toList(), sym);
                buf = buf.prepend(res);
                hasRepeated = true;
            }
        }

        final List<T> attrs = buf.reverse();

        if (!isTypeCompound) {
            // Attach declaration attributes early, so
            // that @Repeatable and other annotations get attached.
            // Since the attacher uses setDeclarationAttributes, this
            // will be overwritten later.
            attacher.attach(sym, attrs);
        }
        if (hasRepeated) {
            repeated(new Annotate.Worker() {
                    @Override
                    public String toString() {
                        return "repeated annotation pass of: " + sym + " in: " + sym.owner;
                    }

                    @Override
                    public void run() {
                        JavaFileObject oldSource =
                            log.useSource(env.toplevel.sourcefile);
                        try {
                            attacher.attach(sym, replacePlaceholders(attrs, ctx, sym));
                        } finally {
                            log.useSource(oldSource);
                        }
                    }
                });
        } else {
            attacher.attach(sym, attrs);
        }
    }

    private interface AttributeAttacher<T extends Attribute.Compound> {
        public void attach(Symbol sym, List<T> attrs);
    }

    private final AttributeAttacher<Attribute.Compound> declAnnotationsAttacher =
        new AttributeAttacher<Attribute.Compound>() {
            @Override
            public void attach(Symbol sym, List<Attribute.Compound> attrs) {
                sym.resetAnnotations();
                sym.setDeclarationAttributes(attrs);
            }
        };

    private final AttributeAttacher<Attribute.TypeCompound> typeAnnotationsAttacher =
        new AttributeAttacher<Attribute.TypeCompound>() {
            @Override
            public void attach(Symbol sym, List<Attribute.TypeCompound> attrs) {
                sym.appendUniqueTypeAttributes(attrs);
            }
        };

    private <T extends Attribute.Compound> List<T>
            replacePlaceholders(List<T> buf,
                                Annotate.AnnotationContext<T> ctx,
                                Symbol sym) {
        List<T> result = List.nil();
        for (T a : buf) {
            if (a instanceof Placeholder) {
                @SuppressWarnings("unchecked")
                    T replacement = replaceOne((Placeholder<T>) a, ctx, sym);

                if (null != replacement) {
                    result = result.prepend(replacement);
                }
            } else {
                result = result.prepend(a);
            }
        }

        return result.reverse();
    }

    private <T extends Attribute.Compound> T replaceOne(Placeholder<T> placeholder,
                                                        Annotate.AnnotationContext<T> ctx,
                                                        Symbol sym) {
        // Process repeated annotations
        T validRepeated =
            processRepeatedAnnotations(placeholder.getPlaceholderFor(),
                                       ctx, sym);

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

/* ********************************************************************
 * Annotation processing
 *********************************************************************/

    /** Queue annotations for later processing. */
    void annotateLater(final List<JCAnnotation> annotations,
                       final Env<AttrContext> localEnv,
                       final Symbol s,
                       final DiagnosticPosition deferPos) {
        if (annotations.isEmpty()) {
            return;
        }
        if (s.kind != PCK) {
            s.resetAnnotations(); // mark Annotations as incomplete for now
        }
        normal(new Annotate.Worker() {
                @Override
                public String toString() {
                    return "annotate " + annotations + " onto " + s + " in " + s.owner;
                }

                @Override
                public void run() {
                    Assert.check(s.kind == PCK || s.annotationsPendingCompletion());
                    JavaFileObject prev = log.useSource(localEnv.toplevel.sourcefile);
                    DiagnosticPosition prevLintPos =
                        deferPos != null
                        ? deferredLintHandler.setPos(deferPos)
                        : deferredLintHandler.immediate();
                    Lint prevLint = deferPos != null ? null : chk.setLint(lint);
                    try {
                        if (s.hasAnnotations() &&
                            annotations.nonEmpty())
                            log.error(annotations.head.pos,
                                      "already.annotated",
                                      kindName(s), s);
                        actualEnterAnnotations(annotations, localEnv, s);
                    } finally {
                        if (prevLint != null)
                            chk.setLint(prevLint);
                        deferredLintHandler.setPos(prevLintPos);
                        log.useSource(prev);
                    }
                }
            });

        validate(new Annotate.Worker() { //validate annotations
            @Override
            public void run() {
                JavaFileObject prev = log.useSource(localEnv.toplevel.sourcefile);
                try {
                    chk.validateAnnotations(annotations, s);
                } finally {
                    log.useSource(prev);
                }
            }
        });
    }

    private interface AttributeCreator<T extends Attribute.Compound> {
        public T create(JCAnnotation a, Type expected, Env<AttrContext> env);
    }

    // TODO: When SE8 features can be used, these can go away and be
    // replaced by method refs.
    private final AttributeCreator<Attribute.Compound> enterAnnotationsCreator =
        new AttributeCreator<Attribute.Compound>() {
        @Override
        public Attribute.Compound create(JCAnnotation a,
                                         Type expected,
                                         Env<AttrContext> env) {
            return enterAnnotation(a, syms.annotationType, env);
        }
    };
    private final AttributeCreator<Attribute.TypeCompound> enterTypeAnnotationsCreator =
        new AttributeCreator<Attribute.TypeCompound>() {
        @Override
        public Attribute.TypeCompound create(JCAnnotation a,
                                             Type expected,
                                             Env<AttrContext> env) {
            return enterTypeAnnotation(a, syms.annotationType, env);
        }
    };

    /** Enter a set of annotations. */
    private void actualEnterAnnotations(List<JCAnnotation> annotations,
                                        Env<AttrContext> env,
                                        Symbol s) {
        Assert.checkNonNull(s, "Symbol argument to actualEnterAnnotations is null");
        attachAttributesLater(annotations, env, s, false,
                              enterAnnotationsCreator,
                              declAnnotationsAttacher);
    }

    /*
     * If the symbol is non-null, attach the type annotation to it.
     */
    private void actualEnterTypeAnnotations(final List<JCAnnotation> annotations,
                                            final Env<AttrContext> env,
                                            final Symbol s,
                                            final DiagnosticPosition deferPos) {
        Assert.checkNonNull(s, "Symbol argument to actualEnterTypeAnnotations is nul/");
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        DiagnosticPosition prevLintPos = null;

        if (deferPos != null) {
            prevLintPos = deferredLintHandler.setPos(deferPos);
        }
        try {
            attachAttributesLater(annotations, env, s, true,
                                  enterTypeAnnotationsCreator,
                                  typeAnnotationsAttacher);
        } finally {
            if (prevLintPos != null)
                deferredLintHandler.setPos(prevLintPos);
            log.useSource(prev);
        }
    }

    public void annotateTypeLater(final JCTree tree,
                                  final Env<AttrContext> env,
                                  final Symbol sym,
                                  final DiagnosticPosition deferPos) {
        Assert.checkNonNull(sym);
        normal(new Annotate.Worker() {
                @Override
                public String toString() {
                    return "type annotate " + tree + " onto " + sym + " in " + sym.owner;
                }
                @Override
                public void run() {
                    tree.accept(new TypeAnnotate(env, sym, deferPos));
                }
            });
    }

    /**
     * We need to use a TreeScanner, because it is not enough to visit the top-level
     * annotations. We also need to visit type arguments, etc.
     */
    private class TypeAnnotate extends TreeScanner {
        private final Env<AttrContext> env;
        private final Symbol sym;
        private DiagnosticPosition deferPos;

        public TypeAnnotate(final Env<AttrContext> env,
                            final Symbol sym,
                            final DiagnosticPosition deferPos) {

            this.env = env;
            this.sym = sym;
            this.deferPos = deferPos;
        }

        @Override
        public void visitAnnotatedType(final JCAnnotatedType tree) {
            actualEnterTypeAnnotations(tree.annotations, env, sym, deferPos);
            super.visitAnnotatedType(tree);
        }

        @Override
        public void visitTypeParameter(final JCTypeParameter tree) {
            actualEnterTypeAnnotations(tree.annotations, env, sym, deferPos);
            super.visitTypeParameter(tree);
        }

        @Override
        public void visitNewArray(final JCNewArray tree) {
            actualEnterTypeAnnotations(tree.annotations, env, sym, deferPos);
            for (List<JCAnnotation> dimAnnos : tree.dimAnnotations)
                actualEnterTypeAnnotations(dimAnnos, env, sym, deferPos);
            super.visitNewArray(tree);
        }

        @Override
        public void visitMethodDef(final JCMethodDecl tree) {
            scan(tree.mods);
            scan(tree.restype);
            scan(tree.typarams);
            scan(tree.recvparam);
            scan(tree.params);
            scan(tree.thrown);
            scan(tree.defaultValue);
            // Do not annotate the body, just the signature.
            // scan(tree.body);
        }

        @Override
        public void visitVarDef(final JCVariableDecl tree) {
            DiagnosticPosition prevPos = deferPos;
            deferPos = tree.pos();
            try {
                if (sym != null && sym.kind == Kinds.VAR) {
                    // Don't visit a parameter once when the sym is the method
                    // and once when the sym is the parameter.
                    scan(tree.mods);
                    scan(tree.vartype);
                }
                scan(tree.init);
            } finally {
                deferPos = prevPos;
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            // We can only hit a classdef if it is declared within
            // a method. Ignore it - the class will be visited
            // separately later.
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            if (tree.def == null) {
                // For an anonymous class instantiation the class
                // will be visited separately.
                super.visitNewClass(tree);
            }
        }
    }
}
