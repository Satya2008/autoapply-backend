package com.naukriradar.matching.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Vector helpers. Vectors are kept unit length, so cosine similarity is a plain dot product;
 * on disk they are little-endian float32, 4 bytes per dimension.
 */
public final class Vectors {

	private Vectors() {
	}

	/** A unit-length copy; an all-zero vector stays all zero. */
	public static float[] normalize(float[] vector) {
		double sum = 0;
		for (float v : vector) {
			sum += v * v;
		}
		float[] unit = new float[vector.length];
		if (sum == 0) {
			return unit;
		}
		double norm = Math.sqrt(sum);
		for (int i = 0; i < vector.length; i++) {
			unit[i] = (float) (vector[i] / norm);
		}
		return unit;
	}

	/**
	 * -1 to 1; 0 when either is missing, empty or of another size (vectors from two models are
	 * not comparable, so that's "no signal", not an error).
	 */
	public static double cosine(float[] a, float[] b) {
		if (a == null || b == null || a.length == 0 || a.length != b.length) {
			return 0;
		}
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		if (normA == 0 || normB == 0) {
			return 0;
		}
		return dot / Math.sqrt(normA * normB);
	}

	public static byte[] toBytes(float[] vector) {
		ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
		for (float v : vector) {
			buffer.putFloat(v);
		}
		return buffer.array();
	}

	public static float[] fromBytes(byte[] bytes) {
		if (bytes == null || bytes.length % Float.BYTES != 0) {
			throw new IllegalArgumentException("Not a float32 vector");
		}
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		float[] vector = new float[bytes.length / Float.BYTES];
		for (int i = 0; i < vector.length; i++) {
			vector[i] = buffer.getFloat();
		}
		return vector;
	}

	/** Hex SHA-256 of a text: the key that tells whether a text was already embedded. */
	public static String hash(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is missing from this JVM", ex);
		}
	}

}
