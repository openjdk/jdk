/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.assembler;

import com.sun.istack.internal.logging.Logger;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.assembler.dev.ClientTubelineAssemblyContext;
import com.sun.xml.internal.ws.assembler.dev.TubeFactory;
import com.sun.xml.internal.ws.assembler.dev.TubelineAssemblyContextUpdater;
import com.sun.xml.internal.ws.resources.TubelineassemblyMessages;
import com.sun.xml.internal.ws.runtime.config.TubeFactoryConfig;

/**
 * Utility class that encapsulates logic of loading TubeFactory
 * instances and creating Tube instances.
 *
 * @author m_potociar
 */
final class TubeCreator {
    private static final Logger LOGGER = Logger.getLogger(TubeCreator.class);
    private final TubeFactory factory;
    private final String msgDumpPropertyBase;

    TubeCreator(TubeFactoryConfig config, ClassLoader tubeFactoryClassLoader) {
        String className = config.getClassName();
        try {
            Class<?> factoryClass;
            if (isJDKInternal(className)) {
                factoryClass = Class.forName(className, true, TubeCreator.class.getClassLoader());
            } else {
                factoryClass = Class.forName(className, true, tubeFactoryClassLoader);
            }
            if (TubeFactory.class.isAssignableFrom(factoryClass)) {
                // We can suppress "unchecked" warning here as we are checking for the correct type in the if statement above
                @SuppressWarnings("unchecked")
                Class<TubeFactory> typedClass = (Class<TubeFactory>) factoryClass;
                this.factory = typedClass.newInstance();
                this.msgDumpPropertyBase = this.factory.getClass().getName() + ".dump";
            } else {
                throw new RuntimeException(TubelineassemblyMessages.MASM_0015_CLASS_DOES_NOT_IMPLEMENT_INTERFACE(factoryClass.getName(), TubeFactory.class.getName()));
            }
        } catch (InstantiationException ex) {
            throw LOGGER.logSevereException(new RuntimeException(TubelineassemblyMessages.MASM_0016_UNABLE_TO_INSTANTIATE_TUBE_FACTORY(className), ex), true);
        } catch (IllegalAccessException ex) {
            throw LOGGER.logSevereException(new RuntimeException(TubelineassemblyMessages.MASM_0016_UNABLE_TO_INSTANTIATE_TUBE_FACTORY(className), ex), true);
        } catch (ClassNotFoundException ex) {
            throw LOGGER.logSevereException(new RuntimeException(TubelineassemblyMessages.MASM_0017_UNABLE_TO_LOAD_TUBE_FACTORY_CLASS(className), ex), true);
        }
    }

    Tube createTube(DefaultClientTubelineAssemblyContext context) {
        // TODO implement passing init parameters (if any) to the factory
        return factory.createTube(context);
    }

    Tube createTube(DefaultServerTubelineAssemblyContext context) {
        // TODO implement passing init parameters (if any) to the factory
        return factory.createTube(context);
    }

    void updateContext(ClientTubelineAssemblyContext context) {
        if (factory instanceof TubelineAssemblyContextUpdater) {
            ((TubelineAssemblyContextUpdater) factory).prepareContext(context);
        }
    }

    void updateContext(DefaultServerTubelineAssemblyContext context) {
        if (factory instanceof TubelineAssemblyContextUpdater) {
            ((TubelineAssemblyContextUpdater) factory).prepareContext(context);
        }
    }

    String getMessageDumpPropertyBase() {
        return msgDumpPropertyBase;
    }

    private boolean isJDKInternal(String className) {
        // avoid repackaging
        return className.startsWith("com." + "sun.xml.internal.ws");
    }

}
