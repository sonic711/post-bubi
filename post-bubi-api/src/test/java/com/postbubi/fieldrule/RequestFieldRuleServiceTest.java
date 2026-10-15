package com.postbubi.fieldrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postbubi.web.error.ApiException;

class RequestFieldRuleServiceTest {

    private final RequestFieldRuleService service = new RequestFieldRuleService(new ObjectMapper());

    @Test
    void appliesBurRangeIncrementAndTodayDd() {
        String value = "PREFIX-0000099-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX00";
        String result = service.applyBurBasicLabelRules(value, List.of(
                rangeRule("sequence", "INCREMENT", 8, 14),
                rangeRule("day", "TODAY_DD", value.length() - 1, value.length())
        ), 1);

        assertThat(result.substring(7, 14)).isEqualTo("0000100");
        assertThat(result.substring(result.length() - 2)).isEqualTo(DateTimeFormatter.ofPattern("dd")
                .format(LocalDate.now(ZoneId.of("Asia/Taipei"))));
    }

    @Test
    void appliesGrpcRulesUsingUtf8BytePositionsAndRejectsSplitCharacters() {
        String applied = service.applyGrpcPayloadDataRules("{\"payload\":{\"data\":\"繁0009\"}}", List.of(
                new FieldRule("id", "序號", true, "#AB005F", "INCREMENT", "GRPC_PAYLOAD_DATA_RANGE", 4, 7, null)
        ), 1);
        assertThat(applied).contains("繁0010");

        assertThatThrownBy(() -> service.applyGrpcPayloadDataRules("{\"payload\":{\"data\":\"繁0009\"}}", List.of(
                new FieldRule("id", "錯誤範圍", true, "#AB005F", "NONE", "GRPC_PAYLOAD_DATA_RANGE", 2, 4, null)
        ), 1)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_UTF8_BOUNDARY_INVALID");
    }

    @Test
    void appliesNestedHttpJsonRulesAndRejectsUnsupportedValues() {
        String result = service.applyHttpJsonRules("{\"payload\":{\"id\":\"0009\",\"count\":5,\"day\":\"00\"}}", List.of(
                httpRule("id", "INCREMENT", "/payload/id"),
                httpRule("count", "INCREMENT", "/payload/count"),
                httpRule("day", "TODAY_DD", "/payload/day")
        ), 2);
        assertThat(result).contains("\"id\":\"0011\"");
        assertThat(result).contains("\"count\":7");
        assertThat(result).contains("\"day\":\"" + DateTimeFormatter.ofPattern("dd").format(LocalDate.now(ZoneId.of("Asia/Taipei"))) + "\"");

        assertThatThrownBy(() -> service.applyHttpJsonRules("{\"day\":2}", List.of(httpRule("日期", "TODAY_DD", "/day")), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_HTTP_TODAY_DD_STRING_REQUIRED");
        assertThatThrownBy(() -> service.applyHttpJsonRules("{\"items\":[{\"id\":\"1\"}]}", List.of(httpRule("陣列", "NONE", "/items/0/id")), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_HTTP_ARRAY_NOT_SUPPORTED");
        assertThatThrownBy(() -> service.applyHttpJsonRules("{\"id\":\"1\"}", List.of(
                new FieldRule("id", "顏色", true, "red", "NONE", "HTTP_JSON_VALUE", null, null, "/id")
        ), 1)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_COLOR_INVALID");
    }

    @Test
    void rejectsOverlappingRangesOverflowAndWrongDateLength() {
        assertThatThrownBy(() -> service.applyBurBasicLabelRules("000099", List.of(
                rangeRule("first", "NONE", 1, 3), rangeRule("second", "INCREMENT", 3, 6)), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_RANGE_OVERLAP");
        assertThatThrownBy(() -> service.applyBurBasicLabelRules("0099", List.of(rangeRule("overflow", "INCREMENT", 3, 4)), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_INCREMENT_OVERFLOW");
        assertThatThrownBy(() -> service.applyBurBasicLabelRules("0000", List.of(rangeRule("day", "TODAY_DD", 1, 3)), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_TODAY_DD_LENGTH_INVALID");
    }

    @Test
    void supportsEscapedPointerKeysAndPreservesDisabledRequests() {
        assertThat(service.applyHttpJsonRules("{\"a/b\":{\"~id\":\"0099\"}}",
                List.of(httpRule("escaped", "INCREMENT", "/a~1b/~0id")), 2)).contains("0101");
        FieldRule disabled = new FieldRule("id", "", false, "invalid", "invalid", "invalid", null, null, null);
        assertThat(service.applyHttpJsonRules("not json", List.of(disabled), 1)).isEqualTo("not json");
        assertThat(service.applyGrpcPayloadDataRules("not json", List.of(disabled), 1)).isEqualTo("not json");
        assertThat(service.applyBurBasicLabelRules("unchanged", List.of(disabled), 1)).isEqualTo("unchanged");
    }

    @Test
    void rejectsInvalidPointersArraysAndNonScalarTargets() {
        for (String path : List.of("/a~2b", "/a~")) {
            assertThatThrownBy(() -> service.applyHttpJsonRules("{\"a\":1}", List.of(httpRule("pointer", "NONE", path)), 1))
                    .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_HTTP_PATH_INVALID");
        }
        assertThatThrownBy(() -> service.applyHttpJsonRules("[]", List.of(httpRule("array", "NONE", "/id")), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_HTTP_ARRAY_NOT_SUPPORTED");
        assertThatThrownBy(() -> service.applyHttpJsonRules("{\"id\":{}}", List.of(httpRule("object", "NONE", "/id")), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_HTTP_VALUE_INVALID");
        assertThatThrownBy(() -> service.applyHttpJsonRules("{}", List.of(httpRule("missing", "NONE", "/id")), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_HTTP_PATH_NOT_FOUND");
    }

    @Test
    void rejectsInvalidRuleMetadataAndDuplicateIds() {
        assertThatThrownBy(() -> service.applyBurBasicLabelRules("0000", List.of(rangeRule("bad", "UNSUPPORTED", 1, 2)), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_ACTION_INVALID");
        assertThatThrownBy(() -> service.applyBurBasicLabelRules("0000", List.of(
                rangeRule("same", "NONE", 1, 2), rangeRule("same", "NONE", 3, 4)), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_ID_INVALID");
        assertThatThrownBy(() -> service.applyHttpJsonRules("{\"id\":1}", List.of(
                httpRule("one", "NONE", "/id"), httpRule("two", "NONE", "/id")), 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FIELD_RULE_HTTP_PATH_DUPLICATE");
    }

    private FieldRule rangeRule(String name, String action, int start, int end) {
        return new FieldRule(name, name, true, "#AB005F", action, "BUR_BASIC_LABEL_RANGE", start, end, null);
    }

    private FieldRule httpRule(String name, String action, String path) {
        return new FieldRule(name, name, true, "#AB005F", action, "HTTP_JSON_VALUE", null, null, path);
    }
}
