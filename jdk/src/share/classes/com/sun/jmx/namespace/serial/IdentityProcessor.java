/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.jmx.namespace.serial;


import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * Class RoutingOnlyProcessor. A RewritingProcessor that uses
 * Java Serialization to rewrite ObjectNames contained in
 * input & results...
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 *
 * @since 1.7
 */
class IdentityProcessor extends RewritingProcessor {


    /** Creates a new instance of SerialRewritingProcessor */
    public IdentityProcessor() {
    }

    @Override
    public <T> T rewriteOutput(T result) {
        return result;
    }

    @Override
    public <T> T rewriteInput(T input) {
        return input;
    }

    @Override
    public final ObjectName toTargetContext(ObjectName sourceName) {
        return sourceName;
    }

    @Override
    public final ObjectInstance toTargetContext(ObjectInstance sourceMoi) {
        return sourceMoi;
    }

    @Override
    public final ObjectName toSourceContext(ObjectName targetName) {
        return targetName;
    }

}
