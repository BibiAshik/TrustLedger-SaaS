package com.trustledgersaas.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileUploadUtil {

    private static final String UPLOAD_DIR = "uploads/";

    /**
     * Saves an uploaded file to the local filesystem.
     *
     * @param file         The uploaded MultipartFile
     * @param subdirectory The target subdirectory (e.g., "shops/1/aadhaar")
     * @return The relative file path where the file was saved
     */
    public static String saveFile(MultipartFile file, String subdirectory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            // Create the target directory if it doesn't exist
            Path targetDir = Paths.get(UPLOAD_DIR + subdirectory);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Clean the filename (replace spaces with underscores, avoid path traversal)
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null) {
                originalFileName = "uploaded_file";
            }
            String fileName = System.currentTimeMillis() + "_" + originalFileName.replaceAll("[^a-zA-Z0-9.\\-]", "_");

            // Save the file
            Path targetPath = targetDir.resolve(fileName);
            Files.copy(file.getInputStream(), targetPath);

            log.info("Saved file successfully to: {}", targetPath);

            // Return the relative path to store in DB (e.g. "/uploads/shops/1/aadhaar/12345_file.jpg")
            return "/uploads/" + subdirectory + "/" + fileName;

        } catch (IOException e) {
            log.error("Failed to save file in directory: {}", subdirectory, e);
            throw new RuntimeException("Could not save uploaded file. Please try again.");
        }
    }
}
