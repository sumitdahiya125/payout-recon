package com.chronon.recon.service;

import com.chronon.recon.demo.SampleDataGenerator;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.Summary;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EndToEndReconciliationTest {

    @Autowired
    private ReconciliationService service;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reconcilesGeneratedDataWithExactExpectedOutcomes(@TempDir Path dir) throws Exception {
        SampleDataGenerator.Generated gen = new SampleDataGenerator().generate(dir, 20250301L);

        byte[] payoutBytes = Files.readAllBytes(gen.payoutFile());
        byte[] bankBytes = Files.readAllBytes(gen.bankFile());

        ReconciliationResult result = service.reconcile(
                payoutBytes, gen.payoutFile().getFileName().toString(),
                bankBytes, gen.bankFile().getFileName().toString());

        assertThat(result.payoutProfile().id()).isEqualTo("chronon-payout-register");
        assertThat(result.bankProfile().id()).isEqualTo("bank-generic-statement");

        Summary summary = result.summary();

        assertThat(summary.payoutCount()).isEqualTo(30);
        assertThat(summary.excludedPayoutCount()).isEqualTo(1);
        assertThat(summary.excludedPayoutTotal()).isEqualTo(Money.of("3000.00"));
        assertThat(summary.matchedCount()).isEqualTo(21);
        assertThat(summary.reviewCount()).isEqualTo(6);
        assertThat(summary.unmatchedPayoutCount()).isEqualTo(3);

        assertThat(summary.registerTotal()).isEqualTo(Money.of("270600.00"));
        assertThat(summary.matchedTotal()).isEqualTo(Money.of("175150.00"));
        assertThat(summary.reviewTotal()).isEqualTo(Money.of("81500.00"));
        assertThat(summary.unmatchedPayoutTotal()).isEqualTo(Money.of("13950.00"));

        assertThat(summary.bankLineCount()).isEqualTo(40);
        assertThat(summary.matchedBankLineCount()).isEqualTo(21);
        assertThat(summary.reviewBankLineCount()).isEqualTo(8);
        assertThat(summary.unmatchedDebitCount()).isEqualTo(1);
        assertThat(summary.nonPayoutCount()).isEqualTo(10);

        assertThat(summary.payoutResidual().amount()).isZero();
        assertThat(summary.bankResidual().amount()).isZero();
        assertThat(summary.tieOutOk()).isTrue();

        byte[] report = service.report(result);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheet("Summary & Tie-Out")).isNotNull();
            assertThat(workbook.getSheet("Payout Reconciliation")).isNotNull();
            assertThat(workbook.getSheet("Bank Statement Activity")).isNotNull();
            assertThat(workbook.getSheet("Data Quality Log")).isNotNull();
        }
    }

    @Test
    void webUiUploadReconcilesAndDownloadsReport(@TempDir Path dir) throws Exception {
        SampleDataGenerator.Generated gen = new SampleDataGenerator().generate(dir, 20250301L);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());

        MockMultipartFile payoutFile = new MockMultipartFile(
                "payoutFile", "payout_register.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(gen.payoutFile()));
        MockMultipartFile bankFile = new MockMultipartFile(
                "bankFile", "bank_statement.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(gen.bankFile()));

        String redirectUrl = mockMvc.perform(multipart("/reconcile")
                        .file(payoutFile)
                        .file(bankFile))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(redirectUrl).startsWith("/runs/RUN-");

        mockMvc.perform(get(redirectUrl))
                .andExpect(status().isOk());

        mockMvc.perform(get(redirectUrl + "/report.xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }
}
