package com.example.pptbackend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DocumentTextExtractionService {

    public String extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        byte[] bytes = file.getBytes();

        if (name.toLowerCase().endsWith(".pdf") || "application/pdf".equalsIgnoreCase(contentType)) {
            return extractPdf(bytes);
        }
        if (name.toLowerCase().endsWith(".docx")
            || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)) {
            return extractDocx(bytes);
        }
        if (name.toLowerCase().endsWith(".txt") || "text/plain".equalsIgnoreCase(contentType)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("不支持的文件类型，请上传 PDF、DOCX 或 TXT");
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("PDF 未解析出文本（可能是扫描件或加密文档）");
            }
            return text.trim();
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph p : paragraphs) {
                String line = p.getText();
                if (line != null && !line.isBlank()) {
                    builder.append(line.trim()).append('\n');
                }
            }
            String text = builder.toString().trim();
            if (text.isBlank()) {
                throw new IllegalStateException("DOCX 未解析出正文");
            }
            return text;
        }
    }
}
