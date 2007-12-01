/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.processor;

import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.tools.internal.ws.processor.config.ModelInfo;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.xml.internal.ws.util.exception.JAXWSExceptionBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * This abstract class contains methods for getting a Modeler and creating a model
 * from that Modeler given a particular configuration. ProcessorActions can also
 * be registered and run with instances of this class.
 *
 * @author WS Development Team
 *
 */
public class Processor {

    public Processor(Configuration configuration, Properties options, Model model) {
        this(configuration,options);
        _model = model;
    }

    public Processor(Configuration configuration, Properties options) {
        _configuration = configuration;
        _options = options;

        // find the value of the "print stack traces" property
        _printStackTrace = Boolean.valueOf(_options.getProperty(ProcessorOptions.PRINT_STACK_TRACE_PROPERTY));
        _env = _configuration.getEnvironment();
    }

    public void add(ProcessorAction action) {
        _actions.add(action);
    }

    public Model getModel() {
        return _model;
    }

    public void run() {
        runModeler();
        if (_model != null) {
            runActions();
        }
    }

    public void runModeler() {
        try {
            ModelInfo modelInfo = _configuration.getModelInfo();
            if (modelInfo == null) {
                throw new ProcessorException("processor.missing.model");
            }

            _model = modelInfo.buildModel(_options);

        } catch (JAXWSExceptionBase e) {
            if (_printStackTrace) {
                _env.printStackTrace(e);
            }
            _env.error(e);
        }
    }

    public void runActions() {
        try {
            if (_model == null) {
                // avoid reporting yet another error here
                return;
            }

            for (ProcessorAction action : _actions) {
                action.perform(_model, _configuration, _options);
            }
        } catch (JAXWSExceptionBase e) {
            if (_printStackTrace || _env.verbose()) {
                _env.printStackTrace(e);
            }
            _env.error(e);
        }
    }

    private final Properties _options;
    private final Configuration _configuration;
    private final List<ProcessorAction> _actions = new ArrayList<ProcessorAction>();
    private Model _model;
    private final boolean _printStackTrace;
    private final ProcessorEnvironment _env;
}
