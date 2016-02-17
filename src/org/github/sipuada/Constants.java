package org.github.sipuada;

public class Constants {

	public enum Transport {
		UDP, TCP, TLS, UNKNOWN
	}

	public enum RequestMethod {
		REGISTER, OPTIONS, INVITE, CANCEL, BYE, ACK, UNKNOWN
	}
	
	public static RequestMethod getRequestMethod(String method) {
		try {
			return RequestMethod.valueOf(method.toUpperCase().trim());
		}
		catch (Exception exception) {
			return RequestMethod.UNKNOWN;
		}
	}

	public enum ResponseClass {
		PROVISIONAL, SUCCESS, REDIRECT, CLIENT_ERROR, SERVER_ERROR, GLOBAL_ERROR, UNKNOWN
	}
	
	public static ResponseClass getResponseClass(int statusCode) {
		if (statusCode <= 199) {
			return ResponseClass.PROVISIONAL;
		}
		else if (statusCode >= 200 && statusCode <= 299) {
			return ResponseClass.SUCCESS;
		}
		else if (statusCode >= 300 && statusCode <= 399) {
			return ResponseClass.REDIRECT;
		}
		else if (statusCode >= 400 && statusCode <= 499) {
			return ResponseClass.CLIENT_ERROR;
		}
		else if (statusCode >= 500 && statusCode <= 599) {
			return ResponseClass.SERVER_ERROR;
		}
		else if (statusCode >= 600 && statusCode <= 699) {
			return ResponseClass.GLOBAL_ERROR;
		}
		else {
			return ResponseClass.UNKNOWN;
		}
	}

}
