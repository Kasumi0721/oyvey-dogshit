package me.alpha432.oyvey.features.settings;

import org.joml.Vector2f;

import java.awt.Color;
import java.util.function.BooleanSupplier;

public interface SettingFactory {
    <T extends Setting<?>> T register(T setting);

    // ==================== 无可见性 ====================
    default Setting<Boolean> bool(String name, boolean value) {
        return register(new Setting<>(name, value));
    }

    default <T extends Number> Setting<T> num(String name, T value, T min, T max) {
        return register(new Setting<>(name, value, min, max));
    }

    default Setting<String> str(String name, String value) {
        return register(new Setting<>(name, value));
    }

    default <T extends Enum<?>> Setting<T> mode(String name, T value) {
        return register(new Setting<>(name, value));
    }

    default Setting<Bind> key(String name, Bind bind) {
        return register(new Setting<>(name, bind));
    }

    // ===== 新增：int 版本的 bind，返回 Setting<Integer> =====
    default Setting<Integer> bind(String name, int key) {
        return register(new Setting<>(name, key));
    }

    default Setting<Color> color(String name, Color value) {
        return register(new Setting<>(name, value));
    }

    default Setting<Color> color(String name, int r, int g, int b, int a) {
        return register(new Setting<>(name, new Color(r, g, b, a)));
    }

    default Setting<Vector2f> vec2f(String name, Vector2f value) {
        return register(new Setting<>(name, value));
    }

    default Setting<Vector2f> vec2f(String name, float x, float y) {
        return register(new Setting<>(name, new Vector2f(x, y)));
    }

    // ==================== 带可见性（BooleanSupplier） ====================

    default Setting<Boolean> bool(String name, boolean value, BooleanSupplier visibility) {
        return register(new Setting<>(name, value, v -> visibility.getAsBoolean()));
    }

    default <T extends Number> Setting<T> num(String name, T value, T min, T max, BooleanSupplier visibility) {
        return register(new Setting<>(name, value, min, max, v -> visibility.getAsBoolean()));
    }

    default Setting<String> str(String name, String value, BooleanSupplier visibility) {
        return register(new Setting<>(name, value, v -> visibility.getAsBoolean()));
    }

    default <T extends Enum<?>> Setting<T> mode(String name, T value, BooleanSupplier visibility) {
        return register(new Setting<>(name, value, v -> visibility.getAsBoolean()));
    }

    default Setting<Bind> key(String name, Bind bind, BooleanSupplier visibility) {
        return register(new Setting<>(name, bind, v -> visibility.getAsBoolean()));
    }

    // ===== 新增：int 版 bind（带可见性） =====
    default Setting<Integer> bind(String name, int key, BooleanSupplier visibility) {
        return register(new Setting<>(name, key, v -> visibility.getAsBoolean()));
    }

    default Setting<Color> color(String name, Color value, BooleanSupplier visibility) {
        return register(new Setting<>(name, value, v -> visibility.getAsBoolean()));
    }

    default Setting<Color> color(String name, int r, int g, int b, int a, BooleanSupplier visibility) {
        return register(new Setting<>(name, new Color(r, g, b, a), v -> visibility.getAsBoolean()));
    }

    default Setting<Vector2f> vec2f(String name, Vector2f value, BooleanSupplier visibility) {
        return register(new Setting<>(name, value, v -> visibility.getAsBoolean()));
    }

    default Setting<Vector2f> vec2f(String name, float x, float y, BooleanSupplier visibility) {
        return register(new Setting<>(name, new Vector2f(x, y), v -> visibility.getAsBoolean()));
    }
}