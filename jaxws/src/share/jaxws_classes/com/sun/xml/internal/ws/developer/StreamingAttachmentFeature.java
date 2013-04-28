/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.developer;

import com.sun.xml.internal.ws.api.FeatureConstructor;
import com.sun.istack.internal.Nullable;

import javax.xml.ws.WebServiceFeature;

import com.sun.xml.internal.org.jvnet.mimepull.MIMEConfig;

import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;

/**
 * Proxy needs to be created with this feature to configure StreamingAttachment
 * attachments behaviour.
 *
 * <pre>
 * for e.g.: To configure all StreamingAttachment attachments to be kept in memory
 * <p>
 *
 * StreamingAttachmentFeature feature = new StreamingAttachmentFeature();
 * feature.setAllMemory(true);
 *
 * proxy = HelloService().getHelloPort(feature);
 *
 * </pre>
 *
 * @author Jitendra Kotamraju
 */
@ManagedData
public final class StreamingAttachmentFeature extends WebServiceFeature {
    /**
     * Constant value identifying the {@link StreamingAttachment} feature.
     */
    public static final String ID = "http://jax-ws.dev.java.net/features/mime";

    private MIMEConfig config;

    private String dir;
    private boolean parseEagerly;
    private long memoryThreshold;

    public StreamingAttachmentFeature() {
    }

    @FeatureConstructor({"dir","parseEagerly","memoryThreshold"})
    public StreamingAttachmentFeature(@Nullable String dir, boolean parseEagerly, long memoryThreshold) {
        this.enabled = true;
        this.dir = dir;
        this.parseEagerly = parseEagerly;
        this.memoryThreshold = memoryThreshold;
    }

    @ManagedAttribute
    public String getID() {
        return ID;
    }

    /**
     * Returns the configuration object. Once this is called, you cannot
     * change the configuration.
     *
     * @return
     */
    @ManagedAttribute
    public MIMEConfig getConfig() {
        if (config == null) {
            config = new MIMEConfig();
            config.setDir(dir);
            config.setParseEagerly(parseEagerly);
            config.setMemoryThreshold(memoryThreshold);
            config.validate();
        }
        return config;
    }

    /**
     * Directory in which large attachments are stored
     */
    public void setDir(String dir) {
        this.dir = dir;
    }

    /**
     * StreamingAttachment message is parsed eagerly
     */
    public void setParseEagerly(boolean parseEagerly) {
        this.parseEagerly = parseEagerly;
    }

    /**
     * After this threshold(no of bytes), large attachments are
     * written to file system
     */
    public void setMemoryThreshold(long memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

}
