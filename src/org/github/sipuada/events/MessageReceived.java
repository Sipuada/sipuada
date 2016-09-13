package org.github.sipuada.events;

import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.Header;

public class MessageReceived {

	private final String callId;
	private final String remoteUser;
	private final String remoteDomain;
	private final String content;
	private final ContentTypeHeader contentTypeHeader;
	private final Header[] additionalHeaders;

	public MessageReceived(String callId, String remoteUser, String remoteDomain,
			String content, ContentTypeHeader contentTypeHeader, Header... additionalHeader) {
		this.callId = callId;
		this.remoteUser = remoteUser;
		this.remoteDomain = remoteDomain;
		this.content = content;
		this.contentTypeHeader = contentTypeHeader;
		this.additionalHeaders = additionalHeader;
	}

	public String getCallId() {
		return callId;
	}

	public String getRemoteUser() {
		return remoteUser;
	}

	public String getRemoteDomain() {
		return remoteDomain;
	}

	public String getContent() {
		return content;
	}

	public ContentTypeHeader getContentTypeHeader() {
		return contentTypeHeader;
	}

	public Header[] getAdditionalHeaders() {
		return additionalHeaders;
	}

}
