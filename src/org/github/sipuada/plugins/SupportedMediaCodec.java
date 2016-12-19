package org.github.sipuada.plugins;

public interface SupportedMediaCodec {

	public String getEncoding();

	public int getType();

	public int getClockRate();

	public SupportedMediaType getMediaType();

	public boolean isEnabledByDefault();

	public SessionType getAllowedSessionType();

}
