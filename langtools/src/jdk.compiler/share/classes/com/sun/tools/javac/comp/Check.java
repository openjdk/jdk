/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.tools.JavaFileManager;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Attribute.Compound;
import com.sun.tools.javac.comp.Annotate.AnnotationTypeMetadata;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.DeferredAttr.DeferredAttrContext;
import com.sun.tools.javac.comp.Infer.FreeTypeListener;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Flags.ANNOTATION;
import static com.sun.tools.javac.code.Flags.SYNCHRONIZED;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.code.TypeTag.WILDCARD;

import static com.sun.tools.javac.tree.JCTree.Tag.*;

/** Type checking helper class for the attribution phase.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Check {
    protected static final Context.Key<Check> checkKey = new Context.Key<>();

    private final Names names;
    private final Log log;
    private final Resolve rs;
    private final Symtab syms;
    private final Enter enter;
    private final DeferredAttr deferredAttr;
    private final Infer infer;
    private final Types types;
    private final TypeAnnotations typeAnnotations;
    private final JCDiagnostic.Factory diags;
    private boolean warnOnSyntheticConflicts;
    private boolean suppressAbortOnBadClassFile;
    private boolean enableSunApiLintControl;
    private final JavaFileManager fileManager;
    private final Source source;
    private final Profile profile;
    private final boolean warnOnAccessToSensitiveMembers;

    // The set of lint options currently in effect. It is initialized
    // from the context, and then is set/reset as needed by Attr as it
    // visits all the various parts of the trees during attribution.
    private Lint lint;

    // The method being analyzed in Attr - it is set/reset as needed by
    // Attr as it visits new method declarations.
    private MethodSymbol method;

    public static Check instance(Context context) {
        Check instance = context.get(checkKey);
        if (instance == null)
            instance = new Check(context);
        return instance;
    }

    protected Check(Context context) {
        context.put(checkKey, this);

        names = Names.instance(context);
        dfltTargetMeta = new Name[] { names.PACKAGE, names.TYPE,
            names.FIELD, names.METHOD, names.CONSTRUCTOR,
            names.ANNOTATION_TYPE, names.LOCAL_VARIABLE, names.PARAMETER};
        log = Log.instance(context);
        rs = Resolve.instance(context);
        syms = Symtab.instance(context);
        enter = Enter.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        infer = Infer.instance(context);
        types = Types.instance(context);
        typeAnnotations = TypeAnnotations.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Options options = Options.instance(context);
        lint = Lint.instance(context);
        fileManager = context.get(JavaFileManager.class);

        source = Source.instance(context);
        allowSimplifiedVarargs = source.allowSimplifiedVarargs();
        allowDefaultMethods = source.allowDefaultMethods();
        allowStrictMethodClashCheck = source.allowStrictMethodClashCheck();
        allowPrivateSafeVarargs = source.allowPrivateSafeVarargs();
        allowDiamondWithAnonymousClassCreation = source.allowDiamondWithAnonymousClassCreation();
        complexInference = options.isSet("complexinference");
        warnOnSyntheticConflicts = options.isSet("warnOnSyntheticConflicts");
        suppressAbortOnBadClassFile = options.isSet("suppressAbortOnBadClassFile");
        enableSunApiLintControl = options.isSet("enableSunApiLintControl");
        warnOnAccessToSensitiveMembers = options.isSet("warnOnAccessToSensitiveMembers");

        Target target = Target.instance(context);
        syntheticNameChar = target.syntheticNameChar();

        profile = Profile.instance(context);

        boolean verboseDeprecated = lint.isEnabled(LintCategory.DEPRECATION);
        boolean verboseUnchecked = lint.isEnabled(LintCategory.UNCHECKED);
        boolean verboseSunApi = lint.isEnabled(LintCategory.SUNAPI);
        boolean enforceMandatoryWarnings = true;

        deprecationHandler = new MandatoryWarningHandler(log, verboseDeprecated,
                enforceMandatoryWarnings, "deprecated", LintCategory.DEPRECATION);
        uncheckedHandler = new MandatoryWarningHandler(log, verboseUnchecked,
                enforceMandatoryWarnings, "unchecked", LintCategory.UNCHECKED);
        sunApiHandler = new MandatoryWarningHandler(log, verboseSunApi,
                enforceMandatoryWarnings, "sunapi", null);

        deferredLintHandler = DeferredLintHandler.instance(context);
    }

    /** Switch: simplified varargs enabled?
     */
    boolean allowSimplifiedVarargs;

    /** Switch: default methods enabled?
     */
    boolean allowDefaultMethods;

    /** Switch: should unrelated return types trigger a method clash?
     */
    boolean allowStrictMethodClashCheck;

    /** Switch: can the @SafeVarargs annotation be applied to private methods?
     */
    boolean allowPrivateSafeVarargs;

    /** Switch: can diamond inference be used in anonymous instance creation ?
     */
    boolean allowDiamondWithAnonymousClassCreation;

    /** Switch: -complexinference option set?
     */
    boolean complexInference;

    /** Character for synthetic names
     */
    char syntheticNameChar;

    /** A table mapping flat names of all compiled classes in this run to their
     *  symbols; maintained from outside.
     */
    public Map<Name,ClassSymbol> compiled = new HashMap<>();

    /** A handler for messages about deprecated usage.
     */
    private MandatoryWarningHandler deprecationHandler;

    /** A handler for messages about unchecked or unsafe usage.
     */
    private MandatoryWarningHandler uncheckedHandler;

    /** A handler for messages about using proprietary API.
     */
    private MandatoryWarningHandler sunApiHandler;

    /** A handler for deferred lint warnings.
     */
    private DeferredLintHandler deferredLintHandler;

/* *************************************************************************
 * Errors and Warnings
 **************************************************************************/

    Lint setLint(Lint newLint) {
        Lint prev = lint;
        lint = newLint;
        return prev;
    }

    MethodSymbol setMethod(MethodSymbol newMethod) {
        MethodSymbol prev = method;
        method = newMethod;
        return prev;
    }

    /** Warn about deprecated symbol.
     *  @param pos        Position to be used for error reporting.
     *  @param sym        The deprecated symbol.
     */
    void warnDeprecated(DiagnosticPosition pos, Symbol sym) {
        if (!lint.isSuppressed(LintCategory.DEPRECATION))
            deprecationHandler.report(pos, "has.been.deprecated", sym, sym.location());
    }

    /** Warn about unchecked operation.
     *  @param pos        Position to be used for error reporting.
     *  @param msg        A string describing the problem.
     */
    public void warnUnchecked(DiagnosticPosition pos, String msg, Object... args) {
        if (!lint.isSuppressed(LintCategory.UNCHECKED))
            uncheckedHandler.report(pos, msg, args);
    }

    /** Warn about unsafe vararg method decl.
     *  @param pos        Position to be used for error reporting.
     */
    void warnUnsafeVararg(DiagnosticPosition pos, String key, Object... args) {
        if (lint.isEnabled(LintCategory.VARARGS) && allowSimplifiedVarargs)
            log.warning(LintCategory.VARARGS, pos, key, args);
    }

    /** Warn about using proprietary API.
     *  @param pos        Position to be used for error reporting.
     *  @param msg        A string describing the problem.
     */
    public void warnSunApi(DiagnosticPosition pos, String msg, Object... args) {
        if (!lint.isSuppressed(LintCategory.SUNAPI))
            sunApiHandler.report(pos, msg, args);
    }

    public void warnStatic(DiagnosticPosition pos, String msg, Object... args) {
        if (lint.isEnabled(LintCategory.STATIC))
            log.warning(LintCategory.STATIC, pos, msg, args);
    }

    /** Warn about division by integer constant zero.
     *  @param pos        Position to be used for error reporting.
     */
    void warnDivZero(DiagnosticPosition pos) {
        if (lint.isEnabled(LintCategory.DIVZERO))
            log.warning(LintCategory.DIVZERO, pos, "div.zero");
    }

    /**
     * Report any deferred diagnostics.
     */
    public void reportDeferredDiagnostics() {
        deprecationHandler.reportDeferredDiagnostic();
        uncheckedHandler.reportDeferredDiagnostic();
        sunApiHandler.reportDeferredDiagnostic();
    }


    /** Report a failure to complete a class.
     *  @param pos        Position to be used for error reporting.
     *  @param ex         The failure to report.
     */
    public Type completionError(DiagnosticPosition pos, CompletionFailure ex) {
        log.error(JCDiagnostic.DiagnosticFlag.NON_DEFERRABLE, pos, "cant.access", ex.sym, ex.getDetailValue());
        if (ex instanceof ClassFinder.BadClassFile
                && !suppressAbortOnBadClassFile) throw new Abort();
        else return syms.errType;
    }

    /** Report an error that wrong type tag was found.
     *  @param pos        Position to be used for error reporting.
     *  @param required   An internationalized string describing the type tag
     *                    required.
     *  @param found      The type that was found.
     */
    Type typeTagError(DiagnosticPosition pos, Object required, Object found) {
        // this error used to be raised by the parser,
        // but has been delayed to this point:
        if (found instanceof Type && ((Type)found).hasTag(VOID)) {
            log.error(pos, "illegal.start.of.type");
            return syms.errType;
        }
        log.error(pos, "type.found.req", found, required);
        return types.createErrorType(found instanceof Type ? (Type)found : syms.errType);
    }

    /** Report an error that symbol cannot be referenced before super
     *  has been called.
     *  @param pos        Position to be used for error reporting.
     *  @param sym        The referenced symbol.
     */
    void earlyRefError(DiagnosticPosition pos, Symbol sym) {
        log.error(pos, "cant.ref.before.ctor.called", sym);
    }

    /** Report duplicate declaration error.
     */
    void duplicateError(DiagnosticPosition pos, Symbol sym) {
        if (!sym.type.isErroneous()) {
            Symbol location = sym.location();
            if (location.kind == MTH &&
                    ((MethodSymbol)location).isStaticOrInstanceInit()) {
                log.error(pos, "already.defined.in.clinit", kindName(sym), sym,
                        kindName(sym.location()), kindName(sym.location().enclClass()),
                        sym.location().enclClass());
            } else {
                log.error(pos, "already.defined", kindName(sym), sym,
                        kindName(sym.location()), sym.location());
            }
        }
    }

    /** Report array/varargs duplicate declaration
     */
    void varargsDuplicateError(DiagnosticPosition pos, Symbol sym1, Symbol sym2) {
        if (!sym1.type.isErroneous() && !sym2.type.isErroneous()) {
            log.error(pos, "array.and.varargs", sym1, sym2, sym2.location());
        }
    }

/* ************************************************************************
 * duplicate declaration checking
 *************************************************************************/

    /** Check that variable does not hide variable with same name in
     *  immediately enclosing local scope.
     *  @param pos           Position for error reporting.
     *  @param v             The symbol.
     *  @param s             The scope.
     */
    void checkTransparentVar(DiagnosticPosition pos, VarSymbol v, Scope s) {
        for (Symbol sym : s.getSymbolsByName(v.name)) {
            if (sym.owner != v.owner) break;
            if (sym.kind == VAR &&
                sym.owner.kind.matches(KindSelector.VAL_MTH) &&
                v.name != names.error) {
                duplicateError(pos, sym);
                return;
            }
        }
    }

    /** Check that a class or interface does not hide a class or
     *  interface with same name in immediately enclosing local scope.
     *  @param pos           Position for error reporting.
     *  @param c             The symbol.
     *  @param s             The scope.
     */
    void checkTransparentClass(DiagnosticPosition pos, ClassSymbol c, Scope s) {
        for (Symbol sym : s.getSymbolsByName(c.name)) {
            if (sym.owner != c.owner) break;
            if (sym.kind == TYP && !sym.type.hasTag(TYPEVAR) &&
                sym.owner.kind.matches(KindSelector.VAL_MTH) &&
                c.name != names.error) {
                duplicateError(pos, sym);
                return;
            }
        }
    }

    /** Check that class does not have the same name as one of
     *  its enclosing classes, or as a class defined in its enclosing scope.
     *  return true if class is unique in its enclosing scope.
     *  @param pos           Position for error reporting.
     *  @param name          The class name.
     *  @param s             The enclosing scope.
     */
    boolean checkUniqueClassName(DiagnosticPosition pos, Name name, Scope s) {
        for (Symbol sym : s.getSymbolsByName(name, NON_RECURSIVE)) {
            if (sym.kind == TYP && sym.name != names.error) {
                duplicateError(pos, sym);
                return false;
            }
        }
        for (Symbol sym = s.owner; sym != null; sym = sym.owner) {
            if (sym.kind == TYP && sym.name == name && sym.name != names.error) {
                duplicateError(pos, sym);
                return true;
            }
        }
        return true;
    }

/* *************************************************************************
 * Class name generation
 **************************************************************************/


    private Map<Pair<Name, Name>, Integer> localClassNameIndexes = new HashMap<>();

    /** Return name of local class.
     *  This is of the form   {@code <enclClass> $ n <classname> }
     *  where
     *    enclClass is the flat name of the enclosing class,
     *    classname is the simple name of the local class
     */
    Name localClassName(ClassSymbol c) {
        Name enclFlatname = c.owner.enclClass().flatname;
        String enclFlatnameStr = enclFlatname.toString();
        Pair<Name, Name> key = new Pair<>(enclFlatname, c.name);
        Integer index = localClassNameIndexes.get(key);
        for (int i = (index == null) ? 1 : index; ; i++) {
            Name flatname = names.fromString(enclFlatnameStr
                    + syntheticNameChar + i + c.name);
            if (compiled.get(flatname) == null) {
                localClassNameIndexes.put(key, i + 1);
                return flatname;
            }
        }
    }

    void clearLocalClassNameIndexes(ClassSymbol c) {
        localClassNameIndexes.remove(new Pair<>(
                c.owner.enclClass().flatname, c.name));
    }

    public void newRound() {
        compiled.clear();
        localClassNameIndexes.clear();
    }

/* *************************************************************************
 * Type Checking
 **************************************************************************/

    /**
     * A check context is an object that can be used to perform compatibility
     * checks - depending on the check context, meaning of 'compatibility' might
     * vary significantly.
     */
    public interface CheckContext {
        /**
         * Is type 'found' compatible with type 'req' in given context
         */
        boolean compatible(Type found, Type req, Warner warn);
        /**
         * Report a check error
         */
        void report(DiagnosticPosition pos, JCDiagnostic details);
        /**
         * Obtain a warner for this check context
         */
        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req);

        public InferenceContext inferenceContext();

        public DeferredAttr.DeferredAttrContext deferredAttrContext();
    }

    /**
     * This class represent a check context that is nested within another check
     * context - useful to check sub-expressions. The default behavior simply
     * redirects all method calls to the enclosing check context leveraging
     * the forwarding pattern.
     */
    static class NestedCheckContext implements CheckContext {
        CheckContext enclosingContext;

        NestedCheckContext(CheckContext enclosingContext) {
            this.enclosingContext = enclosingContext;
        }

        public boolean compatible(Type found, Type req, Warner warn) {
            return enclosingContext.compatible(found, req, warn);
        }

        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            enclosingContext.report(pos, details);
        }

        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req) {
            return enclosingContext.checkWarner(pos, found, req);
        }

        public InferenceContext inferenceContext() {
            return enclosingContext.inferenceContext();
        }

        public DeferredAttrContext deferredAttrContext() {
            return enclosingContext.deferredAttrContext();
        }
    }

    /**
     * Check context to be used when evaluating assignment/return statements
     */
    CheckContext basicHandler = new CheckContext() {
        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            log.error(pos, "prob.found.req", details);
        }
        public boolean compatible(Type found, Type req, Warner warn) {
            return types.isAssignable(found, req, warn);
        }

        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req) {
            return convertWarner(pos, found, req);
        }

        public InferenceContext inferenceContext() {
            return infer.emptyContext;
        }

        public DeferredAttrContext deferredAttrContext() {
            return deferredAttr.emptyDeferredAttrContext;
        }

        @Override
        public String toString() {
            return "CheckContext: basicHandler";
        }
    };

    /** Check that a given type is assignable to a given proto-type.
     *  If it is, return the type, otherwise return errType.
     *  @param pos        Position to be used for error reporting.
     *  @param found      The type that was found.
     *  @param req        The type that was required.
     */
    public Type checkType(DiagnosticPosition pos, Type found, Type req) {
        return checkType(pos, found, req, basicHandler);
    }

    Type checkType(final DiagnosticPosition pos, final Type found, final Type req, final CheckContext checkContext) {
        final InferenceContext inferenceContext = checkContext.inferenceContext();
        if (inferenceContext.free(req) || inferenceContext.free(found)) {
            inferenceContext.addFreeTypeListener(List.of(req, found), new FreeTypeListener() {
                @Override
                public void typesInferred(InferenceContext inferenceContext) {
                    checkType(pos, inferenceContext.asInstType(found), inferenceContext.asInstType(req), checkContext);
                }
            });
        }
        if (req.hasTag(ERROR))
            return req;
        if (req.hasTag(NONE))
            return found;
        if (checkContext.compatible(found, req, checkContext.checkWarner(pos, found, req))) {
            return found;
        } else {
            if (found.isNumeric() && req.isNumeric()) {
                checkContext.report(pos, diags.fragment("possible.loss.of.precision", found, req));
                return types.createErrorType(found);
            }
            checkContext.report(pos, diags.fragment("inconvertible.types", found, req));
            return types.createErrorType(found);
        }
    }

    /** Check that a given type can be cast to a given target type.
     *  Return the result of the cast.
     *  @param pos        Position to be used for error reporting.
     *  @param found      The type that is being cast.
     *  @param req        The target type of the cast.
     */
    Type checkCastable(DiagnosticPosition pos, Type found, Type req) {
        return checkCastable(pos, found, req, basicHandler);
    }
    Type checkCastable(DiagnosticPosition pos, Type found, Type req, CheckContext checkContext) {
        if (types.isCastable(found, req, castWarner(pos, found, req))) {
            return req;
        } else {
            checkContext.report(pos, diags.fragment("inconvertible.types", found, req));
            return types.createErrorType(found);
        }
    }

    /** Check for redundant casts (i.e. where source type is a subtype of target type)
     * The problem should only be reported for non-292 cast
     */
    public void checkRedundantCast(Env<AttrContext> env, final JCTypeCast tree) {
        if (!tree.type.isErroneous()
                && types.isSameType(tree.expr.type, tree.clazz.type)
                && !(ignoreAnnotatedCasts && TreeInfo.containsTypeAnnotation(tree.clazz))
                && !is292targetTypeCast(tree)) {
            deferredLintHandler.report(new DeferredLintHandler.LintLogger() {
                @Override
                public void report() {
                    if (lint.isEnabled(Lint.LintCategory.CAST))
                        log.warning(Lint.LintCategory.CAST,
                                tree.pos(), "redundant.cast", tree.clazz.type);
                }
            });
        }
    }
    //where
        private boolean is292targetTypeCast(JCTypeCast tree) {
            boolean is292targetTypeCast = false;
            JCExpression expr = TreeInfo.skipParens(tree.expr);
            if (expr.hasTag(APPLY)) {
                JCMethodInvocation apply = (JCMethodInvocation)expr;
                Symbol sym = TreeInfo.symbol(apply.meth);
                is292targetTypeCast = sym != null &&
                    sym.kind == MTH &&
                    (sym.flags() & HYPOTHETICAL) != 0;
            }
            return is292targetTypeCast;
        }

        private static final boolean ignoreAnnotatedCasts = true;

    /** Check that a type is within some bounds.
     *
     *  Used in TypeApply to verify that, e.g., X in {@code V<X>} is a valid
     *  type argument.
     *  @param a             The type that should be bounded by bs.
     *  @param bound         The bound.
     */
    private boolean checkExtends(Type a, Type bound) {
         if (a.isUnbound()) {
             return true;
         } else if (!a.hasTag(WILDCARD)) {
             a = types.cvarUpperBound(a);
             return types.isSubtype(a, bound);
         } else if (a.isExtendsBound()) {
             return types.isCastable(bound, types.wildUpperBound(a), types.noWarnings);
         } else if (a.isSuperBound()) {
             return !types.notSoftSubtype(types.wildLowerBound(a), bound);
         }
         return true;
     }

    /** Check that type is different from 'void'.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkNonVoid(DiagnosticPosition pos, Type t) {
        if (t.hasTag(VOID)) {
            log.error(pos, "void.not.allowed.here");
            return types.createErrorType(t);
        } else {
            return t;
        }
    }

    Type checkClassOrArrayType(DiagnosticPosition pos, Type t) {
        if (!t.hasTag(CLASS) && !t.hasTag(ARRAY) && !t.hasTag(ERROR)) {
            return typeTagError(pos,
                                diags.fragment("type.req.class.array"),
                                asTypeParam(t));
        } else {
            return t;
        }
    }

    /** Check that type is a class or interface type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkClassType(DiagnosticPosition pos, Type t) {
        if (!t.hasTag(CLASS) && !t.hasTag(ERROR)) {
            return typeTagError(pos,
                                diags.fragment("type.req.class"),
                                asTypeParam(t));
        } else {
            return t;
        }
    }
    //where
        private Object asTypeParam(Type t) {
            return (t.hasTag(TYPEVAR))
                                    ? diags.fragment("type.parameter", t)
                                    : t;
        }

    /** Check that type is a valid qualifier for a constructor reference expression
     */
    Type checkConstructorRefType(DiagnosticPosition pos, Type t) {
        t = checkClassOrArrayType(pos, t);
        if (t.hasTag(CLASS)) {
            if ((t.tsym.flags() & (ABSTRACT | INTERFACE)) != 0) {
                log.error(pos, "abstract.cant.be.instantiated", t.tsym);
                t = types.createErrorType(t);
            } else if ((t.tsym.flags() & ENUM) != 0) {
                log.error(pos, "enum.cant.be.instantiated");
                t = types.createErrorType(t);
            } else {
                t = checkClassType(pos, t, true);
            }
        } else if (t.hasTag(ARRAY)) {
            if (!types.isReifiable(((ArrayType)t).elemtype)) {
                log.error(pos, "generic.array.creation");
                t = types.createErrorType(t);
            }
        }
        return t;
    }

    /** Check that type is a class or interface type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     *  @param noBounds    True if type bounds are illegal here.
     */
    Type checkClassType(DiagnosticPosition pos, Type t, boolean noBounds) {
        t = checkClassType(pos, t);
        if (noBounds && t.isParameterized()) {
            List<Type> args = t.getTypeArguments();
            while (args.nonEmpty()) {
                if (args.head.hasTag(WILDCARD))
                    return typeTagError(pos,
                                        diags.fragment("type.req.exact"),
                                        args.head);
                args = args.tail;
            }
        }
        return t;
    }

    /** Check that type is a reference type, i.e. a class, interface or array type
     *  or a type variable.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkRefType(DiagnosticPosition pos, Type t) {
        if (t.isReference())
            return t;
        else
            return typeTagError(pos,
                                diags.fragment("type.req.ref"),
                                t);
    }

    /** Check that each type is a reference type, i.e. a class, interface or array type
     *  or a type variable.
     *  @param trees         Original trees, used for error reporting.
     *  @param types         The types to be checked.
     */
    List<Type> checkRefTypes(List<JCExpression> trees, List<Type> types) {
        List<JCExpression> tl = trees;
        for (List<Type> l = types; l.nonEmpty(); l = l.tail) {
            l.head = checkRefType(tl.head.pos(), l.head);
            tl = tl.tail;
        }
        return types;
    }

    /** Check that type is a null or reference type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkNullOrRefType(DiagnosticPosition pos, Type t) {
        if (t.isReference() || t.hasTag(BOT))
            return t;
        else
            return typeTagError(pos,
                                diags.fragment("type.req.ref"),
                                t);
    }

    /** Check that flag set does not contain elements of two conflicting sets. s
     *  Return true if it doesn't.
     *  @param pos           Position to be used for error reporting.
     *  @param flags         The set of flags to be checked.
     *  @param set1          Conflicting flags set #1.
     *  @param set2          Conflicting flags set #2.
     */
    boolean checkDisjoint(DiagnosticPosition pos, long flags, long set1, long set2) {
        if ((flags & set1) != 0 && (flags & set2) != 0) {
            log.error(pos,
                      "illegal.combination.of.modifiers",
                      asFlagSet(TreeInfo.firstFlag(flags & set1)),
                      asFlagSet(TreeInfo.firstFlag(flags & set2)));
            return false;
        } else
            return true;
    }

    /** Check that usage of diamond operator is correct (i.e. diamond should not
     * be used with non-generic classes or in anonymous class creation expressions)
     */
    Type checkDiamond(JCNewClass tree, Type t) {
        if (!TreeInfo.isDiamond(tree) ||
                t.isErroneous()) {
            return checkClassType(tree.clazz.pos(), t, true);
        } else if (tree.def != null && !allowDiamondWithAnonymousClassCreation) {
            log.error(tree.clazz.pos(),
                    Errors.CantApplyDiamond1(t, Fragments.DiamondAndAnonClassNotSupportedInSource(source.name)));
            return types.createErrorType(t);
        } else if (t.tsym.type.getTypeArguments().isEmpty()) {
            log.error(tree.clazz.pos(),
                "cant.apply.diamond.1",
                t, diags.fragment("diamond.non.generic", t));
            return types.createErrorType(t);
        } else if (tree.typeargs != null &&
                tree.typeargs.nonEmpty()) {
            log.error(tree.clazz.pos(),
                "cant.apply.diamond.1",
                t, diags.fragment("diamond.and.explicit.params", t));
            return types.createErrorType(t);
        } else {
            return t;
        }
    }

    /** Check that the type inferred using the diamond operator does not contain
     *  non-denotable types such as captured types or intersection types.
     *  @param t the type inferred using the diamond operator
     *  @return  the (possibly empty) list of non-denotable types.
     */
    List<Type> checkDiamondDenotable(ClassType t) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type arg : t.allparams()) {
            if (!diamondTypeChecker.visit(arg, null)) {
                buf.append(arg);
            }
        }
        return buf.toList();
    }
        // where

        /** diamondTypeChecker: A type visitor that descends down the given type looking for non-denotable
         *  types. The visit methods return false as soon as a non-denotable type is encountered and true
         *  otherwise.
         */
        private static final Types.SimpleVisitor<Boolean, Void> diamondTypeChecker = new Types.SimpleVisitor<Boolean, Void>() {
            @Override
            public Boolean visitType(Type t, Void s) {
                return true;
            }
            @Override
            public Boolean visitClassType(ClassType t, Void s) {
                if (t.isCompound()) {
                    return false;
                }
                for (Type targ : t.allparams()) {
                    if (!visit(targ, s)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Boolean visitTypeVar(TypeVar t, Void s) {
                /* Any type variable mentioned in the inferred type must have been declared as a type parameter
                  (i.e cannot have been produced by inference (18.4))
                */
                return t.tsym.owner.type.getTypeArguments().contains(t);
            }

            @Override
            public Boolean visitCapturedType(CapturedType t, Void s) {
                /* Any type variable mentioned in the inferred type must have been declared as a type parameter
                  (i.e cannot have been produced by capture conversion (5.1.10))
                */
                return false;
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Void s) {
                return visit(t.elemtype, s);
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Void s) {
                return visit(t.type, s);
            }
        };

    void checkVarargsMethodDecl(Env<AttrContext> env, JCMethodDecl tree) {
        MethodSymbol m = tree.sym;
        if (!allowSimplifiedVarargs) return;
        boolean hasTrustMeAnno = m.attribute(syms.trustMeType.tsym) != null;
        Type varargElemType = null;
        if (m.isVarArgs()) {
            varargElemType = types.elemtype(tree.params.last().type);
        }
        if (hasTrustMeAnno && !isTrustMeAllowedOnMethod(m)) {
            if (varargElemType != null) {
                log.error(tree,
                        "varargs.invalid.trustme.anno",
                          syms.trustMeType.tsym,
                          allowPrivateSafeVarargs ?
                          diags.fragment("varargs.trustme.on.virtual.varargs", m) :
                          diags.fragment("varargs.trustme.on.virtual.varargs.final.only", m));
            } else {
                log.error(tree,
                            "varargs.invalid.trustme.anno",
                            syms.trustMeType.tsym,
                            diags.fragment("varargs.trustme.on.non.varargs.meth", m));
            }
        } else if (hasTrustMeAnno && varargElemType != null &&
                            types.isReifiable(varargElemType)) {
            warnUnsafeVararg(tree,
                            "varargs.redundant.trustme.anno",
                            syms.trustMeType.tsym,
                            diags.fragment("varargs.trustme.on.reifiable.varargs", varargElemType));
        }
        else if (!hasTrustMeAnno && varargElemType != null &&
                !types.isReifiable(varargElemType)) {
            warnUnchecked(tree.params.head.pos(), "unchecked.varargs.non.reifiable.type", varargElemType);
        }
    }
    //where
        private boolean isTrustMeAllowedOnMethod(Symbol s) {
            return (s.flags() & VARARGS) != 0 &&
                (s.isConstructor() ||
                    (s.flags() & (STATIC | FINAL |
                                  (allowPrivateSafeVarargs ? PRIVATE : 0) )) != 0);
        }

    Type checkMethod(final Type mtype,
            final Symbol sym,
            final Env<AttrContext> env,
            final List<JCExpression> argtrees,
            final List<Type> argtypes,
            final boolean useVarargs,
            InferenceContext inferenceContext) {
        // System.out.println("call   : " + env.tree);
        // System.out.println("method : " + owntype);
        // System.out.println("actuals: " + argtypes);
        if (inferenceContext.free(mtype)) {
            inferenceContext.addFreeTypeListener(List.of(mtype), new FreeTypeListener() {
                public void typesInferred(InferenceContext inferenceContext) {
                    checkMethod(inferenceContext.asInstType(mtype), sym, env, argtrees, argtypes, useVarargs, inferenceContext);
                }
            });
            return mtype;
        }
        Type owntype = mtype;
        List<Type> formals = owntype.getParameterTypes();
        List<Type> nonInferred = sym.type.getParameterTypes();
        if (nonInferred.length() != formals.length()) nonInferred = formals;
        Type last = useVarargs ? formals.last() : null;
        if (sym.name == names.init && sym.owner == syms.enumSym) {
            formals = formals.tail.tail;
            nonInferred = nonInferred.tail.tail;
        }
        List<JCExpression> args = argtrees;
        if (args != null) {
            //this is null when type-checking a method reference
            while (formals.head != last) {
                JCTree arg = args.head;
                Warner warn = convertWarner(arg.pos(), arg.type, nonInferred.head);
                assertConvertible(arg, arg.type, formals.head, warn);
                args = args.tail;
                formals = formals.tail;
                nonInferred = nonInferred.tail;
            }
            if (useVarargs) {
                Type varArg = types.elemtype(last);
                while (args.tail != null) {
                    JCTree arg = args.head;
                    Warner warn = convertWarner(arg.pos(), arg.type, varArg);
                    assertConvertible(arg, arg.type, varArg, warn);
                    args = args.tail;
                }
            } else if ((sym.flags() & (VARARGS | SIGNATURE_POLYMORPHIC)) == VARARGS) {
                // non-varargs call to varargs method
                Type varParam = owntype.getParameterTypes().last();
                Type lastArg = argtypes.last();
                if (types.isSubtypeUnchecked(lastArg, types.elemtype(varParam)) &&
                    !types.isSameType(types.erasure(varParam), types.erasure(lastArg)))
                    log.warning(argtrees.last().pos(), "inexact.non-varargs.call",
                                types.elemtype(varParam), varParam);
            }
        }
        if (useVarargs) {
            Type argtype = owntype.getParameterTypes().last();
            if (!types.isReifiable(argtype) &&
                (!allowSimplifiedVarargs ||
                 sym.baseSymbol().attribute(syms.trustMeType.tsym) == null ||
                 !isTrustMeAllowedOnMethod(sym))) {
                warnUnchecked(env.tree.pos(),
                                  "unchecked.generic.array.creation",
                                  argtype);
            }
            if ((sym.baseSymbol().flags() & SIGNATURE_POLYMORPHIC) == 0) {
                TreeInfo.setVarargsElement(env.tree, types.elemtype(argtype));
            }
         }
         return owntype;
    }
    //where
    private void assertConvertible(JCTree tree, Type actual, Type formal, Warner warn) {
        if (types.isConvertible(actual, formal, warn))
            return;

        if (formal.isCompound()
            && types.isSubtype(actual, types.supertype(formal))
            && types.isSubtypeUnchecked(actual, types.interfaces(formal), warn))
            return;
    }

    /**
     * Check that type 't' is a valid instantiation of a generic class
     * (see JLS 4.5)
     *
     * @param t class type to be checked
     * @return true if 't' is well-formed
     */
    public boolean checkValidGenericType(Type t) {
        return firstIncompatibleTypeArg(t) == null;
    }
    //WHERE
        private Type firstIncompatibleTypeArg(Type type) {
            List<Type> formals = type.tsym.type.allparams();
            List<Type> actuals = type.allparams();
            List<Type> args = type.getTypeArguments();
            List<Type> forms = type.tsym.type.getTypeArguments();
            ListBuffer<Type> bounds_buf = new ListBuffer<>();

            // For matching pairs of actual argument types `a' and
            // formal type parameters with declared bound `b' ...
            while (args.nonEmpty() && forms.nonEmpty()) {
                // exact type arguments needs to know their
                // bounds (for upper and lower bound
                // calculations).  So we create new bounds where
                // type-parameters are replaced with actuals argument types.
                bounds_buf.append(types.subst(forms.head.getUpperBound(), formals, actuals));
                args = args.tail;
                forms = forms.tail;
            }

            args = type.getTypeArguments();
            List<Type> tvars_cap = types.substBounds(formals,
                                      formals,
                                      types.capture(type).allparams());
            while (args.nonEmpty() && tvars_cap.nonEmpty()) {
                // Let the actual arguments know their bound
                args.head.withTypeVar((TypeVar)tvars_cap.head);
                args = args.tail;
                tvars_cap = tvars_cap.tail;
            }

            args = type.getTypeArguments();
            List<Type> bounds = bounds_buf.toList();

            while (args.nonEmpty() && bounds.nonEmpty()) {
                Type actual = args.head;
                if (!isTypeArgErroneous(actual) &&
                        !bounds.head.isErroneous() &&
                        !checkExtends(actual, bounds.head)) {
                    return args.head;
                }
                args = args.tail;
                bounds = bounds.tail;
            }

            args = type.getTypeArguments();
            bounds = bounds_buf.toList();

            for (Type arg : types.capture(type).getTypeArguments()) {
                if (arg.hasTag(TYPEVAR) &&
                        arg.getUpperBound().isErroneous() &&
                        !bounds.head.isErroneous() &&
                        !isTypeArgErroneous(args.head)) {
                    return args.head;
                }
                bounds = bounds.tail;
                args = args.tail;
            }

            return null;
        }
        //where
        boolean isTypeArgErroneous(Type t) {
            return isTypeArgErroneous.visit(t);
        }

        Types.UnaryVisitor<Boolean> isTypeArgErroneous = new Types.UnaryVisitor<Boolean>() {
            public Boolean visitType(Type t, Void s) {
                return t.isErroneous();
            }
            @Override
            public Boolean visitTypeVar(TypeVar t, Void s) {
                return visit(t.getUpperBound());
            }
            @Override
            public Boolean visitCapturedType(CapturedType t, Void s) {
                return visit(t.getUpperBound()) ||
                        visit(t.getLowerBound());
            }
            @Override
            public Boolean visitWildcardType(WildcardType t, Void s) {
                return visit(t.type);
            }
        };

    /** Check that given modifiers are legal for given symbol and
     *  return modifiers together with any implicit modifiers for that symbol.
     *  Warning: we can't use flags() here since this method
     *  is called during class enter, when flags() would cause a premature
     *  completion.
     *  @param pos           Position to be used for error reporting.
     *  @param flags         The set of modifiers given in a definition.
     *  @param sym           The defined symbol.
     */
    long checkFlags(DiagnosticPosition pos, long flags, Symbol sym, JCTree tree) {
        long mask;
        long implicit = 0;

        switch (sym.kind) {
        case VAR:
            if (TreeInfo.isReceiverParam(tree))
                mask = ReceiverParamFlags;
            else if (sym.owner.kind != TYP)
                mask = LocalVarFlags;
            else if ((sym.owner.flags_field & INTERFACE) != 0)
                mask = implicit = InterfaceVarFlags;
            else
                mask = VarFlags;
            break;
        case MTH:
            if (sym.name == names.init) {
                if ((sym.owner.flags_field & ENUM) != 0) {
                    // enum constructors cannot be declared public or
                    // protected and must be implicitly or explicitly
                    // private
                    implicit = PRIVATE;
                    mask = PRIVATE;
                } else
                    mask = ConstructorFlags;
            }  else if ((sym.owner.flags_field & INTERFACE) != 0) {
                if ((sym.owner.flags_field & ANNOTATION) != 0) {
                    mask = AnnotationTypeElementMask;
                    implicit = PUBLIC | ABSTRACT;
                } else if ((flags & (DEFAULT | STATIC | PRIVATE)) != 0) {
                    mask = InterfaceMethodMask;
                    implicit = (flags & PRIVATE) != 0 ? 0 : PUBLIC;
                    if ((flags & DEFAULT) != 0) {
                        implicit |= ABSTRACT;
                    }
                } else {
                    mask = implicit = InterfaceMethodFlags;
                }
            } else {
                mask = MethodFlags;
            }
            // Imply STRICTFP if owner has STRICTFP set.
            if (((flags|implicit) & Flags.ABSTRACT) == 0 ||
                ((flags) & Flags.DEFAULT) != 0)
                implicit |= sym.owner.flags_field & STRICTFP;
            break;
        case TYP:
            if (sym.isLocal()) {
                mask = LocalClassFlags;
                if (sym.name.isEmpty()) { // Anonymous class
                    // JLS: Anonymous classes are final.
                    implicit |= FINAL;
                }
                if ((sym.owner.flags_field & STATIC) == 0 &&
                    (flags & ENUM) != 0)
                    log.error(pos, "enums.must.be.static");
            } else if (sym.owner.kind == TYP) {
                mask = MemberClassFlags;
                if (sym.owner.owner.kind == PCK ||
                    (sym.owner.flags_field & STATIC) != 0)
                    mask |= STATIC;
                else if ((flags & ENUM) != 0)
                    log.error(pos, "enums.must.be.static");
                // Nested interfaces and enums are always STATIC (Spec ???)
                if ((flags & (INTERFACE | ENUM)) != 0 ) implicit = STATIC;
            } else {
                mask = ClassFlags;
            }
            // Interfaces are always ABSTRACT
            if ((flags & INTERFACE) != 0) implicit |= ABSTRACT;

            if ((flags & ENUM) != 0) {
                // enums can't be declared abstract or final
                mask &= ~(ABSTRACT | FINAL);
                implicit |= implicitEnumFinalFlag(tree);
            }
            // Imply STRICTFP if owner has STRICTFP set.
            implicit |= sym.owner.flags_field & STRICTFP;
            break;
        default:
            throw new AssertionError();
        }
        long illegal = flags & ExtendedStandardFlags & ~mask;
        if (illegal != 0) {
            if ((illegal & INTERFACE) != 0) {
                log.error(pos, "intf.not.allowed.here");
                mask |= INTERFACE;
            }
            else {
                log.error(pos,
                          "mod.not.allowed.here", asFlagSet(illegal));
            }
        }
        else if ((sym.kind == TYP ||
                  // ISSUE: Disallowing abstract&private is no longer appropriate
                  // in the presence of inner classes. Should it be deleted here?
                  checkDisjoint(pos, flags,
                                ABSTRACT,
                                PRIVATE | STATIC | DEFAULT))
                 &&
                 checkDisjoint(pos, flags,
                                STATIC | PRIVATE,
                                DEFAULT)
                 &&
                 checkDisjoint(pos, flags,
                               ABSTRACT | INTERFACE,
                               FINAL | NATIVE | SYNCHRONIZED)
                 &&
                 checkDisjoint(pos, flags,
                               PUBLIC,
                               PRIVATE | PROTECTED)
                 &&
                 checkDisjoint(pos, flags,
                               PRIVATE,
                               PUBLIC | PROTECTED)
                 &&
                 checkDisjoint(pos, flags,
                               FINAL,
                               VOLATILE)
                 &&
                 (sym.kind == TYP ||
                  checkDisjoint(pos, flags,
                                ABSTRACT | NATIVE,
                                STRICTFP))) {
            // skip
        }
        return flags & (mask | ~ExtendedStandardFlags) | implicit;
    }


    /** Determine if this enum should be implicitly final.
     *
     *  If the enum has no specialized enum contants, it is final.
     *
     *  If the enum does have specialized enum contants, it is
     *  <i>not</i> final.
     */
    private long implicitEnumFinalFlag(JCTree tree) {
        if (!tree.hasTag(CLASSDEF)) return 0;
        class SpecialTreeVisitor extends JCTree.Visitor {
            boolean specialized;
            SpecialTreeVisitor() {
                this.specialized = false;
            }

            @Override
            public void visitTree(JCTree tree) { /* no-op */ }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                if ((tree.mods.flags & ENUM) != 0) {
                    if (tree.init instanceof JCNewClass &&
                        ((JCNewClass) tree.init).def != null) {
                        specialized = true;
                    }
                }
            }
        }

        SpecialTreeVisitor sts = new SpecialTreeVisitor();
        JCClassDecl cdef = (JCClassDecl) tree;
        for (JCTree defs: cdef.defs) {
            defs.accept(sts);
            if (sts.specialized) return 0;
        }
        return FINAL;
    }

/* *************************************************************************
 * Type Validation
 **************************************************************************/

    /** Validate a type expression. That is,
     *  check that all type arguments of a parametric type are within
     *  their bounds. This must be done in a second phase after type attribution
     *  since a class might have a subclass as type parameter bound. E.g:
     *
     *  <pre>{@code
     *  class B<A extends C> { ... }
     *  class C extends B<C> { ... }
     *  }</pre>
     *
     *  and we can't make sure that the bound is already attributed because
     *  of possible cycles.
     *
     * Visitor method: Validate a type expression, if it is not null, catching
     *  and reporting any completion failures.
     */
    void validate(JCTree tree, Env<AttrContext> env) {
        validate(tree, env, true);
    }
    void validate(JCTree tree, Env<AttrContext> env, boolean checkRaw) {
        new Validator(env).validateTree(tree, checkRaw, true);
    }

    /** Visitor method: Validate a list of type expressions.
     */
    void validate(List<? extends JCTree> trees, Env<AttrContext> env) {
        for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
            validate(l.head, env);
    }

    /** A visitor class for type validation.
     */
    class Validator extends JCTree.Visitor {

        boolean checkRaw;
        boolean isOuter;
        Env<AttrContext> env;

        Validator(Env<AttrContext> env) {
            this.env = env;
        }

        @Override
        public void visitTypeArray(JCArrayTypeTree tree) {
            validateTree(tree.elemtype, checkRaw, isOuter);
        }

        @Override
        public void visitTypeApply(JCTypeApply tree) {
            if (tree.type.hasTag(CLASS)) {
                List<JCExpression> args = tree.arguments;
                List<Type> forms = tree.type.tsym.type.getTypeArguments();

                Type incompatibleArg = firstIncompatibleTypeArg(tree.type);
                if (incompatibleArg != null) {
                    for (JCTree arg : tree.arguments) {
                        if (arg.type == incompatibleArg) {
                            log.error(arg, "not.within.bounds", incompatibleArg, forms.head);
                        }
                        forms = forms.tail;
                     }
                 }

                forms = tree.type.tsym.type.getTypeArguments();

                boolean is_java_lang_Class = tree.type.tsym.flatName() == names.java_lang_Class;

                // For matching pairs of actual argument types `a' and
                // formal type parameters with declared bound `b' ...
                while (args.nonEmpty() && forms.nonEmpty()) {
                    validateTree(args.head,
                            !(isOuter && is_java_lang_Class),
                            false);
                    args = args.tail;
                    forms = forms.tail;
                }

                // Check that this type is either fully parameterized, or
                // not parameterized at all.
                if (tree.type.getEnclosingType().isRaw())
                    log.error(tree.pos(), "improperly.formed.type.inner.raw.param");
                if (tree.clazz.hasTag(SELECT))
                    visitSelectInternal((JCFieldAccess)tree.clazz);
            }
        }

        @Override
        public void visitTypeParameter(JCTypeParameter tree) {
            validateTrees(tree.bounds, true, isOuter);
            checkClassBounds(tree.pos(), tree.type);
        }

        @Override
        public void visitWildcard(JCWildcard tree) {
            if (tree.inner != null)
                validateTree(tree.inner, true, isOuter);
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            if (tree.type.hasTag(CLASS)) {
                visitSelectInternal(tree);

                // Check that this type is either fully parameterized, or
                // not parameterized at all.
                if (tree.selected.type.isParameterized() && tree.type.tsym.type.getTypeArguments().nonEmpty())
                    log.error(tree.pos(), "improperly.formed.type.param.missing");
            }
        }

        public void visitSelectInternal(JCFieldAccess tree) {
            if (tree.type.tsym.isStatic() &&
                tree.selected.type.isParameterized()) {
                // The enclosing type is not a class, so we are
                // looking at a static member type.  However, the
                // qualifying expression is parameterized.
                log.error(tree.pos(), "cant.select.static.class.from.param.type");
            } else {
                // otherwise validate the rest of the expression
                tree.selected.accept(this);
            }
        }

        @Override
        public void visitAnnotatedType(JCAnnotatedType tree) {
            tree.underlyingType.accept(this);
        }

        @Override
        public void visitTypeIdent(JCPrimitiveTypeTree that) {
            if (that.type.hasTag(TypeTag.VOID)) {
                log.error(that.pos(), "void.not.allowed.here");
            }
            super.visitTypeIdent(that);
        }

        /** Default visitor method: do nothing.
         */
        @Override
        public void visitTree(JCTree tree) {
        }

        public void validateTree(JCTree tree, boolean checkRaw, boolean isOuter) {
            if (tree != null) {
                boolean prevCheckRaw = this.checkRaw;
                this.checkRaw = checkRaw;
                this.isOuter = isOuter;

                try {
                    tree.accept(this);
                    if (checkRaw)
                        checkRaw(tree, env);
                } catch (CompletionFailure ex) {
                    completionError(tree.pos(), ex);
                } finally {
                    this.checkRaw = prevCheckRaw;
                }
            }
        }

        public void validateTrees(List<? extends JCTree> trees, boolean checkRaw, boolean isOuter) {
            for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
                validateTree(l.head, checkRaw, isOuter);
        }
    }

    void checkRaw(JCTree tree, Env<AttrContext> env) {
        if (lint.isEnabled(LintCategory.RAW) &&
            tree.type.hasTag(CLASS) &&
            !TreeInfo.isDiamond(tree) &&
            !withinAnonConstr(env) &&
            tree.type.isRaw()) {
            log.warning(LintCategory.RAW,
                    tree.pos(), "raw.class.use", tree.type, tree.type.tsym.type);
        }
    }
    //where
        private boolean withinAnonConstr(Env<AttrContext> env) {
            return env.enclClass.name.isEmpty() &&
                    env.enclMethod != null && env.enclMethod.name == names.init;
        }

/* *************************************************************************
 * Exception checking
 **************************************************************************/

    /* The following methods treat classes as sets that contain
     * the class itself and all their subclasses
     */

    /** Is given type a subtype of some of the types in given list?
     */
    boolean subset(Type t, List<Type> ts) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (types.isSubtype(t, l.head)) return true;
        return false;
    }

    /** Is given type a subtype or supertype of
     *  some of the types in given list?
     */
    boolean intersects(Type t, List<Type> ts) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (types.isSubtype(t, l.head) || types.isSubtype(l.head, t)) return true;
        return false;
    }

    /** Add type set to given type list, unless it is a subclass of some class
     *  in the list.
     */
    List<Type> incl(Type t, List<Type> ts) {
        return subset(t, ts) ? ts : excl(t, ts).prepend(t);
    }

    /** Remove type set from type set list.
     */
    List<Type> excl(Type t, List<Type> ts) {
        if (ts.isEmpty()) {
            return ts;
        } else {
            List<Type> ts1 = excl(t, ts.tail);
            if (types.isSubtype(ts.head, t)) return ts1;
            else if (ts1 == ts.tail) return ts;
            else return ts1.prepend(ts.head);
        }
    }

    /** Form the union of two type set lists.
     */
    List<Type> union(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = ts1;
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            ts = incl(l.head, ts);
        return ts;
    }

    /** Form the difference of two type lists.
     */
    List<Type> diff(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = ts1;
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            ts = excl(l.head, ts);
        return ts;
    }

    /** Form the intersection of two type lists.
     */
    public List<Type> intersect(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = List.nil();
        for (List<Type> l = ts1; l.nonEmpty(); l = l.tail)
            if (subset(l.head, ts2)) ts = incl(l.head, ts);
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            if (subset(l.head, ts1)) ts = incl(l.head, ts);
        return ts;
    }

    /** Is exc an exception symbol that need not be declared?
     */
    boolean isUnchecked(ClassSymbol exc) {
        return
            exc.kind == ERR ||
            exc.isSubClass(syms.errorType.tsym, types) ||
            exc.isSubClass(syms.runtimeExceptionType.tsym, types);
    }

    /** Is exc an exception type that need not be declared?
     */
    boolean isUnchecked(Type exc) {
        return
            (exc.hasTag(TYPEVAR)) ? isUnchecked(types.supertype(exc)) :
            (exc.hasTag(CLASS)) ? isUnchecked((ClassSymbol)exc.tsym) :
            exc.hasTag(BOT);
    }

    /** Same, but handling completion failures.
     */
    boolean isUnchecked(DiagnosticPosition pos, Type exc) {
        try {
            return isUnchecked(exc);
        } catch (CompletionFailure ex) {
            completionError(pos, ex);
            return true;
        }
    }

    /** Is exc handled by given exception list?
     */
    boolean isHandled(Type exc, List<Type> handled) {
        return isUnchecked(exc) || subset(exc, handled);
    }

    /** Return all exceptions in thrown list that are not in handled list.
     *  @param thrown     The list of thrown exceptions.
     *  @param handled    The list of handled exceptions.
     */
    List<Type> unhandled(List<Type> thrown, List<Type> handled) {
        List<Type> unhandled = List.nil();
        for (List<Type> l = thrown; l.nonEmpty(); l = l.tail)
            if (!isHandled(l.head, handled)) unhandled = unhandled.prepend(l.head);
        return unhandled;
    }

/* *************************************************************************
 * Overriding/Implementation checking
 **************************************************************************/

    /** The level of access protection given by a flag set,
     *  where PRIVATE is highest and PUBLIC is lowest.
     */
    static int protection(long flags) {
        switch ((short)(flags & AccessFlags)) {
        case PRIVATE: return 3;
        case PROTECTED: return 1;
        default:
        case PUBLIC: return 0;
        case 0: return 2;
        }
    }

    /** A customized "cannot override" error message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Object cannotOverride(MethodSymbol m, MethodSymbol other) {
        String key;
        if ((other.owner.flags() & INTERFACE) == 0)
            key = "cant.override";
        else if ((m.owner.flags() & INTERFACE) == 0)
            key = "cant.implement";
        else
            key = "clashes.with";
        return diags.fragment(key, m, m.location(), other, other.location());
    }

    /** A customized "override" warning message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Object uncheckedOverrides(MethodSymbol m, MethodSymbol other) {
        String key;
        if ((other.owner.flags() & INTERFACE) == 0)
            key = "unchecked.override";
        else if ((m.owner.flags() & INTERFACE) == 0)
            key = "unchecked.implement";
        else
            key = "unchecked.clash.with";
        return diags.fragment(key, m, m.location(), other, other.location());
    }

    /** A customized "override" warning message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Object varargsOverrides(MethodSymbol m, MethodSymbol other) {
        String key;
        if ((other.owner.flags() & INTERFACE) == 0)
            key = "varargs.override";
        else  if ((m.owner.flags() & INTERFACE) == 0)
            key = "varargs.implement";
        else
            key = "varargs.clash.with";
        return diags.fragment(key, m, m.location(), other, other.location());
    }

    /** Check that this method conforms with overridden method 'other'.
     *  where `origin' is the class where checking started.
     *  Complications:
     *  (1) Do not check overriding of synthetic methods
     *      (reason: they might be final).
     *      todo: check whether this is still necessary.
     *  (2) Admit the case where an interface proxy throws fewer exceptions
     *      than the method it implements. Augment the proxy methods with the
     *      undeclared exceptions in this case.
     *  (3) When generics are enabled, admit the case where an interface proxy
     *      has a result type
     *      extended by the result type of the method it implements.
     *      Change the proxies result type to the smaller type in this case.
     *
     *  @param tree         The tree from which positions
     *                      are extracted for errors.
     *  @param m            The overriding method.
     *  @param other        The overridden method.
     *  @param origin       The class of which the overriding method
     *                      is a member.
     */
    void checkOverride(JCTree tree,
                       MethodSymbol m,
                       MethodSymbol other,
                       ClassSymbol origin) {
        // Don't check overriding of synthetic methods or by bridge methods.
        if ((m.flags() & (SYNTHETIC|BRIDGE)) != 0 || (other.flags() & SYNTHETIC) != 0) {
            return;
        }

        // Error if static method overrides instance method (JLS 8.4.6.2).
        if ((m.flags() & STATIC) != 0 &&
                   (other.flags() & STATIC) == 0) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree), "override.static",
                      cannotOverride(m, other));
            m.flags_field |= BAD_OVERRIDE;
            return;
        }

        // Error if instance method overrides static or final
        // method (JLS 8.4.6.1).
        if ((other.flags() & FINAL) != 0 ||
                 (m.flags() & STATIC) == 0 &&
                 (other.flags() & STATIC) != 0) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree), "override.meth",
                      cannotOverride(m, other),
                      asFlagSet(other.flags() & (FINAL | STATIC)));
            m.flags_field |= BAD_OVERRIDE;
            return;
        }

        if ((m.owner.flags() & ANNOTATION) != 0) {
            // handled in validateAnnotationMethod
            return;
        }

        // Error if overriding method has weaker access (JLS 8.4.6.3).
        if (protection(m.flags()) > protection(other.flags())) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree), "override.weaker.access",
                      cannotOverride(m, other),
                      (other.flags() & AccessFlags) == 0 ?
                          "package" :
                          asFlagSet(other.flags() & AccessFlags));
            m.flags_field |= BAD_OVERRIDE;
            return;
        }

        Type mt = types.memberType(origin.type, m);
        Type ot = types.memberType(origin.type, other);
        // Error if overriding result type is different
        // (or, in the case of generics mode, not a subtype) of
        // overridden result type. We have to rename any type parameters
        // before comparing types.
        List<Type> mtvars = mt.getTypeArguments();
        List<Type> otvars = ot.getTypeArguments();
        Type mtres = mt.getReturnType();
        Type otres = types.subst(ot.getReturnType(), otvars, mtvars);

        overrideWarner.clear();
        boolean resultTypesOK =
            types.returnTypeSubstitutable(mt, ot, otres, overrideWarner);
        if (!resultTypesOK) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                      "override.incompatible.ret",
                      cannotOverride(m, other),
                      mtres, otres);
            m.flags_field |= BAD_OVERRIDE;
            return;
        } else if (overrideWarner.hasNonSilentLint(LintCategory.UNCHECKED)) {
            warnUnchecked(TreeInfo.diagnosticPositionFor(m, tree),
                    "override.unchecked.ret",
                    uncheckedOverrides(m, other),
                    mtres, otres);
        }

        // Error if overriding method throws an exception not reported
        // by overridden method.
        List<Type> otthrown = types.subst(ot.getThrownTypes(), otvars, mtvars);
        List<Type> unhandledErased = unhandled(mt.getThrownTypes(), types.erasure(otthrown));
        List<Type> unhandledUnerased = unhandled(mt.getThrownTypes(), otthrown);
        if (unhandledErased.nonEmpty()) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                      "override.meth.doesnt.throw",
                      cannotOverride(m, other),
                      unhandledUnerased.head);
            m.flags_field |= BAD_OVERRIDE;
            return;
        }
        else if (unhandledUnerased.nonEmpty()) {
            warnUnchecked(TreeInfo.diagnosticPositionFor(m, tree),
                          "override.unchecked.thrown",
                         cannotOverride(m, other),
                         unhandledUnerased.head);
            return;
        }

        // Optional warning if varargs don't agree
        if ((((m.flags() ^ other.flags()) & Flags.VARARGS) != 0)
            && lint.isEnabled(LintCategory.OVERRIDES)) {
            log.warning(TreeInfo.diagnosticPositionFor(m, tree),
                        ((m.flags() & Flags.VARARGS) != 0)
                        ? "override.varargs.missing"
                        : "override.varargs.extra",
                        varargsOverrides(m, other));
        }

        // Warn if instance method overrides bridge method (compiler spec ??)
        if ((other.flags() & BRIDGE) != 0) {
            log.warning(TreeInfo.diagnosticPositionFor(m, tree), "override.bridge",
                        uncheckedOverrides(m, other));
        }

        // Warn if a deprecated method overridden by a non-deprecated one.
        if (!isDeprecatedOverrideIgnorable(other, origin)) {
            Lint prevLint = setLint(lint.augment(m));
            try {
                checkDeprecated(TreeInfo.diagnosticPositionFor(m, tree), m, other);
            } finally {
                setLint(prevLint);
            }
        }
    }
    // where
        private boolean isDeprecatedOverrideIgnorable(MethodSymbol m, ClassSymbol origin) {
            // If the method, m, is defined in an interface, then ignore the issue if the method
            // is only inherited via a supertype and also implemented in the supertype,
            // because in that case, we will rediscover the issue when examining the method
            // in the supertype.
            // If the method, m, is not defined in an interface, then the only time we need to
            // address the issue is when the method is the supertype implemementation: any other
            // case, we will have dealt with when examining the supertype classes
            ClassSymbol mc = m.enclClass();
            Type st = types.supertype(origin.type);
            if (!st.hasTag(CLASS))
                return true;
            MethodSymbol stimpl = m.implementation((ClassSymbol)st.tsym, types, false);

            if (mc != null && ((mc.flags() & INTERFACE) != 0)) {
                List<Type> intfs = types.interfaces(origin.type);
                return (intfs.contains(mc.type) ? false : (stimpl != null));
            }
            else
                return (stimpl != m);
        }


    // used to check if there were any unchecked conversions
    Warner overrideWarner = new Warner();

    /** Check that a class does not inherit two concrete methods
     *  with the same signature.
     *  @param pos          Position to be used for error reporting.
     *  @param site         The class type to be checked.
     */
    public void checkCompatibleConcretes(DiagnosticPosition pos, Type site) {
        Type sup = types.supertype(site);
        if (!sup.hasTag(CLASS)) return;

        for (Type t1 = sup;
             t1.hasTag(CLASS) && t1.tsym.type.isParameterized();
             t1 = types.supertype(t1)) {
            for (Symbol s1 : t1.tsym.members().getSymbols(NON_RECURSIVE)) {
                if (s1.kind != MTH ||
                    (s1.flags() & (STATIC|SYNTHETIC|BRIDGE)) != 0 ||
                    !s1.isInheritedIn(site.tsym, types) ||
                    ((MethodSymbol)s1).implementation(site.tsym,
                                                      types,
                                                      true) != s1)
                    continue;
                Type st1 = types.memberType(t1, s1);
                int s1ArgsLength = st1.getParameterTypes().length();
                if (st1 == s1.type) continue;

                for (Type t2 = sup;
                     t2.hasTag(CLASS);
                     t2 = types.supertype(t2)) {
                    for (Symbol s2 : t2.tsym.members().getSymbolsByName(s1.name)) {
                        if (s2 == s1 ||
                            s2.kind != MTH ||
                            (s2.flags() & (STATIC|SYNTHETIC|BRIDGE)) != 0 ||
                            s2.type.getParameterTypes().length() != s1ArgsLength ||
                            !s2.isInheritedIn(site.tsym, types) ||
                            ((MethodSymbol)s2).implementation(site.tsym,
                                                              types,
                                                              true) != s2)
                            continue;
                        Type st2 = types.memberType(t2, s2);
                        if (types.overrideEquivalent(st1, st2))
                            log.error(pos, "concrete.inheritance.conflict",
                                      s1, t1, s2, t2, sup);
                    }
                }
            }
        }
    }

    /** Check that classes (or interfaces) do not each define an abstract
     *  method with same name and arguments but incompatible return types.
     *  @param pos          Position to be used for error reporting.
     *  @param t1           The first argument type.
     *  @param t2           The second argument type.
     */
    public boolean checkCompatibleAbstracts(DiagnosticPosition pos,
                                            Type t1,
                                            Type t2,
                                            Type site) {
        if ((site.tsym.flags() & COMPOUND) != 0) {
            // special case for intersections: need to eliminate wildcards in supertypes
            t1 = types.capture(t1);
            t2 = types.capture(t2);
        }
        return firstIncompatibility(pos, t1, t2, site) == null;
    }

    /** Return the first method which is defined with same args
     *  but different return types in two given interfaces, or null if none
     *  exists.
     *  @param t1     The first type.
     *  @param t2     The second type.
     *  @param site   The most derived type.
     *  @returns symbol from t2 that conflicts with one in t1.
     */
    private Symbol firstIncompatibility(DiagnosticPosition pos, Type t1, Type t2, Type site) {
        Map<TypeSymbol,Type> interfaces1 = new HashMap<>();
        closure(t1, interfaces1);
        Map<TypeSymbol,Type> interfaces2;
        if (t1 == t2)
            interfaces2 = interfaces1;
        else
            closure(t2, interfaces1, interfaces2 = new HashMap<>());

        for (Type t3 : interfaces1.values()) {
            for (Type t4 : interfaces2.values()) {
                Symbol s = firstDirectIncompatibility(pos, t3, t4, site);
                if (s != null) return s;
            }
        }
        return null;
    }

    /** Compute all the supertypes of t, indexed by type symbol. */
    private void closure(Type t, Map<TypeSymbol,Type> typeMap) {
        if (!t.hasTag(CLASS)) return;
        if (typeMap.put(t.tsym, t) == null) {
            closure(types.supertype(t), typeMap);
            for (Type i : types.interfaces(t))
                closure(i, typeMap);
        }
    }

    /** Compute all the supertypes of t, indexed by type symbol (except thise in typesSkip). */
    private void closure(Type t, Map<TypeSymbol,Type> typesSkip, Map<TypeSymbol,Type> typeMap) {
        if (!t.hasTag(CLASS)) return;
        if (typesSkip.get(t.tsym) != null) return;
        if (typeMap.put(t.tsym, t) == null) {
            closure(types.supertype(t), typesSkip, typeMap);
            for (Type i : types.interfaces(t))
                closure(i, typesSkip, typeMap);
        }
    }

    /** Return the first method in t2 that conflicts with a method from t1. */
    private Symbol firstDirectIncompatibility(DiagnosticPosition pos, Type t1, Type t2, Type site) {
        for (Symbol s1 : t1.tsym.members().getSymbols(NON_RECURSIVE)) {
            Type st1 = null;
            if (s1.kind != MTH || !s1.isInheritedIn(site.tsym, types) ||
                    (s1.flags() & SYNTHETIC) != 0) continue;
            Symbol impl = ((MethodSymbol)s1).implementation(site.tsym, types, false);
            if (impl != null && (impl.flags() & ABSTRACT) == 0) continue;
            for (Symbol s2 : t2.tsym.members().getSymbolsByName(s1.name)) {
                if (s1 == s2) continue;
                if (s2.kind != MTH || !s2.isInheritedIn(site.tsym, types) ||
                        (s2.flags() & SYNTHETIC) != 0) continue;
                if (st1 == null) st1 = types.memberType(t1, s1);
                Type st2 = types.memberType(t2, s2);
                if (types.overrideEquivalent(st1, st2)) {
                    List<Type> tvars1 = st1.getTypeArguments();
                    List<Type> tvars2 = st2.getTypeArguments();
                    Type rt1 = st1.getReturnType();
                    Type rt2 = types.subst(st2.getReturnType(), tvars2, tvars1);
                    boolean compat =
                        types.isSameType(rt1, rt2) ||
                        !rt1.isPrimitiveOrVoid() &&
                        !rt2.isPrimitiveOrVoid() &&
                        (types.covariantReturnType(rt1, rt2, types.noWarnings) ||
                         types.covariantReturnType(rt2, rt1, types.noWarnings)) ||
                         checkCommonOverriderIn(s1,s2,site);
                    if (!compat) {
                        log.error(pos, "types.incompatible.diff.ret",
                            t1, t2, s2.name +
                            "(" + types.memberType(t2, s2).getParameterTypes() + ")");
                        return s2;
                    }
                } else if (checkNameClash((ClassSymbol)site.tsym, s1, s2) &&
                        !checkCommonOverriderIn(s1, s2, site)) {
                    log.error(pos,
                            "name.clash.same.erasure.no.override",
                            s1, s1.location(),
                            s2, s2.location());
                    return s2;
                }
            }
        }
        return null;
    }
    //WHERE
    boolean checkCommonOverriderIn(Symbol s1, Symbol s2, Type site) {
        Map<TypeSymbol,Type> supertypes = new HashMap<>();
        Type st1 = types.memberType(site, s1);
        Type st2 = types.memberType(site, s2);
        closure(site, supertypes);
        for (Type t : supertypes.values()) {
            for (Symbol s3 : t.tsym.members().getSymbolsByName(s1.name)) {
                if (s3 == s1 || s3 == s2 || s3.kind != MTH || (s3.flags() & (BRIDGE|SYNTHETIC)) != 0) continue;
                Type st3 = types.memberType(site,s3);
                if (types.overrideEquivalent(st3, st1) &&
                        types.overrideEquivalent(st3, st2) &&
                        types.returnTypeSubstitutable(st3, st1) &&
                        types.returnTypeSubstitutable(st3, st2)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Check that a given method conforms with any method it overrides.
     *  @param tree         The tree from which positions are extracted
     *                      for errors.
     *  @param m            The overriding method.
     */
    void checkOverride(Env<AttrContext> env, JCMethodDecl tree, MethodSymbol m) {
        ClassSymbol origin = (ClassSymbol)m.owner;
        if ((origin.flags() & ENUM) != 0 && names.finalize.equals(m.name))
            if (m.overrides(syms.enumFinalFinalize, origin, types, false)) {
                log.error(tree.pos(), "enum.no.finalize");
                return;
            }
        for (Type t = origin.type; t.hasTag(CLASS);
             t = types.supertype(t)) {
            if (t != origin.type) {
                checkOverride(tree, t, origin, m);
            }
            for (Type t2 : types.interfaces(t)) {
                checkOverride(tree, t2, origin, m);
            }
        }

        // Check if this method must override a super method due to being annotated with @Override
        // or by virtue of being a member of a diamond inferred anonymous class. Latter case is to
        // be treated "as if as they were annotated" with @Override.
        boolean mustOverride = m.attribute(syms.overrideType.tsym) != null ||
                (env.info.isAnonymousDiamond && !m.isConstructor() && !m.isPrivate());
        if (mustOverride && !isOverrider(m)) {
            DiagnosticPosition pos = tree.pos();
            for (JCAnnotation a : tree.getModifiers().annotations) {
                if (a.annotationType.type.tsym == syms.overrideType.tsym) {
                    pos = a.pos();
                    break;
                }
            }
            log.error(pos, "method.does.not.override.superclass");
        }
    }

    void checkOverride(JCTree tree, Type site, ClassSymbol origin, MethodSymbol m) {
        TypeSymbol c = site.tsym;
        for (Symbol sym : c.members().getSymbolsByName(m.name)) {
            if (m.overrides(sym, origin, types, false)) {
                if ((sym.flags() & ABSTRACT) == 0) {
                    checkOverride(tree, m, (MethodSymbol)sym, origin);
                }
            }
        }
    }

    private Filter<Symbol> equalsHasCodeFilter = new Filter<Symbol>() {
        public boolean accepts(Symbol s) {
            return MethodSymbol.implementation_filter.accepts(s) &&
                    (s.flags() & BAD_OVERRIDE) == 0;

        }
    };

    public void checkClassOverrideEqualsAndHashIfNeeded(DiagnosticPosition pos,
            ClassSymbol someClass) {
        /* At present, annotations cannot possibly have a method that is override
         * equivalent with Object.equals(Object) but in any case the condition is
         * fine for completeness.
         */
        if (someClass == (ClassSymbol)syms.objectType.tsym ||
            someClass.isInterface() || someClass.isEnum() ||
            (someClass.flags() & ANNOTATION) != 0 ||
            (someClass.flags() & ABSTRACT) != 0) return;
        //anonymous inner classes implementing interfaces need especial treatment
        if (someClass.isAnonymous()) {
            List<Type> interfaces =  types.interfaces(someClass.type);
            if (interfaces != null && !interfaces.isEmpty() &&
                interfaces.head.tsym == syms.comparatorType.tsym) return;
        }
        checkClassOverrideEqualsAndHash(pos, someClass);
    }

    private void checkClassOverrideEqualsAndHash(DiagnosticPosition pos,
            ClassSymbol someClass) {
        if (lint.isEnabled(LintCategory.OVERRIDES)) {
            MethodSymbol equalsAtObject = (MethodSymbol)syms.objectType
                    .tsym.members().findFirst(names.equals);
            MethodSymbol hashCodeAtObject = (MethodSymbol)syms.objectType
                    .tsym.members().findFirst(names.hashCode);
            boolean overridesEquals = types.implementation(equalsAtObject,
                someClass, false, equalsHasCodeFilter).owner == someClass;
            boolean overridesHashCode = types.implementation(hashCodeAtObject,
                someClass, false, equalsHasCodeFilter) != hashCodeAtObject;

            if (overridesEquals && !overridesHashCode) {
                log.warning(LintCategory.OVERRIDES, pos,
                        "override.equals.but.not.hashcode", someClass);
            }
        }
    }

    private boolean checkNameClash(ClassSymbol origin, Symbol s1, Symbol s2) {
        ClashFilter cf = new ClashFilter(origin.type);
        return (cf.accepts(s1) &&
                cf.accepts(s2) &&
                types.hasSameArgs(s1.erasure(types), s2.erasure(types)));
    }


    /** Check that all abstract members of given class have definitions.
     *  @param pos          Position to be used for error reporting.
     *  @param c            The class.
     */
    void checkAllDefined(DiagnosticPosition pos, ClassSymbol c) {
        MethodSymbol undef = types.firstUnimplementedAbstract(c);
        if (undef != null) {
            MethodSymbol undef1 =
                new MethodSymbol(undef.flags(), undef.name,
                                 types.memberType(c.type, undef), undef.owner);
            log.error(pos, "does.not.override.abstract",
                      c, undef1, undef1.location());
        }
    }

    void checkNonCyclicDecl(JCClassDecl tree) {
        CycleChecker cc = new CycleChecker();
        cc.scan(tree);
        if (!cc.errorFound && !cc.partialCheck) {
            tree.sym.flags_field |= ACYCLIC;
        }
    }

    class CycleChecker extends TreeScanner {

        List<Symbol> seenClasses = List.nil();
        boolean errorFound = false;
        boolean partialCheck = false;

        private void checkSymbol(DiagnosticPosition pos, Symbol sym) {
            if (sym != null && sym.kind == TYP) {
                Env<AttrContext> classEnv = enter.getEnv((TypeSymbol)sym);
                if (classEnv != null) {
                    DiagnosticSource prevSource = log.currentSource();
                    try {
                        log.useSource(classEnv.toplevel.sourcefile);
                        scan(classEnv.tree);
                    }
                    finally {
                        log.useSource(prevSource.getFile());
                    }
                } else if (sym.kind == TYP) {
                    checkClass(pos, sym, List.<JCTree>nil());
                }
            } else {
                //not completed yet
                partialCheck = true;
            }
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            super.visitSelect(tree);
            checkSymbol(tree.pos(), tree.sym);
        }

        @Override
        public void visitIdent(JCIdent tree) {
            checkSymbol(tree.pos(), tree.sym);
        }

        @Override
        public void visitTypeApply(JCTypeApply tree) {
            scan(tree.clazz);
        }

        @Override
        public void visitTypeArray(JCArrayTypeTree tree) {
            scan(tree.elemtype);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            List<JCTree> supertypes = List.nil();
            if (tree.getExtendsClause() != null) {
                supertypes = supertypes.prepend(tree.getExtendsClause());
            }
            if (tree.getImplementsClause() != null) {
                for (JCTree intf : tree.getImplementsClause()) {
                    supertypes = supertypes.prepend(intf);
                }
            }
            checkClass(tree.pos(), tree.sym, supertypes);
        }

        void checkClass(DiagnosticPosition pos, Symbol c, List<JCTree> supertypes) {
            if ((c.flags_field & ACYCLIC) != 0)
                return;
            if (seenClasses.contains(c)) {
                errorFound = true;
                noteCyclic(pos, (ClassSymbol)c);
            } else if (!c.type.isErroneous()) {
                try {
                    seenClasses = seenClasses.prepend(c);
                    if (c.type.hasTag(CLASS)) {
                        if (supertypes.nonEmpty()) {
                            scan(supertypes);
                        }
                        else {
                            ClassType ct = (ClassType)c.type;
                            if (ct.supertype_field == null ||
                                    ct.interfaces_field == null) {
                                //not completed yet
                                partialCheck = true;
                                return;
                            }
                            checkSymbol(pos, ct.supertype_field.tsym);
                            for (Type intf : ct.interfaces_field) {
                                checkSymbol(pos, intf.tsym);
                            }
                        }
                        if (c.owner.kind == TYP) {
                            checkSymbol(pos, c.owner);
                        }
                    }
                } finally {
                    seenClasses = seenClasses.tail;
                }
            }
        }
    }

    /** Check for cyclic references. Issue an error if the
     *  symbol of the type referred to has a LOCKED flag set.
     *
     *  @param pos      Position to be used for error reporting.
     *  @param t        The type referred to.
     */
    void checkNonCyclic(DiagnosticPosition pos, Type t) {
        checkNonCyclicInternal(pos, t);
    }


    void checkNonCyclic(DiagnosticPosition pos, TypeVar t) {
        checkNonCyclic1(pos, t, List.<TypeVar>nil());
    }

    private void checkNonCyclic1(DiagnosticPosition pos, Type t, List<TypeVar> seen) {
        final TypeVar tv;
        if  (t.hasTag(TYPEVAR) && (t.tsym.flags() & UNATTRIBUTED) != 0)
            return;
        if (seen.contains(t)) {
            tv = (TypeVar)t;
            tv.bound = types.createErrorType(t);
            log.error(pos, "cyclic.inheritance", t);
        } else if (t.hasTag(TYPEVAR)) {
            tv = (TypeVar)t;
            seen = seen.prepend(tv);
            for (Type b : types.getBounds(tv))
                checkNonCyclic1(pos, b, seen);
        }
    }

    /** Check for cyclic references. Issue an error if the
     *  symbol of the type referred to has a LOCKED flag set.
     *
     *  @param pos      Position to be used for error reporting.
     *  @param t        The type referred to.
     *  @returns        True if the check completed on all attributed classes
     */
    private boolean checkNonCyclicInternal(DiagnosticPosition pos, Type t) {
        boolean complete = true; // was the check complete?
        //- System.err.println("checkNonCyclicInternal("+t+");");//DEBUG
        Symbol c = t.tsym;
        if ((c.flags_field & ACYCLIC) != 0) return true;

        if ((c.flags_field & LOCKED) != 0) {
            noteCyclic(pos, (ClassSymbol)c);
        } else if (!c.type.isErroneous()) {
            try {
                c.flags_field |= LOCKED;
                if (c.type.hasTag(CLASS)) {
                    ClassType clazz = (ClassType)c.type;
                    if (clazz.interfaces_field != null)
                        for (List<Type> l=clazz.interfaces_field; l.nonEmpty(); l=l.tail)
                            complete &= checkNonCyclicInternal(pos, l.head);
                    if (clazz.supertype_field != null) {
                        Type st = clazz.supertype_field;
                        if (st != null && st.hasTag(CLASS))
                            complete &= checkNonCyclicInternal(pos, st);
                    }
                    if (c.owner.kind == TYP)
                        complete &= checkNonCyclicInternal(pos, c.owner.type);
                }
            } finally {
                c.flags_field &= ~LOCKED;
            }
        }
        if (complete)
            complete = ((c.flags_field & UNATTRIBUTED) == 0) && c.isCompleted();
        if (complete) c.flags_field |= ACYCLIC;
        return complete;
    }

    /** Note that we found an inheritance cycle. */
    private void noteCyclic(DiagnosticPosition pos, ClassSymbol c) {
        log.error(pos, "cyclic.inheritance", c);
        for (List<Type> l=types.interfaces(c.type); l.nonEmpty(); l=l.tail)
            l.head = types.createErrorType((ClassSymbol)l.head.tsym, Type.noType);
        Type st = types.supertype(c.type);
        if (st.hasTag(CLASS))
            ((ClassType)c.type).supertype_field = types.createErrorType((ClassSymbol)st.tsym, Type.noType);
        c.type = types.createErrorType(c, c.type);
        c.flags_field |= ACYCLIC;
    }

    /** Check that all methods which implement some
     *  method conform to the method they implement.
     *  @param tree         The class definition whose members are checked.
     */
    void checkImplementations(JCClassDecl tree) {
        checkImplementations(tree, tree.sym, tree.sym);
    }
    //where
        /** Check that all methods which implement some
         *  method in `ic' conform to the method they implement.
         */
        void checkImplementations(JCTree tree, ClassSymbol origin, ClassSymbol ic) {
            for (List<Type> l = types.closure(ic.type); l.nonEmpty(); l = l.tail) {
                ClassSymbol lc = (ClassSymbol)l.head.tsym;
                if ((lc.flags() & ABSTRACT) != 0) {
                    for (Symbol sym : lc.members().getSymbols(NON_RECURSIVE)) {
                        if (sym.kind == MTH &&
                            (sym.flags() & (STATIC|ABSTRACT)) == ABSTRACT) {
                            MethodSymbol absmeth = (MethodSymbol)sym;
                            MethodSymbol implmeth = absmeth.implementation(origin, types, false);
                            if (implmeth != null && implmeth != absmeth &&
                                (implmeth.owner.flags() & INTERFACE) ==
                                (origin.flags() & INTERFACE)) {
                                // don't check if implmeth is in a class, yet
                                // origin is an interface. This case arises only
                                // if implmeth is declared in Object. The reason is
                                // that interfaces really don't inherit from
                                // Object it's just that the compiler represents
                                // things that way.
                                checkOverride(tree, implmeth, absmeth, origin);
                            }
                        }
                    }
                }
            }
        }

    /** Check that all abstract methods implemented by a class are
     *  mutually compatible.
     *  @param pos          Position to be used for error reporting.
     *  @param c            The class whose interfaces are checked.
     */
    void checkCompatibleSupertypes(DiagnosticPosition pos, Type c) {
        List<Type> supertypes = types.interfaces(c);
        Type supertype = types.supertype(c);
        if (supertype.hasTag(CLASS) &&
            (supertype.tsym.flags() & ABSTRACT) != 0)
            supertypes = supertypes.prepend(supertype);
        for (List<Type> l = supertypes; l.nonEmpty(); l = l.tail) {
            if (!l.head.getTypeArguments().isEmpty() &&
                !checkCompatibleAbstracts(pos, l.head, l.head, c))
                return;
            for (List<Type> m = supertypes; m != l; m = m.tail)
                if (!checkCompatibleAbstracts(pos, l.head, m.head, c))
                    return;
        }
        checkCompatibleConcretes(pos, c);
    }

    void checkConflicts(DiagnosticPosition pos, Symbol sym, TypeSymbol c) {
        for (Type ct = c.type; ct != Type.noType ; ct = types.supertype(ct)) {
            for (Symbol sym2 : ct.tsym.members().getSymbolsByName(sym.name, NON_RECURSIVE)) {
                // VM allows methods and variables with differing types
                if (sym.kind == sym2.kind &&
                    types.isSameType(types.erasure(sym.type), types.erasure(sym2.type)) &&
                    sym != sym2 &&
                    (sym.flags() & Flags.SYNTHETIC) != (sym2.flags() & Flags.SYNTHETIC) &&
                    (sym.flags() & BRIDGE) == 0 && (sym2.flags() & BRIDGE) == 0) {
                    syntheticError(pos, (sym2.flags() & SYNTHETIC) == 0 ? sym2 : sym);
                    return;
                }
            }
        }
    }

    /** Check that all non-override equivalent methods accessible from 'site'
     *  are mutually compatible (JLS 8.4.8/9.4.1).
     *
     *  @param pos  Position to be used for error reporting.
     *  @param site The class whose methods are checked.
     *  @param sym  The method symbol to be checked.
     */
    void checkOverrideClashes(DiagnosticPosition pos, Type site, MethodSymbol sym) {
         ClashFilter cf = new ClashFilter(site);
        //for each method m1 that is overridden (directly or indirectly)
        //by method 'sym' in 'site'...

        List<MethodSymbol> potentiallyAmbiguousList = List.nil();
        boolean overridesAny = false;
        for (Symbol m1 : types.membersClosure(site, false).getSymbolsByName(sym.name, cf)) {
            if (!sym.overrides(m1, site.tsym, types, false)) {
                if (m1 == sym) {
                    continue;
                }

                if (!overridesAny) {
                    potentiallyAmbiguousList = potentiallyAmbiguousList.prepend((MethodSymbol)m1);
                }
                continue;
            }

            if (m1 != sym) {
                overridesAny = true;
                potentiallyAmbiguousList = List.nil();
            }

            //...check each method m2 that is a member of 'site'
            for (Symbol m2 : types.membersClosure(site, false).getSymbolsByName(sym.name, cf)) {
                if (m2 == m1) continue;
                //if (i) the signature of 'sym' is not a subsignature of m1 (seen as
                //a member of 'site') and (ii) m1 has the same erasure as m2, issue an error
                if (!types.isSubSignature(sym.type, types.memberType(site, m2), allowStrictMethodClashCheck) &&
                        types.hasSameArgs(m2.erasure(types), m1.erasure(types))) {
                    sym.flags_field |= CLASH;
                    String key = m1 == sym ?
                            "name.clash.same.erasure.no.override" :
                            "name.clash.same.erasure.no.override.1";
                    log.error(pos,
                            key,
                            sym, sym.location(),
                            m2, m2.location(),
                            m1, m1.location());
                    return;
                }
            }
        }

        if (!overridesAny) {
            for (MethodSymbol m: potentiallyAmbiguousList) {
                checkPotentiallyAmbiguousOverloads(pos, site, sym, m);
            }
        }
    }

    /** Check that all static methods accessible from 'site' are
     *  mutually compatible (JLS 8.4.8).
     *
     *  @param pos  Position to be used for error reporting.
     *  @param site The class whose methods are checked.
     *  @param sym  The method symbol to be checked.
     */
    void checkHideClashes(DiagnosticPosition pos, Type site, MethodSymbol sym) {
        ClashFilter cf = new ClashFilter(site);
        //for each method m1 that is a member of 'site'...
        for (Symbol s : types.membersClosure(site, true).getSymbolsByName(sym.name, cf)) {
            //if (i) the signature of 'sym' is not a subsignature of m1 (seen as
            //a member of 'site') and (ii) 'sym' has the same erasure as m1, issue an error
            if (!types.isSubSignature(sym.type, types.memberType(site, s), allowStrictMethodClashCheck)) {
                if (types.hasSameArgs(s.erasure(types), sym.erasure(types))) {
                    log.error(pos,
                            "name.clash.same.erasure.no.hide",
                            sym, sym.location(),
                            s, s.location());
                    return;
                } else {
                    checkPotentiallyAmbiguousOverloads(pos, site, sym, (MethodSymbol)s);
                }
            }
         }
     }

     //where
     private class ClashFilter implements Filter<Symbol> {

         Type site;

         ClashFilter(Type site) {
             this.site = site;
         }

         boolean shouldSkip(Symbol s) {
             return (s.flags() & CLASH) != 0 &&
                s.owner == site.tsym;
         }

         public boolean accepts(Symbol s) {
             return s.kind == MTH &&
                     (s.flags() & SYNTHETIC) == 0 &&
                     !shouldSkip(s) &&
                     s.isInheritedIn(site.tsym, types) &&
                     !s.isConstructor();
         }
     }

    void checkDefaultMethodClashes(DiagnosticPosition pos, Type site) {
        DefaultMethodClashFilter dcf = new DefaultMethodClashFilter(site);
        for (Symbol m : types.membersClosure(site, false).getSymbols(dcf)) {
            Assert.check(m.kind == MTH);
            List<MethodSymbol> prov = types.interfaceCandidates(site, (MethodSymbol)m);
            if (prov.size() > 1) {
                ListBuffer<Symbol> abstracts = new ListBuffer<>();
                ListBuffer<Symbol> defaults = new ListBuffer<>();
                for (MethodSymbol provSym : prov) {
                    if ((provSym.flags() & DEFAULT) != 0) {
                        defaults = defaults.append(provSym);
                    } else if ((provSym.flags() & ABSTRACT) != 0) {
                        abstracts = abstracts.append(provSym);
                    }
                    if (defaults.nonEmpty() && defaults.size() + abstracts.size() >= 2) {
                        //strong semantics - issue an error if two sibling interfaces
                        //have two override-equivalent defaults - or if one is abstract
                        //and the other is default
                        String errKey;
                        Symbol s1 = defaults.first();
                        Symbol s2;
                        if (defaults.size() > 1) {
                            errKey = "types.incompatible.unrelated.defaults";
                            s2 = defaults.toList().tail.head;
                        } else {
                            errKey = "types.incompatible.abstract.default";
                            s2 = abstracts.first();
                        }
                        log.error(pos, errKey,
                                Kinds.kindName(site.tsym), site,
                                m.name, types.memberType(site, m).getParameterTypes(),
                                s1.location(), s2.location());
                        break;
                    }
                }
            }
        }
    }

    //where
     private class DefaultMethodClashFilter implements Filter<Symbol> {

         Type site;

         DefaultMethodClashFilter(Type site) {
             this.site = site;
         }

         public boolean accepts(Symbol s) {
             return s.kind == MTH &&
                     (s.flags() & DEFAULT) != 0 &&
                     s.isInheritedIn(site.tsym, types) &&
                     !s.isConstructor();
         }
     }

    /**
      * Report warnings for potentially ambiguous method declarations. Two declarations
      * are potentially ambiguous if they feature two unrelated functional interface
      * in same argument position (in which case, a call site passing an implicit
      * lambda would be ambiguous).
      */
    void checkPotentiallyAmbiguousOverloads(DiagnosticPosition pos, Type site,
            MethodSymbol msym1, MethodSymbol msym2) {
        if (msym1 != msym2 &&
                allowDefaultMethods &&
                lint.isEnabled(LintCategory.OVERLOADS) &&
                (msym1.flags() & POTENTIALLY_AMBIGUOUS) == 0 &&
                (msym2.flags() & POTENTIALLY_AMBIGUOUS) == 0) {
            Type mt1 = types.memberType(site, msym1);
            Type mt2 = types.memberType(site, msym2);
            //if both generic methods, adjust type variables
            if (mt1.hasTag(FORALL) && mt2.hasTag(FORALL) &&
                    types.hasSameBounds((ForAll)mt1, (ForAll)mt2)) {
                mt2 = types.subst(mt2, ((ForAll)mt2).tvars, ((ForAll)mt1).tvars);
            }
            //expand varargs methods if needed
            int maxLength = Math.max(mt1.getParameterTypes().length(), mt2.getParameterTypes().length());
            List<Type> args1 = rs.adjustArgs(mt1.getParameterTypes(), msym1, maxLength, true);
            List<Type> args2 = rs.adjustArgs(mt2.getParameterTypes(), msym2, maxLength, true);
            //if arities don't match, exit
            if (args1.length() != args2.length()) return;
            boolean potentiallyAmbiguous = false;
            while (args1.nonEmpty() && args2.nonEmpty()) {
                Type s = args1.head;
                Type t = args2.head;
                if (!types.isSubtype(t, s) && !types.isSubtype(s, t)) {
                    if (types.isFunctionalInterface(s) && types.isFunctionalInterface(t) &&
                            types.findDescriptorType(s).getParameterTypes().length() > 0 &&
                            types.findDescriptorType(s).getParameterTypes().length() ==
                            types.findDescriptorType(t).getParameterTypes().length()) {
                        potentiallyAmbiguous = true;
                    } else {
                        break;
                    }
                }
                args1 = args1.tail;
                args2 = args2.tail;
            }
            if (potentiallyAmbiguous) {
                //we found two incompatible functional interfaces with same arity
                //this means a call site passing an implicit lambda would be ambigiuous
                msym1.flags_field |= POTENTIALLY_AMBIGUOUS;
                msym2.flags_field |= POTENTIALLY_AMBIGUOUS;
                log.warning(LintCategory.OVERLOADS, pos, "potentially.ambiguous.overload",
                            msym1, msym1.location(),
                            msym2, msym2.location());
                return;
            }
        }
    }

    void checkElemAccessFromSerializableLambda(final JCTree tree) {
        if (warnOnAccessToSensitiveMembers) {
            Symbol sym = TreeInfo.symbol(tree);
            if (!sym.kind.matches(KindSelector.VAL_MTH)) {
                return;
            }

            if (sym.kind == VAR) {
                if ((sym.flags() & PARAMETER) != 0 ||
                    sym.isLocal() ||
                    sym.name == names._this ||
                    sym.name == names._super) {
                    return;
                }
            }

            if (!types.isSubtype(sym.owner.type, syms.serializableType) &&
                    isEffectivelyNonPublic(sym)) {
                log.warning(tree.pos(),
                        "access.to.sensitive.member.from.serializable.element", sym);
            }
        }
    }

    private boolean isEffectivelyNonPublic(Symbol sym) {
        if (sym.packge() == syms.rootPackage) {
            return false;
        }

        while (sym.kind != PCK) {
            if ((sym.flags() & PUBLIC) == 0) {
                return true;
            }
            sym = sym.owner;
        }
        return false;
    }

    /** Report a conflict between a user symbol and a synthetic symbol.
     */
    private void syntheticError(DiagnosticPosition pos, Symbol sym) {
        if (!sym.type.isErroneous()) {
            if (warnOnSyntheticConflicts) {
                log.warning(pos, "synthetic.name.conflict", sym, sym.location());
            }
            else {
                log.error(pos, "synthetic.name.conflict", sym, sym.location());
            }
        }
    }

    /** Check that class c does not implement directly or indirectly
     *  the same parameterized interface with two different argument lists.
     *  @param pos          Position to be used for error reporting.
     *  @param type         The type whose interfaces are checked.
     */
    void checkClassBounds(DiagnosticPosition pos, Type type) {
        checkClassBounds(pos, new HashMap<TypeSymbol,Type>(), type);
    }
//where
        /** Enter all interfaces of type `type' into the hash table `seensofar'
         *  with their class symbol as key and their type as value. Make
         *  sure no class is entered with two different types.
         */
        void checkClassBounds(DiagnosticPosition pos,
                              Map<TypeSymbol,Type> seensofar,
                              Type type) {
            if (type.isErroneous()) return;
            for (List<Type> l = types.interfaces(type); l.nonEmpty(); l = l.tail) {
                Type it = l.head;
                Type oldit = seensofar.put(it.tsym, it);
                if (oldit != null) {
                    List<Type> oldparams = oldit.allparams();
                    List<Type> newparams = it.allparams();
                    if (!types.containsTypeEquivalent(oldparams, newparams))
                        log.error(pos, "cant.inherit.diff.arg",
                                  it.tsym, Type.toString(oldparams),
                                  Type.toString(newparams));
                }
                checkClassBounds(pos, seensofar, it);
            }
            Type st = types.supertype(type);
            if (st != Type.noType) checkClassBounds(pos, seensofar, st);
        }

    /** Enter interface into into set.
     *  If it existed already, issue a "repeated interface" error.
     */
    void checkNotRepeated(DiagnosticPosition pos, Type it, Set<Type> its) {
        if (its.contains(it))
            log.error(pos, "repeated.interface");
        else {
            its.add(it);
        }
    }

/* *************************************************************************
 * Check annotations
 **************************************************************************/

    /**
     * Recursively validate annotations values
     */
    void validateAnnotationTree(JCTree tree) {
        class AnnotationValidator extends TreeScanner {
            @Override
            public void visitAnnotation(JCAnnotation tree) {
                if (!tree.type.isErroneous()) {
                    super.visitAnnotation(tree);
                    validateAnnotation(tree);
                }
            }
        }
        tree.accept(new AnnotationValidator());
    }

    /**
     *  {@literal
     *  Annotation types are restricted to primitives, String, an
     *  enum, an annotation, Class, Class<?>, Class<? extends
     *  Anything>, arrays of the preceding.
     *  }
     */
    void validateAnnotationType(JCTree restype) {
        // restype may be null if an error occurred, so don't bother validating it
        if (restype != null) {
            validateAnnotationType(restype.pos(), restype.type);
        }
    }

    void validateAnnotationType(DiagnosticPosition pos, Type type) {
        if (type.isPrimitive()) return;
        if (types.isSameType(type, syms.stringType)) return;
        if ((type.tsym.flags() & Flags.ENUM) != 0) return;
        if ((type.tsym.flags() & Flags.ANNOTATION) != 0) return;
        if (types.cvarLowerBound(type).tsym == syms.classType.tsym) return;
        if (types.isArray(type) && !types.isArray(types.elemtype(type))) {
            validateAnnotationType(pos, types.elemtype(type));
            return;
        }
        log.error(pos, "invalid.annotation.member.type");
    }

    /**
     * "It is also a compile-time error if any method declared in an
     * annotation type has a signature that is override-equivalent to
     * that of any public or protected method declared in class Object
     * or in the interface annotation.Annotation."
     *
     * @jls 9.6 Annotation Types
     */
    void validateAnnotationMethod(DiagnosticPosition pos, MethodSymbol m) {
        for (Type sup = syms.annotationType; sup.hasTag(CLASS); sup = types.supertype(sup)) {
            Scope s = sup.tsym.members();
            for (Symbol sym : s.getSymbolsByName(m.name)) {
                if (sym.kind == MTH &&
                    (sym.flags() & (PUBLIC | PROTECTED)) != 0 &&
                    types.overrideEquivalent(m.type, sym.type))
                    log.error(pos, "intf.annotation.member.clash", sym, sup);
            }
        }
    }

    /** Check the annotations of a symbol.
     */
    public void validateAnnotations(List<JCAnnotation> annotations, Symbol s) {
        for (JCAnnotation a : annotations)
            validateAnnotation(a, s);
    }

    /** Check the type annotations.
     */
    public void validateTypeAnnotations(List<JCAnnotation> annotations, boolean isTypeParameter) {
        for (JCAnnotation a : annotations)
            validateTypeAnnotation(a, isTypeParameter);
    }

    /** Check an annotation of a symbol.
     */
    private void validateAnnotation(JCAnnotation a, Symbol s) {
        validateAnnotationTree(a);

        if (!annotationApplicable(a, s))
            log.error(a.pos(), "annotation.type.not.applicable");

        if (a.annotationType.type.tsym == syms.functionalInterfaceType.tsym) {
            if (s.kind != TYP) {
                log.error(a.pos(), "bad.functional.intf.anno");
            } else if (!s.isInterface() || (s.flags() & ANNOTATION) != 0) {
                log.error(a.pos(), "bad.functional.intf.anno.1", diags.fragment("not.a.functional.intf", s));
            }
        }
    }

    public void validateTypeAnnotation(JCAnnotation a, boolean isTypeParameter) {
        Assert.checkNonNull(a.type);
        validateAnnotationTree(a);

        if (a.hasTag(TYPE_ANNOTATION) &&
                !a.annotationType.type.isErroneous() &&
                !isTypeAnnotation(a, isTypeParameter)) {
            log.error(a.pos(), Errors.AnnotationTypeNotApplicableToType(a.type));
        }
    }

    /**
     * Validate the proposed container 'repeatable' on the
     * annotation type symbol 's'. Report errors at position
     * 'pos'.
     *
     * @param s The (annotation)type declaration annotated with a @Repeatable
     * @param repeatable the @Repeatable on 's'
     * @param pos where to report errors
     */
    public void validateRepeatable(TypeSymbol s, Attribute.Compound repeatable, DiagnosticPosition pos) {
        Assert.check(types.isSameType(repeatable.type, syms.repeatableType));

        Type t = null;
        List<Pair<MethodSymbol,Attribute>> l = repeatable.values;
        if (!l.isEmpty()) {
            Assert.check(l.head.fst.name == names.value);
            t = ((Attribute.Class)l.head.snd).getValue();
        }

        if (t == null) {
            // errors should already have been reported during Annotate
            return;
        }

        validateValue(t.tsym, s, pos);
        validateRetention(t.tsym, s, pos);
        validateDocumented(t.tsym, s, pos);
        validateInherited(t.tsym, s, pos);
        validateTarget(t.tsym, s, pos);
        validateDefault(t.tsym, pos);
    }

    private void validateValue(TypeSymbol container, TypeSymbol contained, DiagnosticPosition pos) {
        Symbol sym = container.members().findFirst(names.value);
        if (sym != null && sym.kind == MTH) {
            MethodSymbol m = (MethodSymbol) sym;
            Type ret = m.getReturnType();
            if (!(ret.hasTag(ARRAY) && types.isSameType(((ArrayType)ret).elemtype, contained.type))) {
                log.error(pos, "invalid.repeatable.annotation.value.return",
                        container, ret, types.makeArrayType(contained.type));
            }
        } else {
            log.error(pos, "invalid.repeatable.annotation.no.value", container);
        }
    }

    private void validateRetention(TypeSymbol container, TypeSymbol contained, DiagnosticPosition pos) {
        Attribute.RetentionPolicy containerRetention = types.getRetention(container);
        Attribute.RetentionPolicy containedRetention = types.getRetention(contained);

        boolean error = false;
        switch (containedRetention) {
        case RUNTIME:
            if (containerRetention != Attribute.RetentionPolicy.RUNTIME) {
                error = true;
            }
            break;
        case CLASS:
            if (containerRetention == Attribute.RetentionPolicy.SOURCE)  {
                error = true;
            }
        }
        if (error ) {
            log.error(pos, "invalid.repeatable.annotation.retention",
                      container, containerRetention,
                      contained, containedRetention);
        }
    }

    private void validateDocumented(Symbol container, Symbol contained, DiagnosticPosition pos) {
        if (contained.attribute(syms.documentedType.tsym) != null) {
            if (container.attribute(syms.documentedType.tsym) == null) {
                log.error(pos, "invalid.repeatable.annotation.not.documented", container, contained);
            }
        }
    }

    private void validateInherited(Symbol container, Symbol contained, DiagnosticPosition pos) {
        if (contained.attribute(syms.inheritedType.tsym) != null) {
            if (container.attribute(syms.inheritedType.tsym) == null) {
                log.error(pos, "invalid.repeatable.annotation.not.inherited", container, contained);
            }
        }
    }

    private void validateTarget(TypeSymbol container, TypeSymbol contained, DiagnosticPosition pos) {
        // The set of targets the container is applicable to must be a subset
        // (with respect to annotation target semantics) of the set of targets
        // the contained is applicable to. The target sets may be implicit or
        // explicit.

        Set<Name> containerTargets;
        Attribute.Array containerTarget = getAttributeTargetAttribute(container);
        if (containerTarget == null) {
            containerTargets = getDefaultTargetSet();
        } else {
            containerTargets = new HashSet<>();
            for (Attribute app : containerTarget.values) {
                if (!(app instanceof Attribute.Enum)) {
                    continue; // recovery
                }
                Attribute.Enum e = (Attribute.Enum)app;
                containerTargets.add(e.value.name);
            }
        }

        Set<Name> containedTargets;
        Attribute.Array containedTarget = getAttributeTargetAttribute(contained);
        if (containedTarget == null) {
            containedTargets = getDefaultTargetSet();
        } else {
            containedTargets = new HashSet<>();
            for (Attribute app : containedTarget.values) {
                if (!(app instanceof Attribute.Enum)) {
                    continue; // recovery
                }
                Attribute.Enum e = (Attribute.Enum)app;
                containedTargets.add(e.value.name);
            }
        }

        if (!isTargetSubsetOf(containerTargets, containedTargets)) {
            log.error(pos, "invalid.repeatable.annotation.incompatible.target", container, contained);
        }
    }

    /* get a set of names for the default target */
    private Set<Name> getDefaultTargetSet() {
        if (defaultTargets == null) {
            Set<Name> targets = new HashSet<>();
            targets.add(names.ANNOTATION_TYPE);
            targets.add(names.CONSTRUCTOR);
            targets.add(names.FIELD);
            targets.add(names.LOCAL_VARIABLE);
            targets.add(names.METHOD);
            targets.add(names.PACKAGE);
            targets.add(names.PARAMETER);
            targets.add(names.TYPE);

            defaultTargets = java.util.Collections.unmodifiableSet(targets);
        }

        return defaultTargets;
    }
    private Set<Name> defaultTargets;


    /** Checks that s is a subset of t, with respect to ElementType
     * semantics, specifically {ANNOTATION_TYPE} is a subset of {TYPE},
     * and {TYPE_USE} covers the set {ANNOTATION_TYPE, TYPE, TYPE_USE,
     * TYPE_PARAMETER}.
     */
    private boolean isTargetSubsetOf(Set<Name> s, Set<Name> t) {
        // Check that all elements in s are present in t
        for (Name n2 : s) {
            boolean currentElementOk = false;
            for (Name n1 : t) {
                if (n1 == n2) {
                    currentElementOk = true;
                    break;
                } else if (n1 == names.TYPE && n2 == names.ANNOTATION_TYPE) {
                    currentElementOk = true;
                    break;
                } else if (n1 == names.TYPE_USE &&
                        (n2 == names.TYPE ||
                         n2 == names.ANNOTATION_TYPE ||
                         n2 == names.TYPE_PARAMETER)) {
                    currentElementOk = true;
                    break;
                }
            }
            if (!currentElementOk)
                return false;
        }
        return true;
    }

    private void validateDefault(Symbol container, DiagnosticPosition pos) {
        // validate that all other elements of containing type has defaults
        Scope scope = container.members();
        for(Symbol elm : scope.getSymbols()) {
            if (elm.name != names.value &&
                elm.kind == MTH &&
                ((MethodSymbol)elm).defaultValue == null) {
                log.error(pos,
                          "invalid.repeatable.annotation.elem.nondefault",
                          container,
                          elm);
            }
        }
    }

    /** Is s a method symbol that overrides a method in a superclass? */
    boolean isOverrider(Symbol s) {
        if (s.kind != MTH || s.isStatic())
            return false;
        MethodSymbol m = (MethodSymbol)s;
        TypeSymbol owner = (TypeSymbol)m.owner;
        for (Type sup : types.closure(owner.type)) {
            if (sup == owner.type)
                continue; // skip "this"
            Scope scope = sup.tsym.members();
            for (Symbol sym : scope.getSymbolsByName(m.name)) {
                if (!sym.isStatic() && m.overrides(sym, owner, types, true))
                    return true;
            }
        }
        return false;
    }

    /** Is the annotation applicable to types? */
    protected boolean isTypeAnnotation(JCAnnotation a, boolean isTypeParameter) {
        List<Attribute> targets = typeAnnotations.annotationTargets(a.annotationType.type.tsym);
        return (targets == null) ?
                false :
                targets.stream()
                        .anyMatch(attr -> isTypeAnnotation(attr, isTypeParameter));
    }
    //where
        boolean isTypeAnnotation(Attribute a, boolean isTypeParameter) {
            Attribute.Enum e = (Attribute.Enum)a;
            return (e.value.name == names.TYPE_USE ||
                    (isTypeParameter && e.value.name == names.TYPE_PARAMETER));
        }

    /** Is the annotation applicable to the symbol? */
    boolean annotationApplicable(JCAnnotation a, Symbol s) {
        Attribute.Array arr = getAttributeTargetAttribute(a.annotationType.type.tsym);
        Name[] targets;

        if (arr == null) {
            targets = defaultTargetMetaInfo(a, s);
        } else {
            // TODO: can we optimize this?
            targets = new Name[arr.values.length];
            for (int i=0; i<arr.values.length; ++i) {
                Attribute app = arr.values[i];
                if (!(app instanceof Attribute.Enum)) {
                    return true; // recovery
                }
                Attribute.Enum e = (Attribute.Enum) app;
                targets[i] = e.value.name;
            }
        }
        for (Name target : targets) {
            if (target == names.TYPE) {
                if (s.kind == TYP)
                    return true;
            } else if (target == names.FIELD) {
                if (s.kind == VAR && s.owner.kind != MTH)
                    return true;
            } else if (target == names.METHOD) {
                if (s.kind == MTH && !s.isConstructor())
                    return true;
            } else if (target == names.PARAMETER) {
                if (s.kind == VAR && s.owner.kind == MTH &&
                      (s.flags() & PARAMETER) != 0) {
                    return true;
                }
            } else if (target == names.CONSTRUCTOR) {
                if (s.kind == MTH && s.isConstructor())
                    return true;
            } else if (target == names.LOCAL_VARIABLE) {
                if (s.kind == VAR && s.owner.kind == MTH &&
                      (s.flags() & PARAMETER) == 0) {
                    return true;
                }
            } else if (target == names.ANNOTATION_TYPE) {
                if (s.kind == TYP && (s.flags() & ANNOTATION) != 0) {
                    return true;
                }
            } else if (target == names.PACKAGE) {
                if (s.kind == PCK)
                    return true;
            } else if (target == names.TYPE_USE) {
                if (s.kind == TYP || s.kind == VAR ||
                        (s.kind == MTH && !s.isConstructor() &&
                                !s.type.getReturnType().hasTag(VOID)) ||
                        (s.kind == MTH && s.isConstructor())) {
                    return true;
                }
            } else if (target == names.TYPE_PARAMETER) {
                if (s.kind == TYP && s.type.hasTag(TYPEVAR))
                    return true;
            } else
                return true; // Unknown ElementType. This should be an error at declaration site,
                             // assume applicable.
        }
        return false;
    }


    Attribute.Array getAttributeTargetAttribute(TypeSymbol s) {
        Attribute.Compound atTarget = s.getAnnotationTypeMetadata().getTarget();
        if (atTarget == null) return null; // ok, is applicable
        Attribute atValue = atTarget.member(names.value);
        if (!(atValue instanceof Attribute.Array)) return null; // error recovery
        return (Attribute.Array) atValue;
    }

    private final Name[] dfltTargetMeta;
    private Name[] defaultTargetMetaInfo(JCAnnotation a, Symbol s) {
        return dfltTargetMeta;
    }

    /** Check an annotation value.
     *
     * @param a The annotation tree to check
     * @return true if this annotation tree is valid, otherwise false
     */
    public boolean validateAnnotationDeferErrors(JCAnnotation a) {
        boolean res = false;
        final Log.DiagnosticHandler diagHandler = new Log.DiscardDiagnosticHandler(log);
        try {
            res = validateAnnotation(a);
        } finally {
            log.popDiagnosticHandler(diagHandler);
        }
        return res;
    }

    private boolean validateAnnotation(JCAnnotation a) {
        boolean isValid = true;
        AnnotationTypeMetadata metadata = a.annotationType.type.tsym.getAnnotationTypeMetadata();

        // collect an inventory of the annotation elements
        Set<MethodSymbol> elements = metadata.getAnnotationElements();

        // remove the ones that are assigned values
        for (JCTree arg : a.args) {
            if (!arg.hasTag(ASSIGN)) continue; // recovery
            JCAssign assign = (JCAssign)arg;
            Symbol m = TreeInfo.symbol(assign.lhs);
            if (m == null || m.type.isErroneous()) continue;
            if (!elements.remove(m)) {
                isValid = false;
                log.error(assign.lhs.pos(), "duplicate.annotation.member.value",
                        m.name, a.type);
            }
        }

        // all the remaining ones better have default values
        List<Name> missingDefaults = List.nil();
        Set<MethodSymbol> membersWithDefault = metadata.getAnnotationElementsWithDefault();
        for (MethodSymbol m : elements) {
            if (m.type.isErroneous())
                continue;

            if (!membersWithDefault.contains(m))
                missingDefaults = missingDefaults.append(m.name);
        }
        missingDefaults = missingDefaults.reverse();
        if (missingDefaults.nonEmpty()) {
            isValid = false;
            String key = (missingDefaults.size() > 1)
                    ? "annotation.missing.default.value.1"
                    : "annotation.missing.default.value";
            log.error(a.pos(), key, a.type, missingDefaults);
        }

        return isValid && validateTargetAnnotationValue(a);
    }

    /* Validate the special java.lang.annotation.Target annotation */
    boolean validateTargetAnnotationValue(JCAnnotation a) {
        // special case: java.lang.annotation.Target must not have
        // repeated values in its value member
        if (a.annotationType.type.tsym != syms.annotationTargetType.tsym ||
                a.args.tail == null)
            return true;

        boolean isValid = true;
        if (!a.args.head.hasTag(ASSIGN)) return false; // error recovery
        JCAssign assign = (JCAssign) a.args.head;
        Symbol m = TreeInfo.symbol(assign.lhs);
        if (m.name != names.value) return false;
        JCTree rhs = assign.rhs;
        if (!rhs.hasTag(NEWARRAY)) return false;
        JCNewArray na = (JCNewArray) rhs;
        Set<Symbol> targets = new HashSet<>();
        for (JCTree elem : na.elems) {
            if (!targets.add(TreeInfo.symbol(elem))) {
                isValid = false;
                log.error(elem.pos(), "repeated.annotation.target");
            }
        }
        return isValid;
    }

    void checkDeprecatedAnnotation(DiagnosticPosition pos, Symbol s) {
        if (lint.isEnabled(LintCategory.DEP_ANN) &&
            (s.flags() & DEPRECATED) != 0 &&
            !syms.deprecatedType.isErroneous() &&
            s.attribute(syms.deprecatedType.tsym) == null) {
            log.warning(LintCategory.DEP_ANN,
                    pos, "missing.deprecated.annotation");
        }
    }

    void checkDeprecated(final DiagnosticPosition pos, final Symbol other, final Symbol s) {
        if ((s.flags() & DEPRECATED) != 0 &&
                (other.flags() & DEPRECATED) == 0 &&
                s.outermostClass() != other.outermostClass()) {
            deferredLintHandler.report(new DeferredLintHandler.LintLogger() {
                @Override
                public void report() {
                    warnDeprecated(pos, s);
                }
            });
        }
    }

    void checkSunAPI(final DiagnosticPosition pos, final Symbol s) {
        if ((s.flags() & PROPRIETARY) != 0) {
            deferredLintHandler.report(new DeferredLintHandler.LintLogger() {
                public void report() {
                    if (enableSunApiLintControl)
                      warnSunApi(pos, "sun.proprietary", s);
                    else
                      log.mandatoryWarning(pos, "sun.proprietary", s);
                }
            });
        }
    }

    void checkProfile(final DiagnosticPosition pos, final Symbol s) {
        if (profile != Profile.DEFAULT && (s.flags() & NOT_IN_PROFILE) != 0) {
            log.error(pos, "not.in.profile", s, profile);
        }
    }

/* *************************************************************************
 * Check for recursive annotation elements.
 **************************************************************************/

    /** Check for cycles in the graph of annotation elements.
     */
    void checkNonCyclicElements(JCClassDecl tree) {
        if ((tree.sym.flags_field & ANNOTATION) == 0) return;
        Assert.check((tree.sym.flags_field & LOCKED) == 0);
        try {
            tree.sym.flags_field |= LOCKED;
            for (JCTree def : tree.defs) {
                if (!def.hasTag(METHODDEF)) continue;
                JCMethodDecl meth = (JCMethodDecl)def;
                checkAnnotationResType(meth.pos(), meth.restype.type);
            }
        } finally {
            tree.sym.flags_field &= ~LOCKED;
            tree.sym.flags_field |= ACYCLIC_ANN;
        }
    }

    void checkNonCyclicElementsInternal(DiagnosticPosition pos, TypeSymbol tsym) {
        if ((tsym.flags_field & ACYCLIC_ANN) != 0)
            return;
        if ((tsym.flags_field & LOCKED) != 0) {
            log.error(pos, "cyclic.annotation.element");
            return;
        }
        try {
            tsym.flags_field |= LOCKED;
            for (Symbol s : tsym.members().getSymbols(NON_RECURSIVE)) {
                if (s.kind != MTH)
                    continue;
                checkAnnotationResType(pos, ((MethodSymbol)s).type.getReturnType());
            }
        } finally {
            tsym.flags_field &= ~LOCKED;
            tsym.flags_field |= ACYCLIC_ANN;
        }
    }

    void checkAnnotationResType(DiagnosticPosition pos, Type type) {
        switch (type.getTag()) {
        case CLASS:
            if ((type.tsym.flags() & ANNOTATION) != 0)
                checkNonCyclicElementsInternal(pos, type.tsym);
            break;
        case ARRAY:
            checkAnnotationResType(pos, types.elemtype(type));
            break;
        default:
            break; // int etc
        }
    }

/* *************************************************************************
 * Check for cycles in the constructor call graph.
 **************************************************************************/

    /** Check for cycles in the graph of constructors calling other
     *  constructors.
     */
    void checkCyclicConstructors(JCClassDecl tree) {
        Map<Symbol,Symbol> callMap = new HashMap<>();

        // enter each constructor this-call into the map
        for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
            JCMethodInvocation app = TreeInfo.firstConstructorCall(l.head);
            if (app == null) continue;
            JCMethodDecl meth = (JCMethodDecl) l.head;
            if (TreeInfo.name(app.meth) == names._this) {
                callMap.put(meth.sym, TreeInfo.symbol(app.meth));
            } else {
                meth.sym.flags_field |= ACYCLIC;
            }
        }

        // Check for cycles in the map
        Symbol[] ctors = new Symbol[0];
        ctors = callMap.keySet().toArray(ctors);
        for (Symbol caller : ctors) {
            checkCyclicConstructor(tree, caller, callMap);
        }
    }

    /** Look in the map to see if the given constructor is part of a
     *  call cycle.
     */
    private void checkCyclicConstructor(JCClassDecl tree, Symbol ctor,
                                        Map<Symbol,Symbol> callMap) {
        if (ctor != null && (ctor.flags_field & ACYCLIC) == 0) {
            if ((ctor.flags_field & LOCKED) != 0) {
                log.error(TreeInfo.diagnosticPositionFor(ctor, tree),
                          "recursive.ctor.invocation");
            } else {
                ctor.flags_field |= LOCKED;
                checkCyclicConstructor(tree, callMap.remove(ctor), callMap);
                ctor.flags_field &= ~LOCKED;
            }
            ctor.flags_field |= ACYCLIC;
        }
    }

/* *************************************************************************
 * Miscellaneous
 **************************************************************************/

    /**
     *  Check for division by integer constant zero
     *  @param pos           Position for error reporting.
     *  @param operator      The operator for the expression
     *  @param operand       The right hand operand for the expression
     */
    void checkDivZero(final DiagnosticPosition pos, Symbol operator, Type operand) {
        if (operand.constValue() != null
            && operand.getTag().isSubRangeOf(LONG)
            && ((Number) (operand.constValue())).longValue() == 0) {
            int opc = ((OperatorSymbol)operator).opcode;
            if (opc == ByteCodes.idiv || opc == ByteCodes.imod
                || opc == ByteCodes.ldiv || opc == ByteCodes.lmod) {
                deferredLintHandler.report(new DeferredLintHandler.LintLogger() {
                    @Override
                    public void report() {
                        warnDivZero(pos);
                    }
                });
            }
        }
    }

    /**
     * Check for empty statements after if
     */
    void checkEmptyIf(JCIf tree) {
        if (tree.thenpart.hasTag(SKIP) && tree.elsepart == null &&
                lint.isEnabled(LintCategory.EMPTY))
            log.warning(LintCategory.EMPTY, tree.thenpart.pos(), "empty.if");
    }

    /** Check that symbol is unique in given scope.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope.
     */
    boolean checkUnique(DiagnosticPosition pos, Symbol sym, Scope s) {
        if (sym.type.isErroneous())
            return true;
        if (sym.owner.name == names.any) return false;
        for (Symbol byName : s.getSymbolsByName(sym.name, NON_RECURSIVE)) {
            if (sym != byName &&
                    (byName.flags() & CLASH) == 0 &&
                    sym.kind == byName.kind &&
                    sym.name != names.error &&
                    (sym.kind != MTH ||
                     types.hasSameArgs(sym.type, byName.type) ||
                     types.hasSameArgs(types.erasure(sym.type), types.erasure(byName.type)))) {
                if ((sym.flags() & VARARGS) != (byName.flags() & VARARGS)) {
                    varargsDuplicateError(pos, sym, byName);
                    return true;
                } else if (sym.kind == MTH && !types.hasSameArgs(sym.type, byName.type, false)) {
                    duplicateErasureError(pos, sym, byName);
                    sym.flags_field |= CLASH;
                    return true;
                } else {
                    duplicateError(pos, byName);
                    return false;
                }
            }
        }
        return true;
    }

    /** Report duplicate declaration error.
     */
    void duplicateErasureError(DiagnosticPosition pos, Symbol sym1, Symbol sym2) {
        if (!sym1.type.isErroneous() && !sym2.type.isErroneous()) {
            log.error(pos, "name.clash.same.erasure", sym1, sym2);
        }
    }

    /**Check that types imported through the ordinary imports don't clash with types imported
     * by other (static or ordinary) imports. Note that two static imports may import two clashing
     * types without an error on the imports.
     * @param toplevel       The toplevel tree for which the test should be performed.
     */
    void checkImportsUnique(JCCompilationUnit toplevel) {
        WriteableScope ordinallyImportedSoFar = WriteableScope.create(toplevel.packge);
        WriteableScope staticallyImportedSoFar = WriteableScope.create(toplevel.packge);
        WriteableScope topLevelScope = toplevel.toplevelScope;

        for (JCTree def : toplevel.defs) {
            if (!def.hasTag(IMPORT))
                continue;

            JCImport imp = (JCImport) def;

            if (imp.importScope == null)
                continue;

            for (Symbol sym : imp.importScope.getSymbols(sym -> sym.kind == TYP)) {
                if (imp.isStatic()) {
                    checkUniqueImport(imp.pos(), ordinallyImportedSoFar, staticallyImportedSoFar, topLevelScope, sym, true);
                    staticallyImportedSoFar.enter(sym);
                } else {
                    checkUniqueImport(imp.pos(), ordinallyImportedSoFar, staticallyImportedSoFar, topLevelScope, sym, false);
                    ordinallyImportedSoFar.enter(sym);
                }
            }

            imp.importScope = null;
        }
    }

    /** Check that single-type import is not already imported or top-level defined,
     *  but make an exception for two single-type imports which denote the same type.
     *  @param pos                     Position for error reporting.
     *  @param ordinallyImportedSoFar  A Scope containing types imported so far through
     *                                 ordinary imports.
     *  @param staticallyImportedSoFar A Scope containing types imported so far through
     *                                 static imports.
     *  @param topLevelScope           The current file's top-level Scope
     *  @param sym                     The symbol.
     *  @param staticImport            Whether or not this was a static import
     */
    private boolean checkUniqueImport(DiagnosticPosition pos, Scope ordinallyImportedSoFar,
                                      Scope staticallyImportedSoFar, Scope topLevelScope,
                                      Symbol sym, boolean staticImport) {
        Filter<Symbol> duplicates = candidate -> candidate != sym && !candidate.type.isErroneous();
        Symbol clashing = ordinallyImportedSoFar.findFirst(sym.name, duplicates);
        if (clashing == null && !staticImport) {
            clashing = staticallyImportedSoFar.findFirst(sym.name, duplicates);
        }
        if (clashing != null) {
            if (staticImport)
                log.error(pos, "already.defined.static.single.import", clashing);
            else
                log.error(pos, "already.defined.single.import", clashing);
            return false;
        }
        clashing = topLevelScope.findFirst(sym.name, duplicates);
        if (clashing != null) {
            log.error(pos, "already.defined.this.unit", clashing);
            return false;
        }
        return true;
    }

    /** Check that a qualified name is in canonical form (for import decls).
     */
    public void checkCanonical(JCTree tree) {
        if (!isCanonical(tree))
            log.error(tree.pos(), "import.requires.canonical",
                      TreeInfo.symbol(tree));
    }
        // where
        private boolean isCanonical(JCTree tree) {
            while (tree.hasTag(SELECT)) {
                JCFieldAccess s = (JCFieldAccess) tree;
                if (s.sym.owner != TreeInfo.symbol(s.selected))
                    return false;
                tree = s.selected;
            }
            return true;
        }

    /** Check that an auxiliary class is not accessed from any other file than its own.
     */
    void checkForBadAuxiliaryClassAccess(DiagnosticPosition pos, Env<AttrContext> env, ClassSymbol c) {
        if (lint.isEnabled(Lint.LintCategory.AUXILIARYCLASS) &&
            (c.flags() & AUXILIARY) != 0 &&
            rs.isAccessible(env, c) &&
            !fileManager.isSameFile(c.sourcefile, env.toplevel.sourcefile))
        {
            log.warning(pos, "auxiliary.class.accessed.from.outside.of.its.source.file",
                        c, c.sourcefile);
        }
    }

    private class ConversionWarner extends Warner {
        final String uncheckedKey;
        final Type found;
        final Type expected;
        public ConversionWarner(DiagnosticPosition pos, String uncheckedKey, Type found, Type expected) {
            super(pos);
            this.uncheckedKey = uncheckedKey;
            this.found = found;
            this.expected = expected;
        }

        @Override
        public void warn(LintCategory lint) {
            boolean warned = this.warned;
            super.warn(lint);
            if (warned) return; // suppress redundant diagnostics
            switch (lint) {
                case UNCHECKED:
                    Check.this.warnUnchecked(pos(), "prob.found.req", diags.fragment(uncheckedKey), found, expected);
                    break;
                case VARARGS:
                    if (method != null &&
                            method.attribute(syms.trustMeType.tsym) != null &&
                            isTrustMeAllowedOnMethod(method) &&
                            !types.isReifiable(method.type.getParameterTypes().last())) {
                        Check.this.warnUnsafeVararg(pos(), "varargs.unsafe.use.varargs.param", method.params.last());
                    }
                    break;
                default:
                    throw new AssertionError("Unexpected lint: " + lint);
            }
        }
    }

    public Warner castWarner(DiagnosticPosition pos, Type found, Type expected) {
        return new ConversionWarner(pos, "unchecked.cast.to.type", found, expected);
    }

    public Warner convertWarner(DiagnosticPosition pos, Type found, Type expected) {
        return new ConversionWarner(pos, "unchecked.assign", found, expected);
    }

    public void checkFunctionalInterface(JCClassDecl tree, ClassSymbol cs) {
        Compound functionalType = cs.attribute(syms.functionalInterfaceType.tsym);

        if (functionalType != null) {
            try {
                types.findDescriptorSymbol((TypeSymbol)cs);
            } catch (Types.FunctionDescriptorLookupError ex) {
                DiagnosticPosition pos = tree.pos();
                for (JCAnnotation a : tree.getModifiers().annotations) {
                    if (a.annotationType.type.tsym == syms.functionalInterfaceType.tsym) {
                        pos = a.pos();
                        break;
                    }
                }
                log.error(pos, "bad.functional.intf.anno.1", ex.getDiagnostic());
            }
        }
    }

    public void checkImportsResolvable(final JCCompilationUnit toplevel) {
        for (final JCImport imp : toplevel.getImports()) {
            if (!imp.staticImport || !imp.qualid.hasTag(SELECT))
                continue;
            final JCFieldAccess select = (JCFieldAccess) imp.qualid;
            final Symbol origin;
            if (select.name == names.asterisk || (origin = TreeInfo.symbol(select.selected)) == null || origin.kind != TYP)
                continue;

            TypeSymbol site = (TypeSymbol) TreeInfo.symbol(select.selected);
            if (!checkTypeContainsImportableElement(site, site, toplevel.packge, select.name, new HashSet<Symbol>())) {
                log.error(imp.pos(), "cant.resolve.location",
                          KindName.STATIC,
                          select.name, List.<Type>nil(), List.<Type>nil(),
                          Kinds.typeKindName(TreeInfo.symbol(select.selected).type),
                          TreeInfo.symbol(select.selected).type);
            }
        }
    }

    // Check that packages imported are in scope (JLS 7.4.3, 6.3, 6.5.3.1, 6.5.3.2)
    public void checkImportedPackagesObservable(final JCCompilationUnit toplevel) {
        for (JCImport imp : toplevel.getImports()) {
            if (!imp.staticImport && TreeInfo.name(imp.qualid) == names.asterisk) {
                TypeSymbol tsym = ((JCFieldAccess)imp.qualid).selected.type.tsym;
                if (tsym.kind == PCK && tsym.members().isEmpty() && !tsym.exists()) {
                    log.error(DiagnosticFlag.RESOLVE_ERROR, imp.pos, "doesnt.exist", tsym);
                }
            }
        }
    }

    private boolean checkTypeContainsImportableElement(TypeSymbol tsym, TypeSymbol origin, PackageSymbol packge, Name name, Set<Symbol> processed) {
        if (tsym == null || !processed.add(tsym))
            return false;

            // also search through inherited names
        if (checkTypeContainsImportableElement(types.supertype(tsym.type).tsym, origin, packge, name, processed))
            return true;

        for (Type t : types.interfaces(tsym.type))
            if (checkTypeContainsImportableElement(t.tsym, origin, packge, name, processed))
                return true;

        for (Symbol sym : tsym.members().getSymbolsByName(name)) {
            if (sym.isStatic() &&
                importAccessible(sym, packge) &&
                sym.isMemberOf(origin, types)) {
                return true;
            }
        }

        return false;
    }

    // is the sym accessible everywhere in packge?
    public boolean importAccessible(Symbol sym, PackageSymbol packge) {
        try {
            int flags = (int)(sym.flags() & AccessFlags);
            switch (flags) {
            default:
            case PUBLIC:
                return true;
            case PRIVATE:
                return false;
            case 0:
            case PROTECTED:
                return sym.packge() == packge;
            }
        } catch (ClassFinder.BadClassFile err) {
            throw err;
        } catch (CompletionFailure ex) {
            return false;
        }
    }

}
