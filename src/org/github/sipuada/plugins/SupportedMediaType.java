package org.github.sipuada.plugins;

public enum SupportedMediaType {

    AUDIO("audio"),
//    MESSAGE("message"),
//    DATA("data"),
    VIDEO("video");

	private final String mediaTypeName;

	private SupportedMediaType(String mediaTypeName) {
        this.mediaTypeName = mediaTypeName;
    }

	@Override
    public String toString() {
        return mediaTypeName;
    }

	public static SupportedMediaType parseString(String mediaTypeName)
        throws IllegalArgumentException {
        if (AUDIO.toString().equals(mediaTypeName)) {
        	return AUDIO;
//        } else if (MESSAGE.toString().equals(mediaTypeName)) {
//        	return MESSAGE;
//        } else if (DATA.toString().equals(mediaTypeName)) {
//        	return DATA;
        } else if (VIDEO.toString().equals(mediaTypeName)) {
        	return VIDEO;
        }
        throw new IllegalArgumentException
    		(mediaTypeName + " is not a currently supported MediaType");
    }

}
