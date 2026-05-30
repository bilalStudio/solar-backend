package com.wattvue.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class S3Service {

    @Value("${aws.s3.bucket-name:wattvue-docs}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    private S3Client getClient() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    private boolean isConfigured() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }

    // Upload photo from multipart file
    public String uploadFieldPhoto(MultipartFile file, Long documentId, String fieldKey) throws Exception {
        if (!isConfigured()) {
            log.warn("AWS S3 not configured — photo saved locally only");
            return "local://" + file.getOriginalFilename();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String ext = getExtension(file.getOriginalFilename());
        String key = String.format("field-docs/%d/%s_%s.%s", documentId, fieldKey, timestamp, ext);

        S3Client s3 = getClient();
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build(),
            RequestBody.fromBytes(file.getBytes())
        );

        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }

    // Upload PDF file
    public String uploadPdf(File file, String folder) throws Exception {
        if (!isConfigured()) {
            log.warn("AWS S3 not configured — PDF saved locally only");
            return "local://" + file.getName();
        }

        String key = String.format("%s/%s", folder, file.getName());

        S3Client s3 = getClient();
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/pdf")
                .build(),
            RequestBody.fromFile(file)
        );

        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }

    private String getExtension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "jpg";
    }
}
