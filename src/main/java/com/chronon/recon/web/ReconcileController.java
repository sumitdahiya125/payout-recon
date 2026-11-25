package com.chronon.recon.web;

import com.chronon.recon.exception.RunNotFoundException;
import com.chronon.recon.exception.ValidationException;
import com.chronon.recon.service.ReconciliationResult;
import com.chronon.recon.service.ReconciliationService;
import com.chronon.recon.service.RunStore;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/** The finance user's single entry point: upload two files, get a live summary and download the report. */
@Controller
public class ReconcileController {

    private final ReconciliationService service;
    private final RunStore runStore;

    public ReconcileController(ReconciliationService service, RunStore runStore) {
        this.service = service;
        this.runStore = runStore;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/reconcile")
    public String reconcile(@RequestParam("payoutFile") MultipartFile payoutFile,
                            @RequestParam("bankFile") MultipartFile bankFile,
                            RedirectAttributes redirectAttributes) throws IOException {

        validate(payoutFile, "payout register");
        validate(bankFile, "bank statement");

        ReconciliationResult result = service.reconcile(
                payoutFile.getBytes(), safeName(payoutFile),
                bankFile.getBytes(), safeName(bankFile));

        byte[] report = service.report(result);
        runStore.put(new RunStore.StoredRun(result, report, Instant.now()));

        redirectAttributes.addFlashAttribute("message",
                "Reconciliation finished in run " + result.runId() + ".");
        return "redirect:/runs/" + result.runId();
    }

    @GetMapping("/runs/{runId}")
    public String result(@PathVariable String runId, Model model) {
        RunStore.StoredRun run = runStore.get(runId)
                .orElseThrow(() -> new RunNotFoundException(runId));
        model.addAttribute("run", run.result());
        model.addAttribute("summary", run.result().summary());
        return "result";
    }

    @GetMapping("/runs/{runId}/report.xlsx")
    public ResponseEntity<byte[]> download(@PathVariable String runId) {
        Optional<RunStore.StoredRun> run = runStore.get(runId);
        if (run.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] report = run.get().report();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("reconciliation-" + runId + ".xlsx")
                .build());
        headers.setContentLength(report.length);
        return new ResponseEntity<>(report, headers, org.springframework.http.HttpStatus.OK);
    }

    private void validate(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Please choose the " + label + " file.",
                    "Select an Excel file from your computer to proceed with reconciliation.");
        }
        String name = safeName(file).toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new ValidationException("The " + label + " must be an Excel file (.xlsx or .xls), but '"
                    + name + "' was uploaded.",
                    "Save the export as .xlsx (or .xls) and upload it again. CSV exports are not supported.");
        }
    }

    private String safeName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "upload.xlsx";
        }
        return org.springframework.util.StringUtils.cleanPath(name)
                .replaceAll("[\\\\/]", "_");
    }
}
