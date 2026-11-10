package io.github.hunter.recordingenhance;

final class Settings {
    static final String GROUP = "settings";
    static final String SHORTCUT = "shortcut";
    static final String FEEDBACK = "feedback";
    static final String HIDE_STATUS = "hide_status";
    static final String HIDE_LOCK = "hide_lock";
    static final String HIDE_PRIVACY_DOT = "hide_privacy_dot";
    static final String SHOW_NOTIFICATION = "show_notification";

    static int shortcut(int value) { return value >= 0 && value < 4 ? value : 2; }
    static int feedback(int value) { return value >= 0 && value < 3 ? value : 0; }
    static int keyCode(int shortcut) { return shortcut % 2 == 0 ? 24 : 25; }
    static int presses(int shortcut) { return shortcut < 2 ? 2 : 3; }
}
