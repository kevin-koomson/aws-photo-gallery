package com.kevo.PhotoGallery.controller;

import com.kevo.PhotoGallery.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


@Controller
@RequestMapping("/gallery")
public class GalleryController {

    @Autowired
    private S3Service s3Service;

    private static final List<String> ALLOWED_PHOTO_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", "image/webp"
    );

    /**
     * Display the gallery dashboard
     */
    @GetMapping
    public String gallery(Model model) {
        try {
            List<String> files = s3Service.listFiles();
            // Filter only image files
            List<String> imageFiles = files.stream()
                    .filter(this::isImageFile)
                    .toList();

            model.addAttribute("images", imageFiles);
            model.addAttribute("totalImages", imageFiles.size());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load gallery: " + e.getMessage());
            model.addAttribute("images", List.of());
            model.addAttribute("totalImages", 0);
        }
        return "gallery";
    }

    /**
     * Handle file upload
     */
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/gallery";
            }

            // Check if file is a photo
            if (!isPhotoFile(file)) {
                redirectAttributes.addFlashAttribute("error",
                        "Only image files are allowed (JPEG, PNG, GIF, BMP, WebP)");
                return "redirect:/gallery";
            }

            // Check file size (limit to 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                redirectAttributes.addFlashAttribute("error",
                        "File size must be less than 10MB");
                return "redirect:/gallery";
            }

            String fileName = s3Service.uploadFile(file);
            redirectAttributes.addFlashAttribute("success",
                    "Image uploaded successfully: " + fileName);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to upload image: " + e.getMessage());
        }
        return "redirect:/gallery";
    }

    /**
     * Download/view image
     */
    @GetMapping("/image/{fileName}")
    public ResponseEntity<InputStreamResource> viewImage(@PathVariable String fileName) {
        try {
            if (!s3Service.fileExists(fileName)) {
                return ResponseEntity.notFound().build();
            }

            ResponseInputStream<GetObjectResponse> s3Object = s3Service.downloadFile(fileName);
            GetObjectResponse objectResponse = s3Object.response();

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, objectResponse.contentType());
            headers.add(HttpHeaders.CACHE_CONTROL, "max-age=3600");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(objectResponse.contentLength())
                    .body(new InputStreamResource(s3Object));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Delete image
     */
    @PostMapping("/delete/{fileName}")
    public String deleteImage(@PathVariable String fileName,
                              RedirectAttributes redirectAttributes) {
        try {
            if (!s3Service.fileExists(fileName)) {
                redirectAttributes.addFlashAttribute("error", "Image not found");
                return "redirect:/gallery";
            }

            s3Service.deleteFile(fileName);
            redirectAttributes.addFlashAttribute("success",
                    "Image deleted successfully: " + fileName);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to delete image: " + e.getMessage());
        }
        return "redirect:/gallery";
    }

    /**
     * Get thumbnail - returns smaller version for gallery display
     */
    @GetMapping("/thumbnail/{fileName}")
    public ResponseEntity<InputStreamResource> getThumbnail(@PathVariable String fileName) {
        // For simplicity, we'll return the original image
        // In production, you might want to generate actual thumbnails
        return viewImage(fileName);
    }

    /**
     * Check if file is a photo based on content type
     */
    private boolean isPhotoFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && ALLOWED_PHOTO_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * Check if filename suggests it's an image file
     */
    private boolean isImageFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".jpg") ||
                lowerFileName.endsWith(".jpeg") ||
                lowerFileName.endsWith(".png") ||
                lowerFileName.endsWith(".gif") ||
                lowerFileName.endsWith(".bmp") ||
                lowerFileName.endsWith(".webp");
    }
}