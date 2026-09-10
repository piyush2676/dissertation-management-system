package com.dms.common;

import com.dms.allocation.CapacityExceededException;
import com.dms.storage.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException ex, Model model){
        log.warn("404: {}", ex.getMessage());
        model.addAttribute("reason",ex.getMessage());
        return "error/404";
    }
    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(InvalidStateTransitionException ex, Model model){
        log.warn("409: {}",ex.getMessage());
        model.addAttribute("reason",ex.getMessage());
        return "error/409";
    }

    @ExceptionHandler(CapacityExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String capacity(CapacityExceededException ex, Model model){
        log.warn("409 capacity: {} at {}/{}", ex.getSupervisorName(), ex.getTaken(), ex.getMax());
        model.addAttribute("reason", ex.getMessage());
        return "error/409";
    }

    @ExceptionHandler(StorageException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String storage(StorageException ex, Model model){
        log.warn("409 storage: {}", ex.getMessage());
        model.addAttribute("reason", ex.getMessage());
        return "error/409";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String illegalState(IllegalStateException ex, Model model){
        log.warn("409 state: {}", ex.getMessage());
        model.addAttribute("reason", ex.getMessage());
        return "error/409";
    }
}
