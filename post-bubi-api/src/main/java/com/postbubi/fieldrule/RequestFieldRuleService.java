package com.postbubi.fieldrule;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.postbubi.web.error.ApiException;

@Service
public class RequestFieldRuleService {

    private static final ZoneId TAIPEI_ZONE_ID = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DAY_OF_MONTH = DateTimeFormatter.ofPattern("dd");

    private final ObjectMapper objectMapper;

    public RequestFieldRuleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String applyBurBasicLabelRules(String value, List<FieldRule> rules, int increment) {
        List<FieldRule> enabled = enabledRules(rules, "BUR_BASIC_LABEL_RANGE");
        if (enabled.isEmpty()) {
            return value;
        }
        List<RangeRule> ranges = rangeRules(enabled, value.length(), "Basic Label", false, null);
        StringBuilder result = new StringBuilder(value);
        for (int index = ranges.size() - 1; index >= 0; index--) {
            RangeRule range = ranges.get(index);
            result.replace(range.start(), range.end(), transformText(result.substring(range.start(), range.end()), range.rule(), increment));
        }
        return result.toString();
    }

    public String applyGrpcPayloadDataRules(String body, List<FieldRule> rules, int increment) {
        List<FieldRule> enabled = enabledRules(rules, "GRPC_PAYLOAD_DATA_RANGE");
        if (enabled.isEmpty()) {
            return body;
        }
        try {
            JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            JsonNode data = root.path("payload").path("data");
            if (!(root instanceof ObjectNode objectRoot) || !data.isTextual()) {
                throw invalid("FIELD_RULE_GRPC_PAYLOAD_DATA_INVALID", "gRPC 欄位規則需要字串欄位 payload.data。");
            }
            ((ObjectNode) objectRoot.path("payload")).put("data", applyRangeRules(data.textValue(), enabled, increment, "payload.data", true));
            return objectMapper.writeValueAsString(objectRoot);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("FIELD_RULE_GRPC_BODY_INVALID", "gRPC Body 不是有效的 JSON。");
        }
    }

    public String applyHttpJsonRules(String body, List<FieldRule> rules, int increment) {
        List<FieldRule> enabled = enabledRules(rules, "HTTP_JSON_VALUE");
        if (enabled.isEmpty()) {
            return body;
        }
        try {
            JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            if (root != null && root.isArray()) {
                throw invalid("FIELD_RULE_HTTP_ARRAY_NOT_SUPPORTED", "HTTP 欄位規則第一版不支援 JSON array。");
            }
            if (!(root instanceof ObjectNode objectRoot)) {
                throw invalid("FIELD_RULE_HTTP_BODY_INVALID", "HTTP 欄位規則需要 JSON object Body。");
            }
            Set<String> paths = new HashSet<>();
            for (FieldRule rule : enabled) {
                String path = requiredPath(rule);
                if (!paths.add(path)) {
                    throw ruleError("FIELD_RULE_HTTP_PATH_DUPLICATE", rule, "同一 JSON 欄位不可設定多條啟用規則。");
                }
                JsonValueTarget target = findHttpTarget(objectRoot, path, rule);
                target.parent().set(target.fieldName(), transformJsonValue(target.value(), rule, increment));
            }
            return objectMapper.writeValueAsString(objectRoot);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("FIELD_RULE_HTTP_BODY_INVALID", "HTTP Body 不是有效的 JSON。");
        }
    }

    private String applyRangeRules(String value, List<FieldRule> rules, int increment, String targetLabel, boolean utf8ByteRange) {
        if (rules.isEmpty()) {
            return value;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        Set<Integer> boundaries = utf8Boundaries(value);
        List<RangeRule> ranges = rangeRules(rules, bytes.length, targetLabel, utf8ByteRange, boundaries);
        for (int index = ranges.size() - 1; index >= 0; index--) {
            RangeRule range = ranges.get(index);
            String current = new String(bytes, range.start(), range.end() - range.start(), StandardCharsets.UTF_8);
            String replacement = transformText(current, range.rule(), increment);
            byte[] replacementBytes = replacement.getBytes(StandardCharsets.UTF_8);
            if (replacementBytes.length != range.end() - range.start()) {
                throw ruleError("FIELD_RULE_LENGTH_CHANGED", range.rule(), targetLabel + " 規則不得改變欄位長度。");
            }
            System.arraycopy(replacementBytes, 0, bytes, range.start(), replacementBytes.length);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<RangeRule> rangeRules(List<FieldRule> rules, int length, String targetLabel, boolean utf8ByteRange, Set<Integer> boundaries) {
        List<RangeRule> ranges = new ArrayList<>();
        for (FieldRule rule : rules) {
            int start = requiredPosition(rule, rule.start(), "起始位置");
            int end = requiredPosition(rule, rule.end(), "結束位置");
            if (end < start || end > length) {
                throw ruleError("FIELD_RULE_RANGE_INVALID", rule, targetLabel + " 欄位範圍超出內容長度。");
            }
            int zeroBasedStart = start - 1;
            if (utf8ByteRange && (!boundaries.contains(zeroBasedStart) || !boundaries.contains(end))) {
                throw ruleError("FIELD_RULE_UTF8_BOUNDARY_INVALID", rule, targetLabel + " 欄位範圍不可切開 UTF-8 字元。");
            }
            ranges.add(new RangeRule(rule, zeroBasedStart, end));
        }
        ranges.sort(Comparator.comparingInt(RangeRule::start));
        for (int index = 1; index < ranges.size(); index++) {
            if (ranges.get(index).start() < ranges.get(index - 1).end()) {
                throw ruleError("FIELD_RULE_RANGE_OVERLAP", ranges.get(index).rule(), targetLabel + " 欄位範圍不可重疊。");
            }
        }
        return ranges;
    }

    private JsonNode transformJsonValue(JsonNode value, FieldRule rule, int increment) {
        if ("NONE".equals(normalizeAction(rule))) {
            if (value.isContainerNode()) {
                throw ruleError("FIELD_RULE_HTTP_VALUE_INVALID", rule, "HTTP 欄位規則只能指向單一值，不能指向 object 或 array。");
            }
            return value;
        }
        if ("TODAY_DD".equals(normalizeAction(rule))) {
            if (!value.isTextual()) {
                throw ruleError("FIELD_RULE_HTTP_TODAY_DD_STRING_REQUIRED", rule, "今日 DD 只能套用 JSON 字串欄位。");
            }
            return TextNode.valueOf(todayDd());
        }
        if (value.isTextual()) {
            return TextNode.valueOf(incrementDigits(value.textValue(), rule, increment));
        }
        if (value.isIntegralNumber()) {
            return BigIntegerNode.valueOf(value.bigIntegerValue().add(BigInteger.valueOf(increment)));
        }
        throw ruleError("FIELD_RULE_HTTP_INCREMENT_INVALID", rule, "遞增只能套用 JSON 字串數字或整數欄位。");
    }

    private String transformText(String value, FieldRule rule, int increment) {
        return switch (normalizeAction(rule)) {
            case "NONE" -> value;
            case "INCREMENT" -> incrementDigits(value, rule, increment);
            case "TODAY_DD" -> {
                if (value.getBytes(StandardCharsets.UTF_8).length != 2) {
                    throw ruleError("FIELD_RULE_TODAY_DD_LENGTH_INVALID", rule, "今日 DD 的欄位長度必須為 2。");
                }
                yield todayDd();
            }
            default -> throw ruleError("FIELD_RULE_ACTION_INVALID", rule, "不支援的欄位規則動作。");
        };
    }

    private String incrementDigits(String value, FieldRule rule, int increment) {
        if (!value.matches("\\d+")) {
            throw ruleError("FIELD_RULE_INCREMENT_INVALID", rule, "遞增欄位必須是十進位數字。");
        }
        String next = new BigInteger(value).add(BigInteger.valueOf(increment)).toString();
        if (next.length() > value.length()) {
            throw ruleError("FIELD_RULE_INCREMENT_OVERFLOW", rule, "遞增後超過原欄位長度。");
        }
        return "0".repeat(value.length() - next.length()) + next;
    }

    private JsonValueTarget findHttpTarget(ObjectNode root, String path, FieldRule rule) {
        if (!path.startsWith("/") || "/".equals(path)) {
            throw ruleError("FIELD_RULE_HTTP_PATH_INVALID", rule, "HTTP JSON Pointer 必須指向 object 內的欄位。");
        }
        String[] segments = path.substring(1).split("/", -1);
        ObjectNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = decodePointerSegment(segments[index]);
            JsonNode child = current.get(segment);
            if (child == null) {
                throw ruleError("FIELD_RULE_HTTP_PATH_NOT_FOUND", rule, "找不到 HTTP JSON Pointer 指定的欄位：" + path);
            }
            if (child.isArray()) {
                throw ruleError("FIELD_RULE_HTTP_ARRAY_NOT_SUPPORTED", rule, "HTTP 欄位規則第一版不支援 JSON array。");
            }
            if (!(child instanceof ObjectNode objectChild)) {
                throw ruleError("FIELD_RULE_HTTP_PATH_NOT_FOUND", rule, "HTTP JSON Pointer 中間節點必須是 object：" + path);
            }
            current = objectChild;
        }
        String fieldName = decodePointerSegment(segments[segments.length - 1]);
        JsonNode value = current.get(fieldName);
        if (value == null) {
            throw ruleError("FIELD_RULE_HTTP_PATH_NOT_FOUND", rule, "找不到 HTTP JSON Pointer 指定的欄位：" + path);
        }
        return new JsonValueTarget(current, fieldName, value);
    }

    private List<FieldRule> enabledRules(List<FieldRule> rules, String expectedTargetType) {
        List<FieldRule> result = new ArrayList<>();
        if (rules == null) return result;
        Set<String> ids = new HashSet<>();
        for (FieldRule rule : rules) {
            if (rule == null || !Boolean.TRUE.equals(rule.enabled())) continue;
            if (rule.id() == null || rule.id().isBlank() || !ids.add(rule.id())) {
                throw ruleError("FIELD_RULE_ID_INVALID", rule, "規則識別碼不可空白或重複。");
            }
            if (rule.name() == null || rule.name().isBlank()) {
                throw ruleError("FIELD_RULE_NAME_INVALID", rule, "規則名稱不可空白。");
            }
            if (!Set.of("NONE", "INCREMENT", "TODAY_DD").contains(normalizeAction(rule))) {
                throw ruleError("FIELD_RULE_ACTION_INVALID", rule, "不支援的欄位規則動作。");
            }
            if (!expectedTargetType.equals(rule.targetType())) {
                throw ruleError("FIELD_RULE_TARGET_TYPE_INVALID", rule, "欄位規則不適用於目前 Request 類型。");
            }
            if (rule.color() == null || !rule.color().matches("#[0-9a-fA-F]{6}")) {
                throw ruleError("FIELD_RULE_COLOR_INVALID", rule, "顏色必須為 #RRGGBB 格式。");
            }
            result.add(rule);
        }
        return result;
    }

    private Set<Integer> utf8Boundaries(String value) {
        Set<Integer> result = new HashSet<>();
        int offset = 0;
        result.add(offset);
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            offset += new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            result.add(offset);
            index += Character.charCount(codePoint);
        }
        return result;
    }

    private int requiredPosition(FieldRule rule, Integer value, String label) {
        if (value == null || value < 1) throw ruleError("FIELD_RULE_RANGE_INVALID", rule, label + "必須為正整數。");
        return value;
    }

    private String requiredPath(FieldRule rule) {
        if (rule.path() == null || rule.path().isBlank()) throw ruleError("FIELD_RULE_HTTP_PATH_INVALID", rule, "HTTP JSON Pointer 不可空白。");
        if (rule.path().matches(".*~(?:[^01]|$).*")) throw ruleError("FIELD_RULE_HTTP_PATH_INVALID", rule, "HTTP JSON Pointer 的 ~ 必須跳脫為 ~0，/ 必須跳脫為 ~1。");
        return rule.path();
    }

    private String normalizeAction(FieldRule rule) {
        return rule.action() == null ? "NONE" : rule.action().trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String todayDd() { return DAY_OF_MONTH.format(LocalDate.now(TAIPEI_ZONE_ID)); }

    private String decodePointerSegment(String value) { return value.replace("~1", "/").replace("~0", "~"); }

    private ApiException invalid(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }

    private ApiException ruleError(String code, FieldRule rule, String message) {
        String name = rule == null || rule.name() == null || rule.name().isBlank() ? "未命名規則" : rule.name().trim();
        return new ApiException(HttpStatus.BAD_REQUEST, code, "欄位規則「" + name + "」：" + message);
    }

    private record RangeRule(FieldRule rule, int start, int end) {}
    private record JsonValueTarget(ObjectNode parent, String fieldName, JsonNode value) {}
}
