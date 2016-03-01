package org.github.sipuada.events;

import android.javax.sip.Dialog;
import android.javax.sip.header.ContentTypeHeader;

public class InfoReceived {

	private final String callId;
	private final Dialog dialog;
	private final String content;
	private final ContentTypeHeader contentTypeHeader;

	public InfoReceived(String callId, Dialog dialog, String content, ContentTypeHeader contentTypeHeader) {
		this.callId = callId;
		this.dialog = dialog;
		this.content = content;
		this.contentTypeHeader = contentTypeHeader;
	}

	public String getCallId() {
		return callId;
	}

	public Dialog getDialog() {
		return dialog;
	}

	public String getContent() {
		return content;
	}
	
	public ContentTypeHeader getContentTypeHeader() {
		return contentTypeHeader;
	}

}
