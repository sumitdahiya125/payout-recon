package com.chronon.recon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Chronon Payout Reconciliation Web Application.
 *
 * <p>Finance users navigate to {@code http://localhost:8080} to upload their
 * payout register and bank statement, view live tie-out results, and download
 * the audit workbook.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ReconApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconApplication.class, args);
    }
}
