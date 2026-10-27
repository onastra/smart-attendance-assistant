package com.lianzhi.attendance.knowledge.embedding;

/**
 * 向量的序列化与相似度计算。
 *
 * <p>存储格式选用 JSON 数组字符串而不是二进制 BLOB：kb_chunk 的数据量级（千级）
 * 用 Java 内存算余弦足够快，JSON 可读可调试，排查"这条切片向量对不对"时
 * 直接 SELECT 就能看，不需要额外工具。</p>
 */
public final class VectorCodec {

    private VectorCodec() {
    }

    /** float[] → JSON 数组字符串，保留 6 位有效精度控制体积 */
    public static String toJson(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vector.length * 10 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(java.util.Locale.US, "%.6g", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /** JSON 数组字符串 → float[]；非法输入返回 null，不抛异常（坏数据只影响该切片） */
    public static float[] fromJson(String json) {
        if (json == null || json.length() < 2) {
            return null;
        }
        String body = json.trim();
        if (!body.startsWith("[") || !body.endsWith("]")) {
            return null;
        }
        try {
            String[] parts = body.substring(1, body.length() - 1).split(",");
            float[] vector = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
            return vector;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 余弦相似度，返回 [-1, 1]。任一入参非法或维度不一致返回 -1（视为最不相似）。
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return -1D;
        }
        double dot = 0D;
        double normA = 0D;
        double normB = 0D;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0D || normB == 0D) {
            return -1D;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
