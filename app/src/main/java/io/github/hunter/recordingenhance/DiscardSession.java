package io.github.hunter.recordingenhance;

import java.util.UUID;

final class DiscardSession {
    private boolean mActive;
    private String mToken;

    synchronized void updateState(int state) {
        boolean active = state == 1 || state == 2;
        if (active && !mActive) mToken = UUID.randomUUID().toString();
        if (!active) mToken = null;
        mActive = active;
    }

    synchronized String token() {
        return mToken;
    }

    synchronized boolean consume(String token, boolean canDiscard) {
        if (!canDiscard || !mActive || mToken == null || !mToken.equals(token)) return false;
        mToken = null;
        return true;
    }
}
