package io.github.hunter.recordingenhance;

public final class DiscardSessionSelfTest {
    public static void main(String[] args) {
        DiscardSession session = new DiscardSession();
        assert !session.consume(null, true);
        session.updateState(1);
        String first = session.token();
        session.updateState(2);
        assert first.equals(session.token());
        assert !session.consume("forged", true);
        assert !session.consume(first, false);
        assert session.consume(first, true);
        assert !session.consume(first, true);
        session.updateState(1);
        assert session.token() == null;
        session.updateState(0);
        session.updateState(1);
        assert !first.equals(session.token());
        assert !session.consume(first, true);
        String second = session.token();
        session.updateState(0);
        assert !session.consume(second, true);
        System.out.println("DiscardSession: stale, duplicate, paused and saving checks passed");
    }
}
