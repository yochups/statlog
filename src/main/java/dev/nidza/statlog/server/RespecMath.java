package dev.nidza.statlog.server;

public final class RespecMath {
    public static int levelsToRemove(int allocated, String value) {
        if (allocated < 0 || value == null) throw new IllegalArgumentException("Invalid respec amount");
        try {
            if (value.endsWith("%")) {
                double percentage = Double.parseDouble(value.substring(0, value.length() - 1));
                if (!Double.isFinite(percentage) || percentage < 0) throw new IllegalArgumentException("Invalid percentage");
                return (int) Math.min(allocated, Math.ceil(allocated * percentage / 100.0));
            }
            int flat = Integer.parseInt(value);
            if (flat < 0) throw new IllegalArgumentException("Invalid amount");
            return Math.min(allocated, flat);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Use a flat number such as 2 or a percentage such as 50%");
        }
    }
    public static int refund(int paid, String override, double fallbackPercentage) {
        if (paid <= 0) return 0;
        String value = override == null ? "" : override.trim();
        try {
            if (value.endsWith("%")) {
                double percentage = Double.parseDouble(value.substring(0, value.length() - 1));
                if (Double.isFinite(percentage))
                    return (int) Math.min(paid, Math.floor(paid * Math.max(0, percentage) / 100.0));
            } else if (!value.isEmpty()) return Math.min(paid, Math.max(0, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {}
        return (int) Math.min(paid, Math.floor(paid * Math.max(0, fallbackPercentage)));
    }
    private RespecMath() {}
}
