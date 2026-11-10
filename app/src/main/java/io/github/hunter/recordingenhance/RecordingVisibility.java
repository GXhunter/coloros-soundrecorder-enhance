package io.github.hunter.recordingenhance;

import java.util.List;

final class RecordingVisibility {
    record Use(String packageName, String type) {}

    static boolean onlyRecorderMicrophone(List<Use> uses) {
        if (uses.isEmpty()) return false;
        for (Use use : uses) {
            if (!"com.coloros.soundrecorder".equals(use.packageName())
                    || !"TYPE_MICROPHONE".equals(use.type())) return false;
        }
        return true;
    }

    static boolean filterNotification(boolean show, String packageName, int id) {
        return !show && "com.coloros.soundrecorder".equals(packageName) && id == 1024;
    }
}
