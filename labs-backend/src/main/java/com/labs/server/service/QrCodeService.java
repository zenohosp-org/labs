package com.labs.server.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

/**
 * Tiny ZXing wrapper that renders a QR PNG and returns a data: URI suitable
 * for direct embedding into the report HTML template (no roundtrip to
 * storage). Error correction is HIGH so the QR survives 30% damage —
 * matters for printed reports that get folded / stamped.
 */
@Service
public class QrCodeService {

    public String dataUri(String payload, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    sizePx, sizePx,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                            EncodeHintType.MARGIN, 1
                    )
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("QR generation failed: " + e.getMessage(), e);
        }
    }
}
