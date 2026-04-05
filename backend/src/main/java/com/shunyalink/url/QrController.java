package com.shunyalink.url;

import com.shunyalink.exception.BadRequestException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/url")
public class QrController {

    @Value("${app.base-url}")
    private String baseUrl;

    private final ResourceLoader resourceLoader;

    public QrController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Operation(summary = "Generate Branded QR Code", description = "Generates a high-quality QR code with a center-logo overlay. Uses Error Correction Level H to ensure scannability even with branding.")
    @ApiResponse(responseCode = "200", description = "PNG image of the branded QR code")
    @GetMapping(value = "/qr/{shortId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQr(
            @PathVariable String shortId,
            @RequestParam(defaultValue = "300") int size) {

        if (size < 100 || size > 1000) {
            throw new BadRequestException("Size must be between 100 and 1000");
        }

        String shortUrl = baseUrl + "/" + shortId;

        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN, 2
            );
            BitMatrix matrix = writer.encode(shortUrl, BarcodeFormat.QR_CODE, size, size, hints);

            // Convert BitMatrix to BufferedImage (this returns a binary image)
            BufferedImage binaryImage = MatrixToImageWriter.toBufferedImage(matrix);
            
            // Create a new full-color image and draw the binary QR on it
            BufferedImage qrImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = qrImage.createGraphics();
            g2.drawImage(binaryImage, 0, 0, null);
            g2.dispose();

            // Load and draw the logo in the center
            try {
                Resource resource = resourceLoader.getResource("classpath:qr-logo.png");
                if (resource.exists()) {
                    BufferedImage logo = ImageIO.read(resource.getInputStream());
                    
                    // Calculate logo size (approx 20-25% of QR size)
                    int logoSize = size / 4;
                    int x = (size - logoSize) / 2;
                    int y = (size - logoSize) / 2;

                    Graphics2D g = qrImage.createGraphics();
                    
                    // Smooth scaling for the logo
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    
                    // Draw a white background behind the logo for better visibility
                    g.setColor(Color.WHITE);
                    g.fillRoundRect(x - 2, y - 2, logoSize + 4, logoSize + 4, 10, 10);
                    
                    // Draw the logo
                    g.drawImage(logo, x, y, logoSize, logoSize, null);
                    g.dispose();
                }
            } catch (Exception e) {
                // If logo fails, we still return the plain QR code
                System.err.println("QR Logo Overlay failed: " + e.getMessage());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", out);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"qr-" + shortId + ".png\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(out.toByteArray());

        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}