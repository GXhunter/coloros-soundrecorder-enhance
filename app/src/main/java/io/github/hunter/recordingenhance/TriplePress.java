package io.github.hunter.recordingenhance;

final class TriplePress {
    private int mCount;
    private long mFirstTime;
    private long mBlockedUntil;
    private int mShortcut = -1;

    synchronized boolean release(long time, long duration, boolean eligible, int shortcut) {
        shortcut = Settings.shortcut(shortcut);
        if (shortcut != mShortcut) {
            mCount = 0;
            mShortcut = shortcut;
        }
        if (!eligible || duration < 0 || duration > 400 || time < mBlockedUntil) {
            mCount = 0;
            return false;
        }
        if (mCount == 0 || time < mFirstTime || time - mFirstTime > 1200) {
            mCount = 0;
            mFirstTime = time;
        }
        if (++mCount < Settings.presses(shortcut)) return false;
        mCount = 0;
        mBlockedUntil = time + 2000;
        return true;
    }
}
