package io.github.hunter.recordingenhance;

import java.util.List;

public final class RecordingVisibilitySelfTest {
    public static void main(String[] args) {
        var recorder = new RecordingVisibility.Use("com.coloros.soundrecorder", "TYPE_MICROPHONE");
        var other = new RecordingVisibility.Use("other.app", "TYPE_MICROPHONE");
        var camera = new RecordingVisibility.Use("com.coloros.soundrecorder", "TYPE_CAMERA");
        assert RecordingVisibility.onlyRecorderMicrophone(List.of(recorder));
        assert RecordingVisibility.onlyRecorderMicrophone(List.of(recorder, recorder));
        assert !RecordingVisibility.onlyRecorderMicrophone(List.of());
        assert !RecordingVisibility.onlyRecorderMicrophone(List.of(other));
        assert !RecordingVisibility.onlyRecorderMicrophone(List.of(recorder, other));
        assert !RecordingVisibility.onlyRecorderMicrophone(List.of(recorder, camera));
        assert !RecordingVisibility.onlyRecorderMicrophone(List.of(new RecordingVisibility.Use(null, null)));
        assert RecordingVisibility.filterNotification(false, "com.coloros.soundrecorder", 1024);
        assert !RecordingVisibility.filterNotification(true, "com.coloros.soundrecorder", 1024);
        assert !RecordingVisibility.filterNotification(false, "other.app", 1024);
        assert !RecordingVisibility.filterNotification(false, "com.coloros.soundrecorder", 1025);
        System.out.println("RecordingVisibility: attribution and notification isolation checks passed");
    }
}
