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

package com.sun.jmx.namespace;

import static javax.management.namespace.JMXNamespaces.NAMESPACE_SEPARATOR;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * The ObjectNameRouter is used to rewrite routing object names.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public class ObjectNameRouter {

    private static final int NAMESPACE_SEPARATOR_LENGTH =
            NAMESPACE_SEPARATOR.length();

    final String targetPrefix;
    final String sourcePrefix;
    final int slen;
    final int tlen;
    final boolean identity;

    /** Creates a new instance of ObjectNameRouter */
    public ObjectNameRouter(final String remove, final String add) {
        this.targetPrefix = (remove==null?"":remove);
        this.sourcePrefix = (add==null?"":add);
        tlen = targetPrefix.length();
        slen = sourcePrefix.length();
        identity = targetPrefix.equals(sourcePrefix);
    }

    public final ObjectName toTargetContext(ObjectName sourceName,
            boolean removeLeadingSeparators) {
        if (sourceName == null) return null;
        if (identity) return sourceName;
        String srcDomain = sourceName.getDomain();

        // if the ObjectName starts with // and removeLeadingSeparators is
        // true, then recursively strip leading //.
        // Otherwise, do not rewrite ObjectName.
        //
        if (srcDomain.startsWith(NAMESPACE_SEPARATOR)) {
            if (!removeLeadingSeparators) return sourceName;
            else srcDomain = normalizeDomain(srcDomain,true);
        }
        if (slen != 0) {
            if (!srcDomain.startsWith(sourcePrefix) ||
                    !srcDomain.startsWith(NAMESPACE_SEPARATOR,slen))
                throw new IllegalArgumentException(
                        "ObjectName does not start with expected prefix "
                        + sourcePrefix + ": " +
                        String.valueOf(sourceName));
            srcDomain = srcDomain.substring(slen+NAMESPACE_SEPARATOR_LENGTH);
        }
        final String targetDomain =
                (tlen>0?targetPrefix+NAMESPACE_SEPARATOR+srcDomain:srcDomain);
        try {
            return sourceName.withDomain(targetDomain);
        } catch (MalformedObjectNameException x) {
            throw new IllegalArgumentException(String.valueOf(sourceName),x);
        }
    }

    public final ObjectName toSourceContext(ObjectName targetName,
            boolean removeLeadingSeparators) {
        if (targetName == null) return null;
        if (identity) return targetName;
        String targetDomain = targetName.getDomain();
        if (targetDomain.startsWith(NAMESPACE_SEPARATOR)) {
            if (!removeLeadingSeparators) return targetName;
            else targetDomain =
                    normalizeDomain(targetDomain,true);
        }
        if (tlen != 0) {
            if (!targetDomain.startsWith(targetPrefix) ||
                    !targetDomain.startsWith(NAMESPACE_SEPARATOR,tlen))
                throw new IllegalArgumentException(
                        "ObjectName does not start with expected prefix "
                        + targetPrefix + ": " +
                        String.valueOf(targetName));
            targetDomain = targetDomain.
                    substring(tlen+NAMESPACE_SEPARATOR_LENGTH);
        }
        final String sourceDomain =
                (slen>0?sourcePrefix+NAMESPACE_SEPARATOR+targetDomain:
                    targetDomain);
        try {
            return targetName.withDomain(sourceDomain);
        } catch (MalformedObjectNameException x) {
            throw new IllegalArgumentException(String.valueOf(targetName),x);
        }
    }

    public final ObjectInstance toTargetContext(ObjectInstance sourceMoi,
            boolean removeLeadingSeparators) {
        if (sourceMoi == null) return null;
        if (identity) return sourceMoi;
        return new ObjectInstance(
                toTargetContext(sourceMoi.getObjectName(),
                    removeLeadingSeparators),
                    sourceMoi.getClassName());
    }

    /**
     * Removes leading, trailing, or duplicate // in a name space path.
     **/
    public static String normalizeDomain(String domain,
                                         boolean removeLeadingSep) {
        return normalizeNamespacePath(domain,removeLeadingSep,false,true);
    }

    /**
     * Removes leading, trailing, or duplicate // in a name space path.
     **/
    public static String normalizeNamespacePath(String namespacePath,
                                            boolean removeLeadingSep,
                                            boolean removeTrailingSep,
                                            boolean endsWithDomain) {
        if (namespacePath.equals(""))
            return "";
        final String[] components = namespacePath.split(NAMESPACE_SEPARATOR);
        final StringBuilder b =
                new StringBuilder(namespacePath.length()+NAMESPACE_SEPARATOR_LENGTH);
        String sep = null;
        if (!removeLeadingSep && namespacePath.startsWith(NAMESPACE_SEPARATOR))
            b.append(NAMESPACE_SEPARATOR);
        int count = 0;
        for (int i=0; i<components.length; i++) {
            final String n=components[i];
            if (n.equals("")) continue;
            if (n.startsWith("/")||n.endsWith("/")) {
                // throw exception unless we're looking at the last domain
                // part of the ObjectName
                if (! (endsWithDomain && i==(components.length-1))) {
                    throw new IllegalArgumentException(n+
                        " is not a valid name space identifier");
                } else {
                    // There's a dirty little corner case when the domain
                    // part (last item) is exactly '/' - in that case we must
                    // not append '//'
                    //
                    removeTrailingSep = removeTrailingSep || n.equals("/");
                }
            }
            if (sep != null) b.append(sep);
            b.append(n);
            sep = NAMESPACE_SEPARATOR;
            count++;
        }
        if (!removeTrailingSep && namespacePath.endsWith(NAMESPACE_SEPARATOR)
            && count > 0)
            b.append(NAMESPACE_SEPARATOR);
        return b.toString();
    }
}
