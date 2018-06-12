package com.speedment.webapp;

import com.vaadin.addon.charts.model.style.Color;
import com.vaadin.addon.charts.model.style.SolidColor;
import com.vaadin.addon.charts.model.style.Style;
import com.vaadin.addon.charts.model.style.Theme;

public class ChartTheme extends Theme {

    public ChartTheme() {
        Color[] colors = new Color[2];
        colors[0] = new SolidColor("#5abf95"); // Light green
        colors[1] = new SolidColor("#fce390"); // Yellow
        setColors(colors);

        getChart().setBackgroundColor(new SolidColor("#3C474C"));
        getLegend().setBackgroundColor(new SolidColor("#ffffff"));

        Style textStyle = new Style();
        textStyle.setColor(new SolidColor("#ffffff")); // White text
        setTitle(textStyle);
    }

}
