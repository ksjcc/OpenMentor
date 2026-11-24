package com.service.resume;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import com.common.BizException;
import com.common.ErrorCode;

/**
 * 简历文件 → 纯文本。
 *
 * <p>
 * 只做"能力够用"的抽取：PDF 用 PDFBox 的默认抽取器，DOCX 用 POI 逐段/逐表拼接，
 * TXT 先按 UTF-8 严格解码、失败再退回 GB18030（国内用户常见）。
 * 扫描件（图片型 PDF）抽不出文字，这里会得到空串，由调用方给出明确提示。
 */
@Component
public class ResumeTextExtractor {

    private static final int MAX_CHARS = 200_000;
    private static final int MIN_MEANINGFUL_CHARS = 20;

    /** @param fileType 归一化后的来源类型：PDF / DOCX / TXT */
    public record Extracted(String text, String fileType) {
    }

    public Extracted extract(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传的文件是空的");
        }
        String fileType = resolveType(fileName, bytes);
        String text = switch (fileType) {
            case "PDF" -> readPdf(bytes);
            case "DOCX" -> readDocx(bytes);
            case "TXT" -> decodeText(bytes);
            default -> throw new BizException(ErrorCode.BAD_REQUEST,
                    "仅支持 PDF / DOCX / TXT 格式的简历");
        };
        String cleaned = clean(text);
        if (cleaned.length() < MIN_MEANINGFUL_CHARS) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "没能从文件里读出足够的文字。扫描件（图片 PDF）暂不支持，请上传文本版简历或直接粘贴文本。");
        }
        return new Extracted(cleaned, fileType);
    }

    // ==================== 格式识别 ====================

    private String resolveType(String fileName, byte[] bytes) {
        String ext = "";
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        switch (ext) {
            case "pdf":
                return "PDF";
            case "docx":
                return "DOCX";
            case "txt":
            case "md":
            case "text":
                return "TXT";
            default:
                break;
        }
        // 没有可识别扩展名时按魔数猜
        if (bytes.length > 4) {
            if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
                return "PDF";
            }
            if (bytes[0] == 'P' && bytes[1] == 'K') {
                return "DOCX";
            }
        }
        return "TXT";
    }

    // ==================== 各格式读取 ====================

    private String readPdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "PDF 解析失败：" + ex.getMessage() + "。可以换成 DOCX/TXT，或直接粘贴简历文本。");
        }
    }

    private String readDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendLine(text, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        StringBuilder rowText = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            if (rowText.length() > 0) {
                                rowText.append(" | ");
                            }
                            rowText.append(cell.getText());
                        }
                        appendLine(text, rowText.toString());
                    }
                }
            }
            return text.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "Word 解析失败：" + ex.getMessage() + "。老版 .doc 请另存为 .docx 后再上传。");
        }
    }

    private void appendLine(StringBuilder text, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        text.append(line.trim()).append('\n');
    }

    private String decodeText(byte[] bytes) {
        String utf8 = tryDecodeStrict(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        String gb18030 = tryDecodeStrict(bytes, Charset.forName("GB18030"));
        return gb18030 == null ? new String(bytes, StandardCharsets.UTF_8) : gb18030;
    }

    private String tryDecodeStrict(byte[] bytes, Charset charset) {
        try {
            String text = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return stripBom(text);
        } catch (CharacterCodingException ex) {
            return null;
        }
    }

    // ==================== 清洗 ====================

    private String stripBom(String text) {
        return text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF'
                ? text.substring(1)
                : text;
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
