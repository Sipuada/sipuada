package org.github.sipuada.events;

public class EstablishedCallFinished {

	private final String callId;

	public EstablishedCallFinished(String callId) {
		this.callId = callId;
	}

	public String getCallId() {
		return callId;
	}

}
