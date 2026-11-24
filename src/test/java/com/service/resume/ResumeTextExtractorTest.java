package com.service.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import com.common.BizException;

/**
 * 简历文件抽取的纯单元测试（不依赖 Mockito，避免 inline mock maker 在受限环境里起不来）。
 */
class ResumeTextExtractorTest {

  private final ResumeTextExtractor extractor = new ResumeTextExtractor();

  @Test
  void extractsDocxParagraphsAndTableCells() throws Exception {
    byte[] bytes;
    try (XWPFDocument document = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph paragraph = document.createParagraph();
      paragraph.createRun().setText("张三 · Java 后端开发，5 年经验，主导过订单中台重构。");
      XWPFTable table = document.createTable(1, 2);
      table.getRow(0).getCell(0).setText("项目");
      table.getRow(0).getCell(1).setText("订单中台 QPS 1.2k 提升到 8k");
      document.write(out);
      bytes = out.toByteArray();
    }

    ResumeTextExtractor.Extracted extracted = extractor.extract("简历.docx", bytes);

    assertThat(extracted.fileType()).isEqualTo("DOCX");
    assertThat(extracted.text()).contains("张三").contains("订单中台");
  }

  @Test
  void extractsUtf8TextWithBom() {
    String content = "李四 · 后端开发，熟悉 Redis、MySQL 与消息队列，做过全链路压测。";
    byte[] body = content.getBytes(StandardCharsets.UTF_8);
    byte[] bytes = new byte[body.length + 3];
    bytes[0] = (byte) 0xEF;
    bytes[1] = (byte) 0xBB;
    bytes[2] = (byte) 0xBF;
    System.arraycopy(body, 0, bytes, 3, body.length);

    ResumeExtractedAssert.assertContains(extractor.extract("resume.txt", bytes), content);
  }

  @Test
  void fallsBackToGb18030ForLegacyTextFiles() {
    String content = "王五 · 高级后端工程师，负责支付网关的幂等与对账补偿逻辑设计。";
    byte[] bytes = content.getBytes(Charset.forName("GB18030"));

    ResumeExtractedAssert.assertContains(extractor.extract("resume.txt", bytes), content);
  }

  @Test
  void extractsPdfText() throws Exception {
    byte[] bytes;
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(PDType1Font.HELVETICA, 12);
        stream.newLineAtOffset(50, 700);
        stream.showText("Java backend engineer, 5 years, Redis and MySQL performance tuning.");
        stream.endText();
      }
      document.save(out);
      bytes = out.toByteArray();
    }

    ResumeTextExtractor.Extracted extracted = extractor.extract("resume.pdf", bytes);

    assertThat(extracted.fileType()).isEqualTo("PDF");
    assertThat(extracted.text()).contains("Java backend engineer");
  }

  @Test
  void rejectsUnsupportedFileType() {
    assertThatThrownBy(() -> extractor.extract("resume.png", new byte[] { 1, 2, 3, 4 }))
        .isInstanceOf(BizException.class);
  }

  @Test
  void rejectsEmptyFile() {
    assertThatThrownBy(() -> extractor.extract("resume.txt", new byte[0]))
        .isInstanceOf(BizException.class);
  }

  /** 小助手：断言抽取结果里包含原始文本。 */
  private static final class ResumeExtractedAssert {
    static void assertContains(ResumeTextExtractor.Extracted extracted, String expected) {
      assertThat(extracted.fileType()).isEqualTo("TXT");
      assertThat(extracted.text()).contains(expected);
    }
  }
}
