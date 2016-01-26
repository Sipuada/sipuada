package org.github.sipuada.events;

import org.github.sipuada.requester.SipRequestVerb;

import android.javax.sip.message.Response;

public class SendRequestEvent {

	private SipRequestVerb requestVerb;
	private Response requestResponse;

	public SendRequestEvent(SipRequestVerb verb, Response response) {
		requestVerb = verb;
		requestResponse = response;
	}

	public SipRequestVerb getVerb() {
		return requestVerb;
	}

	public Response getResponse() {
		return requestResponse;
	}

}
