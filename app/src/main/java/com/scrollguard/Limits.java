package com.scrollguard;

/**
 * 两个可以自己填的数字的取值范围与解析规则。
 *
 * <p>抽成纯逻辑是为了让输入框那一层能脱离界面测：填了什么算合法、填错了怎么处理，
 * 都在这里定死，界面上只负责把结果显示出来。
 */
public final class Limits {

    /** 「连着刷多久就拦」最短 1 分钟 */
    public static final int MIN = 1;

    /** 最长 12 小时；再长就等于没有防护了 */
    public static final int MAX = 12 * 60;

    /** 没设过时的时长 */
    public static final int DEFAULT = 10;

    /** 「锁上后多久自动打开」填 0 表示不自动 */
    public static final int AUTO_OFF = 0;

    private Limits() {
    }

    /** 把任意值夹进 [min, max]，用于读出来的旧值兜底 */
    public static int clamp(long v, int min, int max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return (int) v;
    }

    /**
     * 解析用户填的分钟数。
     *
     * @return 落在 [min, max] 里的整数；空的、带别的字符的、越界的都返回 -1
     */
    public static int parse(String text, int min, int max) {
        if (text == null) {
            return -1;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return -1;
        }
        StringBuilder digits = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits.append(ch);
            } else if (ch >= '０' && ch <= '９') {
                // 中文输入法下敲出来的全角数字也认
                digits.append((char) ('0' + (ch - '０')));
            } else {
                return -1;
            }
        }
        long v;
        try {
            v = Long.parseLong(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
        if (v < min || v > max) {
            return -1;
        }
        return (int) v;
    }

    /** 填错时给用户看的那句话 */
    public static String hint(int min, int max) {
        return "填 " + min + " 到 " + max + " 之间的整数";
    }
}
