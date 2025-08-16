package com.ravichanderjha.file_management.controller;

import com.ravichanderjha.file_management.service.FileOrganizerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileMgmtController {

    private final FileOrganizerService fileOrganizerService;

    public FileMgmtController(FileOrganizerService fileOrganizerService) {
        this.fileOrganizerService = fileOrganizerService;
    }

    @PostMapping("/organize")
    public ResponseEntity<String> organizeFiles(@RequestBody Map<String, String> request) {
        String pathStr = request.get("filePath");

        if (pathStr == null || pathStr.isBlank()) {
            return ResponseEntity.badRequest().body("filePath is required");
        }

        try {
            fileOrganizerService.organizeFiles(pathStr);
            return ResponseEntity.ok("Files organized successfully.");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
