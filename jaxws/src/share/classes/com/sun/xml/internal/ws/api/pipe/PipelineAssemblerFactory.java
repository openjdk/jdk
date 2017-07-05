/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.util.ServiceFinder;

import javax.xml.ws.soap.SOAPBinding;
import java.util.logging.Logger;

/**
 * Creates {@link PipelineAssembler}.
 *
 * <p>
 * To create a pipeline,
 * the JAX-WS runtime locates {@link PipelineAssemblerFactory}s through
 * the <tt>META-INF/services/com.sun.xml.internal.ws.api.pipe.PipelineAssemblerFactory</tt> files.
 * Factories found are checked to see if it supports the given binding ID one by one,
 * and the first valid {@link PipelineAssembler} returned will be used to create
 * a pipeline.
 *
 * <p>
 * TODO: is bindingId really extensible? for this to be extensible,
 * someone seems to need to hook into WSDL parsing.
 *
 * <p>
 * TODO: JAX-WSA might not define its own binding ID -- it may just go to an extension element
 * of WSDL. So this abstraction might need to be worked on.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated
 *      Use {@link TubelineAssemblerFactory} instead.
 */
public abstract class PipelineAssemblerFactory {
    /**
     * Creates a {@link PipelineAssembler} applicable for the given binding ID.
     *
     * @param bindingId
     *      The binding ID for which a pipeline will be created,
     *      such as {@link SOAPBinding#SOAP11HTTP_BINDING}.
     *      Must not be null.
     *
     * @return
     *      null if this factory doesn't recognize the given binding ID.
     */
    public abstract PipelineAssembler doCreate(BindingID bindingId);

    /**
     * Locates {@link PipelineAssemblerFactory}s and create
     * a suitable {@link PipelineAssembler}.
     *
     * @param bindingId
     *      The binding ID string for which the new {@link PipelineAssembler}
     *      is created. Must not be null.
     * @return
     *      Always non-null, since we fall back to our default {@link PipelineAssembler}.
     */
    public static PipelineAssembler create(ClassLoader classLoader, BindingID bindingId) {
        for (PipelineAssemblerFactory factory : ServiceFinder.find(PipelineAssemblerFactory.class,classLoader)) {
            PipelineAssembler assembler = factory.doCreate(bindingId);
            if(assembler!=null) {
                logger.fine(factory.getClass()+" successfully created "+assembler);
                return assembler;
            }
        }

        // default binding IDs that are known
        // TODO: replace this with proper ones
        return new com.sun.xml.internal.ws.util.pipe.StandalonePipeAssembler();
    }

    private static final Logger logger = Logger.getLogger(PipelineAssemblerFactory.class.getName());
}
