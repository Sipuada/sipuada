package org.github.sipuada.exceptions;

public class SipuadaException extends Error {

	private static final long serialVersionUID = -5852123403773924702L;
	private final String message;
	private final Throwable cause;

	public SipuadaException(String message, Throwable cause) {
		this.message = message;
		this.cause = cause;
	}

	public String getMessage() {
		return message;
	}

	public Throwable getCause() {
		return cause;
	}

}
