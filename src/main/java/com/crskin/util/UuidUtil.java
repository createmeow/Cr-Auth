package com.crskin.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * UUID 工具类
 */
public class UuidUtil {

    private static final UUID CRSKIN_NAMESPACE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static String generateCrskinUuid(String originalUuid, String source) {
        return uuid5(CRSKIN_NAMESPACE, source + ":" + originalUuid).toString();
    }

    private static UUID uuid5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteBuffer nsBuffer = ByteBuffer.allocate(16);
            nsBuffer.putLong(namespace.getMostSignificantBits());
            nsBuffer.putLong(namespace.getLeastSignificantBits());
            sha1.update(nsBuffer.array());
            sha1.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();
            ByteBuffer buffer = ByteBuffer.wrap(hash, 0, 16);
            long mostSig = buffer.getLong();
            long leastSig = buffer.getLong();
            mostSig = (mostSig & 0xFFFFFFFFFFFF0FFFL) | (5L << 12);
            leastSig = (leastSig & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
            return new UUID(mostSig, leastSig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate UUID v5", e);
        }
    }

    public static String toCleanUuid(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    public static String toHyphenatedUuid(String uuidStr) {
        String clean = uuidStr.replace("-", "");
        if (clean.length() != 32) return uuidStr;
        try {
            return UUID.fromString(
                clean.substring(0, 8) + "-" +
                clean.substring(8, 12) + "-" +
                clean.substring(12, 16) + "-" +
                clean.substring(16, 20) + "-" +
                clean.substring(20)
            ).toString();
        } catch (Exception e) {
            return uuidStr;
        }
    }

    public static UUID parseUuid(String uuidStr) {
        if (uuidStr.contains("-")) return UUID.fromString(uuidStr);
        String clean = uuidStr.replace("-", "");
        return new UUID(
            Long.parseUnsignedLong(clean.substring(0, 16), 16),
            Long.parseUnsignedLong(clean.substring(16), 16)
        );
    }
}
