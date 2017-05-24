/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.code.CompilationResult.CodeComment;
import org.graalvm.compiler.code.CompilationResult.JumpTable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.code.SourceMapping;
import org.graalvm.compiler.graph.NodeSourcePosition;

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
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotCompiledCodeBuilder {

    public static HotSpotCompiledCode createCompiledCode(CodeCacheProvider codeCache, ResolvedJavaMethod method, HotSpotCompilationRequest compRequest, CompilationResult compResult) {
        String name = compResult.getName();

        byte[] targetCode = compResult.getTargetCode();
        int targetCodeSize = compResult.getTargetCodeSize();

        Site[] sites = getSortedSites(codeCache, compResult);

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
        Builder<DataPatch> patchBuilder = Stream.builder();
        data.buildDataSection(buffer, vmConstant -> {
            patchBuilder.accept(new DataPatch(buffer.position(), new ConstantReference(vmConstant)));
        });

        int dataSectionAlignment = data.getSectionAlignment();
        DataPatch[] dataSectionPatches = patchBuilder.build().toArray(len -> new DataPatch[len]);

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
    private static Site[] getSortedSites(CodeCacheProvider codeCache, CompilationResult target) {
        List<Site> sites = new ArrayList<>(
                        target.getExceptionHandlers().size() + target.getInfopoints().size() + target.getDataPatches().size() + target.getMarks().size() + target.getSourceMappings().size());
        sites.addAll(target.getExceptionHandlers());
        sites.addAll(target.getInfopoints());
        sites.addAll(target.getDataPatches());
        sites.addAll(target.getMarks());

        /*
         * Translate the source mapping into appropriate info points. In HotSpot only one position
         * can really be represented and recording the end PC seems to give the best results and
         * corresponds with what C1 and C2 do.
         */
        if (codeCache.shouldDebugNonSafepoints()) {
            for (SourceMapping source : target.getSourceMappings()) {
                sites.add(new Infopoint(source.getEndOffset(), new DebugInfo(source.getSourcePosition()), InfopointReason.BYTECODE_POSITION));
                assert verifySourcePositionReceivers(source.getSourcePosition());
            }
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

    /**
     * Verifies that the captured receiver type agrees with the declared type of the method.
     */
    private static boolean verifySourcePositionReceivers(NodeSourcePosition start) {
        NodeSourcePosition pos = start;
        while (pos != null) {
            if (pos.getReceiver() != null) {
                assert ((HotSpotObjectConstant) pos.getReceiver()).asObject(pos.getMethod().getDeclaringClass()) != null;
            }
            pos = pos.getCaller();
        }
        return true;
    }
}
