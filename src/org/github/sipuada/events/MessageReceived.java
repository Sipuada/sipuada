package org.github.sipuada.events;

import android.javax.sip.Dialog;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.Header;

public class MessageReceived {

	private final String callId;
	private final Dialog dialog;
	private final String remoteUsername;
	private final String remoteHost;
	private final String content;
	private final ContentTypeHeader contentTypeHeader;
	private final Header[] headers;

	public MessageReceived(String callId, Dialog dialog, String remoteUsername, String remoteHost, String content, ContentTypeHeader contentTypeHeader, Header[] additionalHeaders) {
		this.callId = callId;
		this.dialog = dialog;
		this.remoteUsername = remoteUsername;
		this.remoteHost = remoteHost;
		this.content = content;
		this.contentTypeHeader = contentTypeHeader;
		this.headers = additionalHeaders;
	}

	public String getCallId() {
		return callId;
	}

	public Dialog getDialog() {
		return dialog;
	}
	
	public String getRemoteUsername() {
		return remoteUsername;
	}
	
	public String getRemoteHost() {
		return remoteHost;
	}

	public String getContent() {
		return content;
	}
	
	public ContentTypeHeader getContentTypeHeader() {
		return contentTypeHeader;
	}

	public Header[] getHeaders() {
		return headers;
	}

}
