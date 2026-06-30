package com.postbubi.grpc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.proto.ProtoStorageService;
import com.postbubi.web.error.ApiException;

@Service
public class GrpcProtoDescriptorResolver {

    private static final Map<String, DescriptorProtos.FieldDescriptorProto.Type> SCALAR_TYPES = Map.ofEntries(
            Map.entry("double", DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE),
            Map.entry("float", DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT),
            Map.entry("int32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32),
            Map.entry("int64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64),
            Map.entry("uint32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32),
            Map.entry("uint64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64),
            Map.entry("sint32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32),
            Map.entry("sint64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64),
            Map.entry("fixed32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32),
            Map.entry("fixed64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64),
            Map.entry("sfixed32", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32),
            Map.entry("sfixed64", DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64),
            Map.entry("bool", DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL),
            Map.entry("string", DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING),
            Map.entry("bytes", DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES)
    );

    private final ProtoStorageService protoStorageService;

    public GrpcProtoDescriptorResolver(ProtoStorageService protoStorageService) {
        this.protoStorageService = protoStorageService;
    }

    public Descriptors.MethodDescriptor resolveMethod(String protoId, String serviceName, String methodName) {
        Path mainProto = protoStorageService.resolveProtoFile(protoId);
        ProtoBuildContext context = loadProtoGraph(mainProto);
        List<DescriptorProtos.FileDescriptorProto> protos = context.toFileDescriptorProtos();
        Map<String, DescriptorProtos.FileDescriptorProto> protoMap = new LinkedHashMap<>();
        for (DescriptorProtos.FileDescriptorProto proto : protos) {
            protoMap.put(proto.getName(), proto);
        }

        Map<String, Descriptors.FileDescriptor> built = new HashMap<>();
        for (DescriptorProtos.FileDescriptorProto proto : protos) {
            buildFileDescriptor(proto.getName(), protoMap, built);
        }

        for (Descriptors.FileDescriptor descriptor : built.values()) {
            Descriptors.ServiceDescriptor service = descriptor.findServiceByName(shortName(serviceName));
            if (service != null && service.getFullName().equals(serviceName)) {
                Descriptors.MethodDescriptor method = service.findMethodByName(methodName);
                if (method == null) {
                    throw badRequest("GRPC_METHOD_NOT_FOUND", "找不到指定的 gRPC method。");
                }
                if (method.isClientStreaming() || method.isServerStreaming()) {
                    throw badRequest("GRPC_METHOD_NOT_UNARY", "目前只支援 unary gRPC method。");
                }
                return method;
            }
        }

        throw badRequest("GRPC_SERVICE_NOT_FOUND", "找不到指定的 gRPC service。");
    }

    private ProtoBuildContext loadProtoGraph(Path mainProto) {
        ProtoBuildContext context = new ProtoBuildContext();
        loadProto(mainProto, mainProto.getFileName().toString(), context);
        context.indexTypes();
        return context;
    }

    private void loadProto(Path file, String protoName, ProtoBuildContext context) {
        Path normalized = file.toAbsolutePath().normalize();
        if (context.filesByPath.containsKey(normalized)) {
            return;
        }
        String content;
        try {
            content = Files.readString(normalized, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRPC_PROTO_READ_FAILED",
                    "gRPC proto 檔讀取失敗。",
                    Map.of("path", normalized.toString(), "reason", exception.getMessage())
            );
        }

        ProtoFile parsed = new ProtoParser(protoName, content).parse();
        context.filesByPath.put(normalized, parsed);
        context.filesByName.put(parsed.name, parsed);
        for (String importName : parsed.imports) {
            Path dependency = resolveImport(normalized.getParent(), importName);
            loadProto(dependency, importName, context);
        }
    }

    private Path resolveImport(Path currentDir, String importName) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(currentDir.resolve(importName));
        candidates.add(protoStorageService.protosDir().resolve(importName));
        candidates.add(Path.of("data").resolve(importName));
        candidates.add(Path.of("data/proto").resolve(importName));
        if (importName.startsWith("proto/")) {
            candidates.add(Path.of("data").resolve(importName));
            candidates.add(Path.of("data").resolve(importName.substring("proto/".length())));
            candidates.add(Path.of("data/proto").resolve(importName.substring("proto/".length())));
        }
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw badRequest("GRPC_PROTO_IMPORT_NOT_FOUND", "找不到 gRPC proto 相依檔：" + importName);
    }

    private Descriptors.FileDescriptor buildFileDescriptor(
            String name,
            Map<String, DescriptorProtos.FileDescriptorProto> protos,
            Map<String, Descriptors.FileDescriptor> built
    ) {
        if (built.containsKey(name)) {
            return built.get(name);
        }
        DescriptorProtos.FileDescriptorProto proto = protos.get(name);
        if (proto == null) {
            throw badRequest("GRPC_DESCRIPTOR_DEPENDENCY_MISSING", "gRPC descriptor 缺少相依 proto：" + name);
        }
        Descriptors.FileDescriptor[] dependencies = proto.getDependencyList().stream()
                .map(dependency -> buildFileDescriptor(dependency, protos, built))
                .toArray(Descriptors.FileDescriptor[]::new);
        try {
            Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(proto, dependencies);
            built.put(name, descriptor);
            return descriptor;
        } catch (Descriptors.DescriptorValidationException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRPC_DESCRIPTOR_INVALID",
                    "gRPC descriptor 驗證失敗。",
                    Map.of("reason", exception.getMessage())
            );
        }
    }

    private String shortName(String fullName) {
        int index = fullName.lastIndexOf('.');
        return index < 0 ? fullName : fullName.substring(index + 1);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static final class ProtoBuildContext {
        private final Map<Path, ProtoFile> filesByPath = new LinkedHashMap<>();
        private final Map<String, ProtoFile> filesByName = new LinkedHashMap<>();
        private final Set<String> messageTypes = new HashSet<>();
        private final Set<String> enumTypes = new HashSet<>();

        private void indexTypes() {
            for (ProtoFile file : filesByName.values()) {
                for (ProtoMessage message : file.messages) {
                    indexMessage(file.packageName, "", message);
                }
                for (ProtoEnum protoEnum : file.enums) {
                    enumTypes.add(fullName(file.packageName, "", protoEnum.name));
                }
            }
        }

        private void indexMessage(String packageName, String parentName, ProtoMessage message) {
            String messageName = fullName(packageName, parentName, message.name);
            messageTypes.add(messageName);
            for (ProtoMessage nested : message.messages) {
                indexMessage(packageName, parentName.isEmpty() ? message.name : parentName + "." + message.name, nested);
            }
            for (ProtoEnum protoEnum : message.enums) {
                enumTypes.add(messageName + "." + protoEnum.name);
            }
        }

        private List<DescriptorProtos.FileDescriptorProto> toFileDescriptorProtos() {
            return filesByName.values().stream()
                    .map(file -> file.toDescriptor(this))
                    .toList();
        }

        private String resolveType(String packageName, String scopeName, String typeName) {
            String normalized = typeName.startsWith(".") ? typeName.substring(1) : typeName;
            List<String> candidates = new ArrayList<>();
            if (!scopeName.isEmpty()) {
                String scope = scopeName;
                while (!scope.isEmpty()) {
                    candidates.add(fullName(packageName, scope, normalized));
                    int index = scope.lastIndexOf('.');
                    scope = index < 0 ? "" : scope.substring(0, index);
                }
            }
            candidates.add(fullName(packageName, "", normalized));
            candidates.add(normalized);
            for (String candidate : candidates) {
                if (messageTypes.contains(candidate) || enumTypes.contains(candidate)) {
                    return "." + candidate;
                }
            }
            List<String> suffixMatches = new ArrayList<>();
            for (String known : messageTypes) {
                if (known.endsWith("." + normalized) || known.equals(normalized)) {
                    suffixMatches.add(known);
                }
            }
            for (String known : enumTypes) {
                if (known.endsWith("." + normalized) || known.equals(normalized)) {
                    suffixMatches.add(known);
                }
            }
            if (suffixMatches.size() == 1) {
                return "." + suffixMatches.get(0);
            }
            return "." + normalized;
        }

        private boolean isEnum(String typeName) {
            return enumTypes.contains(typeName.startsWith(".") ? typeName.substring(1) : typeName);
        }

        private static String fullName(String packageName, String parentName, String name) {
            String prefix = packageName == null || packageName.isBlank() ? "" : packageName;
            if (parentName != null && !parentName.isBlank()) {
                prefix = prefix.isBlank() ? parentName : prefix + "." + parentName;
            }
            return prefix.isBlank() ? name : prefix + "." + name;
        }
    }

    private record ProtoFile(
            String name,
            String packageName,
            List<String> imports,
            List<ProtoMessage> messages,
            List<ProtoEnum> enums,
            List<ProtoService> services
    ) {
        private DescriptorProtos.FileDescriptorProto toDescriptor(ProtoBuildContext context) {
            DescriptorProtos.FileDescriptorProto.Builder builder = DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName(name)
                    .setSyntax("proto3");
            if (!packageName.isBlank()) {
                builder.setPackage(packageName);
            }
            imports.forEach(builder::addDependency);
            messages.forEach(message -> builder.addMessageType(message.toDescriptor(context, packageName, "")));
            enums.forEach(protoEnum -> builder.addEnumType(protoEnum.toDescriptor()));
            services.forEach(service -> builder.addService(service.toDescriptor(context, packageName)));
            return builder.build();
        }
    }

    private record ProtoMessage(
            String name,
            List<ProtoField> fields,
            List<ProtoMessage> messages,
            List<ProtoEnum> enums
    ) {
        private DescriptorProtos.DescriptorProto toDescriptor(
                ProtoBuildContext context,
                String packageName,
                String parentName
        ) {
            DescriptorProtos.DescriptorProto.Builder builder = DescriptorProtos.DescriptorProto.newBuilder().setName(name);
            String currentScope = parentName.isBlank() ? name : parentName + "." + name;
            for (ProtoField field : fields) {
                if (field.mapValueType != null) {
                    String entryName = mapEntryName(field.name);
                    ProtoMessage entry = new ProtoMessage(
                            entryName,
                            List.of(
                                    new ProtoField("key", field.type, null, false, 1),
                                    new ProtoField("value", field.mapValueType, null, false, 2)
                            ),
                            List.of(),
                            List.of()
                    );
                    builder.addNestedType(entry.toDescriptor(context, packageName, currentScope).toBuilder()
                            .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true))
                            .build());
                    builder.addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName(field.name)
                            .setNumber(field.number)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName("." + ProtoBuildContext.fullName(packageName, currentScope, entryName))
                            .build());
                } else {
                    builder.addField(field.toDescriptor(context, packageName, currentScope));
                }
            }
            messages.forEach(message -> builder.addNestedType(message.toDescriptor(context, packageName, currentScope)));
            enums.forEach(protoEnum -> builder.addEnumType(protoEnum.toDescriptor()));
            return builder.build();
        }

        private static String mapEntryName(String fieldName) {
            StringBuilder result = new StringBuilder();
            boolean upper = true;
            for (char character : fieldName.toCharArray()) {
                if (character == '_') {
                    upper = true;
                } else if (upper) {
                    result.append(Character.toUpperCase(character));
                    upper = false;
                } else {
                    result.append(character);
                }
            }
            result.append("Entry");
            return result.toString();
        }
    }

    private record ProtoField(String name, String type, String mapValueType, boolean repeated, int number) {
        private DescriptorProtos.FieldDescriptorProto toDescriptor(
                ProtoBuildContext context,
                String packageName,
                String scopeName
        ) {
            DescriptorProtos.FieldDescriptorProto.Builder builder = DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(name)
                    .setNumber(number)
                    .setLabel(repeated
                            ? DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
                            : DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
            DescriptorProtos.FieldDescriptorProto.Type scalarType = SCALAR_TYPES.get(type);
            if (scalarType != null) {
                builder.setType(scalarType);
            } else {
                String resolvedType = context.resolveType(packageName, scopeName, type);
                builder.setType(context.isEnum(resolvedType)
                        ? DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM
                        : DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE);
                builder.setTypeName(resolvedType);
            }
            return builder.build();
        }
    }

    private record ProtoEnum(String name, List<ProtoEnumValue> values) {
        private DescriptorProtos.EnumDescriptorProto toDescriptor() {
            DescriptorProtos.EnumDescriptorProto.Builder builder = DescriptorProtos.EnumDescriptorProto.newBuilder()
                    .setName(name);
            if (values.isEmpty()) {
                builder.addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName(name + "_UNSPECIFIED").setNumber(0));
            } else {
                values.forEach(value -> builder.addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder()
                        .setName(value.name)
                        .setNumber(value.number)));
            }
            return builder.build();
        }
    }

    private record ProtoEnumValue(String name, int number) {
    }

    private record ProtoService(String name, List<ProtoRpc> methods) {
        private DescriptorProtos.ServiceDescriptorProto toDescriptor(ProtoBuildContext context, String packageName) {
            DescriptorProtos.ServiceDescriptorProto.Builder builder = DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setName(name);
            methods.forEach(method -> builder.addMethod(method.toDescriptor(context, packageName)));
            return builder.build();
        }
    }

    private record ProtoRpc(String name, String inputType, String outputType, boolean clientStreaming, boolean serverStreaming) {
        private DescriptorProtos.MethodDescriptorProto toDescriptor(ProtoBuildContext context, String packageName) {
            return DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setName(name)
                    .setInputType(context.resolveType(packageName, "", inputType))
                    .setOutputType(context.resolveType(packageName, "", outputType))
                    .setClientStreaming(clientStreaming)
                    .setServerStreaming(serverStreaming)
                    .build();
        }
    }

    private static final class ProtoParser {
        private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
        private static final Pattern LINE_COMMENT = Pattern.compile("(?m)//.*$");

        private final String name;
        private final List<String> tokens;
        private int index;

        private ProtoParser(String name, String content) {
            this.name = name;
            this.tokens = tokenize(stripComments(content));
        }

        private ProtoFile parse() {
            String packageName = "";
            List<String> imports = new ArrayList<>();
            List<ProtoMessage> messages = new ArrayList<>();
            List<ProtoEnum> enums = new ArrayList<>();
            List<ProtoService> services = new ArrayList<>();
            while (hasNext()) {
                if (accept("syntax")) {
                    skipUntil(";");
                } else if (accept("package")) {
                    packageName = readQualifiedName();
                    expect(";");
                } else if (accept("import")) {
                    if (peek("public") || peek("weak")) {
                        next();
                    }
                    imports.add(readString());
                    expect(";");
                } else if (accept("option")) {
                    skipUntil(";");
                } else if (accept("message")) {
                    messages.add(parseMessage());
                } else if (accept("enum")) {
                    enums.add(parseEnum());
                } else if (accept("service")) {
                    services.add(parseService());
                } else {
                    next();
                }
            }
            return new ProtoFile(name, packageName, imports, messages, enums, services);
        }

        private ProtoMessage parseMessage() {
            String messageName = nextIdentifier();
            expect("{");
            List<ProtoField> fields = new ArrayList<>();
            List<ProtoMessage> messages = new ArrayList<>();
            List<ProtoEnum> enums = new ArrayList<>();
            while (!accept("}")) {
                if (accept("message")) {
                    messages.add(parseMessage());
                } else if (accept("enum")) {
                    enums.add(parseEnum());
                } else if (accept("option") || accept("reserved") || accept("extensions")) {
                    skipUntil(";");
                } else if (accept("oneof")) {
                    fields.addAll(parseOneOfFields());
                } else if (hasNext()) {
                    Optional<ProtoField> field = parseField();
                    field.ifPresent(fields::add);
                } else {
                    break;
                }
            }
            return new ProtoMessage(messageName, fields, messages, enums);
        }

        private List<ProtoField> parseOneOfFields() {
            nextIdentifier();
            expect("{");
            List<ProtoField> fields = new ArrayList<>();
            while (!accept("}")) {
                Optional<ProtoField> field = parseField();
                field.ifPresent(fields::add);
            }
            return fields;
        }

        private Optional<ProtoField> parseField() {
            boolean repeated = accept("repeated");
            if (accept("map")) {
                expect("<");
                String keyType = readQualifiedName();
                expect(",");
                String valueType = readQualifiedName();
                expect(">");
                String fieldName = nextIdentifier();
                expect("=");
                int number = readNumber();
                skipFieldTail();
                return Optional.of(new ProtoField(fieldName, keyType, valueType, true, number));
            }

            String type = readQualifiedName();
            if (!hasNext() || peek(";") || peek("}")) {
                skipFieldTail();
                return Optional.empty();
            }
            String fieldName = nextIdentifier();
            if (!accept("=")) {
                skipFieldTail();
                return Optional.empty();
            }
            int number = readNumber();
            skipFieldTail();
            return Optional.of(new ProtoField(fieldName, type, null, repeated, number));
        }

        private ProtoEnum parseEnum() {
            String enumName = nextIdentifier();
            expect("{");
            List<ProtoEnumValue> values = new ArrayList<>();
            while (!accept("}")) {
                if (accept("option")) {
                    skipUntil(";");
                } else {
                    String valueName = nextIdentifier();
                    expect("=");
                    int number = readNumber();
                    skipFieldTail();
                    values.add(new ProtoEnumValue(valueName, number));
                }
            }
            return new ProtoEnum(enumName, values);
        }

        private ProtoService parseService() {
            String serviceName = nextIdentifier();
            expect("{");
            List<ProtoRpc> methods = new ArrayList<>();
            while (!accept("}")) {
                if (accept("rpc")) {
                    methods.add(parseRpc());
                } else if (accept("option")) {
                    skipUntil(";");
                } else {
                    next();
                }
            }
            return new ProtoService(serviceName, methods);
        }

        private ProtoRpc parseRpc() {
            String methodName = nextIdentifier();
            expect("(");
            boolean clientStreaming = accept("stream");
            String inputType = readQualifiedName();
            expect(")");
            expect("returns");
            expect("(");
            boolean serverStreaming = accept("stream");
            String outputType = readQualifiedName();
            expect(")");
            if (accept("{")) {
                int depth = 1;
                while (hasNext() && depth > 0) {
                    if (accept("{")) {
                        depth++;
                    } else if (accept("}")) {
                        depth--;
                    } else {
                        next();
                    }
                }
            } else {
                expect(";");
            }
            return new ProtoRpc(methodName, inputType, outputType, clientStreaming, serverStreaming);
        }

        private void skipFieldTail() {
            int bracketDepth = 0;
            while (hasNext()) {
                if (accept("[")) {
                    bracketDepth++;
                } else if (accept("]")) {
                    bracketDepth--;
                } else if (bracketDepth == 0 && accept(";")) {
                    return;
                } else {
                    next();
                }
            }
        }

        private void skipUntil(String token) {
            while (hasNext() && !accept(token)) {
                next();
            }
        }

        private String readQualifiedName() {
            StringBuilder builder = new StringBuilder();
            if (accept(".")) {
                builder.append('.');
            }
            builder.append(nextIdentifier());
            while (accept(".")) {
                builder.append('.').append(nextIdentifier());
            }
            return builder.toString();
        }

        private String nextIdentifier() {
            String token = next();
            if (!token.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw parseError("預期 identifier，但收到：" + token);
            }
            return token;
        }

        private int readNumber() {
            String token = next();
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException exception) {
                throw parseError("預期欄位編號，但收到：" + token);
            }
        }

        private String readString() {
            String token = next();
            if (!token.startsWith("\"") || !token.endsWith("\"")) {
                throw parseError("預期字串，但收到：" + token);
            }
            return token.substring(1, token.length() - 1);
        }

        private void expect(String token) {
            if (!accept(token)) {
                throw parseError("預期 " + token + "，但收到：" + (hasNext() ? peek() : "EOF"));
            }
        }

        private boolean accept(String token) {
            if (peek(token)) {
                index++;
                return true;
            }
            return false;
        }

        private boolean peek(String token) {
            return hasNext() && tokens.get(index).equals(token);
        }

        private String peek() {
            return tokens.get(index);
        }

        private String next() {
            if (!hasNext()) {
                throw parseError("非預期的檔案結尾。");
            }
            return tokens.get(index++);
        }

        private boolean hasNext() {
            return index < tokens.size();
        }

        private ApiException parseError(String reason) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRPC_PROTO_PARSE_FAILED",
                    "gRPC proto 解析失敗。",
                    Map.of("proto", name, "reason", reason)
            );
        }

        private static String stripComments(String content) {
            return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(content).replaceAll("")).replaceAll("");
        }

        private static List<String> tokenize(String content) {
            List<String> result = new ArrayList<>();
            int position = 0;
            while (position < content.length()) {
                char current = content.charAt(position);
                if (Character.isWhitespace(current)) {
                    position++;
                } else if (current == '"') {
                    int end = position + 1;
                    while (end < content.length()) {
                        char value = content.charAt(end);
                        if (value == '\\') {
                            end += 2;
                        } else if (value == '"') {
                            end++;
                            break;
                        } else {
                            end++;
                        }
                    }
                    result.add(content.substring(position, Math.min(end, content.length())));
                    position = end;
                } else if ("{}()[]=;,.<>".indexOf(current) >= 0) {
                    result.add(String.valueOf(current));
                    position++;
                } else if (Character.isDigit(current) || current == '-') {
                    int end = position + 1;
                    while (end < content.length() && Character.isDigit(content.charAt(end))) {
                        end++;
                    }
                    result.add(content.substring(position, end));
                    position = end;
                } else {
                    int end = position + 1;
                    while (end < content.length()) {
                        char value = content.charAt(end);
                        if (Character.isLetterOrDigit(value) || value == '_') {
                            end++;
                        } else {
                            break;
                        }
                    }
                    result.add(content.substring(position, end));
                    position = end;
                }
            }
            return result;
        }
    }
}
