package com.ravichanderjha.file_management.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class FileOrganizerService {

    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public void organizeFiles(String inputPath) throws IOException {
        Path sourcePath = Paths.get(inputPath);

        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Invalid path: " + inputPath);
        }

        Path originalRoot = sourcePath.resolve("original");
        Path duplicateRoot = sourcePath.resolve("duplicate");

        Files.createDirectories(originalRoot);
        Files.createDirectories(duplicateRoot);

        Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(file -> !file.startsWith(originalRoot) && !file.startsWith(duplicateRoot))
                .forEach(file -> {
                    try {
                        String fileType = getFileExtension(file.getFileName().toString());

                        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                        LocalDateTime modifiedTime = attr.lastModifiedTime()
                                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

                        String year = String.valueOf(modifiedTime.getYear());
                        String month = String.format("%02d", modifiedTime.getMonthValue());
                        String day = String.format("%02d", modifiedTime.getDayOfMonth());

                        // Construct timestamped file name
                        String timestampedName = getTimestampedFilename(file.getFileName().toString(), modifiedTime);

                        // Original path
                        Path targetDir = originalRoot.resolve(Paths.get(fileType, year, month, day));
                        Files.createDirectories(targetDir);

                        Path targetFile = targetDir.resolve(timestampedName);

                        if (!Files.exists(targetFile)) {
                            Files.move(file, targetFile);
                        } else {
                            // Duplicate path
                            Path duplicateDir = duplicateRoot.resolve(Paths.get(fileType, year, month, day));
                            Files.createDirectories(duplicateDir);

                            Path duplicateTarget = duplicateDir.resolve(timestampedName);
                            Path finalDuplicatePath = getUniquePath(duplicateTarget);
                            Files.move(file, finalDuplicatePath);
                        }

                    } catch (IOException e) {
                        e.printStackTrace(); // Replace with proper logging
                    }
                });
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf(".");
        return (dot > 0) ? filename.substring(dot + 1).toLowerCase() : "unknown";
    }

    private String getTimestampedFilename(String originalFilename, LocalDateTime modifiedTime) {
        String baseName = originalFilename;
        String extension = "";

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = originalFilename.substring(0, dotIndex);
            extension = originalFilename.substring(dotIndex); // includes the dot
        }

        String timestamp = modifiedTime.format(FILENAME_TIMESTAMP_FORMAT);
        return baseName + "_" + timestamp + extension;
    }

    private Path getUniquePath(Path originalPath) throws IOException {
        if (!Files.exists(originalPath)) {
            return originalPath;
        }

        String fileName = originalPath.getFileName().toString();
        String baseName = fileName;
        String extension = "";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex); // includes the dot
        }

        int counter = 1;
        Path newPath;
        do {
            newPath = originalPath.getParent().resolve(baseName + "_" + counter + extension);
            counter++;
        } while (Files.exists(newPath));

        return newPath;
    }
}
