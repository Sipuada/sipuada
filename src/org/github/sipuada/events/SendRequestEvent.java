package org.github.sipuada.events;

import org.github.sipuada.RequestVerb;

import android.javax.sip.message.Response;

public class SendRequestEvent {

	private RequestVerb requestVerb;
	private Response requestResponse;

	public SendRequestEvent(RequestVerb verb, Response response) {
		requestVerb = verb;
		requestResponse = response;
	}

	public RequestVerb getVerb() {
		return requestVerb;
	}

	public Response getResponse() {
		return requestResponse;
	}

}
