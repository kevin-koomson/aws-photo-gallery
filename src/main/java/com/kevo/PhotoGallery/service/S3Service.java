package com.kevo.PhotoGallery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Upload a file to S3 bucket
     */
    public String uploadFile(MultipartFile file) throws IOException {
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .metadata(java.util.Map.of(
                            "original-filename", file.getOriginalFilename(),
                            "upload-timestamp", String.valueOf(System.currentTimeMillis()),
                            "file-size", String.valueOf(file.getSize())
                    ))
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return fileName;
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * List all objects in the S3 bucket
     */
    public List<String> listFiles() {
        try {
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);

            return listObjectsResponse.contents()
                    .stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to list files from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Download a file from S3 bucket
     */
    public ResponseInputStream<GetObjectResponse> downloadFile(String fileName) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            return s3Client.getObject(getObjectRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file from S3 bucket
     */
    public void deleteFile(String fileName) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * List images with pagination
     */
    public PaginatedImages listImagesPaginated(int page, int size) {
        try {
            // Get all objects first (S3 doesn't support true pagination, so we implement it manually)
            List<String> allFiles = listFiles();

            // Filter only image files
            List<String> imageFiles = allFiles.stream()
                    .filter(this::isImageFile)
                    .sorted() // Sort for consistent ordering
                    .collect(Collectors.toList());

            int totalImages = imageFiles.size();
            int totalPages = (int) Math.ceil((double) totalImages / size);

            // Calculate pagination boundaries
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalImages);

            // Get the subset for current page
            List<String> pageImages = imageFiles.subList(startIndex, endIndex);

            return new PaginatedImages(
                    pageImages,
                    totalImages,
                    page,
                    size,
                    totalPages,
                    page < totalPages - 1, // hasNext
                    page > 0 // hasPrevious
            );

        } catch (S3Exception e) {
            throw new RuntimeException("Failed to list files from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Check if file exists in S3 bucket
     */
    public boolean fileDoesNotExist(String fileName) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.headObject(headObjectRequest);
            return false;
        } catch (NoSuchKeyException e) {
            return true;
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to check if file exists in S3: " + e.getMessage(), e);
        }
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