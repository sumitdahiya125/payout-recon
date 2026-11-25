package com.chronon.recon.web;

import com.chronon.recon.exception.ColumnMappingException;
import com.chronon.recon.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesReconciliationException() {
        ValidationException ex = new ValidationException("File missing", "Select a valid file");
        ModelAndView mav = handler.handleReconciliationException(ex);

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getModel().get("message")).isEqualTo("File missing");
        assertThat(mav.getModel().get("hint")).isEqualTo("Select a valid file");
    }

    @Test
    void handlesColumnMappingException() {
        ColumnMappingException ex = new ColumnMappingException("Unknown columns");
        ModelAndView mav = handler.handleReconciliationException(ex);

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getModel().get("message")).isEqualTo("Unknown columns");
        assertThat(mav.getModel().get("hint")).isNotNull();
    }

    @Test
    void handlesNoResourceFoundException() {
        NoResourceFoundException ex = new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/favicon.ico");
        ResponseEntity<Void> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handlesUnexpectedException() {
        ModelAndView mav = handler.handleUnexpected(new NullPointerException("NPE"));

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getModel().get("message")).isEqualTo("Something went wrong while reconciling the files.");
        assertThat(mav.getModel().get("hint").toString()).contains("application log");
    }
}
