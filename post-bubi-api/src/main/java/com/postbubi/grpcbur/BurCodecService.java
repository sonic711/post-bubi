package com.postbubi.grpcbur;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.AStar.TBConvert.BUR.ConvertBUR_UTF8;
import com.AStar.TBConvert.BUR.TB_BUR_UCS2;
import com.AStar.TBConvert.UCS2.TB_UCS2_BUR;
import com.AStar.TBConvert.UTF8.ConvertUTF8_BUR;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.web.error.ApiException;

@Service
public class BurCodecService {

    private static final String EMBEDDED_CODE_TABLE_ROOT = "bur/CodeTable/";
    private static final List<String> REQUIRED_CODE_TABLES = List.of("TB_UCS2_BUR.bin", "TB_BUR_UCS2.bin");

    private final Path codeTableDir;
    private TB_UCS2_BUR utf8ToBurTable;
    private TB_BUR_UCS2 burToUtf8Table;

    public BurCodecService(@Value("${post-bubi.bur.code-table-dir:./data/CodeTable}") String codeTableDir) {
        this.codeTableDir = resolveCodeTableDir(codeTableDir);
    }

    @PostConstruct
    void loadTables() {
        Path activeCodeTableDir = resolveActiveCodeTableDir();
        Path utf8ToBur = requiredCodeTable(activeCodeTableDir, "TB_UCS2_BUR.bin");
        Path burToUtf8 = requiredCodeTable(activeCodeTableDir, "TB_BUR_UCS2.bin");
        utf8ToBurTable = new TB_UCS2_BUR();
        burToUtf8Table = new TB_BUR_UCS2();
        long utf8LoadResult = utf8ToBurTable.load(utf8ToBur.toString());
        long burLoadResult = burToUtf8Table.load(burToUtf8.toString());
        if (utf8LoadResult != 0 || burLoadResult != 0) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GRPC_BUR_CODE_TABLE_LOAD_FAILED",
                    "BUR CodeTable 載入失敗。",
                    java.util.Map.of(
                            "codeTableDir", activeCodeTableDir.toString(),
                            "utf8ToBurResult", utf8LoadResult,
                            "burToUtf8Result", burLoadResult
                    )
            );
        }
    }

    public String codecName() {
        return "TBConvert BUR CodeTable";
    }

    public byte[] encode(String value) {
        byte[] rawData = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (rawData.length == 0) {
            return rawData;
        }
        ConvertUTF8_BUR converter = new ConvertUTF8_BUR();
        long result = converter.convert(utf8ToBurTable, rawData, rawData.length);
        if (result != 0) {
            throw conversionFailed("UTF-8 轉 BUR 失敗。", result);
        }
        return converter.getResult();
    }

    public String decode(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        ConvertBUR_UTF8 converter = new ConvertBUR_UTF8();
        long result = converter.convert(burToUtf8Table, value, value.length);
        if (result != 0) {
            throw conversionFailed("BUR 轉 UTF-8 失敗。", result);
        }
        return new String(converter.getResult(), StandardCharsets.UTF_8);
    }

    private Path resolveActiveCodeTableDir() {
        if (hasRequiredCodeTables(codeTableDir)) {
            return codeTableDir;
        }
        return extractEmbeddedCodeTables();
    }

    private boolean hasRequiredCodeTables(Path dir) {
        return Files.isDirectory(dir) && REQUIRED_CODE_TABLES.stream()
                .allMatch(filename -> Files.isRegularFile(dir.resolve(filename).normalize()));
    }

    private Path extractEmbeddedCodeTables() {
        try {
            Path tempDir = Files.createTempDirectory("post-bubi-code-table-").toAbsolutePath().normalize();
            tempDir.toFile().deleteOnExit();
            for (String filename : REQUIRED_CODE_TABLES) {
                Path target = tempDir.resolve(filename).normalize();
                try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(EMBEDDED_CODE_TABLE_ROOT + filename)) {
                    if (inputStream == null) {
                        throw missingCodeTable(filename);
                    }
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                    target.toFile().deleteOnExit();
                }
            }
            return tempDir;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GRPC_BUR_CODE_TABLE_EXTRACT_FAILED",
                    "JAR 內建 BUR CodeTable 解壓縮失敗。",
                    java.util.Map.of("reason", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
            );
        }
    }

    private Path requiredCodeTable(Path dir, String filename) {
        Path file = dir.resolve(filename).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw missingCodeTable(filename);
        }
        return file;
    }

    private ApiException missingCodeTable(String filename) {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "GRPC_BUR_CODE_TABLE_MISSING",
                "缺少 BUR CodeTable 檔案：" + filename,
                java.util.Map.of(
                        "externalCodeTableDir", codeTableDir.toString(),
                        "embeddedResource", EMBEDDED_CODE_TABLE_ROOT + filename
                )
        );
    }

    private Path resolveCodeTableDir(String value) {
        Path configured = Path.of(value);
        Path absolute = configured.toAbsolutePath().normalize();
        if (Files.isDirectory(absolute) || configured.isAbsolute()) {
            return absolute;
        }
        Path parentRelative = Path.of("..").resolve(configured).toAbsolutePath().normalize();
        if (Files.isDirectory(parentRelative)) {
            return parentRelative;
        }
        return absolute;
    }

    private ApiException conversionFailed(String message, long result) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "GRPC_BUR_CONVERT_FAILED",
                message,
                java.util.Map.of("result", result)
        );
    }
}
