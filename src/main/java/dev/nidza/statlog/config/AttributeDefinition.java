package dev.nidza.statlog.config;

import java.util.List;

public record AttributeDefinition(String attribute, String name, String description, String icon,
                                  int minLevel, int maxLevel, int startingLevel, double incrementValue,
                                  int cost, List<String> acceptedPoints, List<String> tabs,
                                  boolean maxIcon, boolean maxIconSepia, boolean percent,
                                  String currentText, String incrementText, java.util.Map<Integer, Double> levelValues,
                                  double percentageBase,
                                  List<StatEffect> extraAttributes) {
    public String displayedIcon(int level) {
        if (!maxIcon || level < maxLevel || !icon.startsWith("statlog:textures/gui/attributes/") || !icon.endsWith(".png"))
            return icon;
        return icon.substring(0, icon.length() - 4) + "_max.png";
    }
    public double bonus(int level) {
        return new StatEffect(attribute, incrementValue, percent, levelValues, percentageBase).bonus(level, minLevel, 0);
    }
    public List<StatEffect> effects() {
        java.util.ArrayList<StatEffect> result = new java.util.ArrayList<>();
        result.add(new StatEffect(attribute, incrementValue, percent, levelValues, percentageBase));
        result.addAll(extraAttributes);
        return result;
    }
}
