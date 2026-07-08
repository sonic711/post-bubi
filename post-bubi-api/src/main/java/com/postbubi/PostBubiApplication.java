package com.postbubi;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;

@SpringBootApplication
public class PostBubiApplication {

    public static void main(String[] args) {
        configureFileLogging(args);
        SpringApplication.run(PostBubiApplication.class, args);
    }

    private static void configureFileLogging(String[] args) {
        if (!fileLoggingEnabled(args) || hasLoggingFileName(args)) {
            return;
        }
        String configuredLogFile = firstText(
                argumentValue(args, "post-bubi.logging.file.name"),
                System.getProperty("post-bubi.logging.file.name"),
                System.getenv("POST_BUBI_LOGGING_FILE_NAME")
        );
        System.setProperty("logging.file.name", configuredLogFile != null ? configuredLogFile : defaultLogFile());
    }

    private static boolean fileLoggingEnabled(String[] args) {
        String value = firstText(
                argumentValue(args, "post-bubi.logging.file.enabled"),
                System.getProperty("post-bubi.logging.file.enabled"),
                System.getenv("POST_BUBI_LOGGING_FILE_ENABLED")
        );
        return value == null || !Set.of("false", "0", "off", "no").contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean hasLoggingFileName(String[] args) {
        return firstText(
                argumentValue(args, "logging.file.name"),
                System.getProperty("logging.file.name"),
                System.getenv("LOGGING_FILE_NAME")
        ) != null;
    }

    private static String defaultLogFile() {
        File directory = new ApplicationHome(PostBubiApplication.class).getDir();
        return new File(new File(directory, "logs"), "post-bubi.log").getAbsolutePath();
    }

    private static String argumentValue(String[] args, String name) {
        String prefix = "--" + name + "=";
        return Arrays.stream(args)
                .filter(argument -> argument.startsWith(prefix))
                .map(argument -> argument.substring(prefix.length()))
                .findFirst()
                .orElse(null);
    }

    private static String firstText(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
