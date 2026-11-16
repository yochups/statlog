package dev.nidza.statlog.config;

public record StatEffect(String attribute, double incrementValue, boolean percent,
                         java.util.Map<Integer, Double> levelValues) {
    public double bonus(int level, int minimum, double baseValue) {
        Double override = levelValues.get(level);
        if (override != null) return override - baseValue;
        return java.math.BigDecimal.valueOf(incrementValue)
                .multiply(java.math.BigDecimal.valueOf(level - minimum)).doubleValue();
    }
}
