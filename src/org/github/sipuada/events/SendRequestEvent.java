package org.github.sipuada.events;

import org.github.sipuada.RequestVerb;

import android.javax.sip.message.Response;

public class SendRequestEvent {

	private RequestVerb requestVerb;
	private Response lastResponse;

	public SendRequestEvent(RequestVerb requestVerb, Response lastResponse) {
		this.requestVerb = requestVerb;
		this.lastResponse = lastResponse;
	}

}
