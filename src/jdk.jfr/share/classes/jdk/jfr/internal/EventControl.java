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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import jdk.internal.module.Modules;
import jdk.jfr.AnnotationElement;
import jdk.jfr.Enabled;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.SettingControl;
import jdk.jfr.SettingDefinition;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.events.ActiveSettingEvent;
import jdk.jfr.internal.EventInstrumentation.SettingInfo;
import jdk.jfr.internal.settings.CutoffSetting;
import jdk.jfr.internal.settings.EnabledSetting;
import jdk.jfr.internal.settings.PeriodSetting;
import jdk.jfr.internal.settings.StackTraceSetting;
import jdk.jfr.internal.settings.ThresholdSetting;

// This class can't have a hard reference from PlatformEventType, since it
// holds SettingControl instances that need to be released
// when a class is unloaded (to avoid memory leaks).
public final class EventControl {

    static final String FIELD_SETTING_PREFIX = "setting";
    private static final Type TYPE_ENABLED = TypeLibrary.createType(EnabledSetting.class);
    private static final Type TYPE_THRESHOLD = TypeLibrary.createType(ThresholdSetting.class);
    private static final Type TYPE_STACK_TRACE = TypeLibrary.createType(StackTraceSetting.class);
    private static final Type TYPE_PERIOD = TypeLibrary.createType(PeriodSetting.class);
    private static final Type TYPE_CUTOFF = TypeLibrary.createType(CutoffSetting.class);

    private final List<SettingInfo> settingInfos = new ArrayList<>();
    private final Map<String, Control> eventControls = new HashMap<>(5);
    private final PlatformEventType type;
    private final String idName;

    EventControl(PlatformEventType eventType) {
        eventControls.put(Enabled.NAME, defineEnabled(eventType));
        if (eventType.hasDuration()) {
            eventControls.put(Threshold.NAME, defineThreshold(eventType));
        }
        if (eventType.hasStackTrace()) {
            eventControls.put(StackTrace.NAME, defineStackTrace(eventType));
        }
        if (eventType.hasPeriod()) {
            eventControls.put(Period.NAME, definePeriod(eventType));
        }
        if (eventType.hasCutoff()) {
            eventControls.put(Cutoff.NAME, defineCutoff(eventType));
        }

        ArrayList<AnnotationElement> aes = new ArrayList<>(eventType.getAnnotationElements());
        remove(eventType, aes, Threshold.class);
        remove(eventType, aes, Period.class);
        remove(eventType, aes, Enabled.class);
        remove(eventType, aes, StackTrace.class);
        remove(eventType, aes, Cutoff.class);
        aes.trimToSize();
        eventType.setAnnotations(aes);
        this.type = eventType;
        this.idName = String.valueOf(eventType.getId());
    }

    static void remove(PlatformEventType type, List<AnnotationElement> aes, Class<? extends java.lang.annotation.Annotation> clazz) {
        long id = Type.getTypeId(clazz);
        for (AnnotationElement a : type.getAnnotationElements()) {
            if (a.getTypeId() == id && a.getTypeName().equals(clazz.getName())) {
                aes.remove(a);
            }
        }
    }

    EventControl(PlatformEventType es, Class<? extends jdk.internal.event.Event> eventClass) {
        this(es);
        defineSettings(eventClass);
    }

    @SuppressWarnings("unchecked")
    private void defineSettings(Class<?> eventClass) {
        // Iterate up the class hierarchy and let
        // subclasses shadow base classes.
        boolean allowPrivateMethod = true;
        while (eventClass != null) {
            for (Method m : eventClass.getDeclaredMethods()) {
                boolean isPrivate = Modifier.isPrivate(m.getModifiers());
                if (m.getReturnType() == Boolean.TYPE && m.getParameterCount() == 1 && (!isPrivate || allowPrivateMethod)) {
                    SettingDefinition se = m.getDeclaredAnnotation(SettingDefinition.class);
                    if (se != null) {
                        Class<?> settingClass = m.getParameters()[0].getType();
                        if (!Modifier.isAbstract(settingClass.getModifiers()) && SettingControl.class.isAssignableFrom(settingClass)) {
                            String name = m.getName();
                            Name n = m.getAnnotation(Name.class);
                            if (n != null) {
                                name = n.value();
                            }
                            if (!eventControls.containsKey(name)) {
                                defineSetting((Class<? extends SettingControl>) settingClass, m, type, name);
                            }
                        }
                    }
                }
            }
            eventClass = eventClass.getSuperclass();
            allowPrivateMethod = false;
        }
    }

    private void defineSetting(Class<? extends SettingControl> settingsClass, Method method, PlatformEventType eventType, String settingName) {
        try {
            Module settingModule = settingsClass.getModule();
            Modules.addReads(settingModule, EventControl.class.getModule());
            int index = settingInfos.size();
            SettingInfo si = new SettingInfo(FIELD_SETTING_PREFIX + index, index);
            si.settingControl = instantiateSettingControl(settingsClass);
            Control c = si.settingControl;
            c.setDefault();
            String defaultValue = c.getValueSafe();
            if (defaultValue != null) {
                Type settingType = TypeLibrary.createType(settingsClass);
                ArrayList<AnnotationElement> aes = new ArrayList<>();
                for (Annotation a : method.getDeclaredAnnotations()) {
                    AnnotationElement ae = TypeLibrary.createAnnotation(a);
                    if (ae != null) {
                        aes.add(ae);
                    }
                }
                aes.trimToSize();
                eventControls.put(settingName, si.settingControl);
                eventType.add(PrivateAccess.getInstance().newSettingDescriptor(settingType, settingName, defaultValue, aes));
                settingInfos.add(si);
            }
        } catch (InstantiationException e) {
            // Programming error by user, fail fast
            throw new InstantiationError("Could not instantiate setting " + settingsClass.getName() + " for event " + eventType.getLogName() + ". " + e.getMessage());
        } catch (IllegalAccessException e) {
            // Programming error by user, fail fast
            throw new IllegalAccessError("Could not access setting " + settingsClass.getName() + " for event " + eventType.getLogName() + ". " + e.getMessage());
        }
    }

    private SettingControl instantiateSettingControl(Class<? extends SettingControl> settingControlClass) throws IllegalAccessException, InstantiationException {
        SecuritySupport.makeVisibleToJFR(settingControlClass);
        final Constructor<?> cc;
        try {
            cc = settingControlClass.getDeclaredConstructors()[0];
        } catch (Exception e) {
            throw (Error) new InternalError("Could not get constructor for " + settingControlClass.getName()).initCause(e);
        }
        SecuritySupport.setAccessible(cc);
        try {
            return (SettingControl) cc.newInstance();
        } catch (IllegalArgumentException | InvocationTargetException e) {
            throw (Error) new InternalError("Could not instantiate setting for class " + settingControlClass.getName());
        }
    }

    private static Control defineEnabled(PlatformEventType type) {
        Enabled enabled = type.getAnnotation(Enabled.class);
        // Java events are enabled by default,
        // JVM events are not, maybe they should be? Would lower learning curve
        // there too.
        String def = type.isJVM() ? "false" : "true";
        if (enabled != null) {
            def = Boolean.toString(enabled.value());
        }
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_ENABLED, Enabled.NAME, def, Collections.emptyList()));
        return new EnabledSetting(type, def);
    }

    private static Control defineThreshold(PlatformEventType type) {
        Threshold threshold = type.getAnnotation(Threshold.class);
        String def = "0 ns";
        if (threshold != null) {
            def = threshold.value();
        }
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_THRESHOLD, Threshold.NAME, def, Collections.emptyList()));
        return new ThresholdSetting(type, def);
    }

    private static Control defineStackTrace(PlatformEventType type) {
        StackTrace stackTrace = type.getAnnotation(StackTrace.class);
        String def = "true";
        if (stackTrace != null) {
            def = Boolean.toString(stackTrace.value());
        }
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_STACK_TRACE, StackTrace.NAME, def, Collections.emptyList()));
        return new StackTraceSetting(type, def);
    }

    private static Control defineCutoff(PlatformEventType type) {
        Cutoff cutoff = type.getAnnotation(Cutoff.class);
        String def = Cutoff.INIFITY;
        if (cutoff != null) {
            def = cutoff.value();
        }
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_CUTOFF, Cutoff.NAME, def, Collections.emptyList()));
        return new CutoffSetting(type, def);
    }


    private static Control definePeriod(PlatformEventType type) {
        Period period = type.getAnnotation(Period.class);
        String def = "everyChunk";
        if (period != null) {
            def = period.value();
        }
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_PERIOD, PeriodSetting.NAME, def, Collections.emptyList()));
        return new PeriodSetting(type, def);
    }

    void disable() {
        for (Control c : eventControls.values()) {
            if (c instanceof EnabledSetting) {
                c.setValueSafe("false");
                return;
            }
        }
    }

    void writeActiveSettingEvent() {
        if (!type.isRegistered()) {
            return;
        }
        for (Map.Entry<String, Control> entry : eventControls.entrySet()) {
            Control c = entry.getValue();
            if (Utils.isSettingVisible(c, type.hasEventHook())) {
                String value = c.getLastValue();
                if (value == null) {
                    value = c.getDefaultValue();
                }
                ActiveSettingEvent ase = new ActiveSettingEvent();
                ase.id = type.getId();
                ase.name = entry.getKey();
                ase.value = value;
                ase.commit();
            }
        }
    }

    public Set<Entry<String, Control>> getEntries() {
        return eventControls.entrySet();
    }

    public PlatformEventType getEventType() {
        return type;
    }

    public String getSettingsId() {
        return idName;
    }

    public List<SettingInfo> getSettingInfos() {
        return settingInfos;
    }
}
