/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */
package checker;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.xml.bind.JAXBContext;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.JAXBException;

/**
 * Reads the device configuration from the XML file specified by -Adevice=device.xml.
 * For each class in a project, checks required modules. If the device doesn't have
 * the required module, then a compilation error will be shown.
 */
@SupportedAnnotationTypes("checker.RequireContainer")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PluginChecker extends javax.annotation.processing.AbstractProcessor {

    /**
     * Name of the option to get the path to the xml with device configuration.
     */
    public static final String DEVICE_OPTION = "device";
    private Device device;

    /**
     * Only the device option is supported.
     *
     * {@inheritDoc}
     */
    @Override
    public Set<String> getSupportedOptions() {
        return new HashSet<>(Arrays.asList(DEVICE_OPTION));
    }

    /**
     * Initializes the processor by loading the device configuration.
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        try {
            String deviceOption = processingEnv.getOptions().get(DEVICE_OPTION);
            device = (Device) JAXBContext.newInstance(Device.class)
                    .createUnmarshaller().unmarshal(new File(deviceOption));
        } catch (JAXBException e) {
            throw new RuntimeException(
                    "Please specify device by -Adevice=device.xml\n"
                    + e.toString(), e);
        }
    }

    /**
     * Processes @Require annotations and checks that Device meets requirements.
     *
     * {@inheritDoc}
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        for (Element el : roundEnv.getElementsAnnotatedWith(RequireContainer.class)) {
            for (Require req : el.getAnnotationsByType(Require.class)) {
                //for every Require annotation checks if device has module of required version.
                Integer version = device.getSupportedModules().get(req.value());

                if (version == null
                        || version < req.minVersion()
                        || version > req.maxVersion()) {
                    //if module is optional then show only warning not error
                    if (req.optional()) {
                        processingEnv.getMessager()
                                .printMessage(Diagnostic.Kind.WARNING,
                                        "Plugin [" + el + "] requires " + req
                                        + "\n but device " + (version == null
                                        ? "doesn't have such module."
                                        + " This module is optional."
                                        + " So plugin will work but miss"
                                        + " some functionality"
                                        : "has " + version
                                        + " version of that module"));
                    } else {
                        processingEnv.getMessager()
                                .printMessage(Diagnostic.Kind.ERROR,
                                        "Plugin [" + el + "] requires " + req
                                        + "\n but device "
                                        + (version == null
                                        ? "doesn't have such module"
                                        : "has " + version
                                        + " version of that module"));
                    }
                }
            }
            return true;
        }
        return false;
    }
}
