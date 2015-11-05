/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import java.nio.*;
import java.util.*;
import java.util.stream.*;
import java.util.stream.Stream.Builder;

import jdk.vm.ci.code.*;
import jdk.vm.ci.code.CompilationResult.CodeAnnotation;
import jdk.vm.ci.code.CompilationResult.CodeComment;
import jdk.vm.ci.code.CompilationResult.DataPatch;
import jdk.vm.ci.code.CompilationResult.ExceptionHandler;
import jdk.vm.ci.code.CompilationResult.Infopoint;
import jdk.vm.ci.code.CompilationResult.JumpTable;
import jdk.vm.ci.code.CompilationResult.Mark;
import jdk.vm.ci.code.CompilationResult.Site;
import jdk.vm.ci.meta.*;
import jdk.vm.ci.meta.Assumptions.Assumption;

/**
 * A {@link CompilationResult} with additional HotSpot-specific information required for installing
 * the code in HotSpot's code cache.
 */
public abstract class HotSpotCompiledCode {

    public final String name;
    public final Site[] sites;
    public final ExceptionHandler[] exceptionHandlers;
    public final Comment[] comments;
    public final Assumption[] assumptions;

    public final byte[] targetCode;
    public final int targetCodeSize;

    public final byte[] dataSection;
    public final int dataSectionAlignment;
    public final DataPatch[] dataSectionPatches;
    public final boolean isImmutablePIC;

    public final int totalFrameSize;
    public final int customStackAreaOffset;

    /**
     * The list of the methods whose bytecodes were used as input to the compilation. If
     * {@code null}, then the compilation did not record method dependencies. Otherwise, the first
     * element of this array is the root method of the compilation.
     */
    public final ResolvedJavaMethod[] methods;

    public static class Comment {

        public final String text;
        public final int pcOffset;

        public Comment(int pcOffset, String text) {
            this.text = text;
            this.pcOffset = pcOffset;
        }
    }

    public HotSpotCompiledCode(CompilationResult compResult) {
        name = compResult.getName();
        sites = getSortedSites(compResult);
        if (compResult.getExceptionHandlers().isEmpty()) {
            exceptionHandlers = null;
        } else {
            exceptionHandlers = compResult.getExceptionHandlers().toArray(new ExceptionHandler[compResult.getExceptionHandlers().size()]);
        }
        List<CodeAnnotation> annotations = compResult.getAnnotations();
        comments = new Comment[annotations.size()];
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
        assumptions = compResult.getAssumptions();
        assert validateFrames();

        targetCode = compResult.getTargetCode();
        targetCodeSize = compResult.getTargetCodeSize();

        DataSection data = compResult.getDataSection();
        if (!data.isFinalized()) {
            data.finalizeLayout();
        }
        dataSection = new byte[data.getSectionSize()];

        ByteBuffer buffer = ByteBuffer.wrap(dataSection).order(ByteOrder.nativeOrder());
        Builder<DataPatch> patchBuilder = Stream.builder();
        data.buildDataSection(buffer, patchBuilder);

        dataSectionAlignment = data.getSectionAlignment();
        dataSectionPatches = patchBuilder.build().toArray(len -> new DataPatch[len]);

        isImmutablePIC = compResult.isImmutablePIC();

        totalFrameSize = compResult.getTotalFrameSize();
        customStackAreaOffset = compResult.getCustomStackAreaOffset();

        methods = compResult.getMethods();
    }

    /**
     * Ensure that all the frames passed into HotSpot are properly formatted with an empty or
     * illegal slot following double word slots.
     */
    private boolean validateFrames() {
        for (Site site : sites) {
            if (site instanceof Infopoint) {
                Infopoint info = (Infopoint) site;
                if (info.debugInfo != null) {
                    BytecodeFrame frame = info.debugInfo.frame();
                    assert frame == null || frame.validateFormat();
                }
            }
        }
        return true;
    }

    static class SiteComparator implements Comparator<Site> {

        public int compare(Site s1, Site s2) {
            if (s1.pcOffset == s2.pcOffset && (s1 instanceof Mark ^ s2 instanceof Mark)) {
                return s1 instanceof Mark ? -1 : 1;
            }
            return s1.pcOffset - s2.pcOffset;
        }
    }

    private static Site[] getSortedSites(CompilationResult target) {
        List<?>[] lists = new List<?>[]{target.getInfopoints(), target.getDataPatches(), target.getMarks()};
        int count = 0;
        for (List<?> list : lists) {
            count += list.size();
        }
        Site[] result = new Site[count];
        int pos = 0;
        for (List<?> list : lists) {
            for (Object elem : list) {
                result[pos++] = (Site) elem;
            }
        }
        Arrays.sort(result, new SiteComparator());
        return result;
    }
}
