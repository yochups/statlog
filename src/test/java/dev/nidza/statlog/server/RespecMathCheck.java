package dev.nidza.statlog.server;

public final class RespecMathCheck {
    public static void main(String[] args) {
        check(RespecMath.levelsToRemove(9, "50%") == 5, "percentage rounds up");
        check(RespecMath.levelsToRemove(9, "2") == 2, "flat count");
        check(RespecMath.levelsToRemove(9, "999") == 9, "count clamps to available levels");
        check(RespecMath.refund(5, "50%", 1) == 2, "percentage refund rounds down");
        check(RespecMath.refund(5, "2", 1) == 2, "flat refund");
        check(RespecMath.refund(5, "20", 1) == 5, "refund cannot exceed payment");
        check(RespecMath.refund(5, "", 0.5) == 2, "fallback percentage");
        try {
            RespecMath.levelsToRemove(9, "-1");
            throw new AssertionError("negative respec count should fail");
        } catch (IllegalArgumentException expected) {}
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
