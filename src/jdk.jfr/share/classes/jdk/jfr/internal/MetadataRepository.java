/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.SettingControl;
import jdk.jfr.internal.EventInstrumentation.SettingInfo;
import jdk.jfr.internal.RequestEngine.RequestHook;
import jdk.jfr.internal.consumer.RepositoryFiles;
import jdk.jfr.internal.event.EventConfiguration;

public final class MetadataRepository {

    private static final JVM jvm = JVM.getJVM();
    private static final MetadataRepository instance = new MetadataRepository();

    private final List<EventType> nativeEventTypes = new ArrayList<>(100);
    private final List<EventControl> nativeControls = new ArrayList<EventControl>(100);
    private final TypeLibrary typeLibrary = TypeLibrary.getInstance();
    private final SettingsManager settingsManager = new SettingsManager();
    private final Map<String, Class<? extends Event>> mirrors = new HashMap<>();
    private Constructor<EventConfiguration> cachedEventConfigurationConstructor;
    private boolean staleMetadata = true;
    private boolean unregistered;
    private long lastUnloaded = -1;

    public MetadataRepository() {
        initializeJVMEventTypes();
    }

    private void initializeJVMEventTypes() {
        List<RequestHook> requestHooks = new ArrayList<>();
        for (Type type : new ArrayList<>(typeLibrary.getTypes())) {
            if (type instanceof PlatformEventType pEventType) {
                EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
                pEventType.setHasDuration(eventType.getAnnotation(Threshold.class) != null);
                pEventType.setHasStackTrace(eventType.getAnnotation(StackTrace.class) != null);
                pEventType.setHasCutoff(eventType.getAnnotation(Cutoff.class) != null);
                pEventType.setHasThrottle(eventType.getAnnotation(Throttle.class) != null);
                pEventType.setHasPeriod(eventType.getAnnotation(Period.class) != null);
                // Must add hook before EventControl is created as it removes
                // annotations, such as Period and Threshold.
                if (pEventType.hasPeriod()) {
                    pEventType.setEventHook(true);
                    if (!pEventType.isMethodSampling()) {
                        requestHooks.add(new RequestHook(pEventType));
                    }
                }
                nativeControls.add(new EventControl(pEventType));
                nativeEventTypes.add(eventType);
            }
        }
        RequestEngine.addHooks(requestHooks);
    }

    public static MetadataRepository getInstance() {
        return instance;
    }

    public synchronized List<EventType> getRegisteredEventTypes() {
        List<EventConfiguration> configurations = getEventConfigurations();
        List<EventType> eventTypes = new ArrayList<>(configurations.size() + nativeEventTypes.size());
        for (EventConfiguration ec : configurations) {
            if (ec.isRegistered()) {
                eventTypes.add(ec.getEventType());
            }
        }
        for (EventType t : nativeEventTypes) {
            if (PrivateAccess.getInstance().isVisible(t)) {
                eventTypes.add(t);
            }
        }
        return eventTypes;
    }

    public synchronized EventType getEventType(Class<? extends jdk.internal.event.Event> eventClass) {
        EventConfiguration ec = getConfiguration(eventClass, false);
        if (ec != null && ec.isRegistered()) {
            return ec.getEventType();
        }
        throw new IllegalStateException("Event class " + eventClass.getName() + " is not registered");
    }

    public synchronized void unregister(Class<? extends Event> eventClass) {
        Utils.checkRegisterPermission();
        EventConfiguration configuration = getConfiguration(eventClass, false);
        if (configuration != null) {
            configuration.getPlatformEventType().setRegistered(false);
        }
        // never registered, ignore call
    }
    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass) {
        return register(eventClass, Collections.emptyList(), Collections.emptyList());
    }

    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) {
        Utils.checkRegisterPermission();
        if (jvm.isExcluded(eventClass)) {
            // Event classes are marked as excluded during class load
            // if they override methods in the jdk.jfr.Event class, i.e. commit().
            // An excluded class lacks a configuration field and can't be used by JFR.
            // The Event::commit() is marked as final, so javac won't
            // compile an override, but it can be constructed by other means.
            throw new IllegalArgumentException("Must not override methods declared in jdk.jfr.Event");
        }
        EventConfiguration configuration = getConfiguration(eventClass, true);
        if (configuration == null) {
            if (eventClass.getAnnotation(MirrorEvent.class) != null) {
                // Don't register mirror classes.
                return null;
            }
            PlatformEventType pe = findMirrorType(eventClass);
            configuration = makeConfiguration(eventClass, pe, dynamicAnnotations, dynamicFields);
        }
        configuration.getPlatformEventType().setRegistered(true);
        typeLibrary.addType(configuration.getPlatformEventType());
        if (jvm.isRecording()) {
            settingsManager.setEventControl(configuration.getEventControl(), true, JVM.counterTime());
            settingsManager.updateRetransform(Collections.singletonList((eventClass)));
       }
       setStaleMetadata();
       return configuration.getEventType();
    }

    private PlatformEventType findMirrorType(Class<? extends jdk.internal.event.Event> eventClass) throws InternalError {
        String fullName = eventClass.getModule().getName() + ":" + eventClass.getName();
        Class<? extends Event> mirrorClass = mirrors.get(fullName);
        if (mirrorClass == null) {
            return null; // not a mirror
        }
        Utils.verifyMirror(mirrorClass, eventClass);
        PlatformEventType et = (PlatformEventType) TypeLibrary.createType(mirrorClass);
        typeLibrary.removeType(et.getId());
        long id = Type.getTypeId(eventClass);
        et.setId(id);
        return et;
    }

    private EventConfiguration getConfiguration(Class<? extends jdk.internal.event.Event> eventClass, boolean ensureInitialized) {
        Utils.ensureValidEventSubclass(eventClass);
        SecuritySupport.makeVisibleToJFR(eventClass);
        if (ensureInitialized) {
            Utils.ensureInitialized(eventClass);
        }
        return Utils.getConfiguration(eventClass);
    }

    private EventConfiguration newEventConfiguration(EventType eventType, EventControl ec, SettingControl[] settings) {
        try {
            if (cachedEventConfigurationConstructor == null) {
                var argClasses = new Class<?>[] { EventType.class, EventControl.class, SettingControl[].class };
                Constructor<EventConfiguration> c = EventConfiguration.class.getDeclaredConstructor(argClasses);
                SecuritySupport.setAccessible(c);
                cachedEventConfigurationConstructor = c;
            }
            return cachedEventConfigurationConstructor.newInstance(eventType, ec, settings);
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new InternalError(e);
        }
    }

    private EventConfiguration makeConfiguration(Class<? extends jdk.internal.event.Event> eventClass, PlatformEventType pEventType, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) throws InternalError {
        SecuritySupport.addInternalEventExport(eventClass);
        if (pEventType == null) {
            pEventType = (PlatformEventType) TypeLibrary.createType(eventClass, dynamicAnnotations, dynamicFields);
        }
        EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
        EventControl ec = new EventControl(pEventType, eventClass);
        List<SettingInfo> settingInfos = ec.getSettingInfos();
        SettingControl[] settings = new SettingControl[settingInfos.size()];
        int index = 0;
        for (var settingInfo : settingInfos) {
            settings[index++] = settingInfo.settingControl();
        }
        EventConfiguration configuration = newEventConfiguration(eventType, ec, settings);
        PlatformEventType pe = configuration.getPlatformEventType();
        pe.setRegistered(true);
        // If class is instrumented or should not be instrumented, mark as instrumented.
        if (jvm.isInstrumented(eventClass) || !Utils.shouldInstrument(pe.isJDK(), pe.getName())) {
            pe.setInstrumented();
        }
        Utils.setConfiguration(eventClass, configuration);
        return configuration;
    }

    public synchronized void setSettings(List<Map<String, String>> list, boolean writeSettingEvents) {
        settingsManager.setSettings(list, writeSettingEvents);
    }

    synchronized void disableEvents() {
        for (EventControl c : getEventControls()) {
            c.disable();
        }
    }

    public synchronized List<EventControl> getEventControls() {
        List<Class<? extends jdk.internal.event.Event>> eventClasses = jvm.getAllEventClasses();
        ArrayList<EventControl> controls = new ArrayList<>(eventClasses.size() + nativeControls.size());
        controls.addAll(nativeControls);
        for (Class<? extends jdk.internal.event.Event> clazz : eventClasses) {
            EventConfiguration eh = Utils.getConfiguration(clazz);
            if (eh != null) {
                controls.add(eh.getEventControl());
            }
        }
        return controls;
    }

    private void storeDescriptorInJVM() throws InternalError {
        jvm.storeMetadataDescriptor(getBinaryRepresentation());
        staleMetadata = false;
    }

    private static List<EventConfiguration> getEventConfigurations() {
        List<Class<? extends jdk.internal.event.Event>> allEventClasses = jvm.getAllEventClasses();
        List<EventConfiguration> eventConfigurations = new ArrayList<>(allEventClasses.size());
        for (Class<? extends jdk.internal.event.Event> clazz : allEventClasses) {
            EventConfiguration ec = Utils.getConfiguration(clazz);
            if (ec != null) {
                eventConfigurations.add(ec);
            }
        }
        return eventConfigurations;
    }

    private byte[] getBinaryRepresentation() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(40000);
        DataOutputStream daos = new DataOutputStream(baos);
        try {
            List<Type> types = typeLibrary.getVisibleTypes();
            if (Logger.shouldLog(LogTag.JFR_METADATA, LogLevel.DEBUG)) {
                Collections.sort(types,Comparator.comparing(Type::getName));
                for (Type t: types) {
                    Logger.log(LogTag.JFR_METADATA, LogLevel.INFO, "Serialized type: " + t.getName() + " id=" + t.getId());
                }
            }
            Collections.sort(types);
            MetadataDescriptor.write(types, daos);
            daos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            // should not happen
            throw new InternalError(e);
        }
    }

    synchronized boolean isEnabled(String eventName) {
        return settingsManager.isEnabled(eventName);
    }

    synchronized void setStaleMetadata() {
        staleMetadata = true;
    }

    // Lock around setOutput ensures that other threads don't
    // emit events after setOutput and unregister the event class, before a call
    // to storeDescriptorInJVM
    synchronized Instant setOutput(String filename) {
        if (staleMetadata) {
            storeDescriptorInJVM();
        }
        jvm.setOutput(filename);
        // Each chunk needs a unique start timestamp and
        // if the clock resolution is low, two chunks may
        // get the same timestamp. Utils.getChunkStartNanos()
        // ensures the timestamp is unique for the next chunk
        long chunkStart = Utils.getChunkStartNanos();
        if (filename != null) {
            RepositoryFiles.notifyNewFile();
        }
        unregisterUnloaded();
        if (unregistered) {
            if (typeLibrary.clearUnregistered()) {
                storeDescriptorInJVM();
            }
            unregistered = false;
        }
        return Utils.epochNanosToInstant(chunkStart);
    }

    private void unregisterUnloaded() {
        long unloaded = jvm.getUnloadedEventClassCount();
        if (this.lastUnloaded != unloaded) {
            this.lastUnloaded = unloaded;
            List<Class<? extends jdk.internal.event.Event>> eventClasses = jvm.getAllEventClasses();
            HashSet<Long> knownIds = new HashSet<>(eventClasses.size());
            for (Class<? extends jdk.internal.event.Event>  ec: eventClasses) {
                knownIds.add(Type.getTypeId(ec));
            }
            for (Type type : typeLibrary.getTypes()) {
                if (type instanceof PlatformEventType pe) {
                    if (!knownIds.contains(pe.getId())) {
                        if (!pe.isJVM()) {
                            pe.setRegistered(false);
                        }
                    }
                }
            }
        }
    }

    synchronized void setUnregistered() {
       unregistered = true;
    }

    public synchronized void registerMirror(Class<? extends Event> eventClass) {
        MirrorEvent me = eventClass.getAnnotation(MirrorEvent.class);
        if (me != null) {
            String fullName = me.module() + ":" + me.className();
            mirrors.put(fullName, eventClass);
            return;
        }
        throw new InternalError("Mirror class must have annotation " + MirrorEvent.class.getName());
    }

    public synchronized void flush() {
        if (staleMetadata) {
            storeDescriptorInJVM();
        }
        jvm.flush();
    }

    static void unhideInternalTypes() {
        for (Type t : TypeLibrary.getInstance().getTypes()) {
            if (t.isInternal()) {
                t.setVisible(true);
                Logger.log(LogTag.JFR_METADATA, LogLevel.DEBUG, "Unhiding internal type " + t.getName());
            }
        }
        // Singleton should have been initialized here.
        // It's not possible to call MetadataRepository().getInstance(),
        // because it will deadlock with Java thread calling flush() or setOutput();
        instance.storeDescriptorInJVM();
    }

    public synchronized List<Type> getVisibleTypes() {
        return typeLibrary.getVisibleTypes();
    }
}
