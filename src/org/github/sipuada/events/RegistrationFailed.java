package org.github.sipuada.events;

public class RegistrationFailed {

	private final String reason;

	public RegistrationFailed(String reason) {
		this.reason = reason;
	}

	public String getReason() {
		return reason;
	}

}
