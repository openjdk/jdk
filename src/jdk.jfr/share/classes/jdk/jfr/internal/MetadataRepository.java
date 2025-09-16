/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.SettingControl;
import jdk.jfr.Throttle;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.consumer.RepositoryFiles;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.management.HiddenWait;
import jdk.jfr.internal.periodic.PeriodicEvents;
import jdk.jfr.internal.settings.Throttler;
import jdk.jfr.internal.util.Utils;

public final class MetadataRepository {

    private static final MetadataRepository instance = new MetadataRepository();

    private final Map<String, EventType> nativeEventTypes = LinkedHashMap.newHashMap(150);
    private final Map<String, EventControl> nativeControls = LinkedHashMap.newHashMap(150);
    private final SettingsManager settingsManager = new SettingsManager();
    private final HiddenWait threadSleeper = new HiddenWait();
    private boolean staleMetadata = true;
    private boolean unregistered;
    private long lastUnloaded = -1;

    private long lastMillis;

    public MetadataRepository() {
        initializeJVMEventTypes();
    }

    private void initializeJVMEventTypes() {
        TypeLibrary.initialize();
        for (Type type : TypeLibrary.getTypes()) {
            if (type instanceof PlatformEventType pEventType) {
                EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
                pEventType.setHasCutoff(type.hasAnnotation(Cutoff.class));
                pEventType.setHasThrottle(type.hasAnnotation(Throttle.class));
                pEventType.setHasLevel(type.hasAnnotation(Level.class));
                pEventType.setHasPeriod(type.hasAnnotation(Period.class));
                // Must add hook before EventControl is created as it removes
                // annotations, such as Period and Threshold.
                if (pEventType.hasPeriod()) {
                    pEventType.setEventHook(true);
                    if (!pEventType.isMethodSampling()) {
                        PeriodicEvents.addJVMEvent(pEventType);
                    }
                }
                String name = eventType.getName();
                nativeControls.put(name, new EventControl(pEventType));
                nativeEventTypes.put(name,eventType);
            }
        }
    }

    public static MetadataRepository getInstance() {
        return instance;
    }

    public synchronized List<EventType> getRegisteredEventTypes() {
        List<EventConfiguration> configurations = getEventConfigurations();
        List<EventType> eventTypes = new ArrayList<>(configurations.size() + nativeEventTypes.size());
        for (EventConfiguration ec : configurations) {
            if (ec.isRegistered()) {
                eventTypes.add(ec.eventType());
            }
        }
        for (EventType t : nativeEventTypes.values()) {
            if (PrivateAccess.getInstance().isVisible(t)) {
                eventTypes.add(t);
            }
        }
        return eventTypes;
    }

    public synchronized EventType getEventType(Class<? extends jdk.internal.event.Event> eventClass) {
        EventConfiguration ec = getConfiguration(eventClass, false);
        if (ec != null && ec.isRegistered()) {
            return ec.eventType();
        }
        throw new IllegalStateException("Event class " + eventClass.getName() + " is not registered");
    }

    public synchronized void unregister(Class<? extends Event> eventClass) {
        EventConfiguration configuration = getConfiguration(eventClass, false);
        if (configuration != null) {
            configuration.platformEventType().setRegistered(false);
        }
        // never registered, ignore call
    }
    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass) {
        return register(eventClass, Collections.emptyList(), Collections.emptyList());
    }

    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) {
        if (JVM.isExcluded(eventClass)) {
            // Event classes are marked as excluded during class load
            // if they override methods in the jdk.jfr.Event class, i.e. commit().
            // An excluded class lacks a configuration field and can't be used by JFR.
            // The Event::commit() is marked as final, so javac won't
            // compile an override, but it can be constructed by other means.
            throw new IllegalArgumentException("Must not override methods declared in jdk.jfr.Event");
        }
        EventConfiguration configuration = getConfiguration(eventClass, true);
        if (configuration == null) {
            PlatformEventType pe = findMirrorType(eventClass);
            configuration = makeConfiguration(eventClass, pe, dynamicAnnotations, dynamicFields);
        }
        configuration.platformEventType().setRegistered(true);
        TypeLibrary.addType(configuration.platformEventType());
        if (JVM.isRecording()) {
            settingsManager.setEventControl(configuration.eventControl(), true, JVM.counterTime());
            settingsManager.updateRetransform(Collections.singletonList((eventClass)));
       }
       setStaleMetadata();
       return configuration.eventType();
    }

    private PlatformEventType findMirrorType(Class<? extends jdk.internal.event.Event> eventClass) throws InternalError {
        Class<? extends MirrorEvent> mirrorClass = MirrorEvents.find(eventClass);
        if (mirrorClass == null) {
            return null; // not a mirror
        }
        Utils.verifyMirror(mirrorClass, eventClass);
        PlatformEventType et = (PlatformEventType) TypeLibrary.createType(mirrorClass);
        TypeLibrary.removeType(et.getId());
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
        return JVMSupport.getConfiguration(eventClass);
    }

    private EventConfiguration makeConfiguration(Class<? extends jdk.internal.event.Event> eventClass, PlatformEventType pEventType, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) throws InternalError {
        SecuritySupport.addInternalEventExport(eventClass);
        if (pEventType == null) {
            pEventType = (PlatformEventType) TypeLibrary.createType(eventClass, dynamicAnnotations, dynamicFields);
        }
        // Check for native mirror.
        // Note, defining an event in metadata.xml is not a generic mechanism to emit
        // native data in Java. For example, calling JVM.getStackTraceId(int, long)
        // and assign the result to a long field is not enough to always get a proper
        // stack trace. Purpose of the mechanism is to transfer metadata, such as
        // native type IDs, without specialized Java logic for each type.
        if (Utils.isJDKClass(eventClass)) {
            Name name = eventClass.getAnnotation(Name.class);
            if (name != null) {
                String n = name.value();
                EventType nativeType = nativeEventTypes.get(n);
                if (nativeType != null) {
                    var nativeFields = nativeType.getFields();
                    var eventFields = pEventType.getFields();
                    var comparator = Comparator.comparing(ValueDescriptor::getName);
                    if (!Utils.compareLists(nativeFields, eventFields, comparator)) {
                        throw new InternalError("Field for native mirror event " + n + " doesn't match Java event");
                    }
                    nativeEventTypes.remove(n);
                    nativeControls.remove(n);
                    TypeLibrary.removeType(nativeType.getId());
                    PrivateAccess access = PrivateAccess.getInstance();
                    for (int i = 0; i < nativeFields.size(); i++) {
                        access.setAnnotations(nativeFields.get(i), eventFields.get(i).getAnnotationElements());
                    }
                    pEventType.setFields(nativeFields);
                }
            }
        }
        EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
        pEventType.setHasThrottle(pEventType.getAnnotation(Throttle.class) != null);
        EventControl ec = new EventControl(pEventType, eventClass);
        SettingControl[] settings = ec.getSettingControls().toArray(new SettingControl[0]);
        Throttler throttler = pEventType.getThrottler();
        EventConfiguration configuration = new EventConfiguration(pEventType, eventType, ec, settings, throttler, eventType.getId());
        pEventType.setRegistered(true);
        // If class is instrumented or should not be instrumented, mark as instrumented.
        if (JVM.isInstrumented(eventClass) || !JVMSupport.shouldInstrument(pEventType.isJDK(), pEventType.getName())) {
            pEventType.setInstrumented();
        }
        JVMSupport.setConfiguration(eventClass, configuration);
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
        List<Class<? extends jdk.internal.event.Event>> eventClasses = JVM.getAllEventClasses();
        ArrayList<EventControl> controls = new ArrayList<>(eventClasses.size() + nativeControls.size());
        controls.addAll(nativeControls.values());
        for (Class<? extends jdk.internal.event.Event> clazz : eventClasses) {
            EventConfiguration eh = JVMSupport.getConfiguration(clazz);
            if (eh != null) {
                controls.add(eh.eventControl());
            }
        }
        return controls;
    }

    private void storeDescriptorInJVM() throws InternalError {
        JVM.storeMetadataDescriptor(getBinaryRepresentation());
        staleMetadata = false;
    }

    private static List<EventConfiguration> getEventConfigurations() {
        List<Class<? extends jdk.internal.event.Event>> allEventClasses = JVM.getAllEventClasses();
        List<EventConfiguration> eventConfigurations = new ArrayList<>(allEventClasses.size());
        for (Class<? extends jdk.internal.event.Event> clazz : allEventClasses) {
            EventConfiguration ec = JVMSupport.getConfiguration(clazz);
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
            List<Type> types = TypeLibrary.getVisibleTypes();
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
        // Each chunk needs a unique timestamp. If two chunks get the same
        // timestamp, the parser may stop prematurely at an earlier chunk.
        // The resolution needs to be measured in milliseconds as this
        // is what RecordingInfo:getStopTime() returns.
        awaitEpochMilliShift();
        JVM.setOutput(filename);
        long chunkStart = JVMSupport.getChunkStartNanos();
        if (filename != null) {
            RepositoryFiles.notifyNewFile();
        }
        unregisterUnloaded();
        if (unregistered) {
            if (TypeLibrary.clearUnregistered()) {
                storeDescriptorInJVM();
            }
            unregistered = false;
        }
        return Utils.epochNanosToInstant(chunkStart);
    }

    private void awaitEpochMilliShift() {
        while (true) {
            long nanos = JVM.nanosNow();
            long millis = Utils.epochNanosToInstant(nanos).toEpochMilli();
            if (millis != lastMillis) {
                lastMillis = millis;
                return;
            }
            threadSleeper.takeNap(1);
        }
    }

    private void unregisterUnloaded() {
        long unloaded = JVM.getUnloadedEventClassCount();
        if (this.lastUnloaded != unloaded) {
            this.lastUnloaded = unloaded;
            List<Class<? extends jdk.internal.event.Event>> eventClasses = JVM.getAllEventClasses();
            HashSet<Long> knownIds = new HashSet<>(eventClasses.size());
            for (Class<? extends jdk.internal.event.Event>  ec: eventClasses) {
                knownIds.add(Type.getTypeId(ec));
            }
            for (Type type : TypeLibrary.getTypes()) {
                if (type instanceof PlatformEventType pe) {
                    if (!knownIds.contains(pe.getId())) {
                        if (!pe.isJVM()) {
                            pe.setRegistered(false);
                            if (pe.hasStackFilters()) {
                                JVM.unregisterStackFilter(pe.getStackFilterId());
                            }
                        }
                    }
                }
            }
        }
    }

    synchronized void setUnregistered() {
       unregistered = true;
    }

    public synchronized void flush() {
        if (staleMetadata) {
            storeDescriptorInJVM();
        }
        JVM.flush();
    }

    static void unhideInternalTypes() {
        for (Type t : TypeLibrary.getTypes()) {
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
        return TypeLibrary.getVisibleTypes();
    }

    public synchronized long registerStackFilter(String[] typeArray, String[] methodArray) {
        return JVM.registerStackFilter(typeArray, methodArray);
    }
}
