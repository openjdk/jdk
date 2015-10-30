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

package jdk.jshell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import com.sun.jdi.ReferenceType;
import jdk.jshell.Snippet.Kind;
import jdk.jshell.Snippet.Status;
import jdk.jshell.Snippet.SubKind;
import jdk.jshell.TaskFactory.AnalyzeTask;
import jdk.jshell.ClassTracker.ClassInfo;
import jdk.jshell.TaskFactory.CompileTask;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_EVNT;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_GEN;
import static jdk.jshell.Snippet.Status.OVERWRITTEN;
import static jdk.jshell.Snippet.Status.RECOVERABLE_DEFINED;
import static jdk.jshell.Snippet.Status.RECOVERABLE_NOT_DEFINED;
import static jdk.jshell.Snippet.Status.REJECTED;
import static jdk.jshell.Snippet.Status.VALID;
import static jdk.jshell.Util.expunge;

/**
 * Tracks the compilation and load of a new or updated snippet.
 * @author Robert Field
 */
final class Unit {

    private final JShell state;
    private final Snippet si;
    private final Snippet siOld;
    private final boolean isDependency;
    private final boolean isNew;
    private final Snippet causalSnippet;
    private final DiagList generatedDiagnostics;

    private int seq;
    private int seqInitial;
    private Wrap activeGuts;
    private Status status;
    private Status prevStatus;
    private boolean signatureChanged;
    private DiagList compilationDiagnostics;
    private DiagList recompilationDiagnostics = null;
    private List<String> unresolved;
    private SnippetEvent replaceOldEvent;
    private List<SnippetEvent> secondaryEvents;
    private boolean isAttemptingCorral;
    private List<ClassInfo> toRedefine;
    private boolean dependenciesNeeded;

    Unit(JShell state, Snippet si, Snippet causalSnippet,
            DiagList generatedDiagnostics) {
        this.state = state;
        this.si = si;
        this.isDependency = causalSnippet != null;
        this.siOld = isDependency
                ? si
                : state.maps.getSnippet(si.key());
        this.isNew = siOld == null;
        this.causalSnippet = causalSnippet;
        this.generatedDiagnostics = generatedDiagnostics;

        this.seq = isNew? 0 : siOld.sequenceNumber();
        this.seqInitial = seq;
        this.prevStatus = (isNew || isDependency)
                ? si.status()
                : siOld.status();
        si.setSequenceNumber(seq);
    }

    // Drop entry
    Unit(JShell state, Snippet si) {
        this.state = state;
        this.si = si;
        this.siOld = null;
        this.isDependency = false;
        this.isNew = false;
        this.causalSnippet = null;
        this.generatedDiagnostics = new DiagList();
        this.prevStatus = si.status();
        si.setDropped();
        this.status = si.status();
    }

    @Override
    public int hashCode() {
        return si.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Unit)
                ? si.equals(((Unit) o).si)
                : false;
    }

    Snippet snippet() {
        return si;
    }

    boolean isDependency() {
        return isDependency;
    }

    boolean isNew() {
        return isNew;
    }

    boolean isRedundant() {
        return !isNew && !isDependency() && !si.isExecutable() &&
                prevStatus.isDefined &&
                siOld.source().equals(si.source());
    }

    void initialize(Collection<Unit> working) {
        isAttemptingCorral = false;
        dependenciesNeeded = false;
        toRedefine = null; // assure NPE if classToLoad not called
        activeGuts = si.guts();
        markOldDeclarationOverwritten();
        setWrap(working, working);
    }

    void setWrap(Collection<Unit> except, Collection<Unit> plus) {
        si.setOuterWrap(isImport()
                ? OuterWrap.wrapImport(si.source(), activeGuts)
                : state.eval.wrapInClass(si,
                        except.stream().map(u -> u.snippet().key()).collect(toSet()),
                        activeGuts,
                        plus.stream().map(u -> u.snippet())
                                .filter(sn -> sn != si)
                                .collect(toList())));
    }

    void setDiagnostics(AnalyzeTask ct) {
        setDiagnostics(ct.getDiagnostics().ofUnit(this));
    }

    void setDiagnostics(DiagList diags) {
        compilationDiagnostics = diags;
        UnresolvedExtractor ue = new UnresolvedExtractor(diags);
        unresolved = ue.unresolved();
        state.debug(DBG_GEN, "++setCompilationInfo() %s\n%s\n-- diags: %s\n",
                si, si.outerWrap().wrapped(), diags);
    }

    private boolean isRecoverable() {
        // Unit failed, use corralling if it is defined on this Snippet,
        // and either all the errors are resolution errors or this is a
        // redeclare of an existing method
        return compilationDiagnostics.hasErrors()
                && si instanceof DeclarationSnippet
                && (isDependency()
                    || (si.subKind() != SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND
                        && compilationDiagnostics.hasResolutionErrorsAndNoOthers()));
    }

    /**
     * If it meets the conditions for corralling, install the corralled wrap
     * @return true is the corralled wrap was installed
     */
    boolean corralIfNeeded(Collection<Unit> working) {
        if (isRecoverable()
                && si.corralled() != null) {
            activeGuts = si.corralled();
            setWrap(working, working);
            return isAttemptingCorral = true;
        }
        return isAttemptingCorral = false;
    }

    void setCorralledDiagnostics(AnalyzeTask cct) {
        // set corralled diagnostics, but don't reset unresolved
        recompilationDiagnostics = cct.getDiagnostics().ofUnit(this);
        state.debug(DBG_GEN, "++recomp %s\n%s\n-- diags: %s\n",
                si, si.outerWrap().wrapped(), recompilationDiagnostics);
    }

    boolean smashingErrorDiagnostics(CompileTask ct) {
        if (isDefined()) {
            // set corralled diagnostics, but don't reset unresolved
            DiagList dl = ct.getDiagnostics().ofUnit(this);
            if (dl.hasErrors()) {
                setDiagnostics(dl);
                status = RECOVERABLE_NOT_DEFINED;
                // overwrite orginal bytes
                state.debug(DBG_GEN, "++smashingErrorDiagnostics %s\n%s\n-- diags: %s\n",
                        si, si.outerWrap().wrapped(), dl);
                return true;
            }
        }
        return false;
    }

    void setStatus() {
        if (!compilationDiagnostics.hasErrors()) {
            status = VALID;
        } else if (isRecoverable()) {
            if (isAttemptingCorral && !recompilationDiagnostics.hasErrors()) {
                status = RECOVERABLE_DEFINED;
            } else {
                status = RECOVERABLE_NOT_DEFINED;
            }
        } else {
            status = REJECTED;
        }
        checkForOverwrite();

        state.debug(DBG_GEN, "setStatus() %s - status: %s\n",
                si, status);
    }

    /**
     * Must be called for each unit
     * @return
     */
    boolean isDefined() {
        return status.isDefined;
    }

    /**
     * Process the class information from the last compile.
     * Requires loading of returned list.
     * @return the list of classes to load
     */
    Stream<ClassInfo> classesToLoad(List<ClassInfo> cil) {
        toRedefine = new ArrayList<>();
        List<ClassInfo> toLoad = new ArrayList<>();
        if (status.isDefined && !isImport()) {
            cil.stream().forEach(ci -> {
                if (!ci.isLoaded()) {
                    if (ci.getReferenceTypeOrNull() == null) {
                        toLoad.add(ci);
                        ci.setLoaded();
                        dependenciesNeeded = true;
                    } else {
                        toRedefine.add(ci);
                    }
                }
            });
        }
        return toLoad.stream();
    }

    /**
     * Redefine classes needing redefine.
     * classesToLoad() must be called first.
     * @return true if all redefines succeeded (can be vacuously true)
     */
    boolean doRedefines() {
         if (toRedefine.isEmpty()) {
            return true;
        }
        Map<ReferenceType, byte[]> mp = toRedefine.stream()
                .collect(toMap(ci -> ci.getReferenceTypeOrNull(), ci -> ci.getBytes()));
        if (state.executionControl().commandRedefine(mp)) {
            // success, mark as loaded
            toRedefine.stream().forEach(ci -> ci.setLoaded());
            return true;
        } else {
            // failed to redefine
            return false;
        }
    }

    void markForReplacement() {
        // increment for replace class wrapper
        si.setSequenceNumber(++seq);
    }

    private boolean isImport() {
        return si.kind() == Kind.IMPORT;
    }

    private boolean sigChanged() {
        return (status.isDefined != prevStatus.isDefined)
                || (seq != seqInitial && status.isDefined)
                || signatureChanged;
    }

    Stream<Unit> effectedDependents() {
        return sigChanged() || dependenciesNeeded || status == RECOVERABLE_NOT_DEFINED
                ? dependents()
                : Stream.empty();
    }

    Stream<Unit> dependents() {
        return state.maps.getDependents(si)
                    .stream()
                    .filter(xsi -> xsi != si && xsi.status().isActive)
                    .map(xsi -> new Unit(state, xsi, si, new DiagList()));
    }

    void finish() {
        recordCompilation();
        state.maps.installSnippet(si);
    }

    private void markOldDeclarationOverwritten() {
        if (si != siOld && siOld != null && siOld.status().isActive) {
            // Mark the old declaraion as replaced
            replaceOldEvent = new SnippetEvent(siOld,
                    siOld.status(), OVERWRITTEN,
                    false, si, null, null);
            siOld.setOverwritten();
        }
    }

    private DiagList computeDiagnostics() {
        DiagList diagnostics = new DiagList();
        DiagList diags = compilationDiagnostics;
        if (status == RECOVERABLE_DEFINED || status == RECOVERABLE_NOT_DEFINED) {
            UnresolvedExtractor ue = new UnresolvedExtractor(diags);
            diagnostics.addAll(ue.otherAll());
        } else {
            unresolved = Collections.emptyList();
            diagnostics.addAll(diags);
        }
        diagnostics.addAll(generatedDiagnostics);
        return diagnostics;
    }

    private void recordCompilation() {
        state.maps.mapDependencies(si);
        DiagList diags = computeDiagnostics();
        si.setCompilationStatus(status, unresolved, diags);
        state.debug(DBG_GEN, "recordCompilation: %s -- status %s, unresolved %s\n",
                si, status, unresolved);
    }

    private void checkForOverwrite() {
        secondaryEvents = new ArrayList<>();
        if (replaceOldEvent != null) secondaryEvents.add(replaceOldEvent);

        // Defined methods can overwrite methods of other (equivalent) snippets
        if (si.kind() == Kind.METHOD && status.isDefined) {
            String oqpt = ((MethodSnippet) si).qualifiedParameterTypes();
            String nqpt = computeQualifiedParameterTypes(si);
            if (!nqpt.equals(oqpt)) {
                ((MethodSnippet) si).setQualifiedParamaterTypes(nqpt);
                Status overwrittenStatus = overwriteMatchingMethod(si);
                if (overwrittenStatus != null) {
                    prevStatus = overwrittenStatus;
                    signatureChanged = true;
                }
            }
        }
    }

    // Check if there is a method whose user-declared parameter types are
    // different (and thus has a different snippet) but whose compiled parameter
    // types are the same. if so, consider it an overwrite replacement.
    private Status overwriteMatchingMethod(Snippet si) {
        String qpt = ((MethodSnippet) si).qualifiedParameterTypes();

        // Look through all methods for a method of the same name, with the
        // same computed qualified parameter types
        Status overwrittenStatus = null;
        for (MethodSnippet sn : state.methods()) {
            if (sn != null && sn != si && sn.status().isActive && sn.name().equals(si.name())) {
                if (qpt.equals(sn.qualifiedParameterTypes())) {
                    overwrittenStatus = sn.status();
                    SnippetEvent se = new SnippetEvent(
                            sn, overwrittenStatus, OVERWRITTEN,
                            false, si, null, null);
                    sn.setOverwritten();
                    secondaryEvents.add(se);
                    state.debug(DBG_EVNT,
                            "Overwrite event #%d -- key: %s before: %s status: %s sig: %b cause: %s\n",
                            secondaryEvents.size(), se.snippet(), se.previousStatus(),
                            se.status(), se.isSignatureChange(), se.causeSnippet());
                }
            }
        }
        return overwrittenStatus;
    }

    private String computeQualifiedParameterTypes(Snippet si) {
        MethodSnippet msi = (MethodSnippet) si;
        String qpt;
        AnalyzeTask at = state.taskFactory.new AnalyzeTask(msi.outerWrap());
        String rawSig = new TreeDissector(at).typeOfMethod();
        String signature = expunge(rawSig);
        int paren = signature.lastIndexOf(')');
        if (paren < 0) {
            // Uncompilable snippet, punt with user parameter types
            qpt = msi.parameterTypes();
        } else {
            qpt = signature.substring(0, paren + 1);
        }
        return qpt;
    }

    SnippetEvent event(String value, Exception exception) {
        boolean wasSignatureChanged = sigChanged();
        state.debug(DBG_EVNT, "Snippet: %s id: %s before: %s status: %s sig: %b cause: %s\n",
                si, si.id(), prevStatus, si.status(), wasSignatureChanged, causalSnippet);
        return new SnippetEvent(si, prevStatus, si.status(),
                wasSignatureChanged, causalSnippet, value, exception);
    }

    List<SnippetEvent> secondaryEvents() {
        return secondaryEvents;
    }

    @Override
    public String toString() {
        return "Unit(" + si.name() + ")";
    }

    /**
     * Separate out the unresolvedDependencies errors from both the other
     * corralling errors and the overall errors.
     */
    private static class UnresolvedExtractor {

        private static final String RESOLVE_ERROR_SYMBOL = "symbol:";
        private static final String RESOLVE_ERROR_LOCATION = "location:";

        //TODO extract from tree instead -- note: internationalization
        private final Set<String> unresolved = new LinkedHashSet<>();
        private final DiagList otherErrors = new DiagList();
        private final DiagList otherAll = new DiagList();

        UnresolvedExtractor(DiagList diags) {
            for (Diag diag : diags) {
                if (diag.isError()) {
                    if (diag.isResolutionError()) {
                        String m = diag.getMessage(null);
                        int symPos = m.indexOf(RESOLVE_ERROR_SYMBOL);
                        if (symPos >= 0) {
                            m = m.substring(symPos + RESOLVE_ERROR_SYMBOL.length());
                            int symLoc = m.indexOf(RESOLVE_ERROR_LOCATION);
                            if (symLoc >= 0) {
                                m = m.substring(0, symLoc);
                            }
                            m = m.trim();
                            unresolved.add(m);
                            continue;
                        }
                    }
                    otherErrors.add(diag);
                }
                otherAll.add(diag);
            }
        }

        DiagList otherCorralledErrors() {
            return otherErrors;
        }

        DiagList otherAll() {
            return otherAll;
        }

        List<String> unresolved() {
            return new ArrayList<>(unresolved);
        }
    }
}
