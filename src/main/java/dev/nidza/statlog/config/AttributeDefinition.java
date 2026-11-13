package dev.nidza.statlog.config;

import java.util.List;

public record AttributeDefinition(String attribute, String name, String description, String icon,
                                  int minLevel, int maxLevel, int startingLevel, double incrementValue,
                                  int cost, List<String> acceptedPoints, List<String> tabs,
                                  boolean maxIcon, boolean maxIconSepia) {
    public String displayedIcon(int level) {
        if (!maxIcon || level < maxLevel || !icon.startsWith("statlog:textures/gui/attributes/") || !icon.endsWith(".png"))
            return icon;
        return icon.substring(0, icon.length() - 4) + "_max.png";
    }
    public double bonus(int level) {
        return java.math.BigDecimal.valueOf(incrementValue)
                .multiply(java.math.BigDecimal.valueOf(level - minLevel)).doubleValue();
    }
}
