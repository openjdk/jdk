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
 * Class DefaultRewritingProcessor. Rewrite ObjectName in input & output
 * parameters.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
// We know that rewriting using serialization is costly.
// This object tries to determine whether an object needs rewriting prior
// to rewriting, and rewrites by creating a new object in those cases
// where we know how to recreate a new object (e.g. a Notification).
// Rewriting is however usually not used - so this object is just a
// skeleton that eventually uses serialization...
//
class DefaultRewritingProcessor extends RewritingProcessor {


    private static enum RewriteMode {
        INPUT,  // Input from target to source  (parameters)
        OUTPUT  // Output from source to target (results)
    };

    private final boolean identity;

    public DefaultRewritingProcessor(String targetDirName) {
        this(targetDirName,null);
    }

    /** Creates a new instance of SerialParamProcessor */
    public DefaultRewritingProcessor(final String remove, final String add) {
        super(new SerialRewritingProcessor(remove, add));
        identity = remove.equals(add);
    }

    private ObjectName rewriteObjectName(RewriteMode mode,
            ObjectName name) {
        return changeContext(mode, name);
    }

    private ObjectInstance rewriteObjectInstance(RewriteMode mode,
            ObjectInstance moi) {
        final ObjectName srcName = moi.getObjectName();
        final ObjectName targetName = changeContext(mode,srcName);
        if (targetName == srcName) return moi;
        return new ObjectInstance(targetName,moi.getClassName());
    }


    private Object processObject(RewriteMode mode, Object obj) {
        if (obj == null) return null;

        // Some things which will always needs rewriting:
        // ObjectName, ObjectInstance, and Notifications.
        // Take care of those we can handle here...
        //
        if (obj instanceof ObjectName)
            return rewriteObjectName(mode,(ObjectName) obj);
        else if (obj instanceof ObjectInstance)
            return rewriteObjectInstance(mode,(ObjectInstance) obj);

        // TODO: add other standard JMX classes - like e.g. MBeanInfo...
        //

        // Well, the object may contain an ObjectName => pass it to
        // our serial rewriting delegate...
        //
        return processAnyObject(mode,obj);
    }


    private Object processAnyObject(RewriteMode mode, Object obj) {
        switch (mode) {
            case INPUT:
                return super.rewriteInput(obj);
            case OUTPUT:
                return super.rewriteOutput(obj);
            default: // can't happen.
                throw new AssertionError();
        }
    }

    private ObjectName changeContext(RewriteMode mode, ObjectName name) {
        switch (mode) {
            case INPUT:
                return toSourceContext(name);
            case OUTPUT:
                return toTargetContext(name);
            default: // can't happen.
                throw new AssertionError();
        }
    }

    @Override
    public ObjectName toTargetContext(ObjectName srcName) {
        if (identity) return srcName;
        return super.toTargetContext(srcName);
    }

    @Override
    public ObjectName toSourceContext(ObjectName targetName) {
        if (identity) return targetName;
        return super.toSourceContext(targetName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T rewriteInput(T input) {
        if (identity) return input;
        return (T) processObject(RewriteMode.INPUT,input);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T rewriteOutput(T result) {
        if (identity) return result;
        return (T) processObject(RewriteMode.OUTPUT,result);
    }
}
