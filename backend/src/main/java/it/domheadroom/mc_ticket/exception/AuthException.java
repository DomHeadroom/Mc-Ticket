package it.domheadroom.mc_ticket.exception;

import org.springframework.http.HttpStatus;

/**
 * Eccezione lanciata dal layer di servizio per errori di autenticazione.
 * Il controller è responsabile di tradurla nella risposta HTTP appropriata.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
