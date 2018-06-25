package jdk.jfr.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.Enabled;
import jdk.jfr.RecordingState;
import jdk.jfr.internal.settings.CutoffSetting;
import jdk.jfr.internal.test.WhiteBox;

// The Old Object event could have been implemented as a periodic event, but
// due to chunk rotations and how settings are calculated when multiple recordings
// are running at the same time, it would lead to unacceptable overhead.
//
// Instead, the event is only emitted before a recording stops and
// if that recording has the event enabled.
//
// This requires special handling and the purpose of this class is to provide that
//
public final class OldObjectSample {

    private static final String EVENT_NAME = Type.EVENT_NAME_PREFIX + "OldObjectSample";
    private static final String OLD_OBJECT_CUTOFF = EVENT_NAME + "#" + Cutoff.NAME;
    private static final String OLD_OBJECT_ENABLED = EVENT_NAME + "#" + Enabled.NAME;

    // Emit if old object is enabled in recoding with cutoff for that recording
    public static void emit(PlatformRecording recording) {
        if (isEnabled(recording)) {
            long nanos = CutoffSetting.parseValueSafe(recording.getSettings().get(OLD_OBJECT_CUTOFF));
            long ticks = Utils.nanosToTicks(nanos);
            JVM.getJVM().emitOldObjectSamples(ticks, WhiteBox.getWriteAllObjectSamples());
        }
    }

    // Emit if old object is enabled for at least one recording, and use the largest
    // cutoff for an enabled recoding
    public static void emit(List<PlatformRecording> recordings, Boolean pathToGcRoots) {
        boolean enabled = false;
        long cutoffNanos = Boolean.TRUE.equals(pathToGcRoots) ? Long.MAX_VALUE : 0L;
        for (PlatformRecording r : recordings) {
            if (r.getState() == RecordingState.RUNNING) {
                if (isEnabled(r)) {
                    enabled = true;
                    long c = CutoffSetting.parseValueSafe(r.getSettings().get(OLD_OBJECT_CUTOFF));
                    cutoffNanos = Math.max(c, cutoffNanos);
                }
            }
        }
        if (enabled) {
            long ticks = Utils.nanosToTicks(cutoffNanos);
            JVM.getJVM().emitOldObjectSamples(ticks, WhiteBox.getWriteAllObjectSamples());
        }
    }

    public static void updateSettingPathToGcRoots(Map<String, String> s, Boolean pathToGcRoots) {
        if (pathToGcRoots != null) {
            s.put(OLD_OBJECT_CUTOFF, pathToGcRoots ? "infinity" : "0 ns");
        }
    }

    public static Map<String, String> createSettingsForSnapshot(PlatformRecording recording, Boolean pathToGcRoots) {
        Map<String, String> settings = new HashMap<>(recording.getSettings());
        updateSettingPathToGcRoots(settings, pathToGcRoots);
        return settings;
    }

    private static boolean isEnabled(PlatformRecording r) {
        Map<String, String> settings = r.getSettings();
        String s = settings.get(OLD_OBJECT_ENABLED);
        return "true".equals(s);
    }
}
