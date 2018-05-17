/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.mlvm.meth.share.transform.v2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MHSamTF extends MHBasicUnaryTF {

    public static interface SAM {
        public Object run(Object[] o) throws Throwable;
    }

    public static class SAMCaller {
        private final SAM sam;

        public SAMCaller(SAM sam) {
            this.sam = sam;
        }

        public Object callSAM(Object[] o) throws Throwable {
            return sam.run(o);
        }
    }

    public MHSamTF(MHCall target) {
        super(target);
    }

    @Override
    protected MethodHandle computeInboundMH(MethodHandle targetMH) throws NoSuchMethodException, IllegalAccessException {
        throw new RuntimeException("Internal error: Functionality is disabled in JDK7");
        /*
        MethodHandle mh = targetMH.asSpreader(Object[].class, targetMH.type().parameterCount());

        SAM sam = MethodHandles.asInstance(mh, SAM.class);

        // The checks below aimed to increase coverage
        MethodHandle mhCopy = MethodHandles.wrapperInstanceTarget(sam);
        if ( ! mh.equals(mhCopy) ) {
            throw new IllegalArgumentException("wrapperInstanceTarget returned a different MH: [" + mhCopy + "]; original was [" + mh + "]");
        }

        Class<?> samClass = MethodHandles.wrapperInstanceType(sam);
        if ( ! SAM.class.equals(samClass) ) {
            throw new IllegalArgumentException("wrapperInstanceType returned a different class: [" + samClass + "]; original was [" + SAM.class + "]");
        }

        if ( ! MethodHandles.isWrapperInstance(sam) ) {
            throw new IllegalArgumentException("isWrapperInstance returned false for SAM object: [" + sam + "]");
        }

        return MethodHandles.convertArguments(
                   MethodHandles.lookup().findVirtual(SAMCaller.class, "callSAM", MethodType.methodType(Object.class, Object[].class))
                       .bindTo(new SAMCaller(sam))
                       .asCollector(Object[].class, targetMH.type().parameterCount()),
                   targetMH.type());

        */
    }

    @Override
    protected String getName() {
        return "SAM";
    }

    @Override
    protected String getDescription() {
        return "";
    }
}
