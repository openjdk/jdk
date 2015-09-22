/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.CapturedType;
import com.sun.tools.javac.code.Type.CapturedUndetVar;
import com.sun.tools.javac.code.Type.TypeMapping;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.UndetVar;
import com.sun.tools.javac.code.Type.UndetVar.InferenceBound;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Infer.BestLeafSolver;
import com.sun.tools.javac.comp.Infer.FreeTypeListener;
import com.sun.tools.javac.comp.Infer.GraphSolver;
import com.sun.tools.javac.comp.Infer.GraphStrategy;
import com.sun.tools.javac.comp.Infer.InferenceException;
import com.sun.tools.javac.comp.Infer.InferenceStep;
import com.sun.tools.javac.comp.Infer.LeafSolver;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.Factory;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Warner;

/**
 * An inference context keeps track of the set of variables that are free
 * in the current context. It provides utility methods for opening/closing
 * types to their corresponding free/closed forms. It also provide hooks for
 * attaching deferred post-inference action (see PendingCheck). Finally,
 * it can be used as an entry point for performing upper/lower bound inference
 * (see InferenceKind).
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
class InferenceContext {

    /** list of inference vars as undet vars */
    List<Type> undetvars;

    /** list of inference vars in this context */
    List<Type> inferencevars;

    Map<FreeTypeListener, List<Type>> freeTypeListeners = new HashMap<>();

    List<FreeTypeListener> freetypeListeners = List.nil();

    Types types;
    Infer infer;

    public InferenceContext(Infer infer, List<Type> inferencevars) {
        this.inferencevars = inferencevars;

        this.infer = infer;
        this.types = infer.types;

        fromTypeVarFun = new TypeMapping<Void>() {
            @Override
            public Type visitTypeVar(TypeVar tv, Void aVoid) {
                return new UndetVar(tv, types);
            }

            @Override
            public Type visitCapturedType(CapturedType t, Void aVoid) {
                return new CapturedUndetVar(t, types);
            }
        };
        this.undetvars = inferencevars.map(fromTypeVarFun);
    }

    TypeMapping<Void> fromTypeVarFun;

    /**
     * add a new inference var to this inference context
     */
    void addVar(TypeVar t) {
        this.undetvars = this.undetvars.prepend(fromTypeVarFun.apply(t));
        this.inferencevars = this.inferencevars.prepend(t);
    }

    /**
     * returns the list of free variables (as type-variables) in this
     * inference context
     */
    List<Type> inferenceVars() {
        return inferencevars;
    }

    /**
     * returns the list of uninstantiated variables (as type-variables) in this
     * inference context
     */
    List<Type> restvars() {
        return filterVars(new Filter<UndetVar>() {
            public boolean accepts(UndetVar uv) {
                return uv.inst == null;
            }
        });
    }

    /**
     * returns the list of instantiated variables (as type-variables) in this
     * inference context
     */
    List<Type> instvars() {
        return filterVars(new Filter<UndetVar>() {
            public boolean accepts(UndetVar uv) {
                return uv.inst != null;
            }
        });
    }

    /**
     * Get list of bounded inference variables (where bound is other than
     * declared bounds).
     */
    final List<Type> boundedVars() {
        return filterVars(new Filter<UndetVar>() {
            public boolean accepts(UndetVar uv) {
                return uv.getBounds(InferenceBound.UPPER)
                         .diff(uv.getDeclaredBounds())
                         .appendList(uv.getBounds(InferenceBound.EQ, InferenceBound.LOWER)).nonEmpty();
            }
        });
    }

    /* Returns the corresponding inference variables.
     */
    private List<Type> filterVars(Filter<UndetVar> fu) {
        ListBuffer<Type> res = new ListBuffer<>();
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            if (fu.accepts(uv)) {
                res.append(uv.qtype);
            }
        }
        return res.toList();
    }

    /**
     * is this type free?
     */
    final boolean free(Type t) {
        return t.containsAny(inferencevars);
    }

    final boolean free(List<Type> ts) {
        for (Type t : ts) {
            if (free(t)) return true;
        }
        return false;
    }

    /**
     * Returns a list of free variables in a given type
     */
    final List<Type> freeVarsIn(Type t) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type iv : inferenceVars()) {
            if (t.contains(iv)) {
                buf.add(iv);
            }
        }
        return buf.toList();
    }

    final List<Type> freeVarsIn(List<Type> ts) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            buf.appendList(freeVarsIn(t));
        }
        ListBuffer<Type> buf2 = new ListBuffer<>();
        for (Type t : buf) {
            if (!buf2.contains(t)) {
                buf2.add(t);
            }
        }
        return buf2.toList();
    }

    /**
     * Replace all free variables in a given type with corresponding
     * undet vars (used ahead of subtyping/compatibility checks to allow propagation
     * of inference constraints).
     */
    final Type asUndetVar(Type t) {
        return types.subst(t, inferencevars, undetvars);
    }

    final List<Type> asUndetVars(List<Type> ts) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            buf.append(asUndetVar(t));
        }
        return buf.toList();
    }

    List<Type> instTypes() {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            buf.append(uv.inst != null ? uv.inst : uv.qtype);
        }
        return buf.toList();
    }

    /**
     * Replace all free variables in a given type with corresponding
     * instantiated types - if one or more free variable has not been
     * fully instantiated, it will still be available in the resulting type.
     */
    Type asInstType(Type t) {
        return types.subst(t, inferencevars, instTypes());
    }

    List<Type> asInstTypes(List<Type> ts) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            buf.append(asInstType(t));
        }
        return buf.toList();
    }

    /**
     * Add custom hook for performing post-inference action
     */
    void addFreeTypeListener(List<Type> types, FreeTypeListener ftl) {
        freeTypeListeners.put(ftl, freeVarsIn(types));
    }

    /**
     * Mark the inference context as complete and trigger evaluation
     * of all deferred checks.
     */
    void notifyChange() {
        notifyChange(inferencevars.diff(restvars()));
    }

    void notifyChange(List<Type> inferredVars) {
        InferenceException thrownEx = null;
        for (Map.Entry<FreeTypeListener, List<Type>> entry :
                new HashMap<>(freeTypeListeners).entrySet()) {
            if (!Type.containsAny(entry.getValue(), inferencevars.diff(inferredVars))) {
                try {
                    entry.getKey().typesInferred(this);
                    freeTypeListeners.remove(entry.getKey());
                } catch (InferenceException ex) {
                    if (thrownEx == null) {
                        thrownEx = ex;
                    }
                }
            }
        }
        //inference exception multiplexing - present any inference exception
        //thrown when processing listeners as a single one
        if (thrownEx != null) {
            throw thrownEx;
        }
    }

    /**
     * Save the state of this inference context
     */
    List<Type> save() {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            UndetVar uv2 = new UndetVar((TypeVar)uv.qtype, types);
            for (InferenceBound ib : InferenceBound.values()) {
                for (Type b : uv.getBounds(ib)) {
                    uv2.addBound(ib, b, types);
                }
            }
            uv2.inst = uv.inst;
            buf.add(uv2);
        }
        return buf.toList();
    }

    /** Restore the state of this inference context to the previous known checkpoint.
    *  Consider that the number of saved undetermined variables can be different to the current
    *  amount. This is because new captured variables could have been added.
    */
    void rollback(List<Type> saved_undet) {
        Assert.check(saved_undet != null);
        //restore bounds (note: we need to preserve the old instances)
        ListBuffer<Type> newUndetVars = new ListBuffer<>();
        ListBuffer<Type> newInferenceVars = new ListBuffer<>();
        while (saved_undet.nonEmpty() && undetvars.nonEmpty()) {
            UndetVar uv = (UndetVar)undetvars.head;
            UndetVar uv_saved = (UndetVar)saved_undet.head;
            if (uv.qtype == uv_saved.qtype) {
                for (InferenceBound ib : InferenceBound.values()) {
                    uv.setBounds(ib, uv_saved.getBounds(ib));
                }
                uv.inst = uv_saved.inst;
                undetvars = undetvars.tail;
                saved_undet = saved_undet.tail;
                newUndetVars.add(uv);
                newInferenceVars.add(uv.qtype);
            } else {
                undetvars = undetvars.tail;
            }
        }
        undetvars = newUndetVars.toList();
        inferencevars = newInferenceVars.toList();
    }

    /**
     * Copy variable in this inference context to the given context
     */
    void dupTo(final InferenceContext that) {
        dupTo(that, false);
    }

    void dupTo(final InferenceContext that, boolean clone) {
        that.inferencevars = that.inferencevars.appendList(inferencevars.diff(that.inferencevars));
        List<Type> undetsToPropagate = clone ? save() : undetvars;
        that.undetvars = that.undetvars.appendList(undetsToPropagate.diff(that.undetvars)); //propagate cloned undet!!
        //set up listeners to notify original inference contexts as
        //propagated vars are inferred in new context
        for (Type t : inferencevars) {
            that.freeTypeListeners.put(new FreeTypeListener() {
                public void typesInferred(InferenceContext inferenceContext) {
                    InferenceContext.this.notifyChange();
                }
            }, List.of(t));
        }
    }

    private void solve(GraphStrategy ss, Warner warn) {
        solve(ss, new HashMap<Type, Set<Type>>(), warn);
    }

    /**
     * Solve with given graph strategy.
     */
    private void solve(GraphStrategy ss, Map<Type, Set<Type>> stuckDeps, Warner warn) {
        GraphSolver s = infer.new GraphSolver(this, stuckDeps, warn);
        s.solve(ss);
    }

    /**
     * Solve all variables in this context.
     */
    public void solve(Warner warn) {
        solve(infer.new LeafSolver() {
            public boolean done() {
                return restvars().isEmpty();
            }
        }, warn);
    }

    /**
     * Solve all variables in the given list.
     */
    public void solve(final List<Type> vars, Warner warn) {
        solve(infer.new BestLeafSolver(vars) {
            public boolean done() {
                return !free(asInstTypes(vars));
            }
        }, warn);
    }

    /**
     * Solve at least one variable in given list.
     */
    public void solveAny(List<Type> varsToSolve, Map<Type, Set<Type>> optDeps, Warner warn) {
        solve(infer.new BestLeafSolver(varsToSolve.intersect(restvars())) {
            public boolean done() {
                return instvars().intersect(varsToSolve).nonEmpty();
            }
        }, optDeps, warn);
    }

    /**
     * Apply a set of inference steps
     */
    private boolean solveBasic(EnumSet<InferenceStep> steps) {
        return solveBasic(inferencevars, steps);
    }

    boolean solveBasic(List<Type> varsToSolve, EnumSet<InferenceStep> steps) {
        boolean changed = false;
        for (Type t : varsToSolve.intersect(restvars())) {
            UndetVar uv = (UndetVar)asUndetVar(t);
            for (InferenceStep step : steps) {
                if (step.accepts(uv, this)) {
                    uv.inst = step.solve(uv, this);
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    /**
     * Instantiate inference variables in legacy mode (JLS 15.12.2.7, 15.12.2.8).
     * During overload resolution, instantiation is done by doing a partial
     * inference process using eq/lower bound instantiation. During check,
     * we also instantiate any remaining vars by repeatedly using eq/upper
     * instantiation, until all variables are solved.
     */
    public void solveLegacy(boolean partial, Warner warn, EnumSet<InferenceStep> steps) {
        while (true) {
            boolean stuck = !solveBasic(steps);
            if (restvars().isEmpty() || partial) {
                //all variables have been instantiated - exit
                break;
            } else if (stuck) {
                //some variables could not be instantiated because of cycles in
                //upper bounds - provide a (possibly recursive) default instantiation
                infer.instantiateAsUninferredVars(restvars(), this);
                break;
            } else {
                //some variables have been instantiated - replace newly instantiated
                //variables in remaining upper bounds and continue
                for (Type t : undetvars) {
                    UndetVar uv = (UndetVar)t;
                    uv.substBounds(inferenceVars(), instTypes(), types);
                }
            }
        }
        infer.checkWithinBounds(this, warn);
    }

    @Override
    public String toString() {
        return "Inference vars: " + inferencevars + '\n' +
               "Undet vars: " + undetvars;
    }

    /* Method Types.capture() generates a new type every time it's applied
     * to a wildcard parameterized type. This is intended functionality but
     * there are some cases when what you need is not to generate a new
     * captured type but to check that a previously generated captured type
     * is correct. There are cases when caching a captured type for later
     * reuse is sound. In general two captures from the same AST are equal.
     * This is why the tree is used as the key of the map below. This map
     * stores a Type per AST.
     */
    Map<JCTree, Type> captureTypeCache = new HashMap<>();

    Type cachedCapture(JCTree tree, Type t, boolean readOnly) {
        Type captured = captureTypeCache.get(tree);
        if (captured != null) {
            return captured;
        }

        Type result = types.capture(t);
        if (result != t && !readOnly) { // then t is a wildcard parameterized type
            captureTypeCache.put(tree, result);
        }
        return result;
    }
}
