package org.github.sipuada.plugins;

import java.util.Locale;

public class MediaCodecInstance {

	private final String encoding;
	private final int type;
	private final int clockRate;
	private final SupportedMediaType mediaType;
	private final SessionType allowedSessionType;
	private boolean isEnabled;

	public MediaCodecInstance(SupportedMediaCodec mediaCodec) {
    	this.encoding = mediaCodec.getEncoding();
		this.type = mediaCodec.getType();
		this.clockRate = mediaCodec.getClockRate();
		this.mediaType = mediaCodec.getMediaType();
		this.allowedSessionType = mediaCodec.getAllowedSessionType();
		this.isEnabled = mediaCodec.isEnabledByDefault();
	}

	public String getEncoding() {
		return encoding;
	}

	public int getType() {
		return type;
	}

	public int getClockRate() {
		return clockRate;
	}

	public String getRtpmap() {
		return String.format(Locale.US, "%s/%d", encoding, clockRate);
	}

	public SupportedMediaType getMediaType() {
		return mediaType;
	}

	public SessionType getAllowedSessionType() {
		return allowedSessionType;
	}

	public boolean isEnabled() {
		return isEnabled;
	}

	public void setEnabled(boolean isEnabled) {
		this.isEnabled = isEnabled;
	}

}
