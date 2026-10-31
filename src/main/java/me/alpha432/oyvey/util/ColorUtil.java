package me.alpha432.oyvey.util;

import me.alpha432.oyvey.features.modules.client.ClickGuiModule;

import java.awt.*;

public class ColorUtil {

    public static Color rainbow(int delay) {
        double rainbowState = Math.ceil((double) (System.currentTimeMillis() + (long) delay) / 20.0);
        return Color.getHSBColor(
                (float) ((rainbowState % 360.0) / 360.0),
                ClickGuiModule.getInstance().rainbowSaturation.getValue() / 255.0f,
                ClickGuiModule.getInstance().rainbowBrightness.getValue() / 255.0f
        );
    }

    public static Color fadeColor(Color c1, Color c2, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) (c1.getRed()   + (c2.getRed()   - c1.getRed())   * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue()  + (c2.getBlue()  - c1.getBlue())  * t);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * t);
        return new Color(r, g, b, a);
    }
}