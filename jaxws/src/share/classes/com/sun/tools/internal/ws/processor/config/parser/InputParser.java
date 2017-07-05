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
package com.sun.tools.internal.ws.processor.config.parser;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;

import org.xml.sax.InputSource;

import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;

/**
 * @author Vivek Pandey
 *
 *
 */
public abstract class InputParser{
    protected LocalizableMessageFactory _messageFactory =
        new LocalizableMessageFactory(
            "com.sun.tools.internal.ws.resources.configuration");

    public InputParser(ProcessorEnvironment env, Properties options) {
        this._env = env;
        this._options = options;
        _modelInfoParsers = new HashMap<QName, Object>();

//        /*
//         * Load modelinfo parsers from the plugins which want to extend
//         * this functionality
//         */
//        Iterator i = ToolPluginFactory.getInstance().getExtensions(
//            ToolPluginConstants.WSCOMPILE_PLUGIN,
//            ToolPluginConstants.WSCOMPILE_MODEL_INFO_EXT_POINT);
//        while(i != null && i.hasNext()) {
//            ModelInfoPlugin plugin = (ModelInfoPlugin)i.next();
//            _modelInfoParsers.put(plugin.getModelInfoName(),
//                plugin.createModelInfoParser(env));
//        }
    }

    protected Configuration parse(InputStream is) throws Exception{
        //TODO: Not implemented exception
        return null;
    }

    protected Configuration parse(InputSource is) throws Exception{
        //TODO: Not implemented exception
        return null;
    }

    protected Configuration parse(List<String> inputSources) throws Exception{
        //TODO: Not implemented exception
        return null;
    }

    /**
     * @return Returns the _env.
     */
    public  ProcessorEnvironment getEnv(){
        return _env;
    }

    /**
     * @param env The ProcessorEnvironment to set.
     */
    public void setEnv(ProcessorEnvironment env){
        this._env = env;
    }

    protected void warn(String key) {
        _env.warn(_messageFactory.getMessage(key));
    }

    protected void warn(String key, String arg) {
        _env.warn(_messageFactory.getMessage(key, arg));
    }

    protected void warn(String key, Object[] args) {
        _env.warn(_messageFactory.getMessage(key, args));
    }

    protected void info(String key) {
        _env.info(_messageFactory.getMessage(key));
    }

    protected void info(String key, String arg) {
        _env.info(_messageFactory.getMessage(key, arg));
    }

    protected ProcessorEnvironment _env;
    protected Properties _options;
    protected Map<QName, Object> _modelInfoParsers;
}
