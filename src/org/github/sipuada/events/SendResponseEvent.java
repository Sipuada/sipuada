package org.github.sipuada.events;

import android.javax.sip.message.Request;

public class SendResponseEvent {

	private int responseCode;
	private Request responseRequest;
	
	public SendResponseEvent(int code, Request request) {
		responseCode = code;
		responseRequest = request;
	}

	public int getCode() {
		return responseCode;
	}

	public Request getRequest() {
		return responseRequest;
	}

}
