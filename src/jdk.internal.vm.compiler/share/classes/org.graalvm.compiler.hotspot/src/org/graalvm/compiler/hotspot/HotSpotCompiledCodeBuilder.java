/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder.Options.ShowSubstitutionSourceInfo;
import static org.graalvm.util.CollectionsUtil.anyMatch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.code.CompilationResult.CodeComment;
import org.graalvm.compiler.code.CompilationResult.JumpTable;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.code.SourceMapping;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotCompiledCode.Comment;
import jdk.vm.ci.hotspot.HotSpotCompiledNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotCompiledCodeBuilder {
    public static class Options {
        // @formatter:off
        @Option(help = "Controls whether the source position information of snippets and method substitutions" +
                " are exposed to HotSpot.  Can be useful when profiling to get more precise position information.")
        public static final OptionKey<Boolean> ShowSubstitutionSourceInfo = new OptionKey<>(false);
    }

    public static HotSpotCompiledCode createCompiledCode(CodeCacheProvider codeCache, ResolvedJavaMethod method, HotSpotCompilationRequest compRequest, CompilationResult compResult, OptionValues options) {
        String name = compResult.getName();

        byte[] targetCode = compResult.getTargetCode();
        int targetCodeSize = compResult.getTargetCodeSize();

        Site[] sites = getSortedSites(compResult, options, codeCache.shouldDebugNonSafepoints() && method != null);

        Assumption[] assumptions = compResult.getAssumptions();

        ResolvedJavaMethod[] methods = compResult.getMethods();

        List<CodeAnnotation> annotations = compResult.getAnnotations();
        Comment[] comments = new Comment[annotations.size()];
        if (!annotations.isEmpty()) {
            for (int i = 0; i < comments.length; i++) {
                CodeAnnotation annotation = annotations.get(i);
                String text;
                if (annotation instanceof CodeComment) {
                    CodeComment codeComment = (CodeComment) annotation;
                    text = codeComment.value;
                } else if (annotation instanceof JumpTable) {
                    JumpTable jumpTable = (JumpTable) annotation;
                    text = "JumpTable [" + jumpTable.low + " .. " + jumpTable.high + "]";
                } else {
                    text = annotation.toString();
                }
                comments[i] = new Comment(annotation.position, text);
            }
        }

        DataSection data = compResult.getDataSection();
        byte[] dataSection = new byte[data.getSectionSize()];

        ByteBuffer buffer = ByteBuffer.wrap(dataSection).order(ByteOrder.nativeOrder());
        List<DataPatch> patches = new ArrayList<>();
        data.buildDataSection(buffer, (position, vmConstant) -> {
            patches.add(new DataPatch(position, new ConstantReference(vmConstant)));
        });

        int dataSectionAlignment = data.getSectionAlignment();
        DataPatch[] dataSectionPatches = patches.toArray(new DataPatch[patches.size()]);

        int totalFrameSize = compResult.getTotalFrameSize();
        StackSlot customStackArea = compResult.getCustomStackArea();
        boolean isImmutablePIC = compResult.isImmutablePIC();

        if (method instanceof HotSpotResolvedJavaMethod) {
            HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
            int entryBCI = compResult.getEntryBCI();
            boolean hasUnsafeAccess = compResult.hasUnsafeAccess();

            int id;
            long jvmciEnv;
            if (compRequest != null) {
                id = compRequest.getId();
                jvmciEnv = compRequest.getJvmciEnv();
            } else {
                id = hsMethod.allocateCompileId(entryBCI);
                jvmciEnv = 0L;
            }
            return new HotSpotCompiledNmethod(name, targetCode, targetCodeSize, sites, assumptions, methods, comments, dataSection, dataSectionAlignment, dataSectionPatches, isImmutablePIC,
                            totalFrameSize, customStackArea, hsMethod, entryBCI, id, jvmciEnv, hasUnsafeAccess);
        } else {
            return new HotSpotCompiledCode(name, targetCode, targetCodeSize, sites, assumptions, methods, comments, dataSection, dataSectionAlignment, dataSectionPatches, isImmutablePIC,
                            totalFrameSize, customStackArea);
        }
    }

    static class SiteComparator implements Comparator<Site> {

        /**
         * Defines an order for sorting {@link Infopoint}s based on their
         * {@linkplain Infopoint#reason reasons}. This is used to choose which infopoint to preserve
         * when multiple infopoints collide on the same PC offset. A negative order value implies a
         * non-optional infopoint (i.e., must be preserved).
         */
        static final Map<InfopointReason, Integer> HOTSPOT_INFOPOINT_SORT_ORDER = new EnumMap<>(InfopointReason.class);

        static {
            HOTSPOT_INFOPOINT_SORT_ORDER.put(InfopointReason.SAFEPOINT, -4);
            HOTSPOT_INFOPOINT_SORT_ORDER.put(InfopointReason.CALL, -3);
            HOTSPOT_INFOPOINT_SORT_ORDER.put(InfopointReason.IMPLICIT_EXCEPTION, -2);
            HOTSPOT_INFOPOINT_SORT_ORDER.put(InfopointReason.METHOD_START, 2);
            HOTSPOT_INFOPOINT_SORT_ORDER.put(InfopointReason.METHOD_END, 3);
            HOTSPOT_INFOPOINT_SORT_ORDER.put(InfopointReason.BYTECODE_POSITION, 4);
        }

        static int ord(Infopoint info) {
            return HOTSPOT_INFOPOINT_SORT_ORDER.get(info.reason);
        }

        static int checkCollision(Infopoint i1, Infopoint i2) {
            int o1 = ord(i1);
            int o2 = ord(i2);
            if (o1 < 0 && o2 < 0) {
                throw new GraalError("Non optional infopoints cannot collide: %s and %s", i1, i2);
            }
            return o1 - o2;
        }

        /**
         * Records whether any two {@link Infopoint}s had the same {@link Infopoint#pcOffset}.
         */
        boolean sawCollidingInfopoints;

        @Override
        public int compare(Site s1, Site s2) {
            if (s1.pcOffset == s2.pcOffset) {
                // Marks must come first since patching a call site
                // may need to know the mark denoting the call type
                // (see uses of CodeInstaller::_next_call_type).
                boolean s1IsMark = s1 instanceof Mark;
                boolean s2IsMark = s2 instanceof Mark;
                if (s1IsMark != s2IsMark) {
                    return s1IsMark ? -1 : 1;
                }

                // Infopoints must group together so put them after
                // other Site types.
                boolean s1IsInfopoint = s1 instanceof Infopoint;
                boolean s2IsInfopoint = s2 instanceof Infopoint;
                if (s1IsInfopoint != s2IsInfopoint) {
                    return s1IsInfopoint ? 1 : -1;
                }

                if (s1IsInfopoint) {
                    sawCollidingInfopoints = true;
                    return checkCollision((Infopoint) s1, (Infopoint) s2);
                }
            }
            return s1.pcOffset - s2.pcOffset;
        }
    }

    /**
     * HotSpot expects sites to be presented in ascending order of PC (see
     * {@code DebugInformationRecorder::add_new_pc_offset}). In addition, it expects
     * {@link Infopoint} PCs to be unique.
     */
    private static Site[] getSortedSites(CompilationResult target, OptionValues options, boolean includeSourceInfo) {
        List<Site> sites = new ArrayList<>(
                        target.getExceptionHandlers().size() + target.getInfopoints().size() + target.getDataPatches().size() + target.getMarks().size() + target.getSourceMappings().size());
        sites.addAll(target.getExceptionHandlers());
        sites.addAll(target.getInfopoints());
        sites.addAll(target.getDataPatches());
        sites.addAll(target.getMarks());

        if (includeSourceInfo) {
            /*
             * Translate the source mapping into appropriate info points. In HotSpot only one
             * position can really be represented and recording the end PC seems to give the best
             * results and corresponds with what C1 and C2 do. HotSpot doesn't like to see these
             * unless -XX:+DebugNonSafepoints is enabled, so don't emit them in that case.
             */

            List<SourceMapping> sourceMappings = new ArrayList<>();
            ListIterator<SourceMapping> sourceMappingListIterator = target.getSourceMappings().listIterator();
            if (sourceMappingListIterator.hasNext()) {
                SourceMapping currentSource = sourceMappingListIterator.next();
                NodeSourcePosition sourcePosition = currentSource.getSourcePosition();
                if (!sourcePosition.isPlaceholder() && !sourcePosition.isSubstitution()) {
                    sourceMappings.add(currentSource);
                }
                while (sourceMappingListIterator.hasNext()) {
                    SourceMapping nextSource = sourceMappingListIterator.next();
                    assert currentSource.getStartOffset() <= nextSource.getStartOffset() : "Must be presorted";
                    currentSource = nextSource;
                    sourcePosition = currentSource.getSourcePosition();
                    if (!sourcePosition.isPlaceholder() && !sourcePosition.isSubstitution()) {
                        sourceMappings.add(currentSource);
                    }
                }
            }

            /*
             * Don't add BYTECODE_POSITION info points that would potentially create conflicts.
             * Under certain conditions the site's pc is not the pc that gets recorded by HotSpot
             * (see @code {CodeInstaller::site_Call}). So, avoid adding any source positions that
             * can potentially map to the same pc. To do that the following code makes sure that the
             * source mapping doesn't contain a pc of any important Site.
             */
            sites.sort(new SiteComparator());

            ListIterator<Site> siteListIterator = sites.listIterator();
            sourceMappingListIterator = sourceMappings.listIterator();

            List<Site> sourcePositionSites = new ArrayList<>();
            Site site = null;

            // Iterate over sourceMappings and sites in parallel. Create source position infopoints
            // only for source mappings that don't have any sites inside their intervals.
            while (sourceMappingListIterator.hasNext()) {
                SourceMapping source = sourceMappingListIterator.next();

                // Skip sites before the current source mapping
                if (site == null || site.pcOffset < source.getStartOffset()) {
                    while (siteListIterator.hasNext()) {
                        site = siteListIterator.next();
                        if (site.pcOffset >= source.getStartOffset()) {
                            break;
                        }
                    }
                }
                assert !siteListIterator.hasNext() || site.pcOffset >= source.getStartOffset();
                if (site != null && source.getStartOffset() <= site.pcOffset && site.pcOffset <= source.getEndOffset()) {
                    // Conflicting source mapping, skip it.
                    continue;
                } else {
                    // Since the sites are sorted there can not be any more sites in this interval.
                }
                assert !siteListIterator.hasNext() || site.pcOffset > source.getEndOffset();
                // Good source mapping. Create an infopoint and add it to the list.
                NodeSourcePosition sourcePosition = source.getSourcePosition();
                assert sourcePosition.verify();
                if (!ShowSubstitutionSourceInfo.getValue(options)) {
                    sourcePosition = sourcePosition.trim();
                    assert verifyTrim(sourcePosition);
                }
                if (sourcePosition != null) {
                    assert !anyMatch(sites, s -> source.getStartOffset() <= s.pcOffset && s.pcOffset <= source.getEndOffset());
                    sourcePositionSites.add(new Infopoint(source.getEndOffset(), new DebugInfo(sourcePosition), InfopointReason.BYTECODE_POSITION));
                }
            }

            sites.addAll(sourcePositionSites);
        }

        SiteComparator c = new SiteComparator();
        Collections.sort(sites, c);

        if (c.sawCollidingInfopoints) {
            Infopoint lastInfopoint = null;
            List<Site> copy = new ArrayList<>(sites.size());
            for (Site site : sites) {
                if (site instanceof Infopoint) {
                    Infopoint info = (Infopoint) site;
                    if (lastInfopoint == null || lastInfopoint.pcOffset != info.pcOffset) {
                        lastInfopoint = info;
                        copy.add(info);
                    } else {
                        // Omit this colliding infopoint
                        assert lastInfopoint.reason.compareTo(info.reason) <= 0;
                    }
                } else {
                    copy.add(site);
                }
            }
            sites = copy;
        }

        return sites.toArray(new Site[sites.size()]);
    }

    private static boolean verifyTrim(NodeSourcePosition sourcePosition) {
        for (NodeSourcePosition sp = sourcePosition; sp != null; sp = sp.getCaller()) {
            assert (sp.getMethod().getAnnotation(Snippet.class) == null && sp.getMethod().getAnnotation(MethodSubstitution.class) == null);
        }
        return true;
    }
}
