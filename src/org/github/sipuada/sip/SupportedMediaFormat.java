package org.github.sipuada.sip;

public class SupportedMediaFormat {

	public enum SupportedMediaType {
		AUDIO,
		VIDEO
	}
	
	private SupportedMediaType type;
	private int format;
	private String name;
	private int sampleRate;
	
	public SupportedMediaFormat(SupportedMediaType type, int format, String name, int sampleRate) {
		this.type = type;
		this.format = format;
		this.name = name;
		this.sampleRate = sampleRate;
	}
	
	public SupportedMediaType getType() {
		return type;
	}
	
	public void setType(SupportedMediaType type) {
		this.type = type;
	}
	
	public int getFormat() {
		return format;
	}
	
	public void setFormat(int format) {
		this.format = format;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public int getSampleRate() {
		return sampleRate;
	}
	
	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
	}
	
	public String getSDPField() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(getFormat())
			.append(" ")
			.append(getName())
			.append("/")
			.append(getSampleRate());
		return stringBuilder.toString();
	}
	
	public boolean equals(Object o) {
		SupportedMediaFormat otherSupportedMediaFormat = (SupportedMediaFormat) o;
		return getType() == otherSupportedMediaFormat.getType() &&
			   getFormat() == otherSupportedMediaFormat.getFormat() &&
			   getName() == otherSupportedMediaFormat.getName() && 
			   getSampleRate() == otherSupportedMediaFormat.getSampleRate();
	}
	
	public static SupportedMediaFormat parseMediaFormat(final String formatToParse, final SupportedMediaType type) {
		try {
			String[] split = formatToParse.replace("rtpmap:", "").split(" "); 
			int format = Integer.valueOf(split[0]);
			String name = split[1].split("/")[0];
			int sampleRate = Integer.valueOf(split[1].split("/")[1]);
			
			return new SupportedMediaFormat(type, format, name, sampleRate);
		} catch (Exception e) {
			return null;
		}
	}
	
	
	
}


