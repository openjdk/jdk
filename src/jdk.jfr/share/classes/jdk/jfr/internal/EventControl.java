/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import jdk.internal.module.Checks;
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
import jdk.jfr.events.StackFilter;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.settings.CutoffSetting;
import jdk.jfr.internal.settings.EnabledSetting;
import jdk.jfr.internal.settings.LevelSetting;
import jdk.jfr.internal.settings.PeriodSetting;
import jdk.jfr.internal.settings.StackTraceSetting;
import jdk.jfr.internal.settings.ThresholdSetting;
import jdk.jfr.internal.settings.ThrottleSetting;
import jdk.jfr.internal.util.Utils;

// This class can't have a hard reference from PlatformEventType, since it
// holds SettingControl instances that need to be released
// when a class is unloaded (to avoid memory leaks).
public final class EventControl {
    record NamedControl(String name, Control control) {
    }
    static final String FIELD_SETTING_PREFIX = "setting";
    private static final Type TYPE_ENABLED = TypeLibrary.createType(EnabledSetting.class);
    private static final Type TYPE_THRESHOLD = TypeLibrary.createType(ThresholdSetting.class);
    private static final Type TYPE_STACK_TRACE = TypeLibrary.createType(StackTraceSetting.class);
    private static final Type TYPE_PERIOD = TypeLibrary.createType(PeriodSetting.class);
    private static final Type TYPE_CUTOFF = TypeLibrary.createType(CutoffSetting.class);
    private static final Type TYPE_THROTTLE = TypeLibrary.createType(ThrottleSetting.class);
    private static final long STACK_FILTER_ID = Type.getTypeId(StackFilter.class);
    private static final Type TYPE_LEVEL = TypeLibrary.createType(LevelSetting.class);

    private final ArrayList<SettingControl> settingControls = new ArrayList<>();
    private final ArrayList<NamedControl> namedControls = new ArrayList<>(5);
    private final PlatformEventType type;
    private final String idName;

    EventControl(PlatformEventType eventType) {
        if (eventType.hasThreshold()) {
            addControl(Threshold.NAME, defineThreshold(eventType));
        }
        if (eventType.hasStackTrace()) {
            addControl(StackTrace.NAME, defineStackTrace(eventType));
        }
        if (eventType.hasPeriod()) {
            addControl(Period.NAME, definePeriod(eventType));
        }
        if (eventType.hasCutoff()) {
            addControl(Cutoff.NAME, defineCutoff(eventType));
        }
        if (eventType.hasThrottle()) {
            addControl(Throttle.NAME, defineThrottle(eventType));
        }
        if (eventType.hasLevel()) {
            addControl(Level.NAME, defineLevel(eventType));
        }
        addControl(Enabled.NAME, defineEnabled(eventType));

        addStackFilters(eventType);
        List<AnnotationElement> aes = new ArrayList<>(eventType.getAnnotationElements());
        remove(eventType, aes, Threshold.class);
        remove(eventType, aes, Period.class);
        remove(eventType, aes, Enabled.class);
        remove(eventType, aes, StackTrace.class);
        remove(eventType, aes, Cutoff.class);
        remove(eventType, aes, Throttle.class);
        remove(eventType, aes, StackFilter.class);
        eventType.setAnnotations(aes);
        this.type = eventType;
        this.idName = String.valueOf(eventType.getId());
    }

    private void addStackFilters(PlatformEventType eventType) {
        String[] filter = getStackFilter(eventType);
        if (filter != null) {
            int size = filter.length;
            List<String> types = new ArrayList<>(size);
            List<String> methods = new ArrayList<>(size);
            for (String frame : filter) {
                int index = frame.indexOf("::");
                String clazz = null;
                String method = null;
                boolean valid = false;
                if (index != -1) {
                    clazz = frame.substring(0, index);
                    method = frame.substring(index + 2);
                    if (clazz.isEmpty()) {
                        clazz = null;
                        valid = isValidMethod(method);
                    } else {
                        valid = isValidType(clazz) && isValidMethod(method);
                    }
                } else {
                    clazz = frame;
                    valid = isValidType(frame);
                }
                if (valid) {
                    if (clazz == null) {
                        types.add(null);
                    } else {
                        types.add(clazz.replace(".", "/"));
                    }
                    // If unqualified class name equals method name, it's a constructor
                    String className = clazz.substring(clazz.lastIndexOf(".") + 1);
                    if (className.equals(method)) {
                        method = "<init>";
                    }
                    methods.add(method);
                } else {
                    Logger.log(LogTag.JFR, LogLevel.WARN, "@StackFrameFilter element ignored, not a valid Java identifier.");
                }
            }
            if (!types.isEmpty()) {
                String[] typeArray = types.toArray(new String[0]);
                String[] methodArray = methods.toArray(new String[0]);
                long id = MetadataRepository.getInstance().registerStackFilter(typeArray, methodArray);
                eventType.setStackFilterId(id);
            }
        }
    }

    private String[] getStackFilter(PlatformEventType eventType) {
        for (var a : eventType.getAnnotationElements()) {
            if (a.getTypeId() == STACK_FILTER_ID) {
                return (String[])a.getValue("value");
            }
        }
        return null;
    }

    private boolean isValidType(String className) {
        if (className.length() < 1 || className.length() > 65535) {
            return false;
        }
        return Checks.isClassName(className);
    }

    private boolean isValidMethod(String method) {
        if (method.length() < 1 || method.length() > 65535) {
            return false;
        }
        return Checks.isJavaIdentifier(method);
    }

    private boolean hasControl(String name) {
        for (NamedControl nc : namedControls) {
            if (name.equals(nc.name)) {
                return true;
            }
        }
        return false;
    }

    private void addControl(String name, Control control) {
        namedControls.add(new NamedControl(name, control));
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
                                name = Utils.validJavaIdentifier(n.value(), name);
                            }

                            if (!hasControl(name)) {
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
            SettingControl settingControl = instantiateSettingControl(settingsClass);
            Control c = new Control(settingControl, null);
            c.setDefault();
            String defaultValue = c.getValue();
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
                addControl(settingName, c);
                eventType.add(PrivateAccess.getInstance().newSettingDescriptor(settingType, settingName, defaultValue, aes));
                settingControls.add(settingControl);
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
            throw new InternalError("Could not instantiate setting for class " + settingControlClass.getName());
        }
    }

    private static Control defineEnabled(PlatformEventType type) {
        // Java events are enabled by default,
        // JVM events are not, maybe they should be? Would lower learning curve
        // there too.
        Boolean defaultValue = Boolean.valueOf(!type.isJVM());
        String def = type.getAnnotationValue(Enabled.class, defaultValue).toString();
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_ENABLED, Enabled.NAME, def, Collections.emptyList()));
        return new Control(new EnabledSetting(type, def), def);
    }

    private static Control defineThreshold(PlatformEventType type) {
        String def = type.getAnnotationValue(Threshold.class, "0 ns");
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_THRESHOLD, Threshold.NAME, def, Collections.emptyList()));
        return new Control(new ThresholdSetting(type), def);
    }

    private static Control defineStackTrace(PlatformEventType type) {
        String def = type.getAnnotationValue(StackTrace.class, Boolean.TRUE).toString();
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_STACK_TRACE, StackTrace.NAME, def, Collections.emptyList()));
        return new Control(new StackTraceSetting(type, def), def);
    }

    private static Control defineCutoff(PlatformEventType type) {
        String def = type.getAnnotationValue(Cutoff.class, Cutoff.INFINITY);
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_CUTOFF, Cutoff.NAME, def, Collections.emptyList()));
        return new Control(new CutoffSetting(type), def);
    }

    private static Control defineThrottle(PlatformEventType type) {
        String def = type.getAnnotationValue(Throttle.class, Throttle.DEFAULT);
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_THROTTLE, Throttle.NAME, def, Collections.emptyList()));
        return new Control(new ThrottleSetting(type), def);
    }

    private static Control defineLevel(PlatformEventType type) {
        String[] levels = type.getAnnotationValue(Level.class, new String[0]);
        String def = levels[0]; // Level value always exists
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_LEVEL, Level.NAME, def, Collections.emptyList()));
        return new Control(new LevelSetting(type, levels), def);
    }

    private static Control definePeriod(PlatformEventType type) {
        String def = type.getAnnotationValue(Period.class, "everyChunk");
        type.add(PrivateAccess.getInstance().newSettingDescriptor(TYPE_PERIOD, PeriodSetting.NAME, def, Collections.emptyList()));
        return new Control(new PeriodSetting(type), def);
    }

    void disable() {
        for (NamedControl nc : namedControls) {
            if (nc.control.isType(EnabledSetting.class)) {
                nc.control.setValue("false");
                return;
            }
        }
    }

    void writeActiveSettingEvent(long timestamp) {
        if (!type.isRegistered()) {
            return;
        }
        for (NamedControl nc : namedControls) {
            if (nc.control.isVisible(type.hasEventHook()) && type.isVisible()) {
                String value = nc.control.getLastValue();
                if (value == null) {
                    value = nc.control.getDefaultValue();
                }
                if (ActiveSettingEvent.enabled()) {
                    ActiveSettingEvent.commit(timestamp, type.getId(), nc.name(), value);
                }
            }
        }
    }

    public ArrayList<NamedControl> getNamedControls() {
        return namedControls;
    }

    public PlatformEventType getEventType() {
        return type;
    }

    public String getSettingsId() {
        return idName;
    }

    /**
     * A malicious user must never be able to run a callback in the wrong
     * context. Methods on SettingControl must therefore never be invoked directly
     * by JFR, instead use jdk.jfr.internal.Control.
     *
     * The returned list is only to be used inside EventConfiguration
     */
    public List<SettingControl> getSettingControls() {
        return settingControls;
    }
}
