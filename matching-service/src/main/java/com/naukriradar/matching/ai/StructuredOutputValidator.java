package com.naukriradar.matching.ai;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks a model's answer against the JSON Schema it was given, so whatever the model says,
 * the rest of the system only ever sees the shape it expects.
 *
 * <p>Covers the part of JSON Schema our prompts use: {@code type}, {@code properties},
 * {@code required}, {@code items}, {@code enum}, {@code minimum}/{@code maximum} and
 * {@code maxLength}. A number outside its range is clamped (a "score" of 140 becomes 100)
 * and a string past its length is cut, because those are mistakes a model makes all the time
 * and the value is still useful. A wrong type or a missing field fails the answer.
 */
@Component
public class StructuredOutputValidator {

	private final JsonMapper json;

	public StructuredOutputValidator(JsonMapper json) {
		this.json = json;
	}

	/** @return the answer, with clamped values where needed */
	public JsonNode validate(String text, Map<String, Object> schema) {
		JsonNode node;
		try {
			node = json.readTree(extractJson(text));
		}
		catch (JacksonException ex) {
			throw new InvalidAiOutputException("The answer is not valid JSON.");
		}
		return check(node, schema, "$");
	}

	/** Models like to wrap JSON in ``` fences or add a sentence around it. */
	static String extractJson(String text) {
		if (text == null) {
			return "";
		}
		String trimmed = text.strip();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
	}

	@SuppressWarnings("unchecked")
	private JsonNode check(JsonNode node, Map<String, Object> schema, String path) {
		String type = (String) schema.get("type");
		if (schema.get("enum") instanceof List<?> allowed && !allowed.contains(plain(node))) {
			throw new InvalidAiOutputException(path + " must be one of " + allowed + ".");
		}
		if (type == null) {
			return node;
		}
		switch (type) {
			case "object" -> {
				if (!(node instanceof ObjectNode object)) {
					throw wrongType(path, type);
				}
				for (Object field : (List<Object>) schema.getOrDefault("required", List.of())) {
					JsonNode value = object.get((String) field);
					if (value == null || value.isNull()) {
						throw new InvalidAiOutputException(path + "." + field + " is missing.");
					}
				}
				Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
				properties.forEach((name, propertySchema) -> {
					JsonNode value = object.get(name);
					if (value != null && !value.isNull()) {
						object.set(name, check(value, (Map<String, Object>) propertySchema, path + "." + name));
					}
				});
				return object;
			}
			case "array" -> {
				if (!(node instanceof ArrayNode array)) {
					throw wrongType(path, type);
				}
				if (schema.get("items") instanceof Map<?, ?> items) {
					for (int i = 0; i < array.size(); i++) {
						array.set(i, check(array.get(i), (Map<String, Object>) items, path + "[" + i + "]"));
					}
				}
				return array;
			}
			case "string" -> {
				if (!node.isString()) {
					throw wrongType(path, type);
				}
				if (schema.get("maxLength") instanceof Number max && node.asString().length() > max.intValue()) {
					return JsonNodeFactory.instance.stringNode(node.asString().substring(0, max.intValue()));
				}
				return node;
			}
			case "integer" -> {
				if (!node.isNumber() || node.asDouble() != Math.rint(node.asDouble())) {
					throw wrongType(path, type);
				}
				return JsonNodeFactory.instance.numberNode((long) clamp(node.asDouble(), schema));
			}
			case "number" -> {
				if (!node.isNumber()) {
					throw wrongType(path, type);
				}
				return JsonNodeFactory.instance.numberNode(clamp(node.asDouble(), schema));
			}
			case "boolean" -> {
				if (!node.isBoolean()) {
					throw wrongType(path, type);
				}
				return node;
			}
			default -> {
				return node;
			}
		}
	}

	private static double clamp(double value, Map<String, Object> schema) {
		double result = value;
		if (schema.get("minimum") instanceof Number min) {
			result = Math.max(result, min.doubleValue());
		}
		if (schema.get("maximum") instanceof Number max) {
			result = Math.min(result, max.doubleValue());
		}
		return result;
	}

	private static Object plain(JsonNode node) {
		if (node.isString()) {
			return node.asString();
		}
		if (node.isIntegralNumber()) {
			return node.asInt();
		}
		return node.isNumber() ? node.asDouble() : node.toString();
	}

	private static InvalidAiOutputException wrongType(String path, String type) {
		return new InvalidAiOutputException(path + " must be " + ("aeiou".indexOf(type.charAt(0)) >= 0 ? "an " : "a ") + type + ".");
	}

}
