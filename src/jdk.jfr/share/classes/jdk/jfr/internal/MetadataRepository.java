/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jfr.internal.LogLevel.DEBUG;
import static jdk.jfr.internal.LogTag.JFR_SYSTEM;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import jdk.jfr.internal.RequestEngine.RequestHook;
import jdk.jfr.internal.handlers.EventHandler;

public final class MetadataRepository {

    private static final JVM jvm = JVM.getJVM();
    private static final MetadataRepository instace = new MetadataRepository();

    private final List<EventType> nativeEventTypes = new ArrayList<>(100);
    private final List<EventControl> nativeControls = new ArrayList<EventControl>(100);
    private final TypeLibrary typeLibrary = TypeLibrary.getInstance();
    private final SettingsManager settingsManager = new SettingsManager();
    private final Map<String, Class<? extends Event>> mirrors = new HashMap<>();
    private boolean staleMetadata = true;
    private boolean unregistered;
    private long lastUnloaded = -1;

    public MetadataRepository() {
        initializeJVMEventTypes();
    }

    private void initializeJVMEventTypes() {
        List<RequestHook> requestHooks = new ArrayList<>();
        for (Type type : typeLibrary.getTypes()) {
            if (type instanceof PlatformEventType) {
                PlatformEventType pEventType = (PlatformEventType) type;
                EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
                pEventType.setHasDuration(eventType.getAnnotation(Threshold.class) != null);
                pEventType.setHasStackTrace(eventType.getAnnotation(StackTrace.class) != null);
                pEventType.setHasCutoff(eventType.getAnnotation(Cutoff.class) != null);
                pEventType.setHasPeriod(eventType.getAnnotation(Period.class) != null);
                // Must add hook before EventControl is created as it removes
                // annotations, such as Period and Threshold.
                if (pEventType.hasPeriod()) {
                    pEventType.setEventHook(true);
                    if (!(Type.EVENT_NAME_PREFIX + "ExecutionSample").equals(type.getName())) {
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
        return instace;
    }

    public synchronized List<EventType> getRegisteredEventTypes() {
        List<EventHandler> handlers = getEventHandlers();
        List<EventType> eventTypes = new ArrayList<>(handlers.size() + nativeEventTypes.size());
        for (EventHandler h : handlers) {
            if (h.isRegistered()) {
                eventTypes.add(h.getEventType());
            }
        }
        eventTypes.addAll(nativeEventTypes);
        return eventTypes;
    }

    public synchronized EventType getEventType(Class<? extends jdk.internal.event.Event> eventClass) {
        EventHandler h = getHandler(eventClass);
        if (h != null && h.isRegistered()) {
            return h.getEventType();
        }
        throw new IllegalStateException("Event class " + eventClass.getName() + " is not registered");
    }

    public synchronized void unregister(Class<? extends Event> eventClass) {
        Utils.checkRegisterPermission();
        EventHandler handler = getHandler(eventClass);
        if (handler != null) {
            handler.setRegistered(false);
        }
        // never registered, ignore call
    }
    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass) {
        return register(eventClass, Collections.emptyList(), Collections.emptyList());
    }

    public synchronized EventType register(Class<? extends jdk.internal.event.Event> eventClass, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) {
        Utils.checkRegisterPermission();
        EventHandler handler = getHandler(eventClass);
        if (handler == null) {
            if (eventClass.getAnnotation(MirrorEvent.class) != null) {
                // don't register mirrors
                return null;
            }
            PlatformEventType pe = findMirrorType(eventClass);
            handler = makeHandler(eventClass, pe, dynamicAnnotations, dynamicFields);
        }
        handler.setRegistered(true);
        typeLibrary.addType(handler.getPlatformEventType());
        if (jvm.isRecording()) {
            storeDescriptorInJVM(); // needed for emergency dump
            settingsManager.setEventControl(handler.getEventControl());
            settingsManager.updateRetransform(Collections.singletonList((eventClass)));
        } else {
            setStaleMetadata();
        }
        return handler.getEventType();
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

    private EventHandler getHandler(Class<? extends jdk.internal.event.Event> eventClass) {
        Utils.ensureValidEventSubclass(eventClass);
        SecuritySupport.makeVisibleToJFR(eventClass);
        Utils.ensureInitialized(eventClass);
        return Utils.getHandler(eventClass);
    }

    private EventHandler makeHandler(Class<? extends jdk.internal.event.Event> eventClass, PlatformEventType pEventType, List<AnnotationElement> dynamicAnnotations, List<ValueDescriptor> dynamicFields) throws InternalError {
        SecuritySupport.addHandlerExport(eventClass);
        if (pEventType == null) {
            pEventType = (PlatformEventType) TypeLibrary.createType(eventClass, dynamicAnnotations, dynamicFields);
        }
        EventType eventType = PrivateAccess.getInstance().newEventType(pEventType);
        EventControl ec = new EventControl(pEventType, eventClass);
        Class<? extends EventHandler> handlerClass = null;
        try {
            String eventHandlerName = EventHandlerCreator.makeEventHandlerName(eventType.getId());
            handlerClass = Class.forName(eventHandlerName, false, Event.class.getClassLoader()).asSubclass(EventHandler.class);
            // Created eagerly on class load, tag as instrumented
            pEventType.setInstrumented();
            Logger.log(JFR_SYSTEM, DEBUG, "Found existing event handler for " + eventType.getName());
       } catch (ClassNotFoundException cne) {
           EventHandlerCreator ehc = new EventHandlerCreator(eventType.getId(),  ec.getSettingInfos(), eventType, eventClass);
           handlerClass = ehc.makeEventHandlerClass();
           Logger.log(LogTag.JFR_SYSTEM, DEBUG, "Created event handler for " + eventType.getName());
       }
        EventHandler handler = EventHandlerCreator.instantiateEventHandler(handlerClass, true, eventType, ec);
        Utils.setHandler(eventClass, handler);
        return handler;
    }


    public synchronized void setSettings(List<Map<String, String>> list) {
        settingsManager.setSettings(list);
    }

    synchronized void disableEvents() {
        for (EventControl c : getEventControls()) {
            c.disable();
        }
    }

    public synchronized List<EventControl> getEventControls() {
        List<EventControl> controls = new ArrayList<>();
        controls.addAll(nativeControls);
        for (EventHandler eh : getEventHandlers()) {
            controls.add(eh.getEventControl());
        }
        return controls;
    }

    private void storeDescriptorInJVM() throws InternalError {
        jvm.storeMetadataDescriptor(getBinaryRepresentation());
        staleMetadata = false;
    }

    private static List<EventHandler> getEventHandlers() {
        List<Class<? extends jdk.internal.event.Event>> allEventClasses = jvm.getAllEventClasses();
        List<EventHandler> eventHandlers = new ArrayList<>(allEventClasses.size());
        for (Class<? extends jdk.internal.event.Event> clazz : allEventClasses) {
            EventHandler eh = Utils.getHandler(clazz);
            if (eh != null) {
                eventHandlers.add(eh);
            }
        }
        return eventHandlers;
    }

    private byte[] getBinaryRepresentation() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(40000);
        DataOutputStream daos = new DataOutputStream(baos);
        try {
            List<Type> types = typeLibrary.getTypes();
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

    // Lock around setOutput ensures that other threads dosn't
    // emit event after setOutput and unregister the event class, before a call
    // to storeDescriptorInJVM
    synchronized void setOutput(String filename) {
        jvm.setOutput(filename);

        unregisterUnloaded();
        if (unregistered) {
            staleMetadata = typeLibrary.clearUnregistered();
            unregistered = false;
        }
        if (staleMetadata) {
            storeDescriptorInJVM();
        }
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
                if (type instanceof PlatformEventType) {
                    if (!knownIds.contains(type.getId())) {
                        PlatformEventType pe = (PlatformEventType) type;
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

}
