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

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.TypeAnnotationPosition.*;
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

        @Override
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

    /**
     * Enter (and attribute) a single regular annotation, returning
     * its Attribute.  We give these annotations a position in case we
     * end up creating a type annotation from using toTypeCompound.
     *
     * In some cases, namely on annotations that can never be type
     * annotations (like package annotations), the position can be
     * null; however, if this annotation is in a place where it might
     * possibly denote a type annotation, it will have a non-null
     * position.
     *
     * @param a Annotation to attribute.
     * @param expected Expected annotation type.
     * @param env The environment.
     * @param position The type annotation position this will have if
     *                 it's converted to a type annotation.
     * @return The Attribute.Compound representing this annotation.
     */
    Attribute.Compound enterAnnotation(JCAnnotation a,
                                       Type expected,
                                       Env<AttrContext> env,
                                       TypeAnnotationPosition position) {
        List<Pair<MethodSymbol,Attribute>> buf =
            enterAttributeValues(a, expected, env, position);
        Attribute.Compound ac =
            new Attribute.Compound(a.type, buf, position);
        a.attribute = ac;

        return ac;
    }

    /**
     * Enter (and attribute) a single type annotation, returning its
     * Attribute.
     *
     * Things are a bit complicated, though, because a single source
     * annotation (JCAnnotation) might give rise to several bytecode
     * annotations (Attribute.TypeCompound), but we can only associate
     * a source annotation with one bytecode annotation.  Thus, we
     * have to distinguish between the "primary" (which will be stored
     * to the JCAnnotation) and "secondary" (which won't) annotations.
     * The primary place this gets used is for anonymous classes.
     *
     * The annotations we generate for the new instruction are the
     * primary, and the ones we generate for the class are the
     * secondaries.  (Note: this choice is arbitrary, and it does not
     * appear to cause any problems if these roles are reversed)
     *
     * @param a The annotation to attribute.
     * @param expected The expected annotation type.
     * @param env The environment.
     * @param position The type annotation position to give the type
     *                 annotation.
     * @param secondaryAttr Whether or not this is a secondary (ie
     *                      will ignore the .attribute field on a).
     * @return The Attribute.TypeCompound representing the annotation.
     */
    Attribute.TypeCompound enterTypeAnnotation(JCAnnotation a,
                                               Type expected,
                                               Env<AttrContext> env,
                                               TypeAnnotationPosition position,
                                               boolean secondaryAttr) {
        List<Pair<MethodSymbol,Attribute>> buf =
            enterAttributeValues(a, expected, env, position);

        // Secondary attr means we do not set the .attribute field of
        // the JCAnnotation, nor do we pay attention to it.
        if (!secondaryAttr || a.attribute == null ||
            !(a.attribute instanceof Attribute.TypeCompound)) {
            // Create a new TypeCompound
            Attribute.TypeCompound tc =
                new Attribute.TypeCompound(a.type, buf, position);
            a.attribute = tc;
            return tc;
        } else {
            // Use an existing TypeCompound
            return (Attribute.TypeCompound)a.attribute;
        }
    }

    // Attribute all the annotation's values.
    private List<Pair<MethodSymbol,Attribute>>
            enterAttributeValues(JCAnnotation a,
                                 Type expected,
                                 Env<AttrContext> env,
                                 TypeAnnotationPosition position) {
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
                enterAttributeValue(t.type = syms.errType, t, env, position);
                continue;
            }
            JCAssign assign = (JCAssign)t;
            if (!assign.lhs.hasTag(IDENT)) {
                log.error(t.pos(), "annotation.value.must.be.name.value");
                enterAttributeValue(t.type = syms.errType, t, env, position);
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
            Attribute value = enterAttributeValue(result, assign.rhs, env, position);
            if (!method.type.isErroneous())
                buf.append(new Pair<>((MethodSymbol)method, value));
            t.type = result;
        }
        return buf.toList();
    }

    Attribute enterAttributeValue(Type expected,
                                  JCExpression tree,
                                  Env<AttrContext> env) {
        return enterAttributeValue(expected, tree, env, null);
    }

    Attribute enterAttributeValue(Type expected,
                                  JCExpression tree,
                                  Env<AttrContext> env,
                                  TypeAnnotationPosition position) {
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
                                               l.head, env, position));
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
                enterAttributeValue(syms.errType, l.head, env, position);
            }
            return new Attribute.Error(syms.errType);
        }
        if ((expected.tsym.flags() & Flags.ANNOTATION) != 0) {
            if (tree.hasTag(ANNOTATION)) {
                return enterAnnotation((JCAnnotation)tree, expected, env, position);
            } else {
                log.error(tree.pos(), "annotation.value.must.be.annotation");
                expected = syms.errType;
            }
        }
        if (tree.hasTag(ANNOTATION)) { //error recovery
            if (!expected.isErroneous())
                log.error(tree.pos(), "annotation.not.valid.for.type", expected);
            enterAnnotation((JCAnnotation)tree, syms.errType, env, position);
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
    private <T extends Attribute.Compound> T processRepeatedAnnotations(
            List<T> annotations,
            AnnotationContext<T> ctx,
            Symbol on,
            TypeAnnotationPosition position) {
        T firstOccurrence = annotations.head;
        List<Attribute> repeated = List.nil();
        Type origAnnoType = null;
        Type arrayOfOrigAnnoType = null;
        Type targetContainerType = null;
        MethodSymbol containerValueSymbol = null;

        Assert.check(!annotations.isEmpty() &&
                     !annotations.tail.isEmpty()); // i.e. size() > 1

        int count = 0;
        for (List<T> al = annotations; !al.isEmpty(); al = al.tail) {
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
                Attribute.TypeCompound at = new Attribute.TypeCompound(targetContainerType, List.of(p), position);
                at.setSynthesized(true);

                @SuppressWarnings("unchecked")
                T x = (T) at;
                return x;
            } else {
                Attribute.Compound c = new Attribute.Compound(targetContainerType,
                                                              List.of(p),
                                                              position);
                JCAnnotation annoTree = m.Annotation(c);

                if (!chk.annotationApplicable(annoTree, on))
                    log.error(annoTree.pos(), "invalid.repeatable.annotation.incompatible.target", targetContainerType, origAnnoType);

                if (!chk.validateAnnotationDeferErrors(annoTree))
                    log.error(annoTree.pos(), "duplicate.annotation.invalid.repeated", origAnnoType);

                c = enterAnnotation(annoTree, targetContainerType, ctx.env, position);
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

    /**
     * First step of repeating annotations handling: go through a list
     * of annotations, and gather up all the repeated ones into a map,
     * which we use to build an AnnotationContext.
     *
     * Because we do this, we need to get all the annotations for a
     * given AST node at once (ie. if we have "@A @B @A int foo;", we
     * have to get "@A @B @A" at the same time).
     *
     * @param annotations The annotations being attached.
     * @param env The environment.
     * @param sym The symbol to which the annotations will be attached.
     * @param creator The attribute creator used to enter the annotations.
     * @param position The position for any type annotations.
     * @return The AnnotaionContext for use in the next phase.
     */
    private <T extends Attribute.Compound> AnnotationContext<T>
            prepareEnterAnnotations(List<JCAnnotation> annotations,
                                    Env<AttrContext> env,
                                    Symbol sym,
                                    AttributeCreator<T> creator,
                                    TypeAnnotationPosition position) {
        Map<TypeSymbol, ListBuffer<T>> annotated = new LinkedHashMap<>();
        Map<T, DiagnosticPosition> pos = new HashMap<>();

        // Go through the annotation list, build up a map from
        // annotation types to lists of annotations.
        for (List<JCAnnotation> al = annotations; !al.isEmpty(); al = al.tail) {
            JCAnnotation a = al.head;
            T c = creator.create(a, syms.annotationType, env, position);

            Assert.checkNonNull(c, "Failed to create annotation");

            if (annotated.containsKey(a.type.tsym)) {
                // Make sure we even allow repeating annotations.
                if (!allowRepeatedAnnos) {
                    log.error(a.pos(), "repeatable.annotations.not.supported.in.source");
                    allowRepeatedAnnos = true;
                }
                // Append the annotation to the list for this kind of
                // annotation.
                ListBuffer<T> l = annotated.get(a.type.tsym);
                l = l.append(c);
                annotated.put(a.type.tsym, l);
                pos.put(c, a.pos());
            } else {
                // We are seeing the first annotation of this kind.
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
                                       creator.createsTypeCompound());
    }

    /**
     * Entry-point for repeating annotations handling.  At this point,
     * we should know the type annotation position, and we should have
     * all the annotations for a given source location.
     *
     * We first gather up all the repeated annotations and build an
     * AnnotationContext.  Then create Placeholder's for any repeated
     * annotations and send them further down the pipeline.
     *
     * Something to keep in mind here is that even if we are handling
     * "declaration" annotations, it is still possible that those
     * might turn into type annotations (consider "@A @B int foo;",
     * for example).
     *
     * The pipeline uses a sort of continuation-passing like style,
     * with creator and attacher.  This allows two things.  First, it
     * allows for a single pipeline for repeating annotations,
     * regardless of what eventually happens to the annotations.
     * Second, it allows us to avoid some unsafe casts we would
     * otherwise have to make.
     *
     * @param annotations The annotations to handle.
     * @param env The environment.
     * @param sym The symbol to which to attach annotations.
     * @param position The position for type annotations.
     * @param creator The creator to use to enter annotations.
     * @param attacher The attacher to use to attach annotations.
     */
    private <T extends Attribute.Compound>
            void attachAttributesLater(final List<JCAnnotation> annotations,
                                       final Env<AttrContext> env,
                                       final Symbol sym,
                                       final TypeAnnotationPosition position,
                                       final AttributeCreator<T> creator,
                                       final AttributeAttacher<T> attacher) {
        // First, gather up all the repeated annotations.
        final AnnotationContext<T> ctx =
            prepareEnterAnnotations(annotations, env, sym, creator, position);
        final Map<Symbol.TypeSymbol, ListBuffer<T>> annotated =
            ctx.annotated;
        boolean hasRepeated = false;

        // Now run through all the annotation types in the
        // AnnotationContext.  If there are any that have more than
        // one entry, then we set up a Placeholder for them.
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

        if (!creator.createsTypeCompound()) {
            // Attach declaration attributes early, so
            // that @Repeatable and other annotations get attached.
            // Since the attacher uses setDeclarationAttributes, this
            // will be overwritten later.
            //
            // The root cause of this is that annotations are
            // themselves defined using annotations.  However, it is
            // never the case that a type annotation affects the
            // definition of an annotation, so we don't have to do
            // this.
            //
            // We really should find a better way to do this.
            @SuppressWarnings("unchecked")
            List<Attribute.Compound> tempattrs = (List<Attribute.Compound>) attrs;
            sym.setDeclarationAttributes(tempattrs);
        }

        if (hasRepeated) {
            // If we have repeated annotations, then we go to the next
            // pipeline step, which replaces all the placeholders.
            replacePlaceholdersAndAttach(attrs, ctx, env, sym, attacher);
        } else {
            // If we don't have repeated annotations, then we still
            // have to run the annotations through the rest of the
            // pipeline.
            //
            // For type annotations, we might have error-reporting to
            // do, and the attacher might end up attaching the
            // annotation to the symbol's owner as well.
            //
            // For regular annotations, we might have a
            // classifyingAttacher, in which case we have to pull the
            // annotations off the symbol, classify them, and then put
            // them in the right place.
            attachAttributesAfterRepeated(attrs, env, attacher);
        }
    }

    /**
     * Next pipeline step for repeated annotations: replate all the
     * placeholders, and then send the result further down the pipe.
     *
     * @param attrs The Attributes representing the annotations.
     * @param ctx The AnnotationContext being used.
     * @param env The environment.
     * @param sym The symbol to which to attach annotations.
     * @param attacher The attacher to use to attach annotations.
     */
    private <T extends Attribute.Compound>
            void replacePlaceholdersAndAttach(final List<T> attrs,
                                              final AnnotationContext<T> ctx,
                                              final Env<AttrContext> env,
                                              final Symbol sym,
                                              final AttributeAttacher<T> attacher) {
        // Set up a Worker.
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
                        // Replace placeholders
                        final List<T> replaced =
                            replacePlaceholders(attrs, ctx, sym);
                        // Then send the result to the final pipeline stage.
                        attachAttributesAfterRepeated(replaced, env, attacher);
                        } finally {
                            log.useSource(oldSource);
                        }
                    }
                });
    }

    /**
     * Final pipeline stage.  Simply use the attacher to deal with the
     * annotations however we want to deal with them.  Note that
     * attachers may do a number of things, like attach annotations to
     * other symbols (as is the case with some type annotations, which
     * get attached to their symbol's owner as well), report errors,
     * or even create copies (as is the case with classifyingAttacher)
     *
     * At this point, we have to be able to guarantee that we don't
     * see any Placeholders.
     *
     * @param attrs The Attributes representing the annotations.
     * @param env The environment.
     * @param attacher The attacher we use to finish handling the
     * annotations.
     */
    private <T extends Attribute.Compound>
            void attachAttributesAfterRepeated(final List<T> attrs,
                                               final Env<AttrContext> env,
                                               final AttributeAttacher<T> attacher) {
        // Set up a Worker that just calls the attacher.
        afterRepeated(new Worker() {
                @Override
                public String toString() {
                    return "attach pass for: " + attrs;
    }

            @Override
                public void run() {
                    JavaFileObject oldSource =
                        log.useSource(env.toplevel.sourcefile);
                    try {
                        attacher.attach(attrs);
                    } finally {
                        log.useSource(oldSource);
                    }
                }
            });
    }

    /**
     * AttributeAttachers are similar to continuations.  That contains
     * the behavior of the final stage of the annotation pipeline,
     * when we've creted Attributes (which have a type annotation
     * position), and we've dealt with repeating annotations.  Once we
     * have done all that, we simply hand off the list of attributes
     * to the attacher and let it do its work.
     *
     * If we didn't have the multiple deferred steps, we could
     * implement this by returning a list of Attributes from a
     * top-level method representing the entire repeating annotations
     * pipeline.  Since we have to handle annotations in multiple
     * steps, we can't do that.  Therefore, in this light, we can
     * think of an attacher as being essentially a return
     * continuation.
     *
     * We also have ways to "build" more complex attachers out of
     * simple ones, such as classifyingAttacher.  This allows us
     * considerable flexibility in how we deal with annotations at the
     * end of the pipeline (which is necessary, because there are a
     * lot of cases).
     */
    public interface AttributeAttacher<T extends Attribute.Compound> {
        public void attach(List<T> attrs);
    }

    /**
     * An interface for describing error reporting behaviors for
     * type-only annotations.  Sometimes, we need to report errors if
     * any annotations wind up being type-only annotations (the best
     * example being for illegal scoping).  But at the point where we
     * know this, we don't always know if a given annotation will be a
     * type-only annotation, a regular annotation, or both.  So we
     * have to defer the error-reporting until we do know.
     */
    public interface Reporter<T extends Attribute.Compound> {
        public void report(List<T> attrs);
    }

    public enum AnnotationKind { DECLARATION, TYPE, BOTH }

    public Attribute[] getTargetTypes(Attribute.Compound a) {
        Attribute.Compound atTarget =
            a.type.tsym.attribute(syms.annotationTargetType.tsym);
        if (atTarget == null) {
            return null;
        }
        Attribute atValue = atTarget.member(names.value);
        Assert.check(atValue instanceof Attribute.Array);
        return ((Attribute.Array) atValue).values;
    }

    public boolean hasTypeUseTarget(Attribute.Compound a,
                                    boolean isTypeParameter) {
        Attribute[] targets = getTargetTypes(a);
        if (targets != null) {
            for (Attribute app : targets) {
                Assert.check(app instanceof Attribute.Enum);
                Attribute.Enum e = (Attribute.Enum) app;
                if (e.value.name == names.TYPE_USE ||
                    (isTypeParameter && e.value.name == names.TYPE_PARAMETER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determine whether an annotation is a declaration annotation,
     * a type annotation, or both.
     */
    public AnnotationKind annotationKind(Attribute.Compound a, Symbol s) {
        Attribute[] targets = getTargetTypes(a);
        if (targets == null) {
            return AnnotationKind.DECLARATION;
        }
        boolean isDecl = false, isType = false;
        for (Attribute app : targets) {
            Assert.check(app instanceof Attribute.Enum);
            Attribute.Enum e = (Attribute.Enum) app;
            if (e.value.name == names.TYPE) {
                if (s.kind == Kinds.TYP) {
                    ElementKind skind = s.getKind();
                    // The only symbols we should see here correspond
                    // to definitions.
                    Assert.check(skind == ElementKind.ANNOTATION_TYPE ||
                                 skind == ElementKind.INTERFACE ||
                                 skind == ElementKind.ENUM ||
                                 skind == ElementKind.CLASS);
                    isDecl = true;
                }
            } else if (e.value.name == names.FIELD) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind != Kinds.MTH)
                    isDecl = true;
            } else if (e.value.name == names.METHOD) {
                if (s.kind == Kinds.MTH &&
                        !s.isConstructor())
                    isDecl = true;
            } else if (e.value.name == names.PARAMETER) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind == Kinds.MTH &&
                        (s.flags() & Flags.PARAMETER) != 0)
                    isDecl = true;
            } else if (e.value.name == names.CONSTRUCTOR) {
                if (s.kind == Kinds.MTH &&
                        s.isConstructor())
                    isDecl = true;
            } else if (e.value.name == names.LOCAL_VARIABLE) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind == Kinds.MTH &&
                        (s.flags() & Flags.PARAMETER) == 0)
                    isDecl = true;
            } else if (e.value.name == names.ANNOTATION_TYPE) {
                if (s.kind == Kinds.TYP &&
                        (s.flags() & Flags.ANNOTATION) != 0)
                    isDecl = true;
            } else if (e.value.name == names.PACKAGE) {
                if (s.kind == Kinds.PCK)
                    isDecl = true;
            } else if (e.value.name == names.TYPE_USE) {
                if (s.kind == Kinds.TYP ||
                    s.kind == Kinds.VAR ||
                    (s.kind == Kinds.MTH && !s.isConstructor() &&
                     !s.type.getReturnType().hasTag(TypeTag.VOID)) ||
                    (s.kind == Kinds.MTH && s.isConstructor()))
                    isType = true;
            } else if (e.value.name == names.TYPE_PARAMETER) {
                /* Irrelevant in this case: we will never see symbols
                 * that are type parameters, as we never attach
                 * declaration annotations to them. */
                Assert.check(s.getKind() != ElementKind.TYPE_PARAMETER);
            } else {
                Assert.error("annotationKind(): unrecognized Attribute name " + e.value.name +
                        " (" + e.value.name.getClass() + ")");
            }
        }
        if (isDecl && isType) {
            return AnnotationKind.BOTH;
        } else if (isType) {
            return AnnotationKind.TYPE;
        } else {
            return AnnotationKind.DECLARATION;
        }
    }

    /**
     * An attacher that just attaches annotations as declaration
     * annotations.  This is used in places where we know for a fact
     * that type annotations cannot occur.
     */
    private AttributeAttacher<Attribute.Compound>
            declAnnotationsAttacher(final Symbol sym) {
        return new AttributeAttacher<Attribute.Compound>() {
            @Override
            public String toString() {
                return "Attacher for strictly declaration annotations, for " +
                    sym;
            }

            @Override
            public void attach(List<Attribute.Compound> attrs) {
                sym.resetAnnotations();
                sym.setDeclarationAttributes(attrs);
            }
        };
    }

    /**
     * An attacher that just attaches annotations as type annotations.
     * We use this in places where only type annotations can occur.
     * The most common use for this is any annotation where we have to
     * "parse" a type (arrays, inner classes, type arguments, bounds,
     * etc.).  We also use this for base types for non-declarations
     * (ie. casts, instanceofs, etc).  A more subtle case is for
     * anonymous classes and receiver parameters, both of which cannot
     * have regular annotations.
     */
    private AttributeAttacher<Attribute.TypeCompound>
            typeAnnotationsAttacher(final Symbol sym) {
        return new AttributeAttacher<Attribute.TypeCompound>() {
            @Override
            public String toString() {
                return "Attacher for strictly type annotations, for " + sym;
            }

            @Override
            public void attach(List<Attribute.TypeCompound> attrs) {
                if (!attrs.isEmpty()) {
                    attachTypeAnnotations(sym, attrs);
                }
            }
        };
    }

    /**
     * Attach type-only annotations using an attacher, and run a
     * reporter.  Either the reporter or the attacher may be null, in
     * which case we skip that step.
     */
    private AttributeAttacher<Attribute.TypeCompound>
        reportingTypeAnnotationsAttacher(final AttributeAttacher<Attribute.TypeCompound> attacher,
                                         final Reporter<Attribute.TypeCompound> reporter) {
        return new AttributeAttacher<Attribute.TypeCompound>() {
            @Override
            public String toString() {
                return "Error-reporting type annotation, attacher is: " + attacher +
                    "\n reporter is: " + reporter;
            }

            @Override
            public void attach(List<Attribute.TypeCompound> attrs) {
                if (attacher != null)
                    attacher.attach(attrs);

                if (reporter != null)
                    reporter.report(attrs);
            }
        };
    }

    /**
     * Attach type-only annotations to a symbol and also update the
     * .type field on a tree (unless it is a package type).  This is
     * used to put annotations on to a type as well as a symbol.
     */
    private AttributeAttacher<Attribute.TypeCompound>
        typeUpdatingTypeAnnotationsAttacher(final AttributeAttacher<Attribute.TypeCompound> attacher,
                                            final JCTree tree) {
        return new AttributeAttacher<Attribute.TypeCompound>() {
            @Override
            public String toString() {
                return "Type-updating type annotation attacher, attacher is: " + attacher +
                    "\n tree is: " + tree;
            }

            @Override
            public void attach(List<Attribute.TypeCompound> attrs) {
                if (null != attacher)
                    attacher.attach(attrs);

                if (!attrs.isEmpty() && !tree.type.hasTag(TypeTag.PACKAGE)) {
                    tree.type = tree.type.annotatedType(attrs);
                }
            }
        };
    }

    /**
     * A Reporter for illegal scoping.  We set one of these up in
     * TypeAnnotate whenever we are in a place that corresponds to a
     * package or static class that cannot be annotated.
     */
    private void reportIllegalScoping(List<Attribute.TypeCompound> attrs,
                                      int pos) {
        switch (attrs.size()) {
        case 0:
            // Don't issue an error if all type annotations are
            // also declaration annotations.
            // If the annotations are also declaration annotations, they are
            // illegal as type annotations but might be legal as declaration annotations.
            // The normal declaration annotation checks make sure that the use is valid.
            break;
        case 1:
            log.error(pos, "cant.type.annotate.scoping.1", attrs);
            break;
        default:
            log.error(pos, "cant.type.annotate.scoping", attrs);
        }
    }

    private Reporter<Attribute.TypeCompound>
            illegalScopingReporter(final int pos) {
        return new Reporter<Attribute.TypeCompound>() {
            @Override
            public String toString() {
                return "Illegal scoping reporter at position " + pos;
            }

            @Override
            public void report(List<Attribute.TypeCompound> attrs) {
                reportIllegalScoping(attrs, pos);
            }
        };
    }

    // Create the "simple case": just attach type and regular
    // annotations, no reporting.
    private AttributeAttacher<Attribute.Compound>
            classifyingAttacher(final Symbol sym) {
        return classifyingAttacher(sym, declAnnotationsAttacher(sym),
                                   typeAnnotationsAttacher(sym),
                                   null);
    }


    /**
     * Build an attacher for handling the case where we have
     * annotations, but we don't know for sure whether they are
     * declaration annotations, type annotations, or both.
     *
     * We do this by taking an attacher for declaration annotations,
     * another one for type annotations, and (possibly) a reporter for
     * type-only annotations.  We then use the logic from
     * annotationKind to figure out what kind each annotation is and
     * deal with it accordingly.
     *
     * Any of the declAttacher, the typeAttacher, or the Reporter can
     * be null, in which case we skip it.
     *
     * We have to have the reporter *separate* from the type
     * annotation attacher, because we might be attaching type
     * annotations that are also declaration annotations.  But the
     * semantics of reporters are that they get called for type-only
     * annotations.  For an example of where this matters, consider
     * "@A java.lang.Object foo;", where @A can annotate packages and
     * type uses.  We would create the classifyingAttacher with null
     * for the type attacher and an illegal scoping reporter.  Both
     * attachers would end up getting called on @A (which, we'd skip
     * the type attacher, because it's null), the result being that @A
     * goes on foo as a declaration annotation.  The reporter would
     * not get called, because there are no type-only annotations.
     * However, if @A can only annotate type uses, then it's a
     * type-only annotation, and we report an illegal scoping error.
     *
     * Note: there is a case where we want both attachers to be null:
     * speculative attribution.
     *
     * @param sym The symbol to which to attach annotations.
     * @param declAttacher The attacher to use for declaration (and
     *                     both) annotations, or null.
     * @param typeAttacher The attacher to use for type (and both)
     *                     annotations, or null.
     * @param reporter The reporter to use for type-only annotations, or null.
     * @return The created attacher.
     */
    private AttributeAttacher<Attribute.Compound>
            classifyingAttacher(final Symbol sym,
                                final AttributeAttacher<Attribute.Compound> declAttacher,
                                final AttributeAttacher<Attribute.TypeCompound> typeAttacher,
                                final Reporter<Attribute.TypeCompound> reporter) {
        return new AttributeAttacher<Attribute.Compound>() {
            @Override
            public String toString() {
                return "Classifying attacher, attaching to " + sym +
                    "\n declaration annotation attacher is: " + declAttacher +
                    "\n type annotation attacher is: " + typeAttacher +
                    "\n reporter for strictly type annotations is: " + reporter;
            }

            @Override
            public void attach(List<Attribute.Compound> attrs) {
                // We sort annotations into "buckets" based on what
                // kind they are.
                ListBuffer<Attribute.Compound> declAnnos = new ListBuffer<>();
                ListBuffer<Attribute.TypeCompound> typeAnnos = new ListBuffer<>();
                // We also need to keep track of the type-only
                // annotations, in case we have a reporting action.
                ListBuffer<Attribute.TypeCompound> onlyTypeAnnos = new ListBuffer<>();

                for (Attribute.Compound a : attrs) {
                    Assert.check(!(a instanceof Placeholder),
                                 "Placeholders found in annotations being attached!");
                    switch (annotationKind(a, sym)) {
                    case DECLARATION:
                        declAnnos.append(a);
                        break;
                    case BOTH: {
                        declAnnos.append(a);
                        Attribute.TypeCompound ta = a.toTypeCompound();
                        Assert.checkNonNull(ta.position);
                        typeAnnos.append(ta);
                        break;
                    }
                    case TYPE: {
                        Attribute.TypeCompound ta = a.toTypeCompound();
                        Assert.checkNonNull(ta.position);
                        typeAnnos.append(ta);
                        // Also keep track which annotations are only type annotations
                        onlyTypeAnnos.append(ta);
                        break;
                    }
                    default:
                        throw new AssertionError("Unknown annotation type");
                    }
                }

                if (declAttacher != null)
                    declAttacher.attach(declAnnos.toList());

                if (typeAttacher != null)
                    typeAttacher.attach(typeAnnos.toList());

                if (reporter != null)
                    reporter.report(onlyTypeAnnos.toList());
            }
        };
    }

    /**
     * Actually attach a list of type annotations to a symbol.  For
     * variables defined in methods, we need to attach to both the
     * variable symbol, as well as the method symbol.  This takes care
     * of that.
     *
     * @param sym The symbol to which to attach.
     * @param attrs The annotations to attach.
     */
    public void attachTypeAnnotations(Symbol sym, List<Attribute.TypeCompound> attrs) {
        sym.appendUniqueTypeAttributes(attrs);

        // For type annotations on variables in methods, make
        // sure they are attached to the owner too.
        switch(sym.getKind()) {
        case PARAMETER:
        case LOCAL_VARIABLE:
        case RESOURCE_VARIABLE:
        case EXCEPTION_PARAMETER:
            // Make sure all type annotations from the symbol are also
            // on the owner.
            sym.owner.appendUniqueTypeAttributes(attrs);
            break;
        }
    }

    /**
     * Final task for repeating annotations: go through a list of
     * Attributes and replace all the placeholders with containers.
     *
     * @param buf The list of Attributes.
     * @param ctx The AnnotationContext.
     * @param sym The symbol to which we are attaching.
     * @return The list of attributes with all placeholders replaced.
     */
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

    /**
     * Replace one placeholder with a container.
     */
    private <T extends Attribute.Compound> T replaceOne(Placeholder<T> placeholder,
                                                        Annotate.AnnotationContext<T> ctx,
                                                        Symbol sym) {
        // Process repeated annotations
        T validRepeated =
            processRepeatedAnnotations(placeholder.getPlaceholderFor(),
                                       ctx, sym, placeholder.position);

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

    /**
     * Run a list of annotations through the repeating annotations
     * pipeline, and attach them.  We don't have any diagnostic
     * position.
     */
    void annotateLater(final List<JCAnnotation> annotations,
                       final Env<AttrContext> localEnv,
                       final Symbol s) {
        annotateLater(annotations, localEnv, s, null);
    }

    /**
     * Run a list of annotations through the repeating annotations
     * pipeline and attach them.  This is for when we have annotations
     * that cannot possibly be type annotations (thus, we have no type
     * annotation position).
     */
    void annotateLater(final List<JCAnnotation> annotations,
                       final Env<AttrContext> localEnv,
                       final Symbol s,
                       final DiagnosticPosition deferPos) {
        // Only attach declaration annotations.
        doAnnotateLater(annotations, localEnv, s, deferPos, null,
                        declAnnotationsAttacher(s));
    }

    /**
     * Run a list of annotations through the repeating annotation
     * pipeline, and then classify and attach them.  This is used
     * whenever we have annotations that might be regular annotations,
     * type annotations, or both.
     */
    void annotateWithClassifyLater(final List<JCAnnotation> annotations,
                                   final Env<AttrContext> localEnv,
                                   final Symbol s,
                                   final DiagnosticPosition deferPos,
                                   final TypeAnnotationPosition tapos) {
        // Set up just the basic classifying attacher.
        doAnnotateLater(annotations, localEnv, s, deferPos, tapos,
                        classifyingAttacher(s));
    }

    /**
     * Set up a worker for handling annotations without parsing a type tree.
     */
    private void doAnnotateLater(final List<JCAnnotation> annotations,
                                 final Env<AttrContext> localEnv,
                                 final Symbol s,
                                 final DiagnosticPosition deferPos,
                                 final TypeAnnotationPosition tapos,
                                 final AttributeAttacher<Attribute.Compound> attacher) {
        if (annotations.isEmpty()) {
            return;
        }
        // Mark annotations as incomplete for now.
        //
        // This should probably get redesigned at some point.
        if (s.kind != PCK) {
            s.resetAnnotations();
        }
        normal(new Annotate.Worker() {
                @Override
                public String toString() {
                    return "annotate " + annotations + " onto " + s + " in " + s.owner;
                }

                @Override
                public void run() {
                    annotateNow(annotations, localEnv, s, deferPos,
                                tapos, attacher);
                }
            });

        validate(annotationValidator(annotations, localEnv, s));
    }

    /**
     * Run a list of declaration (meaning they are in a declaration
     * position) annotations through the repeating annotations
     * pipeline.
     *
     * Note that in some cases, these annotations might end up being
     * type annotations, or both declaration and type annotations.
     *
     * @param annotations The annotations to handle.
     * @param localEnv the environment.
     * @param s The symbol to which to attach.
     * @param deferPos The diagnostic position to use.
     * @param position The type annotation position to use if some of
     *                 the annotations end up being type annotations.
     * @param attacher The attacher to use.
     */
    private void annotateNow(final List<JCAnnotation> annotations,
                             final Env<AttrContext> localEnv,
                             final Symbol s,
                             final DiagnosticPosition deferPos,
                             final TypeAnnotationPosition position,
                             final AttributeAttacher<Attribute.Compound> attacher) {
        if (annotations.isEmpty()) {
            return;
        }
                    Assert.check(s.kind == PCK || s.annotationsPendingCompletion());
                    JavaFileObject prev = log.useSource(localEnv.toplevel.sourcefile);
        DiagnosticPosition prevLintPos = deferPos != null ?
            deferredLintHandler.setPos(deferPos) :
            deferredLintHandler.immediate();
                    Lint prevLint = deferPos != null ? null : chk.setLint(lint);
                    try {
                        if (s.hasAnnotations() &&
                            annotations.nonEmpty())
                            log.error(annotations.head.pos,
                                      "already.annotated",
                                      kindName(s), s);
            actualEnterAnnotations(annotations, localEnv, s, position, attacher);
                    } finally {
                        if (prevLint != null)
                            chk.setLint(prevLint);
                        deferredLintHandler.setPos(prevLintPos);
                        log.useSource(prev);
                    }
                }

    // Set up a validator to enforce some rules on regular annotations.
    private Annotate.Worker annotationValidator(final List<JCAnnotation> annotations,
                                                final Env<AttrContext> localEnv,
                                                final Symbol s) {
        return new Annotate.Worker() { //validate annotations
            @Override
            public void run() {
                JavaFileObject prev = log.useSource(localEnv.toplevel.sourcefile);
                try {
                    chk.validateAnnotations(annotations, s);
                } finally {
                    log.useSource(prev);
                }
            }
        };
    }

    private void checkForDeclarationAnnotations(List<? extends JCAnnotation> annotations,
                                                boolean isTypeParameter) {
        // Ensure that no declaration annotations are present.
        // Note that a tree type might be an AnnotatedType with
        // empty annotations, if only declaration annotations were given.
        // This method will raise an error for such a type.
        for (JCAnnotation ai : annotations) {
            Assert.checkNonNull(ai.type);
            Assert.checkNonNull(ai.attribute);

            if (!ai.type.isErroneous() &&
                !hasTypeUseTarget(ai.attribute, isTypeParameter)) {
                log.error(ai.pos(), "annotation.type.not.applicable");
            }
        }
    }

    // Set up a validator to enforce some rules on type annotations.
    // In addition to those enforced by Check.validateTypeAnnotations,
    // this enforces that declaration annotations cannot occur on type
    // parameters.
    private Annotate.Worker typeAnnotationValidator(final List<JCAnnotation> annotations,
                                                    final Env<AttrContext> localEnv,
                                                    final boolean isTypeParameter) {
        return new Annotate.Worker() { //validate annotations
            @Override
            public void run() {
                JavaFileObject prev = log.useSource(localEnv.toplevel.sourcefile);
                try {
                    checkForDeclarationAnnotations(annotations, isTypeParameter);
                    chk.validateTypeAnnotations(annotations, isTypeParameter);
                } finally {
                    log.useSource(prev);
                }
            }
        };
    }

    /**
     * This is an interface that wraps up the functionality of
     * enterAnnotations and enterTypeAnnotations.  This allows some
     * code duplication to be removed from the original repeating
     * annotations pipeline.  It also allows for some unsafe casts to
     * be eliminated.
     *
     * Note: when Lambdas can be used in the compiler, we can just use
     * method refs for enterAnnotations and enterTypeAnnotations.
     */
    private interface AttributeCreator<T extends Attribute.Compound> {
        public T create(JCAnnotation a,
                        Type expected,
                        Env<AttrContext> env,
                        TypeAnnotationPosition position);
        public abstract boolean createsTypeCompound();
    }

    // Note: try to avoid doing anything that makes these any more
    // than just the equivalent of method refs in a pre-lambda
    // setting.  That way, they can go away when we are allowed to use
    // lambda.
    private final AttributeCreator<Attribute.Compound> enterAnnotationsCreator =
        new AttributeCreator<Attribute.Compound>() {
        @Override
            public String toString() {
                return "Attribute creator for regular declaration annotations";
            }

            @Override
        public Attribute.Compound create(JCAnnotation a,
                                         Type expected,
                                             Env<AttrContext> env,
                                             TypeAnnotationPosition position) {
                return enterAnnotation(a, syms.annotationType, env, position);
        }

            @Override
            public boolean createsTypeCompound() { return false; }
    };

    private AttributeCreator<Attribute.TypeCompound>
            enterTypeAnnotationsCreator(final boolean secondaryAttr) {
        return new AttributeCreator<Attribute.TypeCompound>() {
            @Override
            public String toString() {
                if (!secondaryAttr) {
                    return "Attribute creator for regular type annotations";
                } else {
                    return "Attribute creator for regular type annotations, ignores cached attributes";
                }
            }

        @Override
        public Attribute.TypeCompound create(JCAnnotation a,
                                             Type expected,
                                                 Env<AttrContext> env,
                                                 TypeAnnotationPosition position) {
                return enterTypeAnnotation(a, syms.annotationType,
                                           env, position, secondaryAttr);
        }

            @Override
            public boolean createsTypeCompound() { return true; }
    };
    }

    /**
     * Send a list of annotations (which occurred in a declaration
     * position) into the repeating annotations pipeline.
     */
    private void actualEnterAnnotations(List<JCAnnotation> annotations,
                                        Env<AttrContext> env,
                                        Symbol s,
                                        TypeAnnotationPosition position,
                                        AttributeAttacher<Attribute.Compound> attacher) {
        Assert.checkNonNull(s);
        attachAttributesLater(annotations, env, s, position,
                              enterAnnotationsCreator, attacher);
    }

    /**
     * Send a list of annotations (which occurred in a type-use
     * position) into the repeating annotations pipeline.
     */
    private void actualEnterTypeAnnotations(final List<JCAnnotation> annotations,
                                            final Env<AttrContext> env,
                                            final Symbol s,
                                            final DiagnosticPosition deferPos,
                                            final boolean secondaryAttr,
                                            final TypeAnnotationPosition position,
                                            final AttributeAttacher<Attribute.TypeCompound> attacher) {
        Assert.checkNonNull(s);
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        DiagnosticPosition prevLintPos = null;

        if (deferPos != null) {
            prevLintPos = deferredLintHandler.setPos(deferPos);
        }
        try {
            attachAttributesLater(annotations, env, s, position,
                                  enterTypeAnnotationsCreator(secondaryAttr),
                                  attacher);
        } finally {
            if (prevLintPos != null)
                deferredLintHandler.setPos(prevLintPos);
            log.useSource(prev);
        }
    }

    /**
     * Given a type tree, walk down it and handle any annotations we
     * find.
     *
     * @param tree The type tree to scan.
     * @param env The environment.
     * @param sym The symbol to which to attach any annotations we
     * might find.
     * @param deferPos The diagnostic position to use.
     * @param creator The creator to use for making positions.
     */
    public void annotateTypeLater(final JCTree tree,
                                  final Env<AttrContext> env,
                                  final Symbol sym,
                                  final DiagnosticPosition deferPos,
                                  final PositionCreator creator) {
        doAnnotateTypeLater(tree, List.<JCAnnotation>nil(), env,
                            sym, deferPos, creator, false, false);
    }

    /**
     * Given a type tree, walk down it and handle any annotations we
     * find.  We also have a set of base-type annotations (which
     * occurred in a declaration position in source), which may either
     * be declaration annotations or annotations on the base type.
     * For an example, in "@A int @B []", we would have the type tree
     * "int @B []" with base-type annotations "@A".
     *
     * @param tree The type tree to scan.
     * @param baseTypeAnnos The base-type annotations.
     * @param env The environment.
     * @param sym The symbol to which to attach any annotations we
     * might find.
     * @param deferPos The diagnostic position to use.
     * @param creator The creator to use for making positions.
     */
    public void annotateTypeLater(final JCTree tree,
                                  final List<JCAnnotation> baseTypeAnnos,
                                  final Env<AttrContext> env,
                                  final Symbol sym,
                                  final DiagnosticPosition deferPos,
                                  final PositionCreator creator) {
        doAnnotateTypeLater(tree, baseTypeAnnos, env, sym, deferPos,
                            creator, false, false);
    }

    /**
     * Given a type tree, walk down it and handle any annotations we
     * find.  We also have a set of base-type annotations (which
     * occurred in a declaration position in source), which must be
     * type annotations on the base type.
     *
     * @param tree The type tree to scan.
     * @param baseTypeAnnos The base-type annotations.
     * @param env The environment.
     * @param sym The symbol to which to attach any annotations we
     * might find.
     * @param deferPos The diagnostic position to use.
     * @param creator The creator to use for making positions.
     */
    public void annotateStrictTypeLater(final JCTree tree,
                                        final List<JCAnnotation> baseTypeAnnos,
                                        final Env<AttrContext> env,
                                        final Symbol sym,
                                        final DiagnosticPosition deferPos,
                                        final PositionCreator creator) {
        doAnnotateTypeLater(tree, baseTypeAnnos, env, sym, deferPos,
                            creator, true, false);
    }

    /**
     * Given a type tree representing an anonymous class' supertype,
     * walk down it and handle any annotations we find.  We also have
     * a set of base-type annotations (which occurred in a declaration
     * position in source), which must be type annotations on the base
     * type.
     *
     * @param tree The type tree to scan.
     * @param baseTypeAnnos The base-type annotations.
     * @param env The environment.
     * @param sym The symbol to which to attach any annotations we
     * might find.
     * @param deferPos The diagnostic position to use.
     * @param creator The creator to use for making positions.
     */
    public void annotateAnonClassDefLater(final JCTree tree,
                                          final List<JCAnnotation> baseTypeAnnos,
                                          final Env<AttrContext> env,
                                          final Symbol sym,
                                          final DiagnosticPosition deferPos,
                                          final PositionCreator creator) {
        doAnnotateTypeLater(tree, baseTypeAnnos, env, sym, deferPos,
                            creator, true, true);
    }

    // The combined worker function for the annotateTypeLater family.
    public void doAnnotateTypeLater(final JCTree tree,
                                    final List<JCAnnotation> baseTypeAnnos,
                                    final Env<AttrContext> env,
                                    final Symbol sym,
                                    final DiagnosticPosition deferPos,
                                    final PositionCreator creator,
                                    final boolean onlyTypeAnnos,
                                    final boolean secondaryAttr) {
        Assert.checkNonNull(sym);
        Assert.checkNonNull(baseTypeAnnos);
        Assert.checkNonNull(creator);

        normal(new Annotate.Worker() {
                @Override
                public String toString() {
                    return "type annotate " + tree + " onto " + sym + " in " + sym.owner;
                }
                @Override
                public void run() {
                    if (!baseTypeAnnos.isEmpty()) {
                        sym.resetAnnotations(); // mark Annotations as incomplete for now
                    }

                    tree.accept(typeAnnotator(baseTypeAnnos, sym, env, deferPos,
                                              creator, onlyTypeAnnos,
                                              secondaryAttr));
                }
            });
    }

    /**
     * A client passed into various visitors that takes a type path as
     * an argument and performs an action (typically creating a
     * TypeAnnotationPosition and then creating a {@code Worker} and
     * adding it to a queue.
     */
    public abstract class PositionCreator {
        public abstract TypeAnnotationPosition create(List<TypePathEntry> path,
                                                      JCLambda lambda,
                                                      int typeIndex);
    }

    // For when we don't have a creator.  Throws an exception.
    public final PositionCreator noCreator =
        new PositionCreator() {
            @Override
            public String toString() {
                return "Sentinel null position creator";
        }

        @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                throw new AssertionError("No annotation position creator registered");
            }
        };

    // For when we are creating annotations that will inevitably
    // trigger errors.  Creates null.
    public final PositionCreator errorCreator =
        new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for annotations that represent errors";
        }

        @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return null;
            }
        };

    // Create class extension positions
    public final PositionCreator extendsCreator =
        new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for extends";
        }

        @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.classExtends(path, lambda, -1);
            }
        };

    // Create interface implementation positions
    public PositionCreator implementsCreator(final int idx) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for implements, index " + idx;
        }

        @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.classExtends(path, lambda, idx, -1);
            }
        };
    }

    // Create method parameter positions
    public final PositionCreator paramCreator(final int idx) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for parameter " + idx;
        }

        @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodParameter(path, lambda, idx, -1);
            }
        };
    }

    // Create class type parameter positions
    public PositionCreator typeParamCreator(final int idx) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for class type parameter " + idx;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.typeParameter(path, lambda, idx, -1);
            }
        };
    }

    public PositionCreator typeParamBoundCreator(final JCTypeParameter typaram,
                                                 final int param_idx,
                                                 final int bound_idx) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for class type parameter " + param_idx +
                    ", bound " + bound_idx;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                final int real_bound_idx =
                    typaram.bounds.head.type.isInterface() ? bound_idx + 1 : bound_idx;
                return TypeAnnotationPosition
                    .typeParameterBound(path, lambda, param_idx, real_bound_idx, -1);
            }
        };
    }

    // Create field positions
    public final PositionCreator fieldCreator =
        new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for field declaration";
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.field(path, lambda, -1);
            }
        };

    // Create local variable positions
    public PositionCreator localVarCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for local variable declaration at " +
                    pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.localVariable(path, lambda, pos);
            }
        };
    }

    // Create resource variable positions.
    public PositionCreator resourceVarCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for resource variable declaration at " +
                    pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.resourceVariable(path, lambda, pos);
            }
        };
    }

    // Create exception parameter positions.
    public PositionCreator exceptionParamCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for exception param declaration at " +
                    pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.exceptionParameter(path, lambda,
                                                                 typeIndex, pos);
            }
        };
    }

    // Create constructor reference type argument positions.
    public PositionCreator constructorRefTypeArgCreator(final int idx,
                                                        final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for constructor reference type argument " + idx +
                    " at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition
                    .constructorRefTypeArg(path, lambda, idx, pos);
            }
        };
    }

    public PositionCreator methodInvokeTypeArgCreator(final int idx,
                                                      final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for method invoke type argument " + idx +
                    " at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodInvocationTypeArg(path, lambda, idx, pos);
            }
        };
    }

    public PositionCreator methodTypeParamCreator(final int idx) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for method type parameter " + idx;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodTypeParameter(path, lambda, idx, -1);
            }
        };
    }

    public PositionCreator methodTypeParamBoundCreator(final JCTypeParameter typaram,
                                                       final int param_idx,
                                                       final int bound_idx) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for method type parameter " + param_idx +
                    " bound " + bound_idx;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                final int real_bound_idx =
                    typaram.bounds.head.type.isInterface() ? bound_idx + 1 : bound_idx;
                return TypeAnnotationPosition
                    .methodTypeParameterBound(path, lambda, param_idx, real_bound_idx, -1);
            }
        };
    }

    public PositionCreator throwCreator(final int idx) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for throw, type index " + idx;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodThrows(path, lambda, idx, -1);
            }
        };
    }

    public final PositionCreator returnCreator =
        new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for method return type";
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodReturn(path, lambda, -1);
            }
        };

    public PositionCreator receiverCreator =
        new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for method receiver parameter type";
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodReceiver(path, lambda, -1);
            }
        };

    public PositionCreator methodRefCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for method reference at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodRef(path, lambda, pos);
            }
        };
    }

    public PositionCreator methodRefTypeArgCreator(final int idx,
                                                   final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for method reference type argument " + idx +
                    " at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.methodRefTypeArg(path, lambda, idx, pos);
            }
        };
    }

    public PositionCreator constructorRefCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for constructor reference at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.constructorRef(path, lambda, pos);
            }
        };
    }

    public PositionCreator constructorInvokeTypeArgCreator(final int idx,
                                                           final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for constructor invoke type argument " + idx +
                    " at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.constructorInvocationTypeArg(path, lambda, idx, pos);
            }
        };
    }

    public PositionCreator instanceOfCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for instanceof at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.instanceOf(path, lambda, pos);
            }
        };
    }

    public PositionCreator newObjCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for new at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.newObj(path, lambda, pos);
            }
        };
    }

    public PositionCreator castCreator(final int pos) {
        return new PositionCreator() {
            @Override
            public String toString() {
                return "Position creator for cast at " + pos;
            }

            @Override
            public TypeAnnotationPosition create(List<TypePathEntry> path,
                                                 JCLambda lambda,
                                                 int typeIndex) {
                return TypeAnnotationPosition.typeCast(path, lambda, typeIndex, pos);
            }
        };
    }

    public static List<TypePathEntry> makeInners(Type type) {
        return addInners(type, List.<TypePathEntry>nil());
    }

    private static List<TypePathEntry> addInners(Type type,
                                                 List<TypePathEntry> typepath) {
        Type encl = type.getEnclosingType();
        while (encl != null && encl.getKind() != TypeKind.NONE &&
               encl.getKind() != TypeKind.ERROR) {
            typepath = typepath.append(TypePathEntry.INNER_TYPE);
            encl = encl.getEnclosingType();
        }
        return typepath;
    }

    /**
     * Set up the visitor to scan the type tree and handle any
     * annotations we find.  If we are in speculative attribution, we
     * will not actually attach anything, we will just enter the
     * annotations and run them through the pipeline to pick up any
     * errors that might occur.
     *
     * @param baseTypeAnnos Annotations on the base type, which need
     *                      to be classified if onlyTypeAnnos is false.
     * @param sym The symbol to which to attach.
     * @param env The environment.
     * @param creator The position creator to use.
     * @param onlyTypeAnnos Whether or not baseTypeAnnos can represent
     *                      declaration annotations.
     * @param secondaryAttr Whether or not we are creating secondary
     *                      attributes (see enterTypeAnnotations).
     */
    public TypeAnnotate typeAnnotator(final List<JCAnnotation> baseTypeAnnos,
                                      final Symbol sym,
                                      final Env<AttrContext> env,
                                      final DiagnosticPosition deferPos,
                                      final PositionCreator creator,
                                      final boolean onlyTypeAnnos,
                                      final boolean secondaryAttr) {
        if (!env.info.isSpeculative) {
            return new TypeAnnotate(baseTypeAnnos, sym, env, deferPos, creator,
                                    declAnnotationsAttacher(sym),
                                    typeAnnotationsAttacher(sym),
                                    onlyTypeAnnos, secondaryAttr);
        } else {
            return new TypeAnnotate(baseTypeAnnos, sym, env, deferPos, creator,
                                    null, null, onlyTypeAnnos, secondaryAttr);
        }
    }

    /**
     * A visitor that scans a type tree and handles an annotations it finds.
     *
     */
    private class TypeAnnotate extends TreeScanner {
        // The creator we use to create positions.
        protected PositionCreator creator;
        // The current type path
        private List<TypePathEntry> typepath = List.nil();
        // The current innermost lambda
        private JCLambda currentLambda;
        // The current type index, if we are looking at an
        // intersection type.
        private int type_index = 0;
        // Whether or not we are looking at the innermost type.  This
        // gets used to figure out where to attach base type
        // annotations.
        private boolean innermost;
        // The attachers and reporter we use.
        private AttributeAttacher<Attribute.Compound> declAttacher;
        private AttributeAttacher<Attribute.TypeCompound> typeAttacher;
        private Reporter<Attribute.TypeCompound> reporter;
        // The symbol to which we are attaching.
        private final Symbol sym;
        // The diagnostic position we use.
        private final DiagnosticPosition deferPos;
        // The environment
        private final Env<AttrContext> env;
        private final List<JCAnnotation> baseTypeAnnos;
        // Whether or not baseTypeAnnos can be declaration
        // annotations, or just strictly type annotations.
        private final boolean onlyTypeAnnos;
        // Whether or not we are creating secondary attributes (see
        // enterTypeAnnotations).
        private final boolean secondaryAttr;

        public TypeAnnotate(final List<JCAnnotation> baseTypeAnnos,
                            final Symbol sym,
                            final Env<AttrContext> env,
                            final DiagnosticPosition deferPos,
                            final PositionCreator creator,
                            final AttributeAttacher<Attribute.Compound> declAttacher,
                            final AttributeAttacher<Attribute.TypeCompound> typeAttacher,
                            final boolean onlyTypeAnnos,
                            final boolean secondaryAttr) {
            this.baseTypeAnnos = baseTypeAnnos;
            this.sym = sym;
            this.env = env;
            this.deferPos = deferPos;
            this.currentLambda = env.getLambda();
            this.creator = creator;
            this.innermost = true;
            this.declAttacher = declAttacher;
            this.typeAttacher = typeAttacher;
            this.reporter = null;
            this.onlyTypeAnnos = onlyTypeAnnos;
            this.secondaryAttr = secondaryAttr;
        }

        // Deal with the base-type annotations.  This should only get
        // called when we are at the inner-most type.
        private void doBaseTypeAnnos() {
            if (onlyTypeAnnos) {
                // If the base type annotations can only be type
                // annotations, then handle them as such.
                doTypeAnnos(baseTypeAnnos, false);
            } else if (!baseTypeAnnos.isEmpty()) {
                // Otherwise, send them into the repeating annotations
                // pipeline with a classifying attacher we build based
                // on the current state.
                final TypeAnnotationPosition tapos =
                    creator.create(typepath, currentLambda, type_index);
                annotateNow(baseTypeAnnos, env, sym, deferPos, tapos,
                            classifyingAttacher(sym, declAttacher,
                                                typeAttacher, reporter));
                // Also set up a validator.
                validate(annotationValidator(baseTypeAnnos, env, sym));
            }
        }

        // Deal with type annotations we found while scanning the tree.
        private void doTypeAnnos(List<JCAnnotation> annos,
                                 boolean isTypeParameter) {
            if (!annos.isEmpty()) {
                // Grab the reporter and the type attacher (which,
                // it's ok for either to be null), and combine them
                // into a reporting attacher.
                final AttributeAttacher<Attribute.TypeCompound> attacher =
                    reportingTypeAnnotationsAttacher(typeAttacher, reporter);
                // Create the position using the current type path and
                // type index.
                final TypeAnnotationPosition tapos =
                    creator.create(typepath, currentLambda, type_index);
                // Send the annotations into the repeating annotations
                // pipeline, and set up a validator.
                actualEnterTypeAnnotations(annos, env, sym, deferPos, secondaryAttr,
                                           tapos, attacher);
                validate(typeAnnotationValidator(annos, env, isTypeParameter));
            }
        }

        @Override
        public void visitTypeIdent(final JCPrimitiveTypeTree tree) {
            // This is one place that can represent the base type.
            // But we need to make sure we're actually in the
            // innermost type (ie not a type argument or something).
            if (innermost) {
                final AttributeAttacher<Attribute.TypeCompound> oldTypeAttacher = typeAttacher;
                // We want to update the Type to have annotations.
                typeAttacher = typeUpdatingTypeAnnotationsAttacher(oldTypeAttacher,
                                                                   tree);
                // We can't possibly have any INNER_TYPE type path
                // elements, because these are all primitives.
                doBaseTypeAnnos();
                typeAttacher = oldTypeAttacher;
            }
        }

        @Override
        public void visitIdent(final JCIdent tree) {
            // This is one place that can represent the base type.
            // But we need to make sure we're actually in the
            // innermost type (ie not a type argument or something).
            if (innermost) {
                final AttributeAttacher<Attribute.TypeCompound> oldTypeAttacher = typeAttacher;
                // Set up an attacher that updates the Type, so we get
                // the annotations.
                typeAttacher = typeUpdatingTypeAnnotationsAttacher(oldTypeAttacher,
                                                                   tree);
                // Add any INNER_TYPE type path elements we might need.
                if (tree.type != null) {
                    final List<TypePathEntry> oldpath = typepath;
                    typepath = addInners(tree.type, typepath);
                    doBaseTypeAnnos();
                    typepath = oldpath;
                } else {
                    doBaseTypeAnnos();
                }
                typeAttacher = oldTypeAttacher;
            }
        }

        @Override
        public void visitAnnotatedType(JCAnnotatedType tree) {
            // This is one place where we run into pure type
            // annotations.
            Assert.checkNonNull(tree.getUnderlyingType().type);
            final boolean oldinnermost = innermost;
            // Make sure we don't consider ourselves "innermost" when
            // scanning the annotations.
            innermost = false;
            scan(tree.annotations);
            innermost = oldinnermost;
            scan(tree.underlyingType);
            final List<TypePathEntry> oldpath = typepath;
            typepath = addInners(tree.getUnderlyingType().type, typepath);
            doTypeAnnos(tree.annotations, false);
            typepath = oldpath;
        }

        @Override
        public void visitTypeArray(JCArrayTypeTree tree) {
            // This case is simple: just add an ARRAY to the type path.
            final List<TypePathEntry> oldpath = typepath;
            typepath = typepath.append(TypePathEntry.ARRAY);
            super.visitTypeArray(tree);
            typepath = oldpath;
        }

        @Override
        public void visitTypeApply(JCTypeApply tree) {
            // Handle type arguments
            Assert.checkNonNull(tree.getType().type);
            final List<TypePathEntry> oldpath = typepath;
            // First, look at the base type.
            scan(tree.clazz);

            // Add any INNER_TYPE path elements we need first
            if (tree.getType() != null && tree.getType().type != null) {
                typepath = addInners(tree.getType().type, typepath);
            }
            // Make sure we're not considering ourselves innermost
            // when looking at type arguments.
            final boolean oldinnermost = innermost;
            innermost = false;
            // For each type argument, add a TYPE_ARGUMENT path
            // element for the right index.
            int i = 0;
            for (List<JCExpression> l = tree.arguments; l.nonEmpty();
                 l = l.tail, i++) {
                final JCExpression arg = l.head;
                final List<TypePathEntry> noargpath = typepath;
                typepath = typepath.append(new TypePathEntry(TypePathEntryKind.TYPE_ARGUMENT, i));
                scan(arg);
                typepath = noargpath;
            }
            typepath = oldpath;
            innermost = oldinnermost;
        }

        @Override
        public void visitNewArray(JCNewArray tree) {
            // We can also run into type annotations here, on dimAnnos.
            final List<TypePathEntry> oldpath = typepath;
            final PositionCreator oldcreator = creator;
            creator = newObjCreator(tree.pos);
            doTypeAnnos(tree.annotations, false);

            // Go through the dimensions, set up the type path, and
            // handle any annetations we find.
            for (int i = 0; i < tree.dimAnnotations.size(); i++) {
                final List<JCAnnotation> dimAnnos = tree.dimAnnotations.get(i);
                doTypeAnnos(dimAnnos, false);
                // This is right.  As per the type annotations spec,
                // the first array dimension has no arrays in the type
                // path, the second has one, and so on, and the
                // element type has n for n dimensions.
                typepath = typepath.append(TypePathEntry.ARRAY);
            }

            // The element type is sometimes null, in the case of
            // array literals.
            scan(tree.elemtype);
            typepath = oldpath;
            creator = oldcreator;
        }

        @Override
        public void visitWildcard(JCWildcard tree) {
            // Simple: add a WILDCARD type path element and continue.
            final List<TypePathEntry> oldpath = typepath;
            typepath = typepath.append(TypePathEntry.WILDCARD);
            super.visitWildcard(tree);
            typepath = oldpath;
        }

        @Override
        public void visitTypeParameter(JCTypeParameter tree) {
            // This is another place where we can run into pure type
            // annotations.
            scan(tree.annotations);
            Assert.checkNonNull(tree.type);
            doTypeAnnos(tree.annotations, true);
        }

        @Override
        public void visitLambda(JCLambda tree) {
            // If we run into a lambda, set the current lambda to it.
            final JCLambda oldLambda = currentLambda;
            currentLambda = tree;
            scan(tree.body);
            scan(tree.params);
            currentLambda = oldLambda;
        }

        @Override
        public void visitTypeIntersection(JCTypeIntersection tree) {
            final boolean oldinnermost = innermost;
            // Run through the options, and update the type_index
            // accordingly.
            for (List<JCExpression> l = tree.bounds; l.nonEmpty();
                 l = l.tail, type_index++) {
                scan(l.head);
                // Set innermost to false after the first element
                innermost = false;
            }
            innermost = oldinnermost;
        }

        @Override
        public void visitTypeUnion(JCTypeUnion tree) {
            final boolean oldinnermost = innermost;
            // Run through the options, and update the type_index
            // accordingly.
            for (List<JCExpression> l = tree.alternatives; l.nonEmpty();
                 l = l.tail, type_index++) {
                scan(l.head);
                // Set innermost to false after the first element
                innermost = false;
            }
            innermost = oldinnermost;
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            // In this case, we need to possibly set up an
            // illegalScopingReporter, if the selected type cannot be
            // annotated.
            Symbol sym = tree.sym;
            final AttributeAttacher<Attribute.TypeCompound> oldTypeAttacher = typeAttacher;
            final Reporter<Attribute.TypeCompound> oldReporter = reporter;
            // If we're selecting from an interface or a static class,
            // set up attachers that will only attach declaration
            // annotations and will report type annotations as errors.
            Type selectedTy = tree.selected.type;
            if ((sym != null && (sym.isStatic() || sym.isInterface() ||
                                selectedTy.hasTag(TypeTag.PACKAGE))) ||
                tree.name == names._class) {
                typeAttacher = null;
                reporter = illegalScopingReporter(tree.pos);
            }
            super.visitSelect(tree);
            typeAttacher = oldTypeAttacher;
            reporter = oldReporter;
        }

        // These methods stop the visitor from continuing on when it
        // sees a definition.
        @Override
        public void visitVarDef(final JCVariableDecl tree) {
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
        }

        @Override
        public void visitNewClass(JCNewClass tree) {

            }
        }

    // A derived TypeAnnotate visitor that also scans expressions
    // within Deferred attribution.
    private class TypeAnnotateExpr extends TypeAnnotate {
        // This constructor creates an instance suitable for deferred
        // attribution.
        public TypeAnnotateExpr(final Symbol sym,
                                final Env<AttrContext> env,
                                final DiagnosticPosition deferPos,
                                final PositionCreator creator) {
            super(List.<JCAnnotation>nil(), sym, env, deferPos,
                  creator, null, null, false, false);
        }

        @Override
        public void visitTypeCast(final JCTypeCast tree) {
            final PositionCreator oldcreator = creator;
            creator = castCreator(tree.pos);
            super.visitTypeCast(tree);
            creator = oldcreator;
        }

        @Override
        public void visitTypeTest(JCInstanceOf tree) {
            final PositionCreator oldcreator = creator;
            creator = instanceOfCreator(tree.pos);
            super.visitTypeTest(tree);
            creator = oldcreator;
        }

        @Override
        public void visitReference(JCMemberReference that) {
            final boolean isConstructor = that.getName() == names.init;
            final PositionCreator oldcreator = creator;
            creator = isConstructor ? constructorRefCreator(that.pos) :
                                      methodRefCreator(that.pos);
            scan(that.expr);

            if (null != that.typeargs) {
                int i = 0;
                for (List<JCExpression> l = that.typeargs;
                     l.nonEmpty(); l = l.tail, i++) {
                    final Annotate.PositionCreator typeArgCreator =
                        isConstructor ? constructorRefTypeArgCreator(i, that.pos) :
                                        methodRefTypeArgCreator(i, that.pos);
                    final JCExpression arg = l.head;
                    scan(that.expr);
                }
            }

            creator = oldcreator;
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            // This will be visited by Attr later, so don't do
            // anything.
        }
    }

    /**
     * Set up a visitor to scan an expression and handle any type
     * annotations it finds, within a deferred attribution context.
     */
    public void typeAnnotateExprLater(final JCTree tree,
                                      final Env<AttrContext> env,
                                      final Symbol sym,
                                      final DiagnosticPosition deferPos,
                                      final PositionCreator creator) {
        Assert.checkNonNull(sym);
        Assert.checkNonNull(creator);

        normal(new Annotate.Worker() {
                @Override
                public String toString() {
                    return "type annotate " + tree + " onto " + sym + " in " + sym.owner;
                }
                @Override
                public void run() {
                    tree.accept(new TypeAnnotateExpr(sym, env, deferPos, creator));
                }
            });
    }
}
