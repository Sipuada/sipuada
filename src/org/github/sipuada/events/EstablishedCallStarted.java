package org.github.sipuada.events;

public class EstablishedCallStarted {

	private final String callId;

	public EstablishedCallStarted(String callId) {
		this.callId = callId;
	}

	public String getCallId() {
		return callId;
	}

}
