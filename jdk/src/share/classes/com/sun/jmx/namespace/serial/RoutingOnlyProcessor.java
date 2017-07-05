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

import com.sun.jmx.namespace.ObjectNameRouter;


import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * Class RoutingOnlyProcessor. A RewritingProcessor that uses
 * Java Serialization to rewrite ObjectNames contained in
 * input and results...
 *
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
class RoutingOnlyProcessor extends RewritingProcessor {

    final ObjectNameRouter router;

    public RoutingOnlyProcessor(String targetDirName) {
        this(targetDirName,null);
    }

    /** Creates a new instance of RoutingOnlyProcessor */
    public RoutingOnlyProcessor(final String remove, final String add) {
        super(new IdentityProcessor());
        if (remove == null || add == null)
            throw new IllegalArgumentException("Null argument");
        router = new ObjectNameRouter(remove,add);
    }

    @Override
    public final ObjectName toTargetContext(ObjectName sourceName) {
        return router.toTargetContext(sourceName,false);
    }

    @Override
    public final ObjectName toSourceContext(ObjectName targetName) {
        return router.toSourceContext(targetName,false);
    }

    @Override
    public final ObjectInstance toTargetContext(ObjectInstance sourceMoi) {
        return router.toTargetContext(sourceMoi,false);
    }
}
