package org.github.sipuada.exceptions;

public class InternalJainSipException extends Error {

	private static final long serialVersionUID = -5852123403773924702L;
	private final String message;
	private final Throwable cause;

	public InternalJainSipException(String message, Throwable cause) {
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
