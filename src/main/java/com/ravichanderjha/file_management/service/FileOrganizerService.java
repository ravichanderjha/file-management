package com.ravichanderjha.file_management.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
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

                        // Timestamped name
                        String timestampedName = getTimestampedFilename(file.getFileName().toString(), modifiedTime);
                        Path targetDir = originalRoot.resolve(Paths.get(fileType, year, month, day));
                        Files.createDirectories(targetDir);

                        Path targetFile = targetDir.resolve(timestampedName);

                        if (!Files.exists(targetFile)) {
                            Files.move(file, targetFile);
                        } else {
                            // Compare checksums
                            String existingChecksum = computeChecksum(targetFile);
                            String newFileChecksum = computeChecksum(file);

                            if (existingChecksum.equals(newFileChecksum)) {
                                // Exact duplicate → move to duplicate
                                Path duplicateDir = duplicateRoot.resolve(Paths.get(fileType, year, month, day));
                                Files.createDirectories(duplicateDir);
                                Path duplicateTarget = duplicateDir.resolve(timestampedName);
                                Path finalDuplicatePath = getUniquePath(duplicateTarget);
                                Files.move(file, finalDuplicatePath);
                            } else {
                                // Same name but different file → rename and save in original
                                Path renamedTarget = getUniquePath(targetFile);
                                Files.move(file, renamedTarget);
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace(); // Replace with logger in production
                    }
                });
        deleteEmptyDirectories(sourcePath, originalRoot, duplicateRoot);
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

    private String computeChecksum(Path path) throws IOException {
        try (InputStream fis = Files.newInputStream(path);
             DigestInputStream dis = new DigestInputStream(fis, MessageDigest.getInstance("SHA-256"))) {

            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // Reading file through DigestInputStream
            }

            byte[] digest = dis.getMessageDigest().digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to compute checksum for " + path, e);
        }
    }
    private void deleteEmptyDirectories(Path root, Path... excludeDirs) throws IOException {
        Files.walk(root)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children first
                .filter(Files::isDirectory)
                .filter(dir -> {
                    for (Path exclude : excludeDirs) {
                        if (dir.equals(exclude)) return false;
                    }
                    return true;
                })
                .forEach(dir -> {
                    try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
                        if (!entries.iterator().hasNext()) {
                            Files.delete(dir);
                        }
                    } catch (IOException e) {
                        e.printStackTrace(); // or use a logger
                    }
                });
    }

}
