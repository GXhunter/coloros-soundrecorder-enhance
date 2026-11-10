package io.github.hunter.recordingenhance;

public final class TriplePressSelfTest {
    public static void main(String[] args) {
        TriplePress detector = new TriplePress();
        assert !detector.release(100, 60, true, 2);
        assert !detector.release(300, 60, true, 2);
        assert detector.release(500, 60, true, 2);
        assert !detector.release(600, 60, true, 2);
        assert !detector.release(800, 60, true, 2);
        assert !detector.release(1000, 60, true, 2);
        assert !detector.release(3000, 60, true, 2);
        assert !detector.release(4500, 60, true, 2);
        assert !detector.release(4600, 60, true, 2);
        assert !detector.release(4700, 600, true, 2);
        assert !detector.release(4800, 60, true, 2);
        assert !detector.release(4900, 60, false, 2);
        assert !detector.release(5000, 60, true, 2);
        assert !detector.release(5100, 60, true, 2);
        assert detector.release(5200, 60, true, 2);
        for (int shortcut = 0; shortcut < 4; shortcut++) {
            detector = new TriplePress();
            assert Settings.keyCode(shortcut) == (shortcut % 2 == 0 ? 24 : 25);
            int count = Settings.presses(shortcut);
            for (int press = 1; press <= count; press++) {
                assert detector.release(press * 200L, 60, true, shortcut) == (press == count);
            }
        }
        detector = new TriplePress();
        assert !detector.release(100, 50, true, 2);
        assert !detector.release(200, 50, true, 0);
        assert detector.release(300, 50, true, 0);
        assert Settings.shortcut(-1) == 2;
        assert Settings.shortcut(4) == 2;
        assert Settings.feedback(3) == 0;
        assert Settings.feedback(2) == 2;
        System.out.println("TriplePress: all checks passed");
    }
}
