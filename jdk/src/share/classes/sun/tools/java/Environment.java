/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.util.Stack;
import java.io.IOException;
import sun.tools.tree.Context;
//JCOV
import java.io.File;
//end JCOV

/**
 * This class defines the environment for a compilation.
 * It is used to load classes, resolve class names and
 * report errors. It is an abstract class, a subclass
 * must define implementations for some of the functions.<p>
 *
 * An environment has a source object associated with it.
 * This is the thing against which errors are reported, it
 * is usually a file name, a field or a class.<p>
 *
 * Environments can be nested to change the source object.<p>
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Arthur van Hoff
 */

public class Environment implements Constants {
    /**
     * The actual environment to which everything is forwarded.
     */
    Environment env;

    /**
     * External character encoding name
     */
    String encoding;

    /**
     * The object that is currently being parsed/compiled.
     * It is either a file name (String) or a field (MemberDefinition)
     * or a class (ClassDeclaration or ClassDefinition).
     */
    Object source;

    public Environment(Environment env, Object source) {
        if (env != null && env.env != null && env.getClass() == this.getClass())
            env = env.env;      // a small optimization
        this.env = env;
        this.source = source;
    }
    public Environment() {
        this(null, null);
    }

    /**
     * Tells whether an Identifier refers to a package which should be
     * exempt from the "exists" check in Imports#resolve().
     */
    public boolean isExemptPackage(Identifier id) {
        return env.isExemptPackage(id);
    }

    /**
     * Return a class declaration given a fully qualified class name.
     */
    public ClassDeclaration getClassDeclaration(Identifier nm) {
        return env.getClassDeclaration(nm);
    }

    /**
     * Return a class definition given a fully qualified class name.
     * <p>
     * Should be called only with 'internal' class names, i.e., the result
     * of a call to 'resolveName' or a synthetic class name.
     */
    public final ClassDefinition getClassDefinition(Identifier nm) throws ClassNotFound {
        if (nm.isInner()) {
            ClassDefinition c = getClassDefinition(nm.getTopName());
            Identifier tail = nm.getFlatName();
        walkTail:
            while (tail.isQualified()) {
                tail = tail.getTail();
                Identifier head = tail.getHead();
                //System.out.println("CLASS: " + c + " HEAD: " + head + " TAIL: " + tail);
                String hname = head.toString();
                // If the name is of the form 'ClassName.N$localName', where N is
                // a number, the field 'N$localName' may not necessarily be a member
                // of the class named by 'ClassName', but might be a member of some
                // inaccessible class contained within it.  We use 'getLocalClass'
                // to do the lookup in this case.  This is part of a fix for bugid
                // 4054523 and 4030421.  See also 'BatchEnvironment.makeClassDefinition'.
                // This should also work for anonymous class names of the form
                // 'ClassName.N'.  Note that the '.' qualifications get converted to
                // '$' characters when determining the external name of the class and
                // the name of the class file.
                if (hname.length() > 0
                    && Character.isDigit(hname.charAt(0))) {
                    ClassDefinition localClass = c.getLocalClass(hname);
                    if (localClass != null) {
                        c = localClass;
                        continue walkTail;
                    }
                } else {
                    for (MemberDefinition f = c.getFirstMatch(head);
                         f != null; f = f.getNextMatch()) {
                        if (f.isInnerClass()) {
                            c = f.getInnerClass();
                            continue walkTail;
                        }
                    }
                }
                throw new ClassNotFound(Identifier.lookupInner(c.getName(), head));
            }
            //System.out.println("FOUND " + c + " FOR " + nm);
            return c;
        }
        return getClassDeclaration(nm).getClassDefinition(this);
    }


    /**
     * Return a class declaration given a type. Only works for
     * class types.
     */
    public ClassDeclaration getClassDeclaration(Type t) {
        return getClassDeclaration(t.getClassName());
    }

    /**
     * Return a class definition given a type. Only works for
     * class types.
     */
    public final ClassDefinition getClassDefinition(Type t) throws ClassNotFound {
        return getClassDefinition(t.getClassName());
    }

    /**
     * Check if a class exists (without actually loading it).
     * (Since inner classes cannot in general be examined without
     * loading source, this method does not accept inner names.)
     */
    public boolean classExists(Identifier nm) {
        return env.classExists(nm);
    }

    public final boolean classExists(Type t) {
        return !t.isType(TC_CLASS) || classExists(t.getClassName());
    }

    /**
     * Get the package path for a package
     */
    public Package getPackage(Identifier pkg) throws IOException {
        return env.getPackage(pkg);
    }

    /**
     * Load the definition of a class.
     */
    public void loadDefinition(ClassDeclaration c) {
        env.loadDefinition(c);
    }

    /**
     * Return the source of the environment (ie: the thing being compiled/parsed).
     */
    public final Object getSource() {
        return source;
    }

    /**
     * Resolve a type. Make sure that all the classes referred to by
     * the type have a definition.  Report errors.  Return true if
     * the type is well-formed.  Presently used for types appearing
     * in member declarations, which represent named types internally as
     * qualified identifiers.  Type names appearing in local variable
     * declarations and within expressions are represented as identifier
     * or field expressions, and are resolved by 'toType', which delegates
     * handling of the non-inner portion of the name to this method.
     * <p>
     * In 'toType', the various stages of qualification are represented by
     * separate AST nodes.  Here, we are given a single identifier which
     * contains the entire qualification structure.  It is not possible in
     * general to set the error location to the exact position of a component
     * that is in error, so an error message must refer to the entire qualified
     * name.  An attempt to keep track of the string length of the components of
     * the name and to offset the location accordingly fails because the initial
     * prefix of the name may have been rewritten by an earlier call to
     * 'resolveName'.  See 'SourceMember.resolveTypeStructure'.  The situation
     * is actually even worse than this, because only a single location is
     * passed in for an entire declaration, which may contain many type names.
     * All error messages are thus poorly localized.  These checks should be
     * done while traversing the parse tree for the type, not the type descriptor.
     * <p>
     * DESIGN NOTE:
     * As far as I can tell, the two-stage resolution of names represented in
     * string form is an artifact of the late implementation of inner classes
     * and the use of mangled names internally within the compiler.  All
     * qualified names should have their hiearchical structure made explicit
     * in the parse tree at the phase at which they are presented for static
     * semantic checking.  This would affect class names appearing in 'extends',
     * 'implements', and 'throws' clauses, as well as in member declarations.
     */
    public boolean resolve(long where, ClassDefinition c, Type t) {
        switch (t.getTypeCode()) {
          case TC_CLASS: {
            ClassDefinition def;
            try {
                Identifier nm = t.getClassName();
                if (!nm.isQualified() && !nm.isInner() && !classExists(nm)) {
                    resolve(nm);        // elicit complaints about ambiguity
                }
                def = getQualifiedClassDefinition(where, nm, c, false);
                if (!c.canAccess(this, def.getClassDeclaration())) {
                    // Reported error location may be imprecise
                    // if the name is qualified.
                    error(where, "cant.access.class", def);
                    return true; // return false later
                }
                def.noteUsedBy(c, where, env);
            } catch (AmbiguousClass ee) {
                error(where, "ambig.class", ee.name1, ee.name2);
                return false;
            } catch (ClassNotFound e) {
                // For now, report "class.and.package" only when the code
                // is going to fail anyway.
                try {
                    if (e.name.isInner() &&
                            getPackage(e.name.getTopName()).exists()) {
                        env.error(where, "class.and.package",
                                  e.name.getTopName());
                    }
                } catch (IOException ee) {
                    env.error(where, "io.exception", "package check");
                }
                // This error message is also emitted for 'new' expressions.
                // error(where, "class.not.found", e.name, "declaration");
                error(where, "class.not.found.no.context", e.name);
                return false;
            }
            return true;
          }

          case TC_ARRAY:
            return resolve(where, c, t.getElementType());

          case TC_METHOD:
            boolean ok = resolve(where, c, t.getReturnType());
            Type args[] = t.getArgumentTypes();
            for (int i = args.length ; i-- > 0 ; ) {
                ok &= resolve(where, c, args[i]);
            }
            return ok;
        }
        return true;
    }

    /**
     * Given its fully-qualified name, verify that a class is defined and accessible.
     * Used to check components of qualified names in contexts where a class is expected.
     * Like 'resolve', but is given a single type name, not a type descriptor.
     */
    public boolean resolveByName(long where, ClassDefinition c, Identifier nm) {
        return resolveByName(where, c, nm, false);
    }

    public boolean resolveExtendsByName(long where, ClassDefinition c, Identifier nm) {
        return resolveByName(where, c, nm, true);
    }

    private boolean resolveByName(long where, ClassDefinition c,
                                 Identifier nm, boolean isExtends) {
        ClassDefinition def;
        try {
            if (!nm.isQualified() && !nm.isInner() && !classExists(nm)) {
                resolve(nm);    // elicit complaints about ambiguity
            }
            def = getQualifiedClassDefinition(where, nm, c, isExtends);
            ClassDeclaration decl = def.getClassDeclaration();
            if (!((!isExtends && c.canAccess(this, decl))
                  ||
                  (isExtends && c.extendsCanAccess(this, decl)))) {
                error(where, "cant.access.class", def);
                return true; // return false later
            }
        } catch (AmbiguousClass ee) {
            error(where, "ambig.class", ee.name1, ee.name2);
            return false;
        } catch (ClassNotFound e) {
            // For now, report "class.and.package" only when the code
            // is going to fail anyway.
            try {
                if (e.name.isInner() &&
                    getPackage(e.name.getTopName()).exists()) {
                    env.error(where, "class.and.package",
                              e.name.getTopName());
                }
            } catch (IOException ee) {
                env.error(where, "io.exception", "package check");
            }
            error(where, "class.not.found", e.name, "type name");
            return false;
        }
        return true;
    }

    /**
     * Like 'getClassDefinition(env)', but check access on each component.
     * Currently called only by 'resolve' above.  It is doubtful that calls
     * to 'getClassDefinition(env)' are appropriate now.
     */
    public final ClassDefinition
    getQualifiedClassDefinition(long where,
                                Identifier nm,
                                ClassDefinition ctxClass,
                                boolean isExtends) throws ClassNotFound {
        if (nm.isInner()) {
            ClassDefinition c = getClassDefinition(nm.getTopName());
            Identifier tail = nm.getFlatName();
        walkTail:
            while (tail.isQualified()) {
                tail = tail.getTail();
                Identifier head = tail.getHead();
                // System.out.println("CLASS: " + c + " HEAD: " + head + " TAIL: " + tail);
                String hname = head.toString();
                // Handle synthesized names of local and anonymous classes.
                // See 'getClassDefinition(env)' above.
                if (hname.length() > 0
                    && Character.isDigit(hname.charAt(0))) {
                    ClassDefinition localClass = c.getLocalClass(hname);
                    if (localClass != null) {
                        c = localClass;
                        continue walkTail;
                    }
                } else {
                    for (MemberDefinition f = c.getFirstMatch(head);
                         f != null; f = f.getNextMatch()) {
                        if (f.isInnerClass()) {
                            ClassDeclaration rdecl = c.getClassDeclaration();
                            c = f.getInnerClass();
                            ClassDeclaration fdecl = c.getClassDeclaration();
                            // This check is presumably applicable even if the
                            // original source-code name (expanded by 'resolveNames')
                            // was a simple, unqualified name.  Hopefully, JLS 2e
                            // will clarify the matter.
                            if ((!isExtends
                                 && !ctxClass.canAccess(env, fdecl))
                                ||
                                (isExtends
                                 && !ctxClass.extendsCanAccess(env, fdecl))) {
                                // Reported error location is imprecise.
                                env.error(where, "no.type.access", head, rdecl, ctxClass);
                            }
                            // The JLS 6.6.2 restrictions on access to protected members
                            // depend in an essential way upon the syntactic form of the name.
                            // Since the compiler has previously expanded the class names
                            // here into fully-qualified form ('resolveNames'), this check
                            // cannot be performed here.  Unfortunately, the original names
                            // are clobbered during 'basicCheck', which is also the phase that
                            // resolves the inheritance structure, required to implement the
                            // access restrictions.  Pending a large-scale revision of the
                            // name-resolution machinery, we forgo this check, with the result
                            // that the JLS 6.6.2 restrictions are not enforced for some cases
                            // of qualified access to inner classes.  Some qualified names are
                            // resolved elsewhere via a different mechanism, and will be
                            // treated correctly -- see 'FieldExpression.checkCommon'.
                            /*---------------------------------------*
                            if (f.isProtected()) {
                                Type rty = Type.tClass(rdecl.getName()); // hack
                                if (!ctxClass.protectedAccess(env, f, rty)) {
                                    // Reported error location is imprecise.
                                    env.error(where, "invalid.protected.type.use",
                                              head, ctxClass, rty);
                                }
                            }
                            *---------------------------------------*/
                            continue walkTail;
                        }
                    }
                }
                throw new ClassNotFound(Identifier.lookupInner(c.getName(), head));
            }
            //System.out.println("FOUND " + c + " FOR " + nm);
            return c;
        }
        return getClassDeclaration(nm).getClassDefinition(this);
    }

    /**
     * Resolve the names within a type, returning the adjusted type.
     * Adjust class names to reflect scoping.
     * Do not report errors.
     * <p>
     * NOTE: It would be convenient to check for errors here, such as
     * verifying that each component of a qualified name exists and is
     * accessible.  Why must this be done in a separate phase?
     * <p>
     * If the 'synth' argument is true, indicating that the member whose
     * type is being resolved is synthetic, names are resolved with respect
     * to the package scope.  (Fix for 4097882)
     */
    public Type resolveNames(ClassDefinition c, Type t, boolean synth) {
        if (tracing) dtEvent("Environment.resolveNames: " + c + ", " + t);
        switch (t.getTypeCode()) {
          case TC_CLASS: {
            Identifier name = t.getClassName();
            Identifier rname;
            if (synth) {
                rname = resolvePackageQualifiedName(name);
            } else {
                rname = c.resolveName(this, name);
            }
            if (name != rname) {
                t = Type.tClass(rname);
            }
            break;
          }

          case TC_ARRAY:
            t = Type.tArray(resolveNames(c, t.getElementType(), synth));
            break;

          case TC_METHOD: {
            Type ret = t.getReturnType();
            Type rret = resolveNames(c, ret, synth);
            Type args[] = t.getArgumentTypes();
            Type rargs[] = new Type[args.length];
            boolean changed = (ret != rret);
            for (int i = args.length ; i-- > 0 ; ) {
                Type arg = args[i];
                Type rarg = resolveNames(c, arg, synth);
                rargs[i] = rarg;
                if (arg != rarg) {
                    changed = true;
                }
            }
            if (changed) {
                t = Type.tMethod(rret, rargs);
            }
            break;
          }
        }
        return t;
    }

    /**
     * Resolve a class name, using only package and import directives.
     * Report no errors.
     * <p>
     */
    public Identifier resolveName(Identifier name) {
        // This logic is pretty exactly parallel to that of
        // ClassDefinition.resolveName().
        if (name.isQualified()) {
            // Try to resolve the first identifier component,
            // because inner class names take precedence over
            // package prefixes.  (Cf. ClassDefinition.resolveName.)
            Identifier rhead = resolveName(name.getHead());

            if (rhead.hasAmbigPrefix()) {
                // The first identifier component refers to an
                // ambiguous class.  Limp on.  We throw away the
                // rest of the classname as it is irrelevant.
                // (part of solution for 4059855).
                return rhead;
            }

            if (!this.classExists(rhead)) {
                return this.resolvePackageQualifiedName(name);
            }
            try {
                return this.getClassDefinition(rhead).
                    resolveInnerClass(this, name.getTail());
            } catch (ClassNotFound ee) {
                // return partially-resolved name someone else can fail on
                return Identifier.lookupInner(rhead, name.getTail());
            }
        }
        try {
            return resolve(name);
        } catch (AmbiguousClass ee) {
            // Don't force a resolution of the name if it is ambiguous.
            // Forcing the resolution would tack the current package
            // name onto the front of the class, which would be wrong.
            // Instead, mark the name as ambiguous and let a later stage
            // find the error by calling env.resolve(name).
            // (part of solution for 4059855).

            if (name.hasAmbigPrefix()) {
                return name;
            } else {
                return name.addAmbigPrefix();
            }
        } catch (ClassNotFound ee) {
            // last chance to make something halfway sensible
            Imports imports = getImports();
            if (imports != null)
                return imports.forceResolve(this, name);
        }
        return name;
    }

    /**
     * Discover if name consists of a package prefix, followed by the
     * name of a class (that actually exists), followed possibly by
     * some inner class names.  If we can't find a class that exists,
     * return the name unchanged.
     * <p>
     * This routine is used after a class name fails to
     * be resolved by means of imports or inner classes.
     * However, import processing uses this routine directly,
     * since import names must be exactly qualified to start with.
     */
    public final Identifier resolvePackageQualifiedName(Identifier name) {
        Identifier tail = null;
        for (;;) {
            if (classExists(name)) {
                break;
            }
            if (!name.isQualified()) {
                name = (tail == null) ? name : Identifier.lookup(name, tail);
                tail = null;
                break;
            }
            Identifier nm = name.getName();
            tail = (tail == null)? nm: Identifier.lookup(nm, tail);
            name = name.getQualifier();
        }
        if (tail != null)
            name = Identifier.lookupInner(name, tail);
        return name;
    }

    /**
     * Resolve a class name, using only package and import directives.
     */
    public Identifier resolve(Identifier nm) throws ClassNotFound {
        if (env == null)  return nm;    // a pretty useless no-op
        return env.resolve(nm);
    }

    /**
     * Get the imports used to resolve class names.
     */
    public Imports getImports() {
        if (env == null)  return null; // lame default
        return env.getImports();
    }

    /**
     * Create a new class.
     */
    public ClassDefinition makeClassDefinition(Environment origEnv, long where,
                                               IdentifierToken name,
                                               String doc, int modifiers,
                                               IdentifierToken superClass,
                                               IdentifierToken interfaces[],
                                               ClassDefinition outerClass) {
        if (env == null)  return null; // lame default
        return env.makeClassDefinition(origEnv, where, name,
                                       doc, modifiers,
                                       superClass, interfaces, outerClass);
    }

    /**
     * Create a new field.
     */
    public MemberDefinition makeMemberDefinition(Environment origEnv, long where,
                                               ClassDefinition clazz,
                                               String doc, int modifiers,
                                               Type type, Identifier name,
                                               IdentifierToken argNames[],
                                               IdentifierToken expIds[],
                                               Object value) {
        if (env == null)  return null; // lame default
        return env.makeMemberDefinition(origEnv, where, clazz, doc, modifiers,
                                       type, name, argNames, expIds, value);
    }

    /**
     * Returns true if the given method is applicable to the given arguments
     */

    public boolean isApplicable(MemberDefinition m, Type args[]) throws ClassNotFound {
        Type mType = m.getType();
        if (!mType.isType(TC_METHOD))
            return false;
        Type mArgs[] = mType.getArgumentTypes();
        if (args.length != mArgs.length)
            return false;
        for (int i = args.length ; --i >= 0 ;)
            if (!isMoreSpecific(args[i], mArgs[i]))
                return false;
        return true;
    }


    /**
     * Returns true if "best" is in every argument at least as good as "other"
     */
    public boolean isMoreSpecific(MemberDefinition best, MemberDefinition other)
           throws ClassNotFound {
        Type bestType = best.getClassDeclaration().getType();
        Type otherType = other.getClassDeclaration().getType();
        boolean result = isMoreSpecific(bestType, otherType)
                      && isApplicable(other, best.getType().getArgumentTypes());
        // System.out.println("isMoreSpecific: " + best + "/" + other
        //                      + " => " + result);
        return result;
    }

    /**
     * Returns true if "from" is a more specific type than "to"
     */

    public boolean isMoreSpecific(Type from, Type to) throws ClassNotFound {
        return implicitCast(from, to);
    }

    /**
     * Return true if an implicit cast from this type to
     * the given type is allowed.
     */
    public boolean implicitCast(Type from, Type to) throws ClassNotFound {
        if (from == to)
            return true;

        int toTypeCode = to.getTypeCode();

        switch(from.getTypeCode()) {
        case TC_BYTE:
            if (toTypeCode == TC_SHORT)
                return true;
        case TC_SHORT:
        case TC_CHAR:
            if (toTypeCode == TC_INT) return true;
        case TC_INT:
            if (toTypeCode == TC_LONG) return true;
        case TC_LONG:
            if (toTypeCode == TC_FLOAT) return true;
        case TC_FLOAT:
            if (toTypeCode == TC_DOUBLE) return true;
        case TC_DOUBLE:
        default:
            return false;

        case TC_NULL:
            return to.inMask(TM_REFERENCE);

        case TC_ARRAY:
            if (!to.isType(TC_ARRAY)) {
                return (to == Type.tObject || to == Type.tCloneable
                           || to == Type.tSerializable);
            } else {
                // both are arrays.  recurse down both until one isn't an array
                do {
                    from = from.getElementType();
                    to = to.getElementType();
                } while (from.isType(TC_ARRAY) && to.isType(TC_ARRAY));
                if (  from.inMask(TM_ARRAY|TM_CLASS)
                      && to.inMask(TM_ARRAY|TM_CLASS)) {
                    return isMoreSpecific(from, to);
                } else {
                    return (from.getTypeCode() == to.getTypeCode());
                }
            }

        case TC_CLASS:
            if (toTypeCode == TC_CLASS) {
                ClassDefinition fromDef = getClassDefinition(from);
                ClassDefinition toDef = getClassDefinition(to);
                return toDef.implementedBy(this,
                                           fromDef.getClassDeclaration());
            } else {
                return false;
            }
        }
    }


    /**
     * Return true if an explicit cast from this type to
     * the given type is allowed.
     */
    public boolean explicitCast(Type from, Type to) throws ClassNotFound {
        if (implicitCast(from, to)) {
            return true;
        }
        if (from.inMask(TM_NUMBER)) {
            return to.inMask(TM_NUMBER);
        }
        if (from.isType(TC_CLASS) && to.isType(TC_CLASS)) {
            ClassDefinition fromClass = getClassDefinition(from);
            ClassDefinition toClass = getClassDefinition(to);
            if (toClass.isFinal()) {
                return fromClass.implementedBy(this,
                                               toClass.getClassDeclaration());
            }
            if (fromClass.isFinal()) {
                return toClass.implementedBy(this,
                                             fromClass.getClassDeclaration());
            }

            // The code here used to omit this case.  If both types
            // involved in a cast are interfaces, then JLS 5.5 requires
            // that we do a simple test -- make sure none of the methods
            // in toClass and fromClass have the same signature but
            // different return types.  (bug number 4028359)
            if (toClass.isInterface() && fromClass.isInterface()) {
                return toClass.couldImplement(fromClass);
            }

            return toClass.isInterface() ||
                   fromClass.isInterface() ||
                   fromClass.superClassOf(this, toClass.getClassDeclaration());
        }
        if (to.isType(TC_ARRAY)) {
            if (from.isType(TC_ARRAY))  {
                Type t1 = from.getElementType();
                Type t2 = to.getElementType();
                while ((t1.getTypeCode() == TC_ARRAY)
                       && (t2.getTypeCode() == TC_ARRAY)) {
                    t1 = t1.getElementType();
                    t2 = t2.getElementType();
                }
                if (t1.inMask(TM_ARRAY|TM_CLASS) &&
                    t2.inMask(TM_ARRAY|TM_CLASS)) {
                    return explicitCast(t1, t2);
                }
            } else if (from == Type.tObject || from == Type.tCloneable
                          || from == Type.tSerializable)
                return true;
        }
        return false;
    }

    /**
     * Flags.
     */
    public int getFlags() {
        return env.getFlags();
    }

    /**
     * Debugging flags.  There used to be a method debug()
     * that has been replaced because -g has changed meaning
     * (it now cooperates with -O and line number, variable
     * range and source file info can be toggled separately).
     */
    public final boolean debug_lines() {
        return (getFlags() & F_DEBUG_LINES) != 0;
    }
    public final boolean debug_vars() {
        return (getFlags() & F_DEBUG_VARS) != 0;
    }
    public final boolean debug_source() {
        return (getFlags() & F_DEBUG_SOURCE) != 0;
    }

    /**
     * Optimization flags.  There used to be a method optimize()
     * that has been replaced because -O has changed meaning in
     * javac to be replaced with -O and -O:interclass.
     */
    public final boolean opt() {
        return (getFlags() & F_OPT) != 0;
    }
    public final boolean opt_interclass() {
        return (getFlags() & F_OPT_INTERCLASS) != 0;
    }

    /**
     * Verbose
     */
    public final boolean verbose() {
        return (getFlags() & F_VERBOSE) != 0;
    }

    /**
     * Dump debugging stuff
     */
    public final boolean dump() {
        return (getFlags() & F_DUMP) != 0;
    }

    /**
     * Verbose
     */
    public final boolean warnings() {
        return (getFlags() & F_WARNINGS) != 0;
    }

    /**
     * Dependencies
     */
    public final boolean dependencies() {
        return (getFlags() & F_DEPENDENCIES) != 0;
    }

    /**
     * Print Dependencies to stdout
     */
    public final boolean print_dependencies() {
        return (getFlags() & F_PRINT_DEPENDENCIES) != 0;
    }

    /**
     * Deprecation warnings are enabled.
     */
    public final boolean deprecation() {
        return (getFlags() & F_DEPRECATION) != 0;
    }

    /**
     * Do not support virtual machines before version 1.2.
     * This option is not supported and is only here for testing purposes.
     */
    public final boolean version12() {
        return (getFlags() & F_VERSION12) != 0;
    }

    /**
     * Floating point is strict by default
     */
    public final boolean strictdefault() {
        return (getFlags() & F_STRICTDEFAULT) != 0;
    }

    /**
     * Release resources, if any.
     */
    public void shutdown() {
        if (env != null) {
            env.shutdown();
        }
    }

    /**
     * Issue an error.
     *  source   - the input source, usually a file name string
     *  offset   - the offset in the source of the error
     *  err      - the error number (as defined in this interface)
     *  arg1     - an optional argument to the error (null if not applicable)
     *  arg2     - a second optional argument to the error (null if not applicable)
     *  arg3     - a third optional argument to the error (null if not applicable)
     */
    public void error(Object source, long where, String err, Object arg1, Object arg2, Object arg3) {
        env.error(source, where, err, arg1, arg2, arg3);
    }
    public final void error(long where, String err, Object arg1, Object arg2, Object arg3) {
        error(source, where, err, arg1, arg2, arg3);
    }
    public final void error(long where, String err, Object arg1, Object arg2) {
        error(source, where, err, arg1, arg2, null);
    }
    public final void error(long where, String err, Object arg1) {
        error(source, where, err, arg1, null, null);
    }
    public final void error(long where, String err) {
        error(source, where, err, null, null, null);
    }

    /**
     * Output a string. This can either be an error message or something
     * for debugging. This should be used instead of println.
     */
    public void output(String msg) {
        env.output(msg);
    }

    private static boolean debugging = (System.getProperty("javac.debug") != null);

    public static void debugOutput(Object msg) {
        if (Environment.debugging)
            System.out.println(msg.toString());
    }

    /**
     * set character encoding name
     */
    public void setCharacterEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Return character encoding name
     */
    public String getCharacterEncoding() {
        return encoding;
    }

    /**
     * Return major version to use in generated class files.
     */
    public short getMajorVersion() {
        if (env==null) return JAVA_DEFAULT_VERSION;  // needed for javah
        return env.getMajorVersion();
    }

    /**
     * Return minor version to use in generated class files.
     */
    public short getMinorVersion() {
        if (env==null) return JAVA_DEFAULT_MINOR_VERSION;  // needed for javah
        return env.getMinorVersion();
    }

// JCOV
    /**
     *  get coverage flag
     */
    public final boolean coverage() {
        return (getFlags() & F_COVERAGE) != 0;
    }

    /**
     *  get flag of generation the coverage data file
     */
    public final boolean covdata() {
        return (getFlags() & F_COVDATA) != 0;
    }

    /**
     * Return the coverage data file
     */
    public File getcovFile() {
        return env.getcovFile();
    }

// end JCOV

    /**
     * Debug tracing.
     * Currently, this code is used only for tracing the loading and
     * checking of classes, particularly the demand-driven aspects.
     * This code should probably be integrated with 'debugOutput' above,
     * but we need to give more thought to the issue of classifying debugging
     * messages and allowing those only those of interest to be enabled.
     *
     * Calls to these methods are generally conditioned on the final variable
     * 'Constants.tracing', which allows the calls to be completely omitted
     * in a production release to avoid space and time overhead.
     */

    private static boolean dependtrace =
                (System.getProperty("javac.trace.depend") != null);

    public void dtEnter(String s) {
        if (dependtrace) System.out.println(">>> " + s);
    }

    public void dtExit(String s) {
        if (dependtrace) System.out.println("<<< " + s);
    }

    public void dtEvent(String s) {
        if (dependtrace) System.out.println(s);
    }

    /**
     * Enable diagnostic dump of class modifier bits, including those
     * in InnerClasses attributes, as they are written to the classfile.
     * In the future, may also enable dumping field and method modifiers.
     */

    private static boolean dumpmodifiers =
                (System.getProperty("javac.dump.modifiers") != null);

    public boolean dumpModifiers() { return dumpmodifiers; }

}
