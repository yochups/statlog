package dev.nidza.statlog.config;

public record StatEffect(String attribute, double incrementValue, boolean percent,
                         java.util.Map<Integer, Double> levelValues, double percentageBase) {
    public StatEffect(String attribute, double incrementValue, boolean percent,
                      java.util.Map<Integer, Double> levelValues) {
        this(attribute, incrementValue, percent, levelValues, 0);
    }
    public double bonus(int level, int minimum, double baseValue) {
        Double override = levelValues.get(level);
        if (override != null) return override - baseValue;
        return java.math.BigDecimal.valueOf(incrementValue)
                .multiply(java.math.BigDecimal.valueOf(level - minimum)).doubleValue();
    }
    public double percentageReference(double baseValue, int minimum, int maximum) {
        if (percentageBase > 0) return percentageBase;
        if (Math.abs(baseValue) > 0.000001) return Math.abs(baseValue);
        return Math.max(1, Math.abs(bonus(maximum, minimum, baseValue)));
    }
}
