package me.alpha432.oyvey.features.settings;

import me.alpha432.oyvey.event.impl.ClientEvent;
import me.alpha432.oyvey.features.Feature;
import org.joml.Vector2f;

import java.util.Objects;
import java.util.function.Predicate;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;

public class Setting<T> {
    private final String name;
    private final T defaultValue;
    private T value;
    private T plannedValue;
    private T min;
    private T max;
    private boolean hasRestriction;
    private Predicate<T> visibility;
    private String description;
    private Feature feature;

    // ===== 新增：父/子可见性 =====
    private boolean isParent = false;
    private boolean open = false;

    public Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.plannedValue = defaultValue;
        this.description = "";
    }

    public Setting(String name, T defaultValue, String description) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.plannedValue = defaultValue;
        this.description = description;
    }

    public Setting(String name, T defaultValue, T min, T max, String description) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.plannedValue = defaultValue;
        this.description = description;
        this.hasRestriction = true;
    }

    public Setting(String name, T defaultValue, T min, T max) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.plannedValue = defaultValue;
        this.description = "";
        this.hasRestriction = true;
    }

    public Setting(String name, T defaultValue, T min, T max, Predicate<T> visibility, String description) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.plannedValue = defaultValue;
        this.visibility = visibility;
        this.description = description;
        this.hasRestriction = true;
    }

    public Setting(String name, T defaultValue, T min, T max, Predicate<T> visibility) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.plannedValue = defaultValue;
        this.visibility = visibility;
        this.description = "";
        this.hasRestriction = true;
    }

    public Setting(String name, T defaultValue, Predicate<T> visibility) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.visibility = visibility;
        this.plannedValue = defaultValue;
    }

    public String getName() {
        return this.name;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value) {
        this.setPlannedValue(value);
        if (this.hasRestriction) {
            if (((Number) this.min).floatValue() > ((Number) value).floatValue()) {
                this.setPlannedValue(this.min);
            }
            if (((Number) this.max).floatValue() < ((Number) value).floatValue()) {
                this.setPlannedValue(this.max);
            }
        }
        ClientEvent event = new ClientEvent(this);
        EVENT_BUS.post(event);
        if (!event.isCancelled()) {
            this.value = this.plannedValue;
        } else {
            this.plannedValue = this.value;
        }
    }

    public void reset() {
        setValue(getDefaultValue());
    }

    public T getPlannedValue() {
        return this.plannedValue;
    }

    public void setPlannedValue(T value) {
        this.plannedValue = value;
    }

    public T getMin() {
        return this.min;
    }

    public void setMin(T min) {
        this.min = min;
    }

    public T getMax() {
        return this.max;
    }

    public void setMax(T max) {
        this.max = max;
    }

    public void setValueNoEvent(T value) {
        this.setPlannedValue(value);
        if (this.hasRestriction) {
            if (((Number) this.min).floatValue() > ((Number) value).floatValue()) {
                this.setPlannedValue(this.min);
            }
            if (((Number) this.max).floatValue() < ((Number) value).floatValue()) {
                this.setPlannedValue(this.max);
            }
        }
        this.value = this.plannedValue;
    }

    public Feature getFeature() {
        return this.feature;
    }

    public void setFeature(Feature feature) {
        this.feature = feature;
    }

    public int getEnum(String input) {
        for (int i = 0; i < this.value.getClass().getEnumConstants().length; ++i) {
            Enum<?> e = (Enum<?>) this.value.getClass().getEnumConstants()[i];
            if (!e.name().equalsIgnoreCase(input)) continue;
            return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public void setEnumValue(String value) {
        for (Enum<?> e : ((Enum<?>) this.value).getClass().getEnumConstants()) {
            if (!e.name().equalsIgnoreCase(value)) continue;
            this.value = (T) e;
        }
    }

    public String currentEnumName() {
        return EnumConverter.getProperName((Enum<?>) this.value);
    }

    public int currentEnum() {
        return EnumConverter.currentEnum((Enum<?>) this.value);
    }

    @SuppressWarnings("unchecked")
    public void increaseEnum() {
        this.plannedValue = (T) EnumConverter.increaseEnum((Enum<?>) this.value);
        ClientEvent event = new ClientEvent(this);
        EVENT_BUS.post(event);
        if (!event.isCancelled()) {
            this.value = this.plannedValue;
        } else {
            this.plannedValue = this.value;
        }
    }

    @SuppressWarnings("unchecked")
    public void increaseEnumNoEvent() {
        this.value = (T) EnumConverter.increaseEnum((Enum<?>) this.value);
    }

    public String getType() {
        if (this.isEnumSetting()) {
            return "Enum";
        }
        if (this.isColorSetting()) {
            return "Color";
        }
        if (this.isVec2fSetting()) {
            return "Pos";
        }
        return this.getClassName(this.defaultValue);
    }

    public <K> String getClassName(K value) {
        return value.getClass().getSimpleName();
    }

    public String getDescription() {
        return Objects.requireNonNullElse(this.description, "");
    }

    public boolean isNumberSetting() {
        return this.value instanceof Number;
    }

    public boolean isEnumSetting() {
        return this.value instanceof Enum<?>;
    }

    public boolean isStringSetting() {
        return this.value instanceof String;
    }

    public boolean isColorSetting() {
        return this.value instanceof java.awt.Color;
    }

    public boolean isVec2fSetting() {
        return this.value instanceof Vector2f;
    }

    public T getDefaultValue() {
        return this.defaultValue;
    }

    public String getValueAsString() {
        return this.value.toString();
    }

    public boolean hasRestriction() {
        return this.hasRestriction;
    }

    public void setVisibility(Predicate<T> visibility) {
        this.visibility = visibility;
    }

    public boolean isVisible() {
        return this.visibility == null || this.visibility.test(this.getValue());
    }

    // ==================== 新增：父/子可见性 ====================

    /**
     * 把自己标记为"父"。父设置打开时，子设置才会显示。
     * 返回 this 自身，以便在设置声明处链式调用。
     */
    public Setting<T> setParent() {
        this.isParent = true;
        return this;
    }

    /**
     * 手动设置 open 状态。GUI 或代码中想强制开合时使用。
     */
    public void setOpen(boolean open) {
        this.open = open;
    }

    /**
     * 是否处于"打开"状态（子设置是否应显示）。
     * - 不是父设置 → 恒为 true（不受约束）
     * - 是父设置且值为 Boolean → 直接跟随它的布尔值
     * - 其它情况 → 返回手动 setOpen 设置的值
     */
    public boolean isOpen() {
        if (!this.isParent) return true;
        if (this.value instanceof Boolean b) return b;
        return this.open;
    }

    /**
     * 是否是父设置。
     */
    public boolean hasParent() {
        return this.isParent;
    }

    // ==================== 兼容旧类型化 API ====================

    /**
     * 兼容旧 SliderSetting 的 getValueFloat() / getValueInt() / getValueDouble() 调用。
     * 只在值为 Number 时有意义。
     */
    public float getValueFloat() {
        return ((Number) this.value).floatValue();
    }

    public int getValueInt() {
        return ((Number) this.value).intValue();
    }

    public double getValueDouble() {
        return ((Number) this.value).doubleValue();
    }
}