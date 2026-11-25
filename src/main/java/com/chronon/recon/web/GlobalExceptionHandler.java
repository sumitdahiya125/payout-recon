package com.chronon.recon.web;

import com.chronon.recon.exception.ReconciliationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates domain exceptions and static asset 404s into user-friendly error views.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ReconciliationException.class)
    public ModelAndView handleReconciliationException(ReconciliationException ex) {
        log.warn("Reconciliation business exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ModelAndView view = new ModelAndView("error");
        view.addObject("message", ex.getMessage());
        view.addObject("hint", ex.getHint());
        return view;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception ex) {
        log.error("Unexpected failure during reconciliation", ex);
        ModelAndView view = new ModelAndView("error");
        view.addObject("message", "Something went wrong while reconciling the files.");
        view.addObject("hint", "The details were written to the application log. Nothing was booked.");
        return view;
    }
}
