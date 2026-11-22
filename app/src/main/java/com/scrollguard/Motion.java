package com.scrollguard;

/**
 * 遮罩出现、消失的动效。只影响观感，不影响计时和拦截。
 */
public final class Motion {

    public static final String FADE = "fade";
    public static final String SHEET = "sheet";
    public static final String STAGGER = "stagger";
    public static final String SPRING = "spring";
    public static final String CENTER = "center";

    public static final String DEFAULT = CENTER;

    public static final String LABEL_FADE = "轻推淡入";
    public static final String LABEL_SHEET = "底部浮起";
    public static final String LABEL_STAGGER = "分层浮现";
    public static final String LABEL_SPRING = "轻弹";
    public static final String LABEL_CENTER = "中心放大";

    private Motion() {
    }

    /** 只认这五个；存坏了、升级后出现新值，都退回默认。 */
    public static String normalize(String v) {
        if (FADE.equals(v) || SHEET.equals(v) || STAGGER.equals(v) || SPRING.equals(v)
                || CENTER.equals(v)) {
            return v;
        }
        return DEFAULT;
    }

    public static String label(String v) {
        switch (normalize(v)) {
            case SHEET:
                return LABEL_SHEET;
            case STAGGER:
                return LABEL_STAGGER;
            case SPRING:
                return LABEL_SPRING;
            case CENTER:
                return LABEL_CENTER;
            default:
                return LABEL_FADE;
        }
    }

    public static String[] labels() {
        return new String[]{LABEL_FADE, LABEL_SHEET, LABEL_STAGGER, LABEL_SPRING, LABEL_CENTER};
    }

    public static String[] values() {
        return new String[]{FADE, SHEET, STAGGER, SPRING, CENTER};
    }

    public static int indexOf(String v) {
        String n = normalize(v);
        String[] all = values();
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(n)) {
                return i;
            }
        }
        return 0;
    }
}
