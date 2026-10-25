package com.demo.retailedge.inventory;

import jakarta.validation.ConstraintViolationException;

import com.demo.retailedge.cart.CartConflictException;
import com.demo.retailedge.cart.CartForbiddenException;
import com.demo.retailedge.cart.CartNotFoundException;
import com.demo.retailedge.cart.CartValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Converts inventory and request failures into safe, machine-readable API responses. */
@RestControllerAdvice
public class InventoryApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(InventoryApiExceptionHandler.class);

	/** Maps invalid domain or request values to HTTP 400. */
	@ExceptionHandler({ InventoryValidationException.class, CartValidationException.class,
			MethodArgumentNotValidException.class,
			HandlerMethodValidationException.class, ConstraintViolationException.class, BindException.class,
			HttpMessageNotReadableException.class, MissingServletRequestParameterException.class })
	public ResponseEntity<ApiErrorResponseDto> handleValidation(Exception exception) {
		return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
	}

	/** Maps inventory lookups outside the caller's store or for missing products to HTTP 404. */
	@ExceptionHandler({ InventoryNotFoundException.class, CartNotFoundException.class })
	public ResponseEntity<ApiErrorResponseDto> handleNotFound(RuntimeException exception) {
		String code = exception instanceof CartNotFoundException ? "CART_NOT_FOUND" : "INVENTORY_NOT_FOUND";
		return response(HttpStatus.NOT_FOUND, code, exception.getMessage());
	}

	/** Maps stale versions, duplicate records, and persistence constraint races to HTTP 409. */
	@ExceptionHandler({ InventoryConflictException.class, CartConflictException.class, DataIntegrityViolationException.class })
	public ResponseEntity<ApiErrorResponseDto> handleConflict(Exception exception) {
		return response(HttpStatus.CONFLICT, "INVENTORY_CONFLICT", "Inventory changed or already exists");
	}

	/** Maps store ownership failures to HTTP 403. */
	@ExceptionHandler({ StoreAccessDeniedException.class, CartForbiddenException.class, AccessDeniedException.class })
	public ResponseEntity<ApiErrorResponseDto> handleForbidden(Exception exception) {
		return response(HttpStatus.FORBIDDEN, "FORBIDDEN_RESOURCE", "Access to this resource is forbidden");
	}

	/** Returns a generic HTTP 500 without exposing implementation or database details. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponseDto> handleUnexpected(Exception exception) {
		LOGGER.atError().addKeyValue("event", "inventory_request_failed").log("Inventory request failed");
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed");
	}

	private ResponseEntity<ApiErrorResponseDto> response(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(ApiErrorResponseDto.of(code, message));
	}
}