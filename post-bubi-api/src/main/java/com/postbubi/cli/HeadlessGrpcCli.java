package com.postbubi.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.postbubi.execution.ExecutionCancellationService;
import com.postbubi.grpc.GrpcExecuteService;
import com.postbubi.grpc.GrpcProtoDescriptorResolver;
import com.postbubi.grpc.GrpcReflectionDescriptorResolver;
import com.postbubi.proto.ProtoStorageService;
import com.postbubi.web.dto.GrpcExecuteRequest;
import com.postbubi.web.dto.GrpcExecuteResponse;
import com.postbubi.web.dto.HttpNameValue;
import com.postbubi.web.error.ApiException;

/** Executes one exported unary gRPC request without starting Spring Web or H2. */
public final class HeadlessGrpcCli {

    private static final String COMMAND = "run-grpc";
    private static final String ARCHIVE_ENTRY = "collection.json";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}" );
    private static final Pattern PROTO_IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?:public\\s+|weak\\s+)?\"([^\"]+)\"\\s*;");
    private static final Set<String> OPTIONS_WITH_VALUE = Set.of("--archive", "--request", "--collection", "--folder", "--environment", "--var", "--output");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean isCommand(String[] args) {
        return args != null && args.length > 0 && COMMAND.equals(args[0]);
    }

    public int run(String[] args) {
        CliArguments command;
        try {
            command = parseArguments(args);
            if (command.help()) {
                System.out.println(usage());
                return 0;
            }
        } catch (CliException exception) {
            writeError(exception.code(), exception.getMessage());
            return 2;
        }

        Path temporaryDirectory = null;
        try {
            Archive archive = readArchive(command.archive());
            JsonNode request = selectRequest(archive, command);
            ObjectNode payload = resolvePayload(archive, request.path("payloadJson").asText("{}"), command);

            temporaryDirectory = Files.createTempDirectory("post-bubi-cli-");
            materializeProtos(archive, temporaryDirectory);
            GrpcExecuteResponse response = execute(payload, temporaryDirectory.resolve("protos"));
            String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseOutput(request, response));
            if (command.output() != null) {
                writeOutput(command.output(), output);
            }
            System.out.println(output);
            return "OK".equals(response.statusCode()) ? 0 : 1;
        } catch (CliException | ApiException exception) {
            writeError(errorCode(exception), exception.getMessage());
            return 2;
        } catch (IOException exception) {
            writeError("HEADLESS_GRPC_CLI_IO_FAILED", "CLI 檔案或暫存資源處理失敗。");
            return 3;
        } catch (Exception exception) {
            writeError("HEADLESS_GRPC_CLI_INTERNAL", "Headless gRPC CLI 執行失敗。");
            return 3;
        } finally {
            deleteDirectory(temporaryDirectory);
        }
    }

    private CliArguments parseArguments(String[] args) {
        if (!isCommand(args)) {
            throw usageError("CLI 命令必須為 run-grpc。");
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        boolean help = false;
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if ("--help".equals(argument)) {
                help = true;
                continue;
            }
            if (!OPTIONS_WITH_VALUE.contains(argument) || index + 1 >= args.length) {
                throw usageError("不支援或缺少值的參數：" + argument);
            }
            values.computeIfAbsent(argument, ignored -> new ArrayList<>()).add(args[++index]);
        }
        if (help) {
            return new CliArguments(null, null, null, null, null, Map.of(), null, true);
        }
        Path archive = Path.of(requiredSingle(values, "--archive"));
        String request = requiredSingle(values, "--request");
        Map<String, String> overrides = new LinkedHashMap<>();
        for (String value : values.getOrDefault("--var", List.of())) {
            int delimiter = value.indexOf('=');
            if (delimiter <= 0) {
                throw usageError("--var 必須採 key=value 格式。");
            }
            overrides.put(value.substring(0, delimiter).trim(), value.substring(delimiter + 1));
        }
        return new CliArguments(
                archive,
                request,
                optionalSingle(values, "--collection"),
                optionalSingle(values, "--folder"),
                optionalSingle(values, "--environment"),
                overrides,
                optionalSingle(values, "--output") == null ? null : Path.of(optionalSingle(values, "--output")),
                false
        );
    }

    private Archive readArchive(Path archivePath) throws IOException {
        if (archivePath == null || !Files.isRegularFile(archivePath)) {
            throw new CliException("HEADLESS_GRPC_ARCHIVE_NOT_FOUND", "找不到指定的 ZIP 匯出檔。", false);
        }
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory()) {
                    validateEntryName(name);
                    entries.put(name, input.readAllBytes());
                }
            }
        }
        byte[] archiveJson = entries.get(ARCHIVE_ENTRY);
        if (archiveJson == null) {
            throw new CliException("HEADLESS_GRPC_ARCHIVE_INVALID", "ZIP 缺少 collection.json。", false);
        }
        JsonNode root = objectMapper.readTree(archiveJson);
        int schemaVersion = root.path("schemaVersion").asInt(1);
        if (schemaVersion < 1 || schemaVersion > 3) {
            throw new CliException("HEADLESS_GRPC_ARCHIVE_SCHEMA_UNSUPPORTED", "不支援的 ZIP schema version。", false);
        }
        return new Archive(root, entries);
    }

    private JsonNode selectRequest(Archive archive, CliArguments arguments) {
        Map<Long, JsonNode> collections = indexById(archive.root().path("collections"));
        Map<Long, JsonNode> folders = indexById(archive.root().path("folders"));
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode request : archive.root().path("requests")) {
            if (!"GRPC".equals(request.path("type").asText()) || !arguments.request().equals(request.path("name").asText())) {
                continue;
            }
            JsonNode collection = collections.get(request.path("collectionId").asLong());
            if (collection == null) continue;
            if (arguments.collection() != null && !arguments.collection().equals(collection.path("name").asText())) continue;
            if (arguments.folder() != null && !normalizeFolder(arguments.folder()).equals(folderPath(request.path("folderId"), folders))) continue;
            matches.add(request);
        }
        if (matches.isEmpty()) {
            throw new CliException("HEADLESS_GRPC_REQUEST_NOT_FOUND", "找不到指定的一般 gRPC Request。", false);
        }
        if (matches.size() > 1) {
            throw new CliException("HEADLESS_GRPC_REQUEST_AMBIGUOUS", "Request 名稱不唯一，請加上 --collection 或 --folder。", false);
        }
        return matches.get(0);
    }

    private ObjectNode resolvePayload(Archive archive, String payloadJson, CliArguments arguments) throws IOException {
        JsonNode parsed = objectMapper.readTree(payloadJson);
        if (!(parsed instanceof ObjectNode payload) || !"GRPC".equals(payload.path("requestType").asText("GRPC"))) {
            throw new CliException("HEADLESS_GRPC_PAYLOAD_INVALID", "匯出 Request 不是有效的一般 gRPC payload。", false);
        }
        Map<String, String> variables = environmentVariables(archive.root().path("environments"), arguments.environment());
        variables.putAll(arguments.overrides());
        ObjectNode resolved = payload.deepCopy();
        resolveNode(resolved, variables, new ArrayList<>());
        return resolved;
    }

    private Map<String, String> environmentVariables(JsonNode environments, String name) {
        if (name == null) return new LinkedHashMap<>();
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode environment : environments) {
            if (name.equals(environment.path("name").asText())) matches.add(environment);
        }
        if (matches.size() != 1) {
            throw new CliException("HEADLESS_GRPC_ENVIRONMENT_NOT_FOUND", "找不到或無法唯一識別指定的 Environment。", false);
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (JsonNode variable : matches.get(0).path("variables")) {
            String key = variable.path("key").asText().trim();
            if (!key.isEmpty()) variables.put(key, variable.path("value").asText(""));
        }
        return variables;
    }

    private void resolveNode(JsonNode node, Map<String, String> variables, List<String> stack) {
        if (node instanceof ObjectNode object) {
            List<String> fields = new ArrayList<>();
            object.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode value = object.get(field);
                if (value.isTextual()) {
                    object.put(field, resolveText(value.asText(), variables, stack));
                } else {
                    resolveNode(value, variables, stack);
                }
            }
        } else if (node.isArray()) {
            node.forEach(value -> resolveNode(value, variables, stack));
        }
    }

    private String resolveText(String value, Map<String, String> variables, List<String> stack) {
        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (!variables.containsKey(key)) {
                throw new CliException("HEADLESS_GRPC_VARIABLE_NOT_FOUND", "Environment 找不到 Request 使用的變數：" + key, false);
            }
            if (stack.contains(key)) {
                throw new CliException("HEADLESS_GRPC_VARIABLE_CIRCULAR", "Environment 變數循環引用：" + key, false);
            }
            List<String> nextStack = new ArrayList<>(stack);
            nextStack.add(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolveText(variables.get(key), variables, nextStack)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void materializeProtos(Archive archive, Path temporaryDirectory) throws IOException {
        Path protosDirectory = temporaryDirectory.resolve("protos");
        Files.createDirectories(protosDirectory);
        Map<String, byte[]> protoByFilename = new HashMap<>();
        for (JsonNode proto : archive.root().path("protos")) {
            String id = proto.path("protoId").asText();
            String source = proto.path("path").asText();
            byte[] content = archive.entries().get(source);
            if (id.isBlank() || content == null) continue;
            String filename = sanitizeFilename(proto.path("originalFilename").asText("schema.proto"));
            protoByFilename.putIfAbsent(filename, content);
            Files.write(protosDirectory.resolve(id + "-" + filename), content);
            Path importAlias = protosDirectory.resolve(filename);
            if (!Files.exists(importAlias)) Files.write(importAlias, content);
        }
        for (Map.Entry<String, byte[]> entry : protoByFilename.entrySet()) {
            Matcher matcher = PROTO_IMPORT_PATTERN.matcher(new String(entry.getValue(), StandardCharsets.UTF_8));
            while (matcher.find()) {
                String importName = matcher.group(1);
                if (importName.startsWith("/") || importName.contains("\\") || Path.of(importName).normalize().startsWith("..")) continue;
                byte[] dependency = protoByFilename.get(Path.of(importName).getFileName().toString());
                if (dependency == null) continue;
                Path importAlias = protosDirectory.resolve(importName).normalize();
                if (importAlias.startsWith(protosDirectory) && !Files.exists(importAlias)) {
                    Files.createDirectories(importAlias.getParent());
                    Files.write(importAlias, dependency);
                }
            }
        }
    }

    private GrpcExecuteResponse execute(ObjectNode payload, Path protosDirectory) {
        String protoId = text(payload, "grpcProtoId");
        GrpcExecuteRequest request = new GrpcExecuteRequest(
                null,
                text(payload, "grpcHost"),
                integer(payload, "grpcPort", 50051),
                booleanValue(payload, "grpcPlaintext", true),
                booleanValue(payload, "grpcIgnoreTlsVerification", false),
                metadata(text(payload, "grpcMetadataText")),
                protoId.isBlank() ? null : protoId,
                text(payload, "grpcServiceName"),
                text(payload, "grpcMethodName"),
                text(payload, "grpcBody", "{}"),
                booleanValue(payload, "grpcEncodePayloadDataBase64", false),
                integer(payload, "timeoutMillis", 30000)
        );
        ProtoStorageService storage = new ProtoStorageService(protosDirectory.toString());
        GrpcExecuteService service = new GrpcExecuteService(new GrpcReflectionDescriptorResolver(), new GrpcProtoDescriptorResolver(storage), objectMapper);
        return service.execute(request, new ExecutionCancellationService().start(null));
    }

    private List<HttpNameValue> metadata(String text) {
        List<HttpNameValue> values = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int delimiter = trimmed.indexOf('=');
            String name = delimiter < 0 ? trimmed : trimmed.substring(0, delimiter).trim();
            if (!name.isEmpty()) values.add(new HttpNameValue(name, delimiter < 0 ? "" : trimmed.substring(delimiter + 1).trim(), true));
        }
        return values;
    }

    private Map<Long, JsonNode> indexById(JsonNode values) {
        Map<Long, JsonNode> result = new HashMap<>();
        values.forEach(value -> result.put(value.path("id").asLong(), value));
        return result;
    }

    private String folderPath(JsonNode folderId, Map<Long, JsonNode> folders) {
        if (folderId.isMissingNode() || folderId.isNull()) return "/";
        List<String> names = new ArrayList<>();
        JsonNode current = folders.get(folderId.asLong());
        while (current != null) {
            names.add(current.path("name").asText());
            JsonNode parent = current.path("parentFolderId");
            current = parent.isMissingNode() || parent.isNull() ? null : folders.get(parent.asLong());
        }
        java.util.Collections.reverse(names);
        return String.join("/", names);
    }

    private String requiredSingle(Map<String, List<String>> values, String option) {
        String value = optionalSingle(values, option);
        if (value == null || value.isBlank()) throw usageError("缺少必要參數 " + option + "。");
        return value;
    }

    private String optionalSingle(Map<String, List<String>> values, String option) {
        List<String> found = values.getOrDefault(option, List.of());
        if (found.size() > 1) throw usageError(option + " 不可重複指定。");
        return found.isEmpty() ? null : found.get(0);
    }

    private void validateEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\") || Path.of(name).normalize().startsWith("..")) {
            throw new CliException("HEADLESS_GRPC_ARCHIVE_PATH_INVALID", "ZIP 包含不合法的檔案路徑。", false);
        }
    }

    private String normalizeFolder(String value) {
        String normalized = value == null ? "/" : value.trim();
        if (normalized.isEmpty() || "/".equals(normalized)) return "/";
        return normalized.replaceAll("^/+|/+$", "");
    }

    private String sanitizeFilename(String value) {
        String name = value == null || value.isBlank() ? "schema.proto" : Path.of(value).getFileName().toString();
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String text(ObjectNode value, String field) { return text(value, field, ""); }
    private String text(ObjectNode value, String field, String fallback) { return value.path(field).isMissingNode() || value.path(field).isNull() ? fallback : value.path(field).asText(fallback); }
    private int integer(ObjectNode value, String field, int fallback) {
        try { return Integer.parseInt(text(value, field, String.valueOf(fallback))); }
        catch (NumberFormatException exception) { throw new CliException("HEADLESS_GRPC_PORT_INVALID", "gRPC Port 必須是整數。", false); }
    }
    private boolean booleanValue(ObjectNode value, String field, boolean fallback) { return value.path(field).isMissingNode() ? fallback : value.path(field).asBoolean(fallback); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private CliException usageError(String message) { return new CliException("HEADLESS_GRPC_CLI_USAGE", message, false); }
    private String errorCode(Exception exception) { return exception instanceof ApiException api ? api.getCode() : ((CliException) exception).code(); }
    private void writeError(String code, String message) {
        try { System.err.println(objectMapper.writeValueAsString(Map.of("code", code, "message", nullToEmpty(message)))); }
        catch (Exception ignored) { System.err.println("{\"code\":\"HEADLESS_GRPC_CLI_INTERNAL\"}"); }
    }
    private void writeOutput(Path output, String value) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(output, value + System.lineSeparator(), StandardCharsets.UTF_8);
    }
    private ObjectNode responseOutput(JsonNode request, GrpcExecuteResponse response) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("request", request.path("name").asText());
        result.put("protocol", "GRPC");
        result.put("statusCode", nullToEmpty(response.statusCode()));
        result.put("statusDescription", nullToEmpty(response.statusDescription()));
        if (response.durationMillis() == null) result.putNull("durationMillis"); else result.put("durationMillis", response.durationMillis());
        result.set("metadata", objectMapper.valueToTree(response.metadata() == null ? List.of() : response.metadata()));
        result.put("body", nullToEmpty(response.body()));
        result.put("errorMessage", nullToEmpty(response.errorMessage()));
        return result;
    }
    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (var stream = Files.walk(directory)) { stream.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
    private String usage() { return "Usage: java -jar post-bubi.jar run-grpc --archive <zip> --request <name> [--collection <name>] [--folder <path>] [--environment <name>] [--var key=value] [--output <path>]"; }

    private record CliArguments(Path archive, String request, String collection, String folder, String environment, Map<String, String> overrides, Path output, boolean help) { }
    private record Archive(JsonNode root, Map<String, byte[]> entries) { }
    private static final class CliException extends RuntimeException {
        private final String code;
        private CliException(String code, String message, boolean internal) { super(message); this.code = code; }
        private String code() { return code; }
    }
}
